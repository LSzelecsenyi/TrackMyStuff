#!/bin/bash
# Daily logical backup of the production database "strict".
#
# Flow under the exclusive lock:
#   1. Retry verified local dumps that do not yet have a confirmed upload.
#   2. pg_dump custom format into a temporary file.
#   3. Check the archive header, table of contents, and required tables.
#   4. Rename the temporary file into place. The dump is not modified again.
#   5. When /etc/strict/strict-backup.env sets STRICT_BACKUP_OFFHOST=required,
#      upload automatic/<filename> and require a matching HEAD.
#   6. Record a local receipt only after that HEAD matches.
#   7. Apply local retention. Skip it when any required upload failed.
#
# A local dump without a verified upload is not a full success. The receipt
# is not a backup. Manual files under /opt/strict/db-backup/manual are neve
# listed, uploaded, or deleted.
#
# Object Storage rejects an overwrite of an existing object. This script does
# not delete remote objects. The bucket lifecycle rule deletes automatic/
# objects. A successful OCI CLI exit is not treated as proof of the upload:
# --no-overwrite exits 0 when the name is already present and does not check
# bytes.
#
# Multipart upload is implemented and tested, and it stays off. OCI requires
# OBJECT_OVERWRITE for CreateMultipartUpload, UploadPart, and
# CommitMultipartUpload, including when the object name is new. This VM must
# not have that permission. New objects up to the PutObject limit of 50 GiB
# use one part, with Content-MD5 and SHA-256 checked on HEAD.

set -euo pipefail

readonly AUTOMATIC_DIR_DEFAULT=/opt/strict/db-backup/automatic
readonly MANUAL_DIR=/opt/strict/db-backup/manual
readonly CONTAINER=strict-postgres
readonly DATABASE=strict
readonly DB_USER=strict_app
readonly RETENTION_DAYS=20
readonly RETENTION_SECONDS=$((RETENTION_DAYS * 24 * 60 * 60))
readonly MINIMUM_BYTES=1024
readonly REQUIRED_REGION=eu-frankfurt-1
readonly REQUIRED_PREFIX=automatic/
readonly MAX_PENDING_UPLOADS=30

# Not readonly: --self-test points these at a temporary directory.
ENV_FILE=/etc/strict/strict-backup.env
AUTOMATIC_DIR=$AUTOMATIC_DIR_DEFAULT
LOG_FILE=$AUTOMATIC_DIR/backup.log
partial=""
OFFHOST_STAGE=""
RETRY_FAILED=0
STRICT_BACKUP_OFFHOST=disabled
STRICT_BACKUP_REGION=$REQUIRED_REGION
STRICT_BACKUP_NAMESPACE=""
STRICT_BACKUP_BUCKET=""
STRICT_BACKUP_PREFIX=$REQUIRED_PREFIX

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
    if [ -n "${partial:-}" ] && [ -f "$partial" ]; then
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

