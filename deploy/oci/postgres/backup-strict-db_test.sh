#!/bin/bash
# Local tests for the Strict backup scripts. No production host, database,
# or Object Storage bucket is contacted. OCI and docker are functions.

set -euo pipefail

if [ "$(id -u)" -eq 0 ]; then
    printf 'backup tests must run without root\n' >&2
    exit 1
fi
unset STRICT_BACKUP_CLI_TEST_DIR STRICT_BACKUP_CLI_TEST_ENV

ROOT=$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")
SCRIPT=$ROOT/backup-strict-db.sh
VERIFY=$ROOT/verify-strict-backup.sh
EXAMPLE=$ROOT/../env/strict-backup.env.example

fail() {
    printf 'FAIL %s\n' "$*" >&2
    exit 1
}

pass() {
    printf 'PASS %s\n' "$1"
}

run() {
    local name=$1
    shift
    local out err
    out=$(mktemp)
    err=$(mktemp)
    if ( "$@" ) >"$out" 2>"$err"; then
        pass "$name"
    else
        printf 'FAIL %s\n' "$name" >&2
        cat "$out" "$err" >&2
        exit 1
    fi
    rm -f -- "$out" "$err"
}

docker() {
    case "$*" in
        *pg_dump*)
            printf 'PGDMP'
            dd if=/dev/zero bs=1024 count=2 status=none
            ;;
        *pg_restore*)
            printf '%s\n' \
                'TABLE DATA public app_user' \
                'TABLE DATA public external_identity' \
                'TABLE DATA public auth_session' \
                'TABLE DATA public flyway_schema_history'
            ;;
        *)
            printf 'unexpected docker %s\n' "$*" >&2
            return 1
            ;;
    esac
}

mock_state() {
    local base=$1
    if [ -f "$MOCK_DIR/$base" ]; then
        cat "$MOCK_DIR/$base"
    else
        printf '%s\n' missing
    fi
}

mock_head_json() {
    local path=$1 mode=$2
    local part_bytes part_mib
    part_mib=$(multipart_part_mib)
    part_bytes=$((part_mib * 1024 * 1024))
    MODE=$mode PART_BYTES=$part_bytes python3 - "$path" << 'PY'
import base64, hashlib, json, os, sys
path = sys.argv[1]
mode = os.environ["MODE"]
part = int(os.environ["PART_BYTES"])
data = open(path, "rb").read()
md5 = base64.b64encode(hashlib.md5(data).digest()).decode("ascii")
sha = base64.b64encode(hashlib.sha256(data).digest()).decode("ascii")
hexa = hashlib.sha256(data).hexdigest()
body = {
    "content-length": str(len(data)),
    "etag": "etag-is-not-an-md5",
}
if mode == "multipart":
    def multi(algo):
        digests = []
        for offset in range(0, len(data), part):
            digests.append(hashlib.new(algo, data[offset:offset + part]).digest())
        if not digests:
            digests.append(hashlib.new(algo, b"").digest())
        outer = hashlib.new(algo)
        for item in digests:
            outer.update(item)
        return base64.b64encode(outer.digest()).decode("ascii") + "-" + str(len(digests))
    body["content-md5"] = "not-the-whole-file-md5"
    body["opc-multipart-md5"] = multi("md5")
    body["opc-multipart-sha256"] = multi("sha256")
    body["opc-meta-sha256"] = hexa
    body["opc-meta-method"] = "multipart"
    body["opc-meta-partmib"] = str(int(os.environ["MODE"] and part // (1024 * 1024)))
elif mode == "mismatch":
    body["content-md5"] = "AAAAAAAAAAAAAAAAAAAAAA=="
    body["opc-content-sha256"] = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb="
    body["opc-meta-sha256"] = "00" * 32
    body["opc-meta-method"] = "single"
else:
    body["content-md5"] = md5
    body["opc-content-sha256"] = sha
    body["opc-meta-sha256"] = hexa
    body["opc-meta-method"] = "single"
json.dump(body, sys.stdout)
PY
}

oci() {
    printf '%s\n' "$*" >> "$OCI_LOG"
    local joined=" $* "
    if [[ "$joined" == *" object delete "* ]] || [[ "$joined" == *" --force "* ]]; then
        printf 'refusing remote delete or overwrite\n' >&2
        return 99
    fi
    local name="" file="" prev="" arg
    for arg in "$@"; do
        case "$prev" in
            --name) name=$arg ;;
            --file) file=$arg ;;
        esac
        prev=$arg
    done
    local base
    base=$(basename -- "$name")
    case "$joined" in
        *" object head "*)
            case "$(mock_state "$base")" in
                missing)
                    printf '%s\n' '{"code":"ObjectNotFound","status":404}' >&2
                    return 1
                    ;;
                error)
                    printf '%s\n' '{"code":"InternalServerError","status":500}' >&2
                    return 1
                    ;;
                match|mismatch|multipart)
                    mock_head_json "$AUTOMATIC_DIR/$base" "$(mock_state "$base")"
                    ;;
                *)
                    printf 'bad mock state\n' >&2
                    return 1
                    ;;
            esac
            ;;
        *" object put "*)
            if [ -f "$MOCK_DIR/$base.noput" ]; then
                printf 'put failed\n' >&2
                return 1
            fi
            if [ -f "$MOCK_DIR/$base.badverify" ]; then
                printf 'mismatch\n' > "$MOCK_DIR/$base"
                return 0
            fi
            if [ -f "$MOCK_DIR/$base.multipart" ]; then
                printf 'multipart\n' > "$MOCK_DIR/$base"
                return 0
            fi
            printf 'match\n' > "$MOCK_DIR/$base"
            ;;
        *)
            printf 'unexpected oci\n' >&2
            return 97
            ;;
    esac
}

