#!/bin/bash
# Restore one Strict dump into a throwaway PostgreSQL container.
# This never mounts the production volume and never restores into the
# production database. Archive listing is not a substitute for this script.
#
# A local file argument must already be an automatic or manual dump.
# --object downloads one automatic/ object, checks its size and checksum,
# and restores that copy. The download is not written into the backup
# directories and is removed before the script exits.

set -euo pipefail

SOURCE_DIR=$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")
# shellcheck source=backup-strict-db.sh
source "$SOURCE_DIR/backup-strict-db.sh"

readonly TEST_CONTAINER=strict-restore-test
readonly TEST_VOLUME=strict_restore_test_data
readonly PROD_VOLUME=strict_postgres_data

downloaded=""

fail() {
    printf '%s\n' "result=failed stage=$1"
    cleanup
    exit 1
}

assert_names() {
    if [ "$TEST_CONTAINER" = "$CONTAINER" ] || [ "$TEST_VOLUME" = "$PROD_VOLUME" ]; then
        printf '%s\n' "result=failed stage=names"
        exit 1
    fi
}

cleanup() {
    local real mounts
    if [ -n "${downloaded:-}" ]; then
        if [ -e "$downloaded" ]; then
            real=$(readlink -f -- "$downloaded")
            case "$real" in
                /tmp/strict-restore.*.dump) rm -f -- "$real" ;;
                *)
                    printf '%s\n' "result=failed stage=cleanup_download"
                    exit 1
                    ;;
            esac
        fi
        downloaded=""
    fi
    if ! command -v docker >/dev/null 2>&1; then
        return 0
    fi
    if docker inspect "$TEST_CONTAINER" >/dev/null 2>&1; then
        if [ "$(docker inspect -f '{{.Name}}' "$TEST_CONTAINER")" != "/$TEST_CONTAINER" ]; then
            printf '%s\n' "result=failed stage=cleanup_name"
            exit 1
        fi
        mounts=$(docker inspect -f '{{range .Mounts}}{{.Name}} {{end}}' "$TEST_CONTAINER")
        case " $mounts " in
            *" $PROD_VOLUME "*)
                printf '%s\n' "result=failed stage=prod_volume_attached"
                exit 1
                ;;
        esac
        docker rm -f "$TEST_CONTAINER" >/dev/null
    fi
    if [ "$TEST_VOLUME" = "$PROD_VOLUME" ]; then
        printf '%s\n' "result=failed stage=cleanup_volume"
        exit 1
    fi
    if docker volume inspect "$TEST_VOLUME" >/dev/null 2>&1; then
        docker volume rm "$TEST_VOLUME" >/dev/null
    fi
}

valid_object_key() {
    [[ "$1" =~ ^automatic/strict-automatic-[0-9]{8}T[0-9]{6}Z\.dump$ ]]
}

classify_dump() {
    local dir=$1 base=$2
    if [ "$dir" = "$AUTOMATIC_DIR_DEFAULT" ]; then
        [[ "$base" =~ ^strict-automatic-[0-9]{8}T[0-9]{6}Z\.dump$ ]]
    elif [ "$dir" = "$MANUAL_DIR" ]; then
        [[ "$base" =~ ^strict-[A-Za-z0-9._-]+\.dump$ ]]
    else
        return 1
    fi
}

accept_dump() {
    local resolved dir base
    resolved=$(readlink -f -- "$1")
    dir=$(dirname -- "$resolved")
    base=$(basename -- "$resolved")
    classify_dump "$dir" "$base" || return 1
    [ -f "$resolved" ] || return 1
    printf '%s\n' "$resolved"
}

require_restore_tools() {
    if ! oci_available || ! python_available || ! openssl_available; then
        fail offhost_cli
    fi
    if [ "$STRICT_BACKUP_REGION" != "$REQUIRED_REGION" ]; then
        fail offhost_region
    fi
    if [ "$STRICT_BACKUP_PREFIX" != "$REQUIRED_PREFIX" ]; then
        fail offhost_prefix
    fi
    if [ -z "$STRICT_BACKUP_NAMESPACE" ] || [ -z "$STRICT_BACKUP_BUCKET" ]; then
        fail offhost_target
    fi
}