valid_receipt_partial_name() {
    [[ "$1" =~ ^\.strict-automatic-[0-9]{8}T[0-9]{6}Z\.dump\.uploaded\.partial$ ]]
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

# Non-root CLI tests set STRICT_BACKUP_CLI_TEST_DIR to a directory under /tmp.
# Root, including the production timer, ignores that variable.
cli_test_requested() {
    [ "$(id -u)" -ne 0 ] && [ -n "${STRICT_BACKUP_CLI_TEST_DIR:-}" ]
}

apply_cli_test_paths() {
    local resolved env_resolved
    resolved=$(readlink -f -- "$STRICT_BACKUP_CLI_TEST_DIR" 2>/dev/null || true)
    if [ -z "$resolved" ] || [ ! -d "$resolved" ]; then
        printf '%s\n' "result=failed stage=automatic_dir"
        exit 1
    fi
    case "$resolved" in
        /tmp/*|/var/tmp/*) ;;
        *)
            printf '%s\n' "result=failed stage=automatic_dir"
            exit 1
            ;;
    esac
    if [ "$resolved" = "$AUTOMATIC_DIR_DEFAULT" ] || [ "$resolved" = "$MANUAL_DIR" ]; then
        printf '%s\n' "result=failed stage=automatic_dir"
        exit 1
    fi
    chmod 0700 "$resolved" || {
        printf '%s\n' "result=failed stage=automatic_dir"
        exit 1
    }
    AUTOMATIC_DIR=$resolved
    LOG_FILE=$AUTOMATIC_DIR/backup.log
    if [ -n "${STRICT_BACKUP_CLI_TEST_ENV:-}" ]; then
        env_resolved=$(readlink -f -- "$STRICT_BACKUP_CLI_TEST_ENV" 2>/dev/null || true)
        if [ -z "$env_resolved" ] || [ ! -f "$env_resolved" ]; then
            printf '%s\n' "result=failed stage=offhost_env"
            exit 1
        fi
        case "$env_resolved" in
            /tmp/*|/var/tmp/*) ;;
            *)
                printf '%s\n' "result=failed stage=offhost_env"
                exit 1
                ;;
        esac
        ENV_FILE=$env_resolved
    else
        ENV_FILE=$AUTOMATIC_DIR/.cli-test-env-absent
    fi
}

require_root() {
    if [ "$(id -u)" -eq 0 ]; then
        return 0
    fi
    if [ -n "${STRICT_BACKUP_CLI_TEST_DIR:-}" ]; then
        return 0
    fi
    printf '%s\n' "result=failed stage=root"
    exit 1
}

require_automatic_dir() {
    if cli_test_requested; then
        apply_cli_test_paths
        return
    fi
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

acquire_backup_lock() {
    exec 9>"$AUTOMATIC_DIR/backup.lock"
    if ! flock -n 9; then
        log "result=failed stage=lock"
        return 3
    fi
}

env_owner() {
    stat -c %u "$1"
}

env_mode() {
    stat -c %a "$1"
}

oci_available() {
    command -v oci >/dev/null 2>&1
}

python_available() {
    command -v python3 >/dev/null 2>&1
}

openssl_available() {
    command -v openssl >/dev/null 2>&1
}

# Returns failure on purpose. CreateMultipartUpload, UploadPart, and
# CommitMultipartUpload require OBJECT_OVERWRITE. Do not enable this on the
# production VM unless that permission is an explicit, reviewed IAM change.
multipart_enabled() {
    return 1
}

multipart_part_mib() {
    printf '%s\n' 128
}

single_part_max_bytes() {
    printf '%s\n' $((50 * 1024 * 1024 * 1024))
}

load_offhost_mode() {
    local mode owner line key value
    STRICT_BACKUP_OFFHOST=disabled
    STRICT_BACKUP_REGION=$REQUIRED_REGION
    STRICT_BACKUP_NAMESPACE=""
    STRICT_BACKUP_BUCKET=""
    STRICT_BACKUP_PREFIX=$REQUIRED_PREFIX
    if [ -f "$ENV_FILE" ]; then
        mode=$(env_mode "$ENV_FILE")
        owner=$(env_owner "$ENV_FILE")
        if cli_test_requested; then
            if [ "$owner" != "$(id -u)" ] || [ "$mode" != "600" ]; then
                fail offhost_env_mode
            fi
        elif [ "$owner" != "0" ] || [ "$mode" != "600" ]; then
            fail offhost_env_mode
        fi
        while IFS= read -r line || [ -n "$line" ]; do
            line=${line%$'\r'}
            case "$line" in
                ""|\#*) continue ;;
            esac
            key=${line%%=*}
            value=${line#*=}
            case "$key" in
                STRICT_BACKUP_OFFHOST|STRICT_BACKUP_REGION|STRICT_BACKUP_NAMESPACE|STRICT_BACKUP_BUCKET|STRICT_BACKUP_PREFIX) ;;
                *) fail offhost_env ;;
            esac
            if [ "$key" = "STRICT_BACKUP_PREFIX" ]; then
                if [ "$value" != "$REQUIRED_PREFIX" ]; then
                    fail offhost_prefix
                fi
            elif [ -n "$value" ] && ! [[ "$value" =~ ^[A-Za-z0-9._-]+$ ]]; then
                fail offhost_env
            fi
            printf -v "$key" '%s' "$value"
        done < "$ENV_FILE"
    fi
    case "${STRICT_BACKUP_OFFHOST}" in
        disabled|required) ;;
        *) fail offhost_mode ;;
    esac
    if [ "$STRICT_BACKUP_PREFIX" != "$REQUIRED_PREFIX" ]; then
        fail offhost_prefix
    fi
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
        if ! [[ "$STRICT_BACKUP_NAMESPACE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{1,254}$ ]]; then
            fail offhost_target
        fi
        if ! oci_available; then
            fail offhost_cli
        fi
        if ! python_available; then
            fail offhost_python
        fi
        if ! openssl_available; then
            fail offhost_openssl
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

file_bytes() {
    local n
    n=$(wc -c < "$1")
    printf '%s\n' "${n//[[:space:]]/}"
}

file_md5_b64() {
    openssl dgst -md5 -binary "$1" | openssl base64 -A
}

file_sha256_b64() {
    openssl dgst -sha256 -binary "$1" | openssl base64 -A
}

file_sha256_hex() {
    openssl dgst -sha256 -hex "$1" | awk '{print $NF}'
}

json_value() {
    python3 - "$1" "$2" << 'PY'
import json, sys
path, key = sys.argv[1], sys.argv[2]
try:
    with open(path, encoding="utf-8") as handle:
        raw = handle.read().strip()
    if not raw:
        print("")
        raise SystemExit(0)
    data = json.loads(raw)
    if isinstance(data, dict) and key not in data and isinstance(data.get("data"), dict):
        data = data["data"]
    if not isinstance(data, dict):
        print("")
        raise SystemExit(0)
    value = data.get(key, "")
    if value is None:
        value = ""
    print(value)
except Exception:
    print("")
PY
}

# OCI opc-multipart-md5 / opc-multipart-sha256: hash each part, concatenate
# the raw digests in order, hash that, base64-encode it, append -partcount.
# This is not the hash of the whole file. Content-MD5 is absent for multipart
# objects and must not be compared to a whole-file MD5.
multipart_digest() {
    local file=$1 algo=$2 part_bytes=$3
    python3 - "$file" "$algo" "$part_bytes" << 'PY'
import base64, hashlib, sys
path, algo, part = sys.argv[1], sys.argv[2], int(sys.argv[3])
if algo not in ("md5", "sha256") or part < 1:
    raise SystemExit(1)
digests = []
with open(path, "rb") as handle:
    while True:
        chunk = handle.read(part)
        if not chunk:
            break
        digests.append(hashlib.new(algo, chunk).digest())
if not digests:
    digests.append(hashlib.new(algo, b"").digest())
outer = hashlib.new(algo)
for item in digests:
    outer.update(item)
print(base64.b64encode(outer.digest()).decode("ascii") + "-" + str(len(digests)))
PY
}

choose_upload_method() {
    local bytes=$1 part max
    part=$(( $(multipart_part_mib) * 1024 * 1024 ))
    max=$(single_part_max_bytes)
    if multipart_enabled && [ "$bytes" -gt "$part" ]; then
        printf 'multipart\n'
        return 0
    fi
    if [ "$bytes" -gt "$max" ]; then
        printf 'too_large\n'
        return 0
    fi
    printf 'single\n'
}

remote_object_key() {
    local base=$1
    valid_automatic_name "$base" || return 1
    [ "${STRICT_BACKUP_PREFIX:-}" = "$REQUIRED_PREFIX" ] || return 1
    printf 'automatic/%s\n' "$base"
}

resolve_backup_file() {
    local file=$1 real dir base
    [ -f "$file" ] || return 1
    real=$(readlink -f -- "$file")
    dir=$(dirname -- "$real")
    base=$(basename -- "$real")
    [ "$dir" = "$AUTOMATIC_DIR" ] || return 1
    valid_automatic_name "$base" || return 1
    printf '%s\n' "$real"
}

read_remote_header() {
    local header=$1
    REMOTE_BYTES=$(json_value "$header" content-length)
    REMOTE_MD5=$(json_value "$header" content-md5)
    REMOTE_SHA=$(json_value "$header" opc-content-sha256)
    REMOTE_META=$(json_value "$header" opc-meta-sha256)
    REMOTE_METHOD=$(json_value "$header" opc-meta-method)
    REMOTE_PARTMIB=$(json_value "$header" opc-meta-partmib)
    REMOTE_MP_MD5=$(json_value "$header" opc-multipart-md5)
    REMOTE_MP_SHA=$(json_value "$header" opc-multipart-sha256)
}

remote_matches() {
    local file=$1 expected_bytes=$2 header=$3
    local local_bytes local_md5 local_sha local_sha_hex part_bytes expected
    read_remote_header "$header"
    local_bytes=$(file_bytes "$file")
    local_md5=$(file_md5_b64 "$file")
    local_sha=$(file_sha256_b64 "$file")
    local_sha_hex=$(file_sha256_hex "$file")
    [ "$REMOTE_BYTES" = "$expected_bytes" ] || return 1
    [ "$REMOTE_BYTES" = "$local_bytes" ] || return 1
    if [ -n "$REMOTE_META" ] && [ "$REMOTE_META" != "$local_sha_hex" ]; then
        return 1
    fi
    if [ -n "$REMOTE_MP_MD5" ]; then
        part_bytes=$(( $(multipart_part_mib) * 1024 * 1024 ))
        if [ -n "$REMOTE_PARTMIB" ]; then
            [[ "$REMOTE_PARTMIB" =~ ^[0-9]+$ ]] || return 1
            part_bytes=$((REMOTE_PARTMIB * 1024 * 1024))
        fi
        expected=$(multipart_digest "$file" md5 "$part_bytes") || return 1
        [ "$REMOTE_MP_MD5" = "$expected" ] || return 1
        if [ -n "$REMOTE_MP_SHA" ]; then
            expected=$(multipart_digest "$file" sha256 "$part_bytes") || return 1
            [ "$REMOTE_MP_SHA" = "$expected" ] || return 1
        fi
        return 0
    fi
    [ -n "$REMOTE_MD5" ] || return 1
    [ "$REMOTE_MD5" = "$local_md5" ] || return 1
    if [ -n "$REMOTE_SHA" ] && [ "$REMOTE_SHA" != "$local_sha" ]; then
        return 1
    fi
    return 0
}

remote_matches_after_put() {
    local file=$1 expected_bytes=$2 header=$3 method=$4
    local local_sha local_sha_hex
    remote_matches "$file" "$expected_bytes" "$header" || return 1
    local_sha=$(file_sha256_b64 "$file")
    local_sha_hex=$(file_sha256_hex "$file")
    [ "$REMOTE_META" = "$local_sha_hex" ] || return 1
    if [ -n "$REMOTE_METHOD" ] && [ "$REMOTE_METHOD" != "$method" ]; then
        return 1
    fi
    if [ "$method" = "single" ]; then
        [ -z "$REMOTE_MP_MD5" ] || return 1
        [ "$REMOTE_SHA" = "$local_sha" ] || return 1
    elif [ "$method" = "multipart" ]; then
        [ -n "$REMOTE_MP_MD5" ] || return 1
    else
        return 1
    fi
    return 0
}

receipt_file() {
    printf '%s\n' "$AUTOMATIC_DIR/$1.uploaded"
}

write_receipt() {
    local base=$1 method=${2:-single}
    local dest tmp file sha md5 bytes
    valid_automatic_name "$base" || return 1
    [ "$STRICT_BACKUP_PREFIX" = "$REQUIRED_PREFIX" ] || return 1
    case "$method" in
        single|multipart) ;;
        *) return 1 ;;
    esac
    file="$AUTOMATIC_DIR/$base"
    [ -f "$file" ] || return 1
    dest=$(receipt_file "$base")
    tmp="$AUTOMATIC_DIR/.$base.uploaded.partial"
    bytes=$(file_bytes "$file")
    sha=$(file_sha256_hex "$file")
    md5=$(file_md5_b64 "$file")
    umask 077
    {
        printf 'object=automatic/%s\n' "$base"
        printf 'bytes=%s\n' "$bytes"
        printf 'md5=%s\n' "$md5"
        printf 'sha256=%s\n' "$sha"
        printf 'method=%s\n' "$method"
        printf 'verified_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$tmp"
    chmod 0600 "$tmp"
    mv -f -- "$tmp" "$dest"
    chmod 0600 "$dest"
}

valid_receipt() {
    local base=$1 path dir mode line bytes sha object method got_bytes got_sha
    valid_automatic_name "$base" || return 1
    path=$(receipt_file "$base")
    [ -f "$path" ] || return 1
    dir=$(dirname -- "$(readlink -f -- "$path")")
    [ "$dir" = "$AUTOMATIC_DIR" ] || return 1
    [ "$(basename -- "$path")" = "$base.uploaded" ] || return 1
    mode=$(stat -c %a "$path")
    [ "$mode" = "600" ] || return 1
    bytes=""
    sha=""
    object=""
    method=""
    while IFS= read -r line || [ -n "$line" ]; do
        line=${line%$'\r'}
        case "$line" in
            bytes=*) bytes=${line#bytes=} ;;
            sha256=*) sha=${line#sha256=} ;;
            object=*) object=${line#object=} ;;
            method=*) method=${line#method=} ;;
            verified_at=*|md5=*) ;;
            *) return 1 ;;
        esac
    done < "$path"
    [ "$object" = "automatic/$base" ] || return 1
    case "$method" in
        single|multipart) ;;
        *) return 1 ;;
    esac
    [ -f "$AUTOMATIC_DIR/$base" ] || return 1
    got_bytes=$(file_bytes "$AUTOMATIC_DIR/$base")
    got_sha=$(file_sha256_hex "$AUTOMATIC_DIR/$base")
    [ "$bytes" = "$got_bytes" ] || return 1
    [ "$sha" = "$got_sha" ] || return 1
    return 0
}

upload_is_pending() {
    [ "${STRICT_BACKUP_OFFHOST:-disabled}" = "required" ] || return 1
    if valid_receipt "$1"; then
        return 1
    fi
    return 0
}

plan_retention() {
    local mode now cutoff base epoch newest_name newest_epoch pending
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
        pending=0
        if upload_is_pending "$candidate"; then
            pending=1
            log "retention_pending=$candidate"
        fi
        if [ "$candidate" = "$newest_name" ] || [ "$pending" -eq 1 ]; then
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
    local base real dir receipt rreal rdi
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
    receipt=$(receipt_file "$base")
    if [ -f "$receipt" ]; then
        rreal=$(readlink -f -- "$receipt")
        rdir=$(dirname -- "$rreal")
        if [ "$rdir" != "$AUTOMATIC_DIR" ] || [ "$(basename -- "$rreal")" != "$base.uploaded" ]; then
            fail retention_path
        fi
        rm -f -- "$rreal"
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
    for file in "$AUTOMATIC_DIR"/.strict-automatic-*.dump.uploaded.partial; do
        [ -e "$file" ] || continue
        [ -f "$file" ] || continue
        base=$(basename -- "$file")
        valid_receipt_partial_name "$base" || fail partial_name
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
    bytes=$(file_bytes "$file")
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

head_object() {
    local object=$1 header=$2 err=$3
    if oci os object head \
        --auth instance_principal \
        --region "$REQUIRED_REGION" \
        --read-timeout 120 \
        --max-retries 2 \
        --namespace-name "$STRICT_BACKUP_NAMESPACE" \
        --bucket-name "$STRICT_BACKUP_BUCKET" \
        --name "$object" \
        >"$header" 2>"$err"; then
        return 0
    fi
    if grep -q 'ObjectNotFound' "$err" || grep -E -q '"status": *404' "$err"; then
        return 2
    fi
    return 1
}

put_object() {
    local object=$1 file=$2 method=$3
    local sha_hex sha_b64 md5_b64 meta part_mib err out rc
    sha_hex=$(file_sha256_hex "$file")
    sha_b64=$(file_sha256_b64 "$file")
    md5_b64=$(file_md5_b64 "$file")
    err=$(mktemp)
    out=$(mktemp)
    rc=0
    if [ "$method" = "single" ]; then
        meta=$(printf '{"sha256":"%s","method":"single"}' "$sha_hex")
        oci os object put \
            --auth instance_principal \
            --region "$REQUIRED_REGION" \
            --read-timeout 3600 \
            --max-retries 2 \
            --namespace-name "$STRICT_BACKUP_NAMESPACE" \
            --bucket-name "$STRICT_BACKUP_BUCKET" \
            --name "$object" \
            --file "$file" \
            --no-overwrite \
            --no-multipart \
            --content-md5 "$md5_b64" \
            --opc-checksum-algorithm SHA256 \
            --opc-content-sha256 "$sha_b64" \
            --metadata "$meta" \
            >"$out" 2>"$err" || rc=$?
    elif [ "$method" = "multipart" ]; then
        part_mib=$(multipart_part_mib)
        meta=$(printf '{"sha256":"%s","method":"multipart","partmib":"%s"}' "$sha_hex" "$part_mib")
        oci os object put \
            --auth instance_principal \
            --region "$REQUIRED_REGION" \
            --read-timeout 3600 \
            --max-retries 2 \
            --namespace-name "$STRICT_BACKUP_NAMESPACE" \
            --bucket-name "$STRICT_BACKUP_BUCKET" \
            --name "$object" \
            --file "$file" \
            --no-overwrite \
            --part-size "$part_mib" \
            --opc-checksum-algorithm SHA256 \
            --metadata "$meta" \
            >"$out" 2>"$err" || rc=$?
    else
        rm -f -- "$err" "$out"
        return 1
    fi
    rm -f -- "$err" "$out"
    return "$rc"
}

upload_offhost() {
    local requested=$1
    local file base object bytes method header err head_rc
    OFFHOST_STAGE=offhost_put
    file=$(resolve_backup_file "$requested") || {
        OFFHOST_STAGE=offhost_path
        return 1
    }
    base=$(basename -- "$file")
    if ! object=$(remote_object_key "$base"); then
        OFFHOST_STAGE=offhost_prefix
        return 1
    fi
    bytes=$(file_bytes "$file")
    if [ "$bytes" -lt "$MINIMUM_BYTES" ]; then
        OFFHOST_STAGE=offhost_size
        return 1
    fi
    method=$(choose_upload_method "$bytes")
    if [ "$method" = "too_large" ]; then
        log "offhost_limit=put_object bytes=$bytes max=$(single_part_max_bytes) multipart=blocked permission=OBJECT_OVERWRITE"
        OFFHOST_STAGE=offhost_size
        return 1
    fi
    header=$(mktemp)
    err=$(mktemp)
    head_rc=0
    head_object "$object" "$header" "$err" || head_rc=$?
    if [ "$head_rc" -eq 0 ]; then
        if [ -z "$(json_value "$header" content-length)" ]; then
            rm -f -- "$header" "$err"
            OFFHOST_STAGE=offhost_head
            return 1
        fi
        if remote_matches "$file" "$bytes" "$header"; then
            rm -f -- "$header" "$err"
            write_receipt "$base" "$method" || {
                OFFHOST_STAGE=offhost_receipt
                return 1
            }
            log "offhost=already_verified object=$object bytes=$bytes method=$method"
            return 0
        fi
        rm -f -- "$header" "$err"
        log "offhost_mismatch object=$object bytes=$bytes"
        OFFHOST_STAGE=offhost_mismatch
        return 1
    elif [ "$head_rc" -ne 2 ]; then
        rm -f -- "$header" "$err"
        OFFHOST_STAGE=offhost_head
        return 1
    fi
    rm -f -- "$header" "$err"
    put_object "$object" "$file" "$method" || true
    header=$(mktemp)
    err=$(mktemp)
    head_rc=0
    head_object "$object" "$header" "$err" || head_rc=$?
    if [ "$head_rc" -eq 2 ]; then
        rm -f -- "$header" "$err"
        OFFHOST_STAGE=offhost_put
        return 1
    fi
    if [ "$head_rc" -ne 0 ]; then
        rm -f -- "$header" "$err"
        OFFHOST_STAGE=offhost_head
        return 1
    fi
    if [ -z "$(json_value "$header" content-length)" ]; then
        rm -f -- "$header" "$err"
        OFFHOST_STAGE=offhost_head
        return 1
    fi
    if ! remote_matches_after_put "$file" "$bytes" "$header" "$method"; then
        rm -f -- "$header" "$err"
        log "offhost_verify_failed object=$object bytes=$bytes method=$method"
        OFFHOST_STAGE=offhost_verify
        return 1
    fi
    rm -f -- "$header" "$err"
    write_receipt "$base" "$method" || {
        OFFHOST_STAGE=offhost_receipt
        return 1
    }
    log "offhost=verified object=$object bytes=$bytes method=$method"
    return 0
}

list_pending_basenames() {
    local file base epoch
    local -a lines=()
    for file in "$AUTOMATIC_DIR"/strict-automatic-*.dump; do
        [ -e "$file" ] || continue
        [ -f "$file" ] || continue
        base=$(basename -- "$file")
        valid_automatic_name "$base" || continue
        if [ "$(dirname -- "$(readlink -f -- "$file")")" != "$AUTOMATIC_DIR" ]; then
            continue
        fi
        if valid_receipt "$base"; then
            continue
        fi
        epoch=$(epoch_from_name "$base")
        lines+=("$epoch $base")
    done
    if [ "${#lines[@]}" -eq 0 ]; then
        return 0
    fi
    printf '%s\n' "${lines[@]}" | sort -n | awk '{print $2}'
}

retry_pending_uploads() {
    local -a pending=()
    local base index
    mapfile -t pending < <(list_pending_basenames || true)
    RETRY_FAILED=0
    index=0
    for base in "${pending[@]+"${pending[@]}"}"; do
        [ -n "$base" ] || continue
        index=$((index + 1))
        if [ "$index" -gt "$MAX_PENDING_UPLOADS" ]; then
            log "retry_deferred=$base"
            RETRY_FAILED=1
            continue
        fi
        if ! upload_offhost "$AUTOMATIC_DIR/$base"; then
            log "retry_failed file=$base stage=${OFFHOST_STAGE:-offhost_put}"
            RETRY_FAILED=1
        else
            log "retry_verified file=$base"
        fi
    done
}

run_backup() {
    local stamp final bytes base
    umask 077
    RETRY_FAILED=0
    if [ "$STRICT_BACKUP_OFFHOST" = "required" ]; then
        retry_pending_uploads
    fi
    remove_stale_partials
    stamp=$(date -u +%Y%m%dT%H%M%SZ)
    base="strict-automatic-${stamp}.dump"
    final="$AUTOMATIC_DIR/$base"
    partial="$AUTOMATIC_DIR/.$base.partial"
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
    log "local=verified file=$base bytes=$bytes database=$DATABASE format=custom"
    if [ "$STRICT_BACKUP_OFFHOST" = "required" ]; then
        if ! upload_offhost "$final"; then
            log "local_success=yes offhost_success=no full_success=no"
            log "result=failed stage=${OFFHOST_STAGE:-offhost_put} file=$base"
            return 1
        fi
        if [ "$RETRY_FAILED" -ne 0 ]; then
            log "local_success=yes offhost_success=no full_success=no"
            log "result=failed stage=offhost_pending file=$base"
            return 1
        fi
        log "local_success=yes offhost_success=yes full_success=yes"
    else
        log "local_success=yes offhost_success=disabled full_success=no"
    fi
    plan_retention delete
    log "result=verified file=$base"
}

self_test_lock() {
    local dir holder rc _try
    dir=$(mktemp -d)
    (
        AUTOMATIC_DIR=${dir}
        LOG_FILE=$dir/holder.log
        acquire_backup_lock
        touch "$dir/ready"
        sleep 5
    ) >"$dir/holder.log" 2>&1 &
    holder=$!
    for _try in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
        [ -f "$dir/ready" ] && break
        sleep 0.1
    done
    if [ ! -f "$dir/ready" ]; then
        kill "$holder" >/dev/null 2>&1 || true
        wait "$holder" >/dev/null 2>&1 || true
        rm -rf -- "$dir"
        printf '%s\n' "self_test=failed stage=lock_holder"
        exit 1
    fi
    rc=0
    (
        AUTOMATIC_DIR=${dir}
        LOG_FILE=$dir/waiter.log
        acquire_backup_lock
    ) >"$dir/waiter.log" 2>&1 || rc=$?
    kill "$holder" >/dev/null 2>&1 || true
    wait "$holder" >/dev/null 2>&1 || true
    rm -rf -- "$dir"
    if [ "$rc" -ne 3 ]; then
        printf '%s\n' "self_test=failed stage=lock"
        exit 1
    fi
}

self_test_digests() {
    local work got
    if ! python_available; then
        printf '%s\n' "self_test=failed stage=python3"
        exit 1
    fi
    work=$(mktemp -d)
    printf 'abcdefghij' > "$work/vector.bin"
    got=$(multipart_digest "$work/vector.bin" md5 4)
    if [ "$got" != "RG/rpMG1zHrZO/TUSg42rA==-3" ]; then
        printf '%s\n' "self_test=failed stage=multipart_md5"
        exit 1
    fi
    got=$(multipart_digest "$work/vector.bin" sha256 4)
    if [ "$got" != "Obym+jYZ9n7k1vjfUUkVWFYPQpkgWT9/ZJEZuj8oXtk=-3" ]; then
        printf '%s\n' "self_test=failed stage=multipart_sha256"
        exit 1
    fi
    rm -rf -- "$work"
}

self_test_remote_checksums() {
    local work file header whole_md5 whole_sha whole_hex part_md5
    work=$(mktemp -d)
    file=$work/sample.bin
    header=$work/head.json
    printf 'abcdefghij' > "$file"
    # Pad so the sample is a normal file. Checksums below are of that payload.
    whole_md5=$(file_md5_b64 "$file")
    whole_sha=$(file_sha256_b64 "$file")
    whole_hex=$(file_sha256_hex "$file")
    cat > "$header" << EOF
{
  "content-length": "10",
  "content-md5": "$whole_md5",
  "etag": "etag-is-not-an-md5",
  "opc-content-sha256": "$whole_sha",
  "opc-meta-sha256": "$whole_hex",
  "opc-meta-method": "single"
}
EOF
    if ! remote_matches "$file" 10 "$header"; then
        printf '%s\n' "self_test=failed stage=single_match"
        exit 1
    fi
    if ! remote_matches_after_put "$file" 10 "$header" single; then
        printf '%s\n' "self_test=failed stage=single_after_put"
        exit 1
    fi
    cat > "$header" << EOF
{
  "content-length": "10",
  "content-md5": "AAAAAAAAAAAAAAAAAAAAAA==",
  "etag": "$whole_md5",
  "opc-content-sha256": "$whole_sha"
}
EOF
    if remote_matches "$file" 10 "$header"; then
        printf '%s\n' "self_test=failed stage=single_md5_mismatch"
        exit 1
    fi
    part_md5=$(multipart_digest "$file" md5 4)
    if [ "$part_md5" = "$whole_md5" ]; then
        printf '%s\n' "self_test=failed stage=multipart_equals_whole_md5"
        exit 1
    fi
    cat > "$header" << EOF
{
  "content-length": "10",
  "content-md5": "$whole_md5",
  "opc-multipart-md5": "AAAAAAAAAAAAAAAAAAAAAA==-1",
  "opc-meta-sha256": "$whole_hex"
}
EOF
    if remote_matches "$file" 10 "$header"; then
        printf '%s\n' "self_test=failed stage=multipart_mismatch_ignored"
        exit 1
    fi
    rm -rf -- "$work"
}

self_test_pending_retention() {
    local work now keep pending doomed exact manual_file
    local SELF_TEST_ACTIVE=1
    local SELF_TEST_NOW
    work=$(mktemp -d)
    AUTOMATIC_DIR=$work/automatic
    mkdir -p "$AUTOMATIC_DIR" "$work/manual"
    LOG_FILE=$work/backup.log
    STRICT_BACKUP_OFFHOST=required
    STRICT_BACKUP_PREFIX=$REQUIRED_PREFIX
    now=$(date -u +%s)
    SELF_TEST_NOW=$now
    keep=$(name_from_epoch "$now")
    pending=$(name_from_epoch $((now - RETENTION_SECONDS - 5)))
    doomed=$(name_from_epoch $((now - RETENTION_SECONDS - 10)))
    exact=$(name_from_epoch $((now - RETENTION_SECONDS)))
    : > "$AUTOMATIC_DIR/$keep"
    : > "$AUTOMATIC_DIR/$pending"
    : > "$AUTOMATIC_DIR/$doomed"
    : > "$AUTOMATIC_DIR/$exact"
    write_receipt "$doomed" single
    write_receipt "$exact" single
    manual_file=$work/manual/strict-anna-test-cleanup-20261108T190018Z.dump
    : > "$manual_file"
    plan_retention dry-run > "$work/dry.out"
    if ! grep -q "retention_pending=$pending" "$work/dry.out"; then
        printf '%s\n' "self_test=failed stage=pending_listed"
        exit 1
    fi
    if ! grep -q "delete=$doomed" "$work/dry.out"; then
        printf '%s\n' "self_test=failed stage=receipt_delete"
        exit 1
    fi
    if grep -q "delete=$pending" "$work/dry.out" || grep -q "delete=$keep" "$work/dry.out" || grep -q "delete=$exact" "$work/dry.out"; then
        printf '%s\n' "self_test=failed stage=pending_boundary"
        exit 1
    fi
    plan_retention delete > "$work/apply.out"
    if [ ! -f "$AUTOMATIC_DIR/$pending" ] || [ ! -f "$AUTOMATIC_DIR/$keep" ] || [ ! -f "$AUTOMATIC_DIR/$exact" ]; then
        printf '%s\n' "self_test=failed stage=pending_kept"
        exit 1
    fi
    if [ -e "$AUTOMATIC_DIR/$doomed" ] || [ -e "$AUTOMATIC_DIR/$doomed.uploaded" ]; then
        printf '%s\n' "self_test=failed stage=receipt_removed"
        exit 1
    fi
    if [ ! -f "$manual_file" ]; then
        printf '%s\n' "self_test=failed stage=manual_deleted"
        exit 1
    fi
    rm -rf -- "$work"
    STRICT_BACKUP_OFFHOST=disabled
}

self_test() {
    local work now old_exact old_plus newer only_old manual_file nested partial
    local SELF_TEST_ACTIVE=1
    local SELF_TEST_NOW
    STRICT_BACKUP_OFFHOST=disabled
    STRICT_BACKUP_PREFIX=$REQUIRED_PREFIX
    self_test_lock
    self_test_digests
    self_test_remote_checksums
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
    self_test_pending_retention
    printf '%s\n' "self_test=ok"
}

main() {
    case "${1:-}" in
        --self-test)
            if [ "$#" -ne 1 ]; then
                printf '%s\n' "result=failed stage=arguments"
                exit 2
            fi
            self_test
            ;;
        --retention-dry-run)
            if [ "$#" -ne 1 ]; then
                printf '%s\n' "result=failed stage=arguments"
                exit 2
            fi
            unset SELF_TEST_ACTIVE SELF_TEST_NOW
            require_root
            require_automatic_dir
            if ! acquire_backup_lock; then
                exit 3
            fi
            load_offhost_mode
            plan_retention dry-run
            ;;
        "")
            if [ "$#" -ne 0 ]; then
                printf '%s\n' "result=failed stage=arguments"
                exit 2
            fi
            unset SELF_TEST_ACTIVE SELF_TEST_NOW
            require_root
            require_automatic_dir
            if ! acquire_backup_lock; then
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

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