begin() {
    # shellcheck source=backup-strict-db.sh
    source "$SCRIPT"
    AUTOMATIC_DIR=$(mktemp -d)
    LOG_FILE=$AUTOMATIC_DIR/backup.log
    MOCK_DIR=$(mktemp -d)
    OCI_LOG=$AUTOMATIC_DIR/oci.log
    : > "$OCI_LOG"
    STRICT_BACKUP_OFFHOST=required
    STRICT_BACKUP_REGION=eu-frankfurt-1
    STRICT_BACKUP_NAMESPACE=frdlgn4i5lzy
    STRICT_BACKUP_BUCKET=strict-prod-backups
    STRICT_BACKUP_PREFIX=automatic/
    umask 077
    unset STRICT_BACKUP_CLI_TEST_DIR STRICT_BACKUP_CLI_TEST_ENV
}

make_dump() {
    local name=$1
    local path="$AUTOMATIC_DIR/$name"
    printf 'PGDMP' > "$path"
    dd if=/dev/zero bs=1024 count=2 status=none >> "$path"
    printf '%s\n' "$path"
}

expect_stage() {
    local stage=$1
    shift
    local rc=0
    : > "$LOG_FILE"
    set +e
    ( "$@" )
    rc=$?
    set -e
    [ "$rc" -eq 1 ]
    grep -q "stage=$stage" "$LOG_FILE"
}

assert_clean_oci() {
    if [ -s "$OCI_LOG" ] && grep -q 'object delete' "$OCI_LOG"; then
        printf 'remote delete was called\n' >&2
        return 1
    fi
    if [ -s "$OCI_LOG" ] && grep -q -- '--force' "$OCI_LOG"; then
        printf 'overwrite flag was passed\n' >&2
        return 1
    fi
}

put_lines() {
    grep 'object put' "$OCI_LOG" || true
}

test_static_prohibitions() {
    if grep -q 'os object delete' "$SCRIPT"; then
        printf 'script deletes remote objects\n' >&2
        return 1
    fi
    if grep -q -- '--force' "$SCRIPT"; then
        printf 'script passes an overwrite flag\n' >&2
        return 1
    fi
    grep -q -- '--no-overwrite' "$SCRIPT"
    grep -q -- '--no-multipart' "$SCRIPT"
    grep -q -- '--namespace-name' "$SCRIPT"
    grep -q 'instance_principal' "$SCRIPT"
    grep -q 'eu-frankfurt-1' "$SCRIPT"
    if grep -q '100 \* 1024 \* 1024' "$SCRIPT" || grep -q 'SINGLE_PUT_LIMIT' "$SCRIPT"; then
        printf '100 MB cap is still present\n' >&2
        return 1
    fi
    grep -q 'acquire_backup_lock' "$SCRIPT"
    if grep -q 'require_automatic_di[^r]' "$SCRIPT"; then
        printf 'truncated automatic directory call\n' >&2
        return 1
    fi
    grep -q 'require_automatic_dir' "$SCRIPT"
    grep -q 'TimeoutStartSec=6h' "$ROOT/../systemd/strict-db-backup.service"
}

test_self_test() {
    local out err text
    out=$(mktemp)
    err=$(mktemp)
    bash "$SCRIPT" --self-test >"$out" 2>"$err"
    [ ! -s "$err" ]
    text=$(tr -d '\r' < "$out")
    [ "$text" = "self_test=ok" ]
    bash "$VERIFY" --self-test >"$out" 2>"$err"
    [ ! -s "$err" ]
    text=$(tr -d '\r' < "$out")
    [ "$text" = "self_test=ok" ]
    rm -f -- "$out" "$err"
}

