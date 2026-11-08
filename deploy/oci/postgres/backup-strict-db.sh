#!/bin/bash
# Daily logical backup of the production database "strict".
#
# The dump is one consistent pg_dump snapshot of that database. It is custom
# format, written to a temporary file in the automatic directory, and renamed
# into place only after the archive header and table-of-contents checks pass.
# Those checks are not a restore test. verify-strict-backup.sh does that.
#
# Retention runs only after this attempt has produced a verified local file
# and, when off-host upload is required, a verified upload. A failed attempt
# leaves every existing backup in place. The newest automatic backup is never
# deleted. Manual backups are never listed or deleted.
#
# Off-host upload is inactive unless /etc/strict/strict-backup.env sets
# STRICT_BACKUP_OFFHOST=required. A green local run does not mean an off-host
# copy exists.

set -euo pipefail

readonly AUTOMATIC_DIR_DEFAULT=/opt/strict/db-backup/automatic
readonly MANUAL_DIR=/opt/strict/db-backup/manual
readonly ENV_FILE=/etc/strict/strict-backup.env
readonly CONTAINER=strict-postgres
readonly DATABASE=strict
readonly DB_USER=strict_app
readonly RETENTION_DAYS=20
readonly RETENTION_SECONDS=$((RETENTION_DAYS * 24 * 60 * 60))
readonly MINIMUM_BYTES=1024
readonly SINGLE_PUT_LIMIT=$((100 * 1024 * 1024))
readonly REQUIRED_REGION=eu-frankfurt-1

AUTOMATIC_DIR=$AUTOMATIC_DIR_DEFAULT
LOG_FILE=$AUTOMATIC_DIR/backup.log
partial=""

log() {
    local line now
    now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    line="$now $*"
    printf '%s\n' "$line"
    if [ -d "$AUTOMATIC_DIR" ] && [ -w "$AUTOMATIC_DIR" ]; then
        printf '%s\n' "$line" >> "$LOG_FILE"
        chmod 0600 "$LOG_FILE" || true
    fi
}

fail() {
    log "result=failed stage=$1"
    if [ -n "$partial" ] && [ -f "$partial" ]; then
        rm -f -- "$partial"
    fi
    exit 1
}

valid_automatic_name() {
    [[ "$1" =~ ^strict-automatic-[0-9]{8}T[0-9]{6}Z\.dump$ ]]
}

valid_partial_name() {
    [[ "$1" =~ ^\.strict-automatic-[0-9]{8}T[0-9]{6}Z\.dump\.partial$ ]]
}

epoch_from_name() {
    local name stamp
    name=$1
    stamp=${name#strict-automatic-}
    stamp=${stamp%.dump}
    date -u -d "${stamp:0:4}-${stamp:4:2}-${stamp:6:2} ${stamp:9:2}:${stamp:11:2}:${stamp:13:2} UTC" +%s
}

name_from_epoch() {
    date -u -d "@$1" +strict-automatic-%Y%m%dT%H%M%SZ.dump
}

require_root() {
    if [ "$(id -u)" -ne 0 ]; then
        printf '%s\n' "result=failed stage=root"
        exit 1
    fi
}

require_automatic_dir() {
    if [ ! -d "$AUTOMATIC_DIR" ]; then
        install -d -m 0700 "$AUTOMATIC_DIR"
    fi
    chmod 0700 "$AUTOMATIC_DIR"
    local resolved
    resolved=$(readlink -f -- "$AUTOMATIC_DIR")
    if [ "$resolved" != "$AUTOMATIC_DIR_DEFAULT" ]; then
        printf '%s\n' "result=failed stage=automatic_dir"
        exit 1
    fi
    AUTOMATIC_DIR=$resolved
    LOG_FILE=$AUTOMATIC_DIR/backup.log
    if [ "$AUTOMATIC_DIR" = "$MANUAL_DIR" ]; then
        printf '%s\n' "result=failed stage=automatic_dir"
        exit 1
    fi
}

load_offhost_mode() {
    local mode owner line key value
    STRICT_BACKUP_OFFHOST=disabled
    STRICT_BACKUP_REGION=$REQUIRED_REGION
    STRICT_BACKUP_NAMESPACE=""
    STRICT_BACKUP_BUCKET=""
    if [ -f "$ENV_FILE" ]; then
        mode=$(stat -c %a "$ENV_FILE")
        owner=$(stat -c %u "$ENV_FILE")
        if [ "$owner" != "0" ] || [ "$mode" != "600" ]; then
            fail offhost_env_mode
        fi
        while IFS= read -r line || [ -n "$line" ]; do
            case "$line" in
                ""|\#*) continue ;;
            esac
            key=${line%%=*}
            value=${line#*=}
            case "$key" in
                STRICT_BACKUP_OFFHOST|STRICT_BACKUP_REGION|STRICT_BACKUP_NAMESPACE|STRICT_BACKUP_BUCKET) ;;
                *) fail offhost_env ;;
            esac
            if [ -n "$value" ] && ! [[ "$value" =~ ^[A-Za-z0-9._-]+$ ]]; then
                fail offhost_env
            fi
            printf -v "$key" '%s' "$value"
        done < "$ENV_FILE"
    fi
    case "${STRICT_BACKUP_OFFHOST}" in
        disabled|required) ;;
        *) fail offhost_mode ;;
    esac
    if [ "$STRICT_BACKUP_OFFHOST" = "required" ]; then
        if [ "$STRICT_BACKUP_REGION" != "$REQUIRED_REGION" ]; then
            fail offhost_region
        fi
        if [ -z "$STRICT_BACKUP_NAMESPACE" ] || [ -z "$STRICT_BACKUP_BUCKET" ]; then
            fail offhost_target
        fi
        if ! [[ "$STRICT_BACKUP_BUCKET" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{1,254}$ ]]; then
            fail offhost_target
        fi
        if ! command -v oci >/dev/null 2>&1; then
            fail offhost_cli
        fi
    fi
}