download_object() {
    local object=$1 header err
    valid_object_key "$object" || fail object_name
    load_offhost_mode
    require_restore_tools
    header=$(mktemp)
    err=$(mktemp)
    downloaded=$(mktemp /tmp/strict-restore.XXXXXXXX.dump)
    chmod 0600 "$downloaded"
    if ! oci os object head \
        --auth instance_principal \
        --region "$REQUIRED_REGION" \
        --read-timeout 120 \
        --max-retries 2 \
        --namespace-name "$STRICT_BACKUP_NAMESPACE" \
        --bucket-name "$STRICT_BACKUP_BUCKET" \
        --name "$object" \
        >"$header" 2>"$err"; then
        rm -f -- "$header" "$err"
        fail offhost_head
    fi
    if ! oci os object get \
        --auth instance_principal \
        --region "$REQUIRED_REGION" \
        --read-timeout 3600 \
        --max-retries 2 \
        --namespace-name "$STRICT_BACKUP_NAMESPACE" \
        --bucket-name "$STRICT_BACKUP_BUCKET" \
        --name "$object" \
        --file "$downloaded" \
        --no-multipart \
        >/dev/null 2>"$err"; then
        rm -f -- "$header" "$err"
        fail offhost_get
    fi
    local bytes
    bytes=$(file_bytes "$downloaded")
    if ! remote_matches "$downloaded" "$bytes" "$header"; then
        rm -f -- "$header" "$err"
        fail offhost_verify
    fi
    local magic
    magic=$(dd if="$downloaded" bs=5 count=1 2>/dev/null || true)
    rm -f -- "$header" "$err"
    if [ "$magic" != "PGDMP" ]; then
        fail magic
    fi
}

restore_dump() {
    local dump=$1
    local image ready restore_err table count
    if docker inspect "$TEST_CONTAINER" >/dev/null 2>&1; then
        fail test_container_exists
    fi
    if docker volume inspect "$TEST_VOLUME" >/dev/null 2>&1; then
        fail test_volume_exists
    fi
    image=$(docker inspect "$CONTAINER" --format '{{.Image}}')
    if [ -z "$image" ]; then
        fail image
    fi
    docker run -d \
        --name strict-restore-test \
        --network none \
        -e POSTGRES_HOST_AUTH_METHOD=trust \
        -e POSTGRES_PASSWORD=restore-test-not-production \
        -e POSTGRES_USER=restore_check \
        -e POSTGRES_DB=strict \
        -v strict_restore_test_data:/var/lib/postgresql/data \
        "$image" >/dev/null
    ready=0
    for _ in $(seq 1 30); do
        if docker exec strict-restore-test pg_isready -U restore_check -d strict >/dev/null 2>&1; then
            ready=1
            break
        fi
        sleep 1
    done
    if [ "$ready" -ne 1 ]; then
        fail ready
    fi
    install -d -m 0700 "$AUTOMATIC_DIR_DEFAULT"
    restore_err=$AUTOMATIC_DIR_DEFAULT/restore-last.err
    : > "$restore_err"
    chmod 0600 "$restore_err"
    if ! docker exec -i strict-restore-test pg_restore \
        -U restore_check \
        -d strict \
        --no-owner \
        --no-acl \
        --exit-on-error \
        --single-transaction \
        < "$dump" 2>"$restore_err"; then
        fail restore
    fi
    : > "$restore_err"
    for table in app_user external_identity auth_session play_subscription flyway_schema_history; do
        count=$(docker exec strict-restore-test psql -U restore_check -d strict -tA \
            -c "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname = '$table';") || fail schema
        if [ "$count" != "1" ]; then
            printf '%s\n' "$table=missing"
            fail schema
        fi
        printf '%s\n' "$table=present"
    done
    count=$(docker exec strict-restore-test psql -U restore_check -d strict -tA \
        -c "SELECT count(*) FROM flyway_schema_history WHERE success;") || fail flyway
    if ! [[ "$count" =~ ^[0-9]+$ ]] || [ "$count" -lt 1 ]; then
        fail flyway
    fi
    printf '%s\n' "flyway_success_count=$count"
    printf '%s\n' "result=restored file=$(basename -- "$dump")"
}