test_successful_upload() {
    begin
    [ "$(single_part_max_bytes)" -eq 53687091200 ]
    [ "$(multipart_part_mib)" -eq 128 ]
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 3600 )))
    path=$(make_dump "$name")
    upload_offhost "$path"
    [ -f "$path.uploaded" ]
    valid_receipt "$name"
    local puts
    puts=$(put_lines)
    printf '%s\n' "$puts" | grep -q -- '--no-overwrite'
    printf '%s\n' "$puts" | grep -q -- '--no-multipart'
    printf '%s\n' "$puts" | grep -q -- '--content-md5'
    printf '%s\n' "$puts" | grep -q -- '--opc-checksum-algorithm SHA256'
    printf '%s\n' "$puts" | grep -q 'instance_principal'
    printf '%s\n' "$puts" | grep -q 'eu-frankfurt-1'
    printf '%s\n' "$puts" | grep -q -- '--namespace-name frdlgn4i5lzy'
    printf '%s\n' "$puts" | grep -q -- '--bucket-name strict-prod-backups'
    printf '%s\n' "$puts" | grep -q "automatic/$name"
    grep -q 'offhost=verified' "$LOG_FILE"
    grep -q 'method=single' "$LOG_FILE"
    assert_clean_oci
    upload_offhost "$path"
    [ "$(grep -c 'object put' "$OCI_LOG")" -eq 1 ]
    grep -q 'offhost=already_verified' "$LOG_FILE"
}

test_run_backup_success() {
    begin
    local old manual
    old=$(name_from_epoch $(( $(date -u +%s) - 30 * 86400 )))
    make_dump "$old" >/dev/null
    write_receipt "$old" single
    mkdir -p "$AUTOMATIC_DIR/nested" "$(dirname -- "$AUTOMATIC_DIR")/manual"
    manual="$(dirname -- "$AUTOMATIC_DIR")/manual/strict-anna-test-cleanup-20261108T190018Z.dump"
    printf 'manual\n' > "$manual"
    printf 'PGDMP' > "$AUTOMATIC_DIR/nested/$old"
    run_backup
    grep -q 'local_success=yes offhost_success=yes full_success=yes' "$LOG_FILE"
    [ ! -e "$AUTOMATIC_DIR/$old" ]
    [ -f "$manual" ]
    [ -f "$AUTOMATIC_DIR/nested/$old" ]
    assert_clean_oci
}

test_upload_failure_keeps_files() {
    begin
    local ancient pending
    ancient=$(name_from_epoch $(( $(date -u +%s) - 30 * 86400 )))
    pending=$(name_from_epoch $(( $(date -u +%s) - 25 * 86400 )))
    : > "$AUTOMATIC_DIR/$ancient"
    write_receipt "$ancient" single
    make_dump "$pending" >/dev/null
    printf 'mismatch\n' > "$MOCK_DIR/$pending"
    set +e
    run_backup
    local rc=$?
    set -e
    [ "$rc" -eq 1 ]
    [ -f "$AUTOMATIC_DIR/$ancient" ]
    [ -f "$AUTOMATIC_DIR/$pending" ]
    [ ! -f "$AUTOMATIC_DIR/$pending.uploaded" ]
    grep -q 'local_success=yes offhost_success=no full_success=no' "$LOG_FILE"
    grep -q 'stage=offhost_pending' "$LOG_FILE"
    grep -q 'retry_failed' "$LOG_FILE"
    grep -q 'stage=offhost_mismatch' "$LOG_FILE"
    if grep -q 'delete=' "$LOG_FILE"; then
        printf 'retention ran after a failed upload\n' >&2
        return 1
    fi
    assert_clean_oci
    [ "$(grep -c 'object put' "$OCI_LOG")" -eq 1 ]
}

test_put_failure() {
    begin
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 7200 )))
    path=$(make_dump "$name")
    touch "$MOCK_DIR/$name.noput"
    if upload_offhost "$path"; then
        return 1
    fi
    [ "$OFFHOST_STAGE" = "offhost_put" ]
    [ -f "$path" ]
    [ ! -f "$path.uploaded" ]
    assert_clean_oci
}

test_verify_failure() {
    begin
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 7300 )))
    path=$(make_dump "$name")
    touch "$MOCK_DIR/$name.badverify"
    if upload_offhost "$path"; then
        return 1
    fi
    [ "$OFFHOST_STAGE" = "offhost_verify" ]
    [ -f "$path" ]
    [ ! -f "$path.uploaded" ]
    [ "$(grep -c 'object put' "$OCI_LOG")" -eq 1 ]
    assert_clean_oci
}

test_existing_mismatch_does_not_put() {
    begin
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 7400 )))
    path=$(make_dump "$name")
    printf 'mismatch\n' > "$MOCK_DIR/$name"
    if upload_offhost "$path"; then
        return 1
    fi
    [ "$OFFHOST_STAGE" = "offhost_mismatch" ]
    [ -f "$path" ]
    [ ! -f "$path.uploaded" ]
    [ "$(grep -c 'object put' "$OCI_LOG" || true)" -eq 0 ]
    assert_clean_oci
}

