#!/bin/bash
# Local tests for backup failure mail. They talk to a process on localhost
# and never open a connection to SendGrid.

set -euo pipefail

if [ "$(id -u)" -eq 0 ]; then
    printf 'notify tests must run without root\n' >&2
    exit 1
fi

ROOT=$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")
NOTIFY=$ROOT/notify-strict-backup-failure.sh
PY=$ROOT/notify-strict-backup-failure.py
FAKE=$ROOT/notify-strict-backup-failure_fakesmtp.py
BACKUP=$ROOT/backup-strict-db.sh
BACKUP_UNIT=$ROOT/../systemd/strict-db-backup.service
NOTIFY_UNIT=$ROOT/../systemd/strict-db-backup-notify.service
EXAMPLE=$ROOT/../env/strict-backup-alert.env.example

API_KEY='SG.unit-test-key-do-not-send'
DECOY='decoy-db-password-do-not-leak'
AMBIENT='SG.ambient-secret-do-not-leak'
RECIPIENT='laszlo.szelecsenyi@gmail.com'
SENDER='noreply@puff.hu'
FILENAME='strict-automatic-20261109T150852Z.dump'
STAMP='2026-11-09T15:08:55Z'

WORK=$(mktemp -d /tmp/strict-backup-notify.XXXXXX)
PUFF=$WORK/puff.env
ALERT=$WORK/alert.env
STATE=$WORK/state
LOG=$WORK/failure.log
CERT=$WORK/cert.pem
KEY=$WORK/key.pem
PORT_FILE=$WORK/port
STATS=$WORK/stats.json
OUT=$WORK/out
ERR=$WORK/err
SERVER_PID=""
PORT=""
RC=0
INVOCATION=failure-001