retention_now() {
    if [ "${SELF_TEST_ACTIVE:-0}" = "1" ]; then
        printf '%s\n' "$SELF_TEST_NOW"
    else
        date -u +%s
    fi
}

plan_retention() {
    local mode now cutoff base epoch newest_name newest_epoch
    local -a names=()
    local -a delete_names=()
    mode=$1
    now=$(retention_now)
    cutoff=$((now - RETENTION_SECONDS))
    newest_name=""
    newest_epoch=0

    local file
    for file in "$AUTOMATIC_DIR"/strict-automatic-*.dump; do
        [ -e "$file" ] || continue
        [ -f "$file" ] || continue
        base=$(basename -- "$file")
        if ! valid_automatic_name "$base"; then
            log "retention_ignored=$base"
            continue
        fi
        if [ "$(dirname -- "$(readlink -f -- "$file")")" != "$AUTOMATIC_DIR" ]; then
            fail retention_path
        fi
        epoch=$(epoch_from_name "$base")
        names+=("$base")
        if [ "$newest_name" = "" ] || [ "$epoch" -gt "$newest_epoch" ] || { [ "$epoch" -eq "$newest_epoch" ] && [[ "$base" > "$newest_name" ]]; }; then
            newest_name=$base
            newest_epoch=$epoch
        fi
    done

    local candidate
    for candidate in "${names[@]+"${names[@]}"}"; do
        [ -n "$candidate" ] || continue
        epoch=$(epoch_from_name "$candidate")
        if [ "$candidate" = "$newest_name" ]; then
            continue
        fi
        if [ "$epoch" -lt "$cutoff" ]; then
            delete_names+=("$candidate")
        fi
    done

    if [ "$newest_name" = "" ]; then
        log "retention_mode=$mode scope=automatic_only keep=none delete_count=0"
    else
        log "retention_mode=$mode scope=automatic_only keep=$newest_name delete_count=${#delete_names[@]}"
    fi
    local doomed
    for doomed in "${delete_names[@]+"${delete_names[@]}"}"; do
        [ -n "$doomed" ] || continue
        log "delete=$doomed"
    done

    if [ "$mode" = "dry-run" ]; then
        return 0
    fi

    for doomed in "${delete_names[@]+"${delete_names[@]}"}"; do
        [ -n "$doomed" ] || continue
        delete_automatic "$doomed"
    done
}

delete_automatic() {
    local base real dir
    base=$1
    valid_automatic_name "$base" || fail retention_name
    real=$(readlink -f -- "$AUTOMATIC_DIR/$base")
    dir=$(dirname -- "$real")
    if [ "$dir" != "$AUTOMATIC_DIR" ] || [ "$(basename -- "$real")" != "$base" ]; then
        fail retention_path
    fi
    if [[ "$real" == *"/manual/"* ]] || [ "$dir" = "$MANUAL_DIR" ]; then
        fail retention_path
    fi
    rm -f -- "$real"
}