test_retry_after_put_failure() {
    begin
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 7500 )))
    path=$(make_dump "$name")
    touch "$MOCK_DIR/$name.noput"
    if upload_offhost "$path"; then
        return 1
    fi
    rm -f -- "$MOCK_DIR/$name.noput"
    upload_offhost "$path"
    valid_receipt "$name"
    [ -f "$path" ]
    grep -q 'offhost=verified' "$LOG_FILE"
    assert_clean_oci
}

test_pending_cap() {
    begin
    local now i name
    now=$(date -u +%s)
    for i in $(seq 0 30); do
        name=$(name_from_epoch $((now - 1000 - i)))
        make_dump "$name" >/dev/null
    done
    retry_pending_uploads
    [ "$RETRY_FAILED" -eq 1 ]
    grep -q 'retry_deferred=' "$LOG_FILE"
    [ "$(grep -c 'retry_verified=' "$LOG_FILE")" -eq 30 ]
    [ "$(grep -c 'object put' "$OCI_LOG")" -eq 30 ]
    assert_clean_oci
}

test_disabled_does_not_upload() {
    begin
    STRICT_BACKUP_OFFHOST=disabled
    local old
    old=$(name_from_epoch $(( $(date -u +%s) - 30 * 86400 )))
    : > "$AUTOMATIC_DIR/$old"
    run_backup
    [ ! -s "$OCI_LOG" ]
    grep -q 'local_success=yes offhost_success=disabled full_success=no' "$LOG_FILE"
    [ ! -e "$AUTOMATIC_DIR/$old" ]
}

test_too_large_without_multipart_permission() {
    begin
    single_part_max_bytes() { printf '%s\n' 1500; }
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 7600 )))
    path=$(make_dump "$name")
    if upload_offhost "$path"; then
        return 1
    fi
    [ "$OFFHOST_STAGE" = "offhost_size" ]
    [ -f "$path" ]
    [ ! -s "$OCI_LOG" ]
    grep -q 'permission=OBJECT_OVERWRITE' "$LOG_FILE"
}

test_multipart_upload() {
    begin
    multipart_enabled() { return 0; }
    multipart_part_mib() { printf '%s\n' 1; }
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 7700 )))
    path="$AUTOMATIC_DIR/$name"
    dd if=/dev/zero of="$path" bs=1024 count=1026 status=none
    touch "$MOCK_DIR/$name.multipart"
    upload_offhost "$path"
    valid_receipt "$name"
    local puts
    puts=$(put_lines)
    printf '%s\n' "$puts" | grep -q -- '--part-size 1'
    printf '%s\n' "$puts" | grep -q -- '--no-overwrite'
    if printf '%s\n' "$puts" | grep -q -- '--no-multipart'; then
        printf 'multipart upload forced a single part\n' >&2
        return 1
    fi
    if printf '%s\n' "$puts" | grep -q -- '--content-md5'; then
        printf 'multipart upload sent a whole-file content-md5\n' >&2
        return 1
    fi
    grep -q 'method=multipart' "$LOG_FILE"
    assert_clean_oci
}

test_multipart_verify_failure() {
    begin
    multipart_enabled() { return 0; }
    multipart_part_mib() { printf '%s\n' 1; }
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 7800 )))
    path="$AUTOMATIC_DIR/$name"
    dd if=/dev/zero of="$path" bs=1024 count=1026 status=none
    touch "$MOCK_DIR/$name.multipart"
    touch "$MOCK_DIR/$name.badverify"
    if upload_offhost "$path"; then
        return 1
    fi
    [ "$OFFHOST_STAGE" = "offhost_verify" ]
    [ -f "$path" ]
    [ ! -f "$path.uploaded" ]
}

test_prefix_and_path() {
    begin
    local name
    name=$(name_from_epoch $(( $(date -u +%s) - 7900 )))
    [ "$(remote_object_key "$name")" = "automatic/$name" ]
    if remote_object_key "strict-anna-test-cleanup-20261108T190018Z.dump"; then
        return 1
    fi
    STRICT_BACKUP_PREFIX=manual/
    if remote_object_key "$name"; then
        return 1
    fi
    STRICT_BACKUP_PREFIX=automatic/
    local outside
    outside=$(mktemp /tmp/strict-automatic.XXXXXXXX.dump)
    mv -- "$outside" "/tmp/$name"
    outside="/tmp/$name"
    if upload_offhost "$outside"; then
        rm -f -- "$outside"
        return 1
    fi
    [ "$OFFHOST_STAGE" = "offhost_path" ]
    rm -f -- "$outside"
    env_owner() { printf '0\n'; }
    env_mode() { printf '600\n'; }
    oci_available() { return 0; }
    ENV_FILE=$AUTOMATIC_DIR/bad-prefix.env
    printf '%s\n' 'STRICT_BACKUP_OFFHOST=disabled' 'STRICT_BACKUP_PREFIX=manual/' > "$ENV_FILE"
    expect_stage offhost_prefix load_offhost_mode
}