on_err() {
    trap - ERR
    printf 'FAIL command: %s\n' "$BASH_COMMAND" >&2
    exit 1
}
cleanup() {
    if [ -n "${SERVER_PID}" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
        SERVER_PID=""
    fi
    rm -rf -- "$WORK"
}
trap on_err ERR
trap cleanup EXIT

pass() {
    printf 'PASS %s\n' "$1"
}

bash -n "$NOTIFY"
bash -n "$BACKUP"
python3 -m py_compile "$PY" "$FAKE"

openssl req -x509 -newkey rsa:2048 -keyout "$KEY" -out "$CERT" -days 1 -nodes \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost" >/dev/null 2>&1
chmod 600 "$KEY" "$CERT"

cat > "$LOG" <<EOF
2026-11-09T15:00:00Z result=failed stage=dump
${STAMP} result=failed stage=offhost_put file=${FILENAME}
${STAMP} local_success=yes offhost_success=no full_success=no
EOF

write_alert() {
    local recipient=$1
    printf 'STRICT_BACKUP_ALERT_TO=%s\n' "$recipient" > "$ALERT"
    chmod 600 "$ALERT"
}

write_puff() {
    local include_key=$1
    {
        printf 'SENDGRID_SMTP_HOST=localhost\n'
        printf 'SENDGRID_SMTP_PORT=%s\n' "$PORT"
        printf 'SENDGRID_SMTP_USERNAME=apikey\n'
        if [ "$include_key" = yes ]; then
            printf 'SENDGRID_API_KEY=%s\n' "$API_KEY"
        fi
        printf 'DECOY_DATABASE_PASSWORD=%s\n' "$DECOY"
        printf 'SPRING_MAIL_PASSWORD=%s\n' "$DECOY"
        printf 'STRICT_POSTGRES_PASSWORD=%s\n' "$DECOY"
    } > "$PUFF"
    chmod 600 "$PUFF"
    grep -q '^SENDGRID_SMTP_HOST=localhost$' "$PUFF"
    if grep -q 'smtp.sendgrid.net' "$PUFF"; then
        printf 'test env points at SendGrid\n' >&2
        return 1
    fi
}

stop_server() {
    if [ -n "${SERVER_PID}" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
        SERVER_PID=""
    fi
}

start_server() {
    local mode=$1
    local attempt
    stop_server
    rm -f -- "$PORT_FILE" "$STATS"
    PORT=""
    python3 "$FAKE" "$mode" "$CERT" "$KEY" "$PORT_FILE" "$STATS" "$PUFF" &
    SERVER_PID=$!
    attempt=0
    while [ "$attempt" -lt 50 ]; do
        if [ -s "$PORT_FILE" ]; then
            PORT=$(tr -d '[:space:]' < "$PORT_FILE")
            break
        fi
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            printf 'fake smtp exited\n' >&2
            return 1
        fi
        attempt=$((attempt + 1))
        sleep 0.1
    done
    case $PORT in
        ''|25|465|587)
            printf 'refusing SMTP port %s\n' "$PORT" >&2
            return 1
            ;;
    esac
}

reset_state() {
    rm -f -- "$STATE" "${STATE}.lock"
}

invoke() {
    RC=0
    : > "$OUT"
    : > "$ERR"
    STRICT_BACKUP_PUFF_ENV=$PUFF \
        STRICT_BACKUP_ALERT_ENV=$ALERT \
        STRICT_BACKUP_ALERT_STATE=$STATE \
        STRICT_BACKUP_ALERT_LOG=$LOG \
        STRICT_BACKUP_ALERT_ID=$INVOCATION \
        STRICT_BACKUP_ALERT_ALLOW_TEST_PORT=1 \
        STRICT_BACKUP_ALERT_CA_FILE=$CERT \
        timeout 15 bash "$NOTIFY" >"$OUT" 2>"$ERR" || RC=$?
}

invoke_ambient() {
    RC=0
    : > "$OUT"
    : > "$ERR"
    SENDGRID_API_KEY=$AMBIENT \
        SPRING_MAIL_PASSWORD=$AMBIENT \
        DATABASE_URL=$AMBIENT \
        STRICT_BACKUP_PUFF_ENV=$PUFF \
        STRICT_BACKUP_ALERT_ENV=$ALERT \
        STRICT_BACKUP_ALERT_STATE=$STATE \
        STRICT_BACKUP_ALERT_LOG=$LOG \
        STRICT_BACKUP_ALERT_ID=$INVOCATION \
        STRICT_BACKUP_ALERT_ALLOW_TEST_PORT=1 \
        STRICT_BACKUP_ALERT_CA_FILE=$CERT \
        timeout 15 bash "$NOTIFY" >"$OUT" 2>"$ERR" || RC=$?
}

json_get() {
    python3 -c 'import json,sys; value=json.load(open(sys.argv[1],encoding="utf-8")); print(value[sys.argv[2]] if not isinstance(value[sys.argv[2]], list) else len(value[sys.argv[2]]))' "$STATS" "$1"
}

assert_no_secrets() {
    local file
    for file in "$OUT" "$ERR" "$STATS" "$STATE"; do
        [ -f "$file" ] || continue
        if grep -F -q -e "$API_KEY" -e "$DECOY" -e "$AMBIENT" -- "$file"; then
            printf 'secret leaked into %s\n' "$file" >&2
            return 1
        fi
    done
}

assert_stage() {
    grep -q -- "^notify=failed stage=$1$" "$OUT" "$ERR"
}

prepare_success_files() {
    write_puff yes
    start_server "$1"
    write_puff yes
    write_alert "$RECIPIENT"
    reset_state
}

test_sender_and_recipient() {
    local server_name
    prepare_success_files ok
    INVOCATION=sender-001
    invoke
    [ "$RC" -eq 0 ]
    grep -q '^smtp_starttls=220$' "$OUT"
    grep -q '^smtp_auth=235$' "$OUT"
    grep -q '^smtp_mail_from=250$' "$OUT"
    grep -q '^smtp_rcpt=250$' "$OUT"
    grep -q '^smtp_data=250$' "$OUT"
    grep -q '^notify=sent$' "$OUT"
    [ "$(json_get connections)" -eq 1 ]
    [ "$(json_get data)" -eq 1 ]
    server_name=$(hostname)
    python3 - "$STATS" "$SENDER" "$RECIPIENT" "$server_name" "$STAMP" "$FILENAME" <<'PY'
import json
import sys
stats, sender, recipient, server_name, stamp, filename = sys.argv[1:]
data = json.load(open(stats, encoding="utf-8"))
if data["mail_from"] != [sender] or data["rcpt"] != [recipient]:
    raise SystemExit("envelope mismatch")
if sender == recipient:
    raise SystemExit("sender and recipient are the same address")
body = data["messages"][0]
required = [
    "From: " + sender,
    "To: " + recipient,
    "Subject: [STRICT PROD] PostgreSQL backup FAILED",
    "Environment: production",
    "Server: " + server_name,
    "Timestamp: " + stamp,
    "Backup status: FAILED",
    "Failure stage: offhost_put",
    "Backup file: " + filename,
    "Systemd unit: strict-db-backup.service",
]
for line in required:
    if line not in body:
        raise SystemExit("missing " + line)
if "To: " + sender in body or "From: " + recipient in body:
    raise SystemExit("sender and recipient were swapped")
if "Failure stage: dump" in body or "local_success" in body:
    raise SystemExit("journal text was copied into the message")
PY
    assert_no_secrets
    write_alert "$SENDER"
    INVOCATION=sender-002
    invoke
    [ "$RC" -ne 0 ]
    assert_stage sender_equals_recipient
    [ "$(json_get connections)" -eq 1 ]
    assert_no_secrets
    stop_server
}

test_smtp_auth_failure() {
    prepare_success_files auth
    INVOCATION=auth-001
    invoke
    [ "$RC" -ne 0 ]
    grep -q '^smtp_auth=535$' "$OUT"
    assert_stage smtp_auth
    [ "$(json_get connections)" -eq 1 ]
    [ "$(json_get mail_from)" -eq 0 ]
    [ "$(json_get data)" -eq 0 ]
    if [ -f "$STATE" ] && grep -q 'auth-001' "$STATE"; then
        printf 'auth failure was recorded as sent\n' >&2
        return 1
    fi
    invoke
    [ "$RC" -ne 0 ]
    [ "$(json_get connections)" -eq 2 ]
    assert_no_secrets
    stop_server
}

test_smtp_sender_rejection() {
    prepare_success_files sender
    INVOCATION=sender-reject-001
    invoke
    [ "$RC" -ne 0 ]
    grep -q '^smtp_auth=235$' "$OUT"
    grep -q '^smtp_mail_from=550$' "$OUT"
    assert_stage smtp_sender
    [ "$(json_get mail_from)" -eq 1 ]
    [ "$(json_get rcpt)" -eq 0 ]
    [ "$(json_get data)" -eq 0 ]
    assert_no_secrets
    stop_server
}

test_smtp_recipient_rejection() {
    prepare_success_files recipient
    INVOCATION=recipient-reject-001
    invoke
    [ "$RC" -ne 0 ]
    grep -q '^smtp_rcpt=550$' "$OUT"
    assert_stage smtp_recipient
    [ "$(json_get rcpt)" -eq 1 ]
    [ "$(json_get data)" -eq 0 ]
    assert_no_secrets
    stop_server
}

test_smtp_delivery_rejection() {
    prepare_success_files data
    INVOCATION=data-reject-001
    invoke
    [ "$RC" -ne 0 ]
    grep -q '^smtp_data=550$' "$OUT"
    assert_stage smtp_data
    [ "$(json_get data)" -eq 1 ]
    if [ -f "$STATE" ] && grep -q 'data-reject-001' "$STATE"; then
        printf 'rejected delivery was recorded as sent\n' >&2
        return 1
    fi
    assert_no_secrets
    stop_server
}

test_missing_credentials() {
    prepare_success_files ok
    write_puff no
    INVOCATION=missing-key-001
    invoke
    [ "$RC" -ne 0 ]
    assert_stage credentials
    [ "$(json_get connections)" -eq 0 ]
    rm -f -- "$PUFF"
    INVOCATION=missing-file-001
    invoke
    [ "$RC" -ne 0 ]
    assert_stage credentials
    [ "$(json_get connections)" -eq 0 ]
    assert_no_secrets
    stop_server
}

test_missing_recipient() {
    prepare_success_files ok
    rm -f -- "$ALERT"
    INVOCATION=missing-alert-001
    invoke
    [ "$RC" -ne 0 ]
    assert_stage recipient
    [ "$(json_get connections)" -eq 0 ]
    printf '# no recipient\n' > "$ALERT"
    chmod 600 "$ALERT"
    INVOCATION=blank-alert-001
    invoke
    [ "$RC" -ne 0 ]
    assert_stage recipient
    [ "$(json_get connections)" -eq 0 ]
    assert_no_secrets
    stop_server
}

test_no_secret_leakage() {
    local connections
    prepare_success_files ok
    INVOCATION=leak-001
    invoke_ambient
    [ "$RC" -eq 0 ]
    grep -q '^notify=sent$' "$OUT"
    assert_no_secrets
    connections=$(json_get connections)
    chmod 644 "$PUFF"
    INVOCATION=leak-002
    invoke_ambient
    [ "$RC" -ne 0 ]
    assert_stage env_mode
    [ "$(json_get connections)" -eq "$connections" ]
    chmod 600 "$PUFF"
    assert_no_secrets
    if grep -q "$RECIPIENT" "$PY" "$NOTIFY" "$EXAMPLE"; then
        printf 'recipient is hardcoded in the notifier\n' >&2
        return 1
    fi
    grep -q "$SENDER" "$PY"
    grep -q "$SENDER" "$NOTIFY"
    if grep -E -q '^[[:space:]]*(source|set -a)([[:space:]]|$)' "$NOTIFY"; then
        printf 'notifier sources an env file\n' >&2
        return 1
    fi
    if grep -E -q '^[[:space:]]*\.[[:space:]]+/' "$NOTIFY"; then
        printf 'notifier sources an env file\n' >&2
        return 1
    fi
    stop_server
}

test_duplicate_notification() {
    prepare_success_files ok
    INVOCATION=duplicate-001
    invoke
    [ "$RC" -eq 0 ]
    [ "$(json_get data)" -eq 1 ]
    invoke
    [ "$RC" -eq 0 ]
    grep -qx 'notify=skipped reason=duplicate' "$OUT"
    [ "$(json_get data)" -eq 1 ]
    [ "$(json_get connections)" -eq 1 ]
    INVOCATION=duplicate-002
    invoke
    [ "$RC" -eq 0 ]
    grep -q '^notify=sent$' "$OUT"
    [ "$(json_get data)" -eq 2 ]
    assert_no_secrets
    stop_server
}

test_systemd_units() {
    awk '
        /^\[/ { section = $0 }
        $0 == "OnFailure=strict-db-backup-notify.service" {
            if (section != "[Unit]") exit 1
            found = 1
        }
        END { exit !found }
    ' "$BACKUP_UNIT"
    grep -q '^Type=oneshot$' "$NOTIFY_UNIT"
    grep -q '^User=root$' "$NOTIFY_UNIT"
    grep -q '^Group=root$' "$NOTIFY_UNIT"
    grep -q '^ExecStart=/opt/strict/db-backup/notify-strict-backup-failure.sh$' "$NOTIFY_UNIT"
    grep -q '^TimeoutStartSec=2min$' "$NOTIFY_UNIT"
    if grep -q '^Restart=' "$NOTIFY_UNIT"; then
        printf 'notify unit restarts\n' >&2
        return 1
    fi
    if grep -q '^EnvironmentFile=' "$NOTIFY_UNIT" "$BACKUP_UNIT"; then
        printf 'unit loads an environment file\n' >&2
        return 1
    fi
    if grep -q '^OnFailure=' "$NOTIFY_UNIT"; then
        printf 'notify unit has its own OnFailure\n' >&2
        return 1
    fi
    if grep -q '^\[Install\]' "$NOTIFY_UNIT"; then
        printf 'notify unit can be enabled\n' >&2
        return 1
    fi
    if grep -E -q 'notify-strict-backup|sendgrid|smtplib|OnFailure' "$BACKUP"; then
        printf 'backup script sends mail or depends on notification\n' >&2
        return 1
    fi
    if grep -q $'\r' "$NOTIFY" "$PY" "$NOTIFY_UNIT" "$BACKUP_UNIT"; then
        printf 'CRLF in notifier files\n' >&2
        return 1
    fi
    grep -q '^STRICT_BACKUP_ALERT_TO=$' "$EXAMPLE"
}

test_sender_and_recipient
pass sender_and_recipient
test_smtp_auth_failure
pass smtp_auth_failure
test_smtp_sender_rejection
pass smtp_sender_rejection
test_smtp_recipient_rejection
pass smtp_recipient_rejection
test_smtp_delivery_rejection
pass smtp_delivery_rejection
test_missing_credentials
pass missing_credentials
test_missing_recipient
pass missing_recipient
test_no_secret_leakage
pass no_secret_leakage
test_duplicate_notification
pass duplicate_notification
test_systemd_units
pass systemd_units

printf '%s\n' 'all notify tests passed'