self_test() {
    local work file header whole_md5 whole_sha
    if ! valid_object_key "automatic/strict-automatic-20261109T031500Z.dump"; then
        printf '%s\n' "self_test=failed stage=object_key"
        exit 1
    fi
    if valid_object_key "automatic/../strict-automatic-20261109T031500Z.dump" \
        || valid_object_key "manual/strict-automatic-20261109T031500Z.dump" \
        || valid_object_key "automatic/strict-anna-test-cleanup-20261108T190018Z.dump" \
        || valid_object_key "automatic/connection-test.txt" \
        || valid_object_key "strict-automatic-20261109T031500Z.dump"; then
        printf '%s\n' "self_test=failed stage=object_key_rejected"
        exit 1
    fi
    if ! classify_dump "$AUTOMATIC_DIR_DEFAULT" "strict-automatic-20261109T031500Z.dump"; then
        printf '%s\n' "self_test=failed stage=classify_automatic"
        exit 1
    fi
    if ! classify_dump "$MANUAL_DIR" "strict-anna-test-cleanup-20261108T190018Z.dump"; then
        printf '%s\n' "self_test=failed stage=classify_manual"
        exit 1
    fi
    if classify_dump "$AUTOMATIC_DIR_DEFAULT" "strict-anna-test-cleanup-20261108T190018Z.dump" \
        || classify_dump "$MANUAL_DIR" "notes.txt" \
        || classify_dump "/tmp" "strict-automatic-20261109T031500Z.dump"; then
        printf '%s\n' "self_test=failed stage=classify_rejected"
        exit 1
    fi
    local prod_mount="${PROD_VOLUME}:/"
    if grep -F -e "-v ${prod_mount}" "$SOURCE_DIR/verify-strict-backup.sh" \
        || grep -F -e "-v ${prod_mount}" "$SOURCE_DIR/backup-strict-db.sh"; then
        printf '%s\n' "self_test=failed stage=prod_mount"
        exit 1
    fi
    if ! grep -q 'strict_restore_test_data:/var/lib/postgresql/data' "$SOURCE_DIR/verify-strict-backup.sh"; then
        printf '%s\n' "self_test=failed stage=test_mount"
        exit 1
    fi
    if ! grep -q -- '--network none' "$SOURCE_DIR/verify-strict-backup.sh"; then
        printf '%s\n' "self_test=failed stage=network"
        exit 1
    fi
    work=$(mktemp -d)
    file=$work/sample.bin
    header=$work/head.json
    printf 'abcdefghij' > "$file"
    whole_md5=$(file_md5_b64 "$file")
    whole_sha=$(file_sha256_b64 "$file")
    cat > "$header" << EOF
{"content-length":"10","content-md5":"$whole_md5","etag":"not-the-md5","opc-content-sha256":"$whole_sha"}
EOF
    if ! remote_matches "$file" 10 "$header"; then
        printf '%s\n' "self_test=failed stage=download_checksum"
        exit 1
    fi
    printf '%s\n' '{"content-length":"10","content-md5":"AAAAAAAAAAAAAAAAAAAAAA=="}' > "$header"
    if remote_matches "$file" 10 "$header"; then
        printf '%s\n' "self_test=failed stage=download_checksum_mismatch"
        exit 1
    fi
    rm -rf -- "$work"
    printf '%s\n' "self_test=ok"
}

main() {
    local dump mode
    assert_names
    case "${1:-}" in
        --self-test)
            if [ "$#" -ne 1 ]; then
                printf '%s\n' "result=failed stage=arguments"
                exit 2
            fi
            self_test
            return 0
            ;;
        --object)
            if [ "$#" -ne 2 ]; then
                printf '%s\n' "result=failed stage=arguments"
                exit 2
            fi
            mode=object
            ;;
        -*)
            printf '%s\n' "result=failed stage=arguments"
            exit 2
            ;;
        "")
            printf '%s\n' "result=failed stage=arguments"
            exit 2
            ;;
        *)
            if [ "$#" -ne 1 ]; then
                printf '%s\n' "result=failed stage=arguments"
                exit 2
            fi
            mode=file
            ;;
    esac
    if [ "$(id -u)" -ne 0 ]; then
        printf '%s\n' "result=failed stage=root"
        exit 1
    fi
    trap cleanup EXIT
    if [ "$mode" = "object" ]; then
        download_object "$2"
        dump=$downloaded
    else
        dump=$(accept_dump "$1") || fail dump_path
    fi
    restore_dump "$dump"
    trap - EXIT
    cleanup
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