test_missing_config() {
    begin
    env_owner() { printf '0\n'; }
    env_mode() { printf '600\n'; }
    oci_available() { return 0; }
    python_available() { return 0; }
    openssl_available() { return 0; }
    ENV_FILE=$AUTOMATIC_DIR/missing-ns.env
    printf '%s\n' \
        'STRICT_BACKUP_OFFHOST=required' \
        'STRICT_BACKUP_REGION=eu-frankfurt-1' \
        'STRICT_BACKUP_NAMESPACE=' \
        'STRICT_BACKUP_BUCKET=strict-prod-backups' \
        'STRICT_BACKUP_PREFIX=automatic/' > "$ENV_FILE"
    expect_stage offhost_target load_offhost_mode
    printf '%s\n' 'STRICT_BACKUP_OFFHOST=required' 'STRICT_BACKUP_REGION=eu-frankfurt-2' \
        'STRICT_BACKUP_NAMESPACE=frdlgn4i5lzy' 'STRICT_BACKUP_BUCKET=strict-prod-backups' \
        > "$ENV_FILE"
    expect_stage offhost_region load_offhost_mode
    printf '%s\n' 'AWS_SECRET_ACCESS_KEY=not-used' > "$ENV_FILE"
    expect_stage offhost_env load_offhost_mode
    printf '%s\n' 'STRICT_BACKUP_OFFHOST=required' 'STRICT_BACKUP_NAMESPACE=$(id)' > "$ENV_FILE"
    expect_stage offhost_env load_offhost_mode
    ENV_FILE=$AUTOMATIC_DIR/absent.env
    load_offhost_mode
    [ "$STRICT_BACKUP_OFFHOST" = "disabled" ]
    env_mode() { printf '644\n'; }
    ENV_FILE=$AUTOMATIC_DIR/loose.env
    printf '%s\n' 'STRICT_BACKUP_OFFHOST=disabled' > "$ENV_FILE"
    expect_stage offhost_env_mode load_offhost_mode
    env_mode() { printf '600\n'; }
    env_owner() { printf '1000\n'; }
    expect_stage offhost_env_mode load_offhost_mode
}

test_missing_cli() {
    begin
    env_owner() { printf '0\n'; }
    env_mode() { printf '600\n'; }
    oci_available() { return 1; }
    ENV_FILE=$EXAMPLE
    expect_stage offhost_cli load_offhost_mode
}

test_example_config() {
    begin
    env_owner() { printf '0\n'; }
    env_mode() { printf '600\n'; }
    oci_available() { return 0; }
    python_available() { return 0; }
    openssl_available() { return 0; }
    ENV_FILE=$EXAMPLE
    load_offhost_mode
    [ "$STRICT_BACKUP_OFFHOST" = "required" ]
    [ "$STRICT_BACKUP_REGION" = "eu-frankfurt-1" ]
    [ "$STRICT_BACKUP_NAMESPACE" = "frdlgn4i5lzy" ]
    [ "$STRICT_BACKUP_BUCKET" = "strict-prod-backups" ]
    [ "$STRICT_BACKUP_PREFIX" = "automatic/" ]
}

test_head_error_does_not_put() {
    begin
    local name path
    name=$(name_from_epoch $(( $(date -u +%s) - 8000 )))
    path=$(make_dump "$name")
    printf 'error\n' > "$MOCK_DIR/$name"
    if upload_offhost "$path"; then
        return 1
    fi
    [ "$OFFHOST_STAGE" = "offhost_head" ]
    [ "$(grep -c 'object put' "$OCI_LOG" || true)" -eq 0 ]
    [ -f "$path" ]
}

automatic_name() {
    date -u -d "@$1" +strict-automatic-%Y%m%dT%H%M%SZ.dump
}

install_forbidden_commands() {
    local bin=$1 cmd
    mkdir -p "$bin"
    cat > "$bin/forbid" << 'EOF'
#!/bin/bash
if [ -n "${CLI_CALL_LOG:-}" ]; then
    printf '%s\n' "$(basename -- "$0") $*" >> "$CLI_CALL_LOG"
fi
exit 99
EOF
    chmod 755 "$bin/forbid"
    for cmd in oci docker psql pg_dump pg_restore pg_isready; do
        ln -s forbid "$bin/$cmd"
    done
}