remove_stale_partials() {
    local file base
    for file in "$AUTOMATIC_DIR"/.strict-automatic-*.dump.partial; do
        [ -e "$file" ] || continue
        [ -f "$file" ] || continue
        base=$(basename -- "$file")
        valid_partial_name "$base" || fail partial_name
        if [ "$(dirname -- "$(readlink -f -- "$file")")" != "$AUTOMATIC_DIR" ]; then
            fail partial_path
        fi
        rm -f -- "$file"
        log "removed_partial=$base"
    done
}

verify_archive() {
    local file toc table
    file=$1
    local magic
    magic=$(dd if="$file" bs=5 count=1 2>/dev/null || true)
    if [ "$magic" != "PGDMP" ]; then
        fail magic
    fi
    local bytes
    bytes=$(wc -c < "$file")
    if [ "$bytes" -lt "$MINIMUM_BYTES" ]; then
        fail size
    fi
    if ! toc=$(docker exec -i "$CONTAINER" pg_restore --list < "$file"); then
        fail toc
    fi
    for table in app_user external_identity auth_session flyway_schema_history; do
        if [[ "$toc" != *"TABLE DATA public ${table}"* ]]; then
            fail "toc_${table}"
        fi
    done
    printf '%s\n' "$bytes"
}

upload_offhost() {
    local file bytes object remote_bytes remote_md5 local_md5
    file=$1
    bytes=$2
    if [ "$bytes" -gt "$SINGLE_PUT_LIMIT" ]; then
        fail offhost_size
    fi
    object="automatic/$(basename -- "$file")"
    if ! oci os object put \
        --auth instance_principal \
        --region "$REQUIRED_REGION" \
        --namespace "$STRICT_BACKUP_NAMESPACE" \
        --bucket-name "$STRICT_BACKUP_BUCKET" \
        --name "$object" \
        --file "$file" \
        --force \
        --no-multipart \
        >/dev/null; then
        fail offhost_put
    fi
    remote_bytes=$(oci os object head \
        --auth instance_principal \
        --region "$REQUIRED_REGION" \
        --namespace "$STRICT_BACKUP_NAMESPACE" \
        --bucket-name "$STRICT_BACKUP_BUCKET" \
        --name "$object" \
        --query 'data."content-length"' \
        --raw-output)
    remote_md5=$(oci os object head \
        --auth instance_principal \
        --region "$REQUIRED_REGION" \
        --namespace "$STRICT_BACKUP_NAMESPACE" \
        --bucket-name "$STRICT_BACKUP_BUCKET" \
        --name "$object" \
        --query 'data."content-md5"' \
        --raw-output)
    local_md5=$(openssl dgst -md5 -binary "$file" | openssl base64 -A)
    remote_bytes=${remote_bytes//\"/}
    remote_md5=${remote_md5//\"/}
    remote_bytes=${remote_bytes//$'\r'/}
    remote_md5=${remote_md5//$'\r'/}
    if [ "$remote_bytes" != "$bytes" ] || [ "$remote_md5" != "$local_md5" ]; then
        fail offhost_verify
    fi
    log "offhost=verified object=$object bytes=$bytes"
}

run_backup() {
    local stamp final bytes
    umask 077
    remove_stale_partials
    stamp=$(date -u +%Y%m%dT%H%M%SZ)
    final="$AUTOMATIC_DIR/strict-automatic-${stamp}.dump"
    partial="$AUTOMATIC_DIR/.strict-automatic-${stamp}.dump.partial"
    if [ -e "$final" ] || [ -e "$partial" ]; then
        fail collision
    fi
    if ! docker exec "$CONTAINER" pg_dump \
        -U "$DB_USER" \
        --format=custom \
        --no-owner \
        --no-acl \
        --dbname="$DATABASE" > "$partial"; then
        fail dump
    fi
    chmod 0600 "$partial"
    bytes=$(verify_archive "$partial")
    mv -f -- "$partial" "$final"
    partial=""
    chmod 0600 "$final"
    log "local=verified file=$(basename -- "$final") bytes=$bytes database=$DATABASE format=custom"
    if [ "$STRICT_BACKUP_OFFHOST" = "required" ]; then
        upload_offhost "$final" "$bytes"
        log "full_success=yes"
    else
        log "offhost=disabled full_success=no"
    fi
    plan_retention delete
    log "result=verified file=$(basename -- "$final")"
}

self_test() {
    local work now old_exact old_plus newer only_old manual_file nested partial
    local SELF_TEST_ACTIVE=1
    local SELF_TEST_NOW
    work=$(mktemp -d)
    AUTOMATIC_DIR=$work/automatic
    mkdir -p "$AUTOMATIC_DIR/nested" "$work/manual"
    LOG_FILE=$work/backup.log
    now=$(date -u +%s)
    SELF_TEST_NOW=$now
    newer=$(name_from_epoch "$now")
    old_exact=$(name_from_epoch $((now - RETENTION_SECONDS)))
    old_plus=$(name_from_epoch $((now - RETENTION_SECONDS - 1)))
    only_old=$(name_from_epoch $((now - RETENTION_SECONDS - 86400)))
    : > "$AUTOMATIC_DIR/$newer"
    : > "$AUTOMATIC_DIR/$old_exact"
    : > "$AUTOMATIC_DIR/$old_plus"
    : > "$AUTOMATIC_DIR/strict-automatic-not-a-backup.dump"
    partial="$AUTOMATIC_DIR/.strict-automatic-${newer#strict-automatic-}.partial"
    : > "$partial"
    manual_file=$work/manual/strict-manual.dump
    : > "$manual_file"
    nested="$AUTOMATIC_DIR/nested/$old_plus"
    : > "$nested"

    plan_retention dry-run > "$work/dry.out"
    if [ ! -f "$AUTOMATIC_DIR/$old_plus" ] || [ ! -f "$manual_file" ] || [ ! -f "$nested" ] || [ ! -f "$partial" ]; then
        printf '%s\n' "self_test=failed stage=dry_run_deleted"
        exit 1
    fi
    if ! grep -q "delete=$old_plus" "$work/dry.out"; then
        printf '%s\n' "self_test=failed stage=expected_delete"
        exit 1
    fi
    if grep -q "delete=$old_exact" "$work/dry.out" || grep -q "delete=$newer" "$work/dry.out"; then
        printf '%s\n' "self_test=failed stage=unexpected_delete"
        exit 1
    fi
    if ! grep -q "keep=$newer" "$work/dry.out"; then
        printf '%s\n' "self_test=failed stage=keep"
        exit 1
    fi

    plan_retention delete > "$work/apply.out"
    if [ -e "$AUTOMATIC_DIR/$old_plus" ]; then
        printf '%s\n' "self_test=failed stage=apply"
        exit 1
    fi
    if [ ! -f "$AUTOMATIC_DIR/$newer" ] || [ ! -f "$AUTOMATIC_DIR/$old_exact" ] || [ ! -f "$AUTOMATIC_DIR/strict-automatic-not-a-backup.dump" ] || [ ! -f "$partial" ]; then
        printf '%s\n' "self_test=failed stage=kept_files"
        exit 1
    fi
    if [ ! -f "$manual_file" ] || [ ! -f "$nested" ]; then
        printf '%s\n' "self_test=failed stage=outside_files"
        exit 1
    fi

    rm -f -- "$AUTOMATIC_DIR/$newer" "$AUTOMATIC_DIR/$old_exact"
    : > "$AUTOMATIC_DIR/$only_old"
    plan_retention delete > "$work/only.out"
    if [ ! -f "$AUTOMATIC_DIR/$only_old" ]; then
        printf '%s\n' "self_test=failed stage=only_backup"
        exit 1
    fi
    if grep -q "delete=$only_old" "$work/only.out"; then
        printf '%s\n' "self_test=failed stage=only_backup_listed"
        exit 1
    fi
    rm -rf -- "$work"
    printf '%s\n' "self_test=ok"
}

main() {
    case "${1:-}" in
        --self-test)
            self_test
            ;;
        --retention-dry-run)
            unset SELF_TEST_ACTIVE SELF_TEST_NOW
            require_root
            require_automatic_dir
            exec 9>"$AUTOMATIC_DIR/backup.lock"
            if ! flock -n 9; then
                log "result=failed stage=lock"
                exit 3
            fi
            plan_retention dry-run
            ;;
        "")
            unset SELF_TEST_ACTIVE SELF_TEST_NOW
            require_root
            require_automatic_dir
            exec 9>"$AUTOMATIC_DIR/backup.lock"
            if ! flock -n 9; then
                log "result=failed stage=lock"
                exit 3
            fi
            load_offhost_mode
            run_backup
            ;;
        *)
            printf '%s\n' "result=failed stage=arguments"
            exit 2
            ;;
    esac
}

main "$@"
