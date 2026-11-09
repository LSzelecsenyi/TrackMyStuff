#!/bin/bash
# Notify once when strict-db-backup.service fails.
# The sender is the verified PUFF address and is not configurable.
# The recipient comes only from STRICT_BACKUP_ALERT_TO.
# SendGrid values stay in the PUFF env file. This script does not source it
# and does not put the API key in the environment or on a command line.

set -euo pipefail
umask 077

readonly SENDER=noreply@puff.hu
readonly UNIT=strict-db-backup.service

PUFF_ENV=${STRICT_BACKUP_PUFF_ENV:-/etc/puff/puff-backend-prod.env}
ALERT_ENV=${STRICT_BACKUP_ALERT_ENV:-/etc/strict/strict-backup-alert.env}
STATE_FILE=${STRICT_BACKUP_ALERT_STATE:-/var/lib/strict/backup-notify.state}

die() {
    printf 'notify=failed stage=%s\n' "$1" >&2
    exit 1
}

require_private() {
    local path=$1
    local stage=$2
    local mode
    [ -f "$path" ] || die "$stage"
    [ -L "$path" ] && die "$stage"
    mode=$(stat -c %a -- "$path")
    mode=${mode#0}
    case $mode in
        600|400) ;;
        *) die env_mode ;;
    esac
}

require_recipient_key() {
    grep -Eq '^[[:space:]]*(export[[:space:]]+)?STRICT_BACKUP_ALERT_TO=[^[:space:]#]+[[:space:]]*$' \
        "$ALERT_ENV" || die recipient
}

parse_failure() {
    local line
    STAGE=unknown
    BACKUP_FILE=""
    TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    while IFS= read -r line || [ -n "${line:-}" ]; do
        if [[ $line =~ result=failed[[:space:]]stage=([A-Za-z0-9_]+) ]]; then
            STAGE=${BASH_REMATCH[1]}
            if [[ $line =~ ([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z) ]]; then
                TIMESTAMP=${BASH_REMATCH[1]}
            fi
            BACKUP_FILE=""
            if [[ $line =~ file=(strict-automatic-[0-9]{8}T[0-9]{6}Z\.dump) ]]; then
                BACKUP_FILE=${BASH_REMATCH[1]}
            fi
        fi
        true
    done
    return 0
}

main() {
    local log_text invocation identity state_dir script_dir output status server

    require_private "$PUFF_ENV" credentials
    require_private "$ALERT_ENV" recipient
    require_recipient_key

    if [ -n "${STRICT_BACKUP_ALERT_LOG:-}" ]; then
        [ -f "$STRICT_BACKUP_ALERT_LOG" ] || die journal
        log_text=$(cat -- "$STRICT_BACKUP_ALERT_LOG")
    else
        log_text=$(journalctl -u "$UNIT" -n 200 --no-pager -o cat 2>/dev/null || true)
    fi
    parse_failure <<< "$log_text"

    server=${STRICT_BACKUP_ALERT_HOSTNAME:-$(hostname)}
    if [[ ! $server =~ ^[A-Za-z0-9._-]+$ ]]; then
        server=unknown
    fi

    if [ -n "${STRICT_BACKUP_ALERT_ID:-}" ]; then
        invocation=$STRICT_BACKUP_ALERT_ID
    else
        invocation=$(systemctl show -p InvocationID --value "$UNIT" 2>/dev/null || true)
    fi
    if [[ ! $invocation =~ ^[A-Za-z0-9._:-]+$ ]]; then
        invocation="log:${TIMESTAMP}:${STAGE}:${BACKUP_FILE}"
    fi
    identity="${invocation}|${TIMESTAMP}|${STAGE}|${BACKUP_FILE}"

    state_dir=$(dirname -- "$STATE_FILE")
    mkdir -p -- "$state_dir"
    chmod 0700 "$state_dir" || true
    touch -- "$STATE_FILE"
    chmod 0600 "$STATE_FILE"
    exec 9>>"${STATE_FILE}.lock"
    flock 9

    if grep -qxF -- "$identity" "$STATE_FILE"; then
        printf 'notify=skipped reason=duplicate\n'
        exit 0
    fi

    script_dir=$(dirname -- "$(readlink -f -- "$0")")
    [ -f "$script_dir/notify-strict-backup-failure.py" ] || die notifier

    unset SENDGRID_API_KEY \
        SPRING_MAIL_PASSWORD \
        SPRING_DATASOURCE_PASSWORD \
        STRICT_POSTGRES_PASSWORD \
        POSTGRES_PASSWORD \
        DATABASE_URL

    set +e
    output=$(
        STRICT_BACKUP_PUFF_ENV=$PUFF_ENV \
        STRICT_BACKUP_ALERT_ENV=$ALERT_ENV \
        STRICT_BACKUP_ALERT_STAGE=$STAGE \
        STRICT_BACKUP_ALERT_FILE=$BACKUP_FILE \
        STRICT_BACKUP_ALERT_TIMESTAMP=$TIMESTAMP \
        STRICT_BACKUP_ALERT_SERVER=$server \
        STRICT_BACKUP_ALERT_ENVIRONMENT=production \
        STRICT_BACKUP_ALERT_UNIT=$UNIT \
        python3 "$script_dir/notify-strict-backup-failure.py"
    )
    status=$?
    set -e
    printf '%s\n' "$output"
    if [ "$status" -eq 0 ] && printf '%s\n' "$output" | grep -qx 'notify=sent'; then
        printf '%s\n' "$identity" >> "$STATE_FILE"
        chmod 0600 "$STATE_FILE"
    fi
    exit "$status"
}

main "$@"