write_cli_receipt() {
    local path=$1 base bytes sha md5
    base=$(basename -- "$path")
    bytes=$(wc -c < "$path")
    bytes=${bytes//[[:space:]]/}
    sha=$(openssl dgst -sha256 -hex "$path" | awk '{print $NF}')
    md5=$(openssl dgst -md5 -binary "$path" | openssl base64 -A)
    umask 077
    cat > "$path.uploaded" << EOF
object=automatic/$base
bytes=$bytes
md5=$md5
sha256=$sha
method=single
verified_at=2026-11-09T00:00:00Z
EOF
    chmod 600 "$path.uploaded"
}

snapshot_protected() {
    local dest=$1
    if [ -e /opt/strict ]; then
        find /opt/strict -printf '%p %y %s %T@\n' | sort > "$dest.opt"
    else
        printf 'absent\n' > "$dest.opt"
    fi
    if [ -e /etc/strict ]; then
        find /etc/strict -printf '%p %y %s %T@\n' | sort > "$dest.etc"
    else
        printf 'absent\n' > "$dest.etc"
    fi
}

file_manifest() {
    local dir=$1 dest=$2 file
    : > "$dest"
    while IFS= read -r file; do
        [ -n "$file" ] || continue
        sha256sum "$file" >> "$dest"
    done < <(find "$dir" -type f ! -name backup.log ! -name backup.lock | sort)
}

prepare_cli_tree() {
    local now
    CLI_WORK=$(mktemp -d)
    trap 'rm -rf -- "$CLI_WORK"' EXIT
    CLI_AUTO=$CLI_WORK/automatic
    CLI_MANUAL=$CLI_WORK/manual
    mkdir -p "$CLI_AUTO" "$CLI_MANUAL" "$CLI_WORK/bin"
    chmod 700 "$CLI_AUTO" "$CLI_MANUAL"
    install_forbidden_commands "$CLI_WORK/bin"
    : > "$CLI_WORK/calls.log"
    now=$(date -u +%s)
    CLI_RECENT=$(automatic_name "$now")
    CLI_EXPIRED=$(automatic_name $((now - 21 * 86400)))
    CLI_PENDING=$(automatic_name $((now - 30 * 86400)))
    printf 'recent\n' > "$CLI_AUTO/$CLI_RECENT"
    printf 'expired\n' > "$CLI_AUTO/$CLI_EXPIRED"
    printf 'pending\n' > "$CLI_AUTO/$CLI_PENDING"
    write_cli_receipt "$CLI_AUTO/$CLI_RECENT"
    write_cli_receipt "$CLI_AUTO/$CLI_EXPIRED"
    printf 'not-a-backup\n' > "$CLI_AUTO/strict-automatic-not-a-backup.dump"
    printf 'notes\n' > "$CLI_AUTO/notes.txt"
    CLI_MANUAL_FILE=$CLI_MANUAL/strict-anna-test-cleanup-20261108T190018Z.dump
    printf 'manual\n' > "$CLI_MANUAL_FILE"
    CLI_ENV=$CLI_AUTO/cli.env
    cat > "$CLI_ENV" << 'EOF'
STRICT_BACKUP_OFFHOST=required
STRICT_BACKUP_REGION=eu-frankfurt-1
STRICT_BACKUP_NAMESPACE=frdlgn4i5lzy
STRICT_BACKUP_BUCKET=strict-prod-backups
STRICT_BACKUP_PREFIX=automatic/
EOF
    chmod 600 "$CLI_ENV"
}

invoke_dry_run() {
    local script=$1
    CLI_OUT=$CLI_WORK/stdout
    CLI_ERR=$CLI_WORK/stderr
    set +e
    env \
        PATH="$CLI_WORK/bin:$PATH" \
        CLI_CALL_LOG="$CLI_WORK/calls.log" \
        STRICT_BACKUP_CLI_TEST_DIR="$CLI_AUTO" \
        STRICT_BACKUP_CLI_TEST_ENV="$CLI_ENV" \
        bash "$script" --retention-dry-run >"$CLI_OUT" 2>"$CLI_ERR"
    CLI_RC=$?
    set -e
}

assert_files_unchanged() {
    file_manifest "$CLI_AUTO" "$CLI_WORK/after-auto"
    file_manifest "$CLI_MANUAL" "$CLI_WORK/after-manual"
    snapshot_protected "$CLI_WORK/protected-after"
    cmp -s "$CLI_WORK/before-auto" "$CLI_WORK/after-auto"
    cmp -s "$CLI_WORK/before-manual" "$CLI_WORK/after-manual"
    cmp -s "$CLI_WORK/protected-before.opt" "$CLI_WORK/protected-after.opt"
    cmp -s "$CLI_WORK/protected-before.etc" "$CLI_WORK/protected-after.etc"
    [ ! -s "$CLI_WORK/calls.log" ]
}

remember_files() {
    file_manifest "$CLI_AUTO" "$CLI_WORK/before-auto"
    file_manifest "$CLI_MANUAL" "$CLI_WORK/before-manual"
    snapshot_protected "$CLI_WORK/protected-before"
}

check_dry_run_success() {
    local script=$1
    remember_files
    invoke_dry_run "$script"
    [ "$CLI_RC" -eq 0 ]
    [ ! -s "$CLI_ERR" ]
    if grep -q 'not found' "$CLI_ERR" "$CLI_OUT"; then
        printf 'dry-run reported a missing command\n' >&2
        return 1
    fi
    assert_files_unchanged
    grep -q "retention_mode=dry-run scope=automatic_only keep=$CLI_RECENT delete_count=1" "$CLI_OUT"
    grep -q "delete=$CLI_EXPIRED" "$CLI_OUT"
    grep -q "retention_pending=$CLI_PENDING" "$CLI_OUT"
    grep -q "retention_ignored=strict-automatic-not-a-backup.dump" "$CLI_OUT"
    [ "$(grep -c ' delete=' "$CLI_OUT")" -eq 1 ]
    if grep -q "delete=$CLI_PENDING" "$CLI_OUT" || grep -q "delete=$CLI_RECENT" "$CLI_OUT"; then
        printf 'dry-run selected a protected backup\n' >&2
        return 1
    fi
    if grep -q 'notes.txt' "$CLI_OUT" || grep -q 'strict-anna-test-cleanup' "$CLI_OUT"; then
        printf 'dry-run mentioned a file outside retention scope\n' >&2
        return 1
    fi
    [ -f "$CLI_AUTO/$CLI_EXPIRED" ]
    [ -f "$CLI_AUTO/$CLI_RECENT" ]
    [ -f "$CLI_AUTO/$CLI_PENDING" ]
    [ -f "$CLI_AUTO/notes.txt" ]
    [ -f "$CLI_MANUAL_FILE" ]
    [ ! -f "$CLI_AUTO/$CLI_PENDING.uploaded" ]
}

test_cli_retention_dry_run() {
    prepare_cli_tree
    check_dry_run_success "$SCRIPT"
}

test_cli_unknown_option() {
    local work out err rc
    work=$(mktemp -d)
    trap "rm -rf -- $(printf '%q' "$work")" EXIT
    install_forbidden_commands "$work/bin"
    : > "$work/calls.log"
    out=$work/out
    err=$work/err
    snapshot_protected "$work/before"
    set +e
    env -u STRICT_BACKUP_CLI_TEST_DIR -u STRICT_BACKUP_CLI_TEST_ENV \
        bash "$SCRIPT" --not-a-real-option >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 2 ]
    grep -q 'stage=arguments' "$out"
    [ ! -s "$err" ]
    set +e
    bash "$SCRIPT" --retention-dry-run --extra >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 2 ]
    grep -q 'stage=arguments' "$out"
    set +e
    bash "$SCRIPT" --self-test --extra >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 2 ]
    grep -q 'stage=arguments' "$out"
    set +e
    bash "$VERIFY" --not-a-real-option >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 2 ]
    grep -q 'stage=arguments' "$out"
    set +e
    bash "$VERIFY" --object >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 2 ]
    grep -q 'stage=arguments' "$out"
    set +e
    bash "$VERIFY" >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 2 ]
    grep -q 'stage=arguments' "$out"
    set +e
    env PATH="$work/bin:$PATH" CLI_CALL_LOG="$work/calls.log" \
        bash "$VERIFY" --object automatic/strict-automatic-20261109T031500Z.dump >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 1 ]
    grep -q 'stage=root' "$out"
    [ ! -s "$work/calls.log" ]
    snapshot_protected "$work/after"
    cmp -s "$work/before.opt" "$work/after.opt"
    cmp -s "$work/before.etc" "$work/after.etc"
}

test_cli_missing_config() {
    prepare_cli_tree
    cat > "$CLI_ENV" << 'EOF'
STRICT_BACKUP_OFFHOST=required
STRICT_BACKUP_REGION=eu-frankfurt-1
STRICT_BACKUP_NAMESPACE=
STRICT_BACKUP_BUCKET=strict-prod-backups
STRICT_BACKUP_PREFIX=automatic/
EOF
    chmod 600 "$CLI_ENV"
    remember_files
    invoke_dry_run "$SCRIPT"
    [ "$CLI_RC" -eq 1 ]
    grep -q 'stage=offhost_target' "$CLI_OUT"
    assert_files_unchanged
    chmod 644 "$CLI_ENV"
    : > "$CLI_WORK/calls.log"
    invoke_dry_run "$SCRIPT"
    [ "$CLI_RC" -eq 1 ]
    grep -q 'stage=offhost_env_mode' "$CLI_OUT"
    assert_files_unchanged
}

test_cli_invalid_directory() {
    local work out err rc
    work=$(mktemp -d)
    trap "rm -rf -- $(printf '%q' "$work")" EXIT
    out=$work/out
    err=$work/err
    snapshot_protected "$work/before"
    set +e
    env -u STRICT_BACKUP_CLI_TEST_DIR -u STRICT_BACKUP_CLI_TEST_ENV \
        bash "$SCRIPT" --retention-dry-run >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 1 ]
    grep -q 'stage=root' "$out"
    if grep -q 'not found' "$err"; then
        printf 'dry-run failed before the directory check\n' >&2
        return 1
    fi
    set +e
    env STRICT_BACKUP_CLI_TEST_DIR="$work/missing" \
        bash "$SCRIPT" --retention-dry-run >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 1 ]
    grep -q 'stage=automatic_dir' "$out"
    [ ! -e "$work/missing" ]
    printf 'x\n' > "$work/not-a-dir"
    set +e
    env STRICT_BACKUP_CLI_TEST_DIR="$work/not-a-dir" \
        bash "$SCRIPT" --retention-dry-run >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 1 ]
    grep -q 'stage=automatic_dir' "$out"
    set +e
    env STRICT_BACKUP_CLI_TEST_DIR=/opt/strict/db-backup/automatic \
        bash "$SCRIPT" --retention-dry-run >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 1 ]
    grep -q 'stage=automatic_dir' "$out"
    ln -s /opt/strict/db-backup/automatic "$work/link"
    set +e
    env STRICT_BACKUP_CLI_TEST_DIR="$work/link" \
        bash "$SCRIPT" --retention-dry-run >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 1 ]
    grep -q 'stage=automatic_dir' "$out"
    mkdir -p "$work/automatic"
    chmod 700 "$work/automatic"
    set +e
    env STRICT_BACKUP_CLI_TEST_DIR="$work/automatic" \
        STRICT_BACKUP_CLI_TEST_ENV=/etc/strict/strict-backup.env \
        bash "$SCRIPT" --retention-dry-run >"$out" 2>"$err"
    rc=$?
    set -e
    [ "$rc" -eq 1 ]
    grep -q 'stage=offhost_env' "$out"
    snapshot_protected "$work/after"
    cmp -s "$work/before.opt" "$work/after.opt"
    cmp -s "$work/before.etc" "$work/after.etc"
}

test_cli_truncated_function() {
    local copy
    prepare_cli_tree
    copy=$CLI_WORK/broken-backup.sh
    sed -E 's/^([[:space:]]*)require_automatic_dir[[:space:]]*$/\1require_automatic_di/' "$SCRIPT" > "$copy"
    chmod 700 "$copy"
    [ "$(grep -c '^require_automatic_dir()' "$copy")" -eq 1 ]
    [ "$(grep -c '^[[:space:]]*require_automatic_di$' "$copy")" -eq 2 ]
    bash -n "$copy"
    remember_files
    invoke_dry_run "$copy"
    [ "$CLI_RC" -eq 127 ]
    grep -q 'require_automatic_di' "$CLI_ERR"
    grep -q 'not found' "$CLI_ERR"
    if grep -q ' delete=' "$CLI_OUT"; then
        printf 'broken dry-run reached retention\n' >&2
        return 1
    fi
    assert_files_unchanged
    : > "$CLI_WORK/calls.log"
    check_dry_run_success "$SCRIPT"
}

test_systemd_units() {
    local service timer
    service=$ROOT/../systemd/strict-db-backup.service
    timer=$ROOT/../systemd/strict-db-backup.timer
    grep -q '^User=root$' "$service"
    grep -q '^Group=root$' "$service"
    grep -q '^ExecStart=/opt/strict/db-backup/backup-strict-db.sh$' "$service"
    grep -q '^TimeoutStartSec=6h$' "$service"
    grep -q '^OnFailure=strict-db-backup-notify.service$' "$service"
    if grep -q '^EnvironmentFile=' "$service"; then
        printf 'service must read the env file from the backup script\n' >&2
        return 1
    fi
    grep -q '^OnCalendar=\*-\*-\* 03:15:00 UTC' "$timer"
    grep -q '^RandomizedDelaySec=300' "$timer"
    grep -q '^Persistent=true' "$timer"
    grep -q '^Unit=strict-db-backup.service' "$timer"
}

if ! test_static_prohibitions; then
    fail static_prohibitions
fi
pass static_prohibitions

run self_test test_self_test
run successful_upload test_successful_upload
run run_backup_success test_run_backup_success
run upload_failure_keeps_files test_upload_failure_keeps_files
run put_failure test_put_failure
run verify_failure test_verify_failure
run existing_mismatch test_existing_mismatch_does_not_put
run retry_after_put_failure test_retry_after_put_failure
run pending_cap test_pending_cap
run disabled_does_not_upload test_disabled_does_not_upload
run too_large_without_multipart test_too_large_without_multipart_permission
run multipart_upload test_multipart_upload
run multipart_verify_failure test_multipart_verify_failure
run prefix_and_path test_prefix_and_path
run missing_config test_missing_config
run missing_cli test_missing_cli
run example_config test_example_config
run head_error test_head_error_does_not_put
run cli_retention_dry_run test_cli_retention_dry_run
run cli_unknown_option test_cli_unknown_option
run cli_missing_config test_cli_missing_config
run cli_invalid_directory test_cli_invalid_directory
run cli_truncated_function test_cli_truncated_function
run systemd_units test_systemd_units

printf '%s\n' 'all backup tests passed'
