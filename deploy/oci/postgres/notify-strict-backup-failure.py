#!/usr/bin/env python3
"""Send one Strict backup-failure message over SMTP with STARTTLS.

The process reads only SENDGRID_SMTP_HOST, SENDGRID_SMTP_PORT,
SENDGRID_SMTP_USERNAME, and SENDGRID_API_KEY from the PUFF env file.
The recipient is STRICT_BACKUP_ALERT_TO from the Strict alert env file.
The sender is always noreply@puff.hu. Secret values are never printed.
"""

import os
import re
import smtplib
import ssl
import stat
import sys
from email.message import EmailMessage
from email.utils import formatdate

SENDER = "noreply@puff.hu"
ALLOWED_SMTP = (
    "SENDGRID_SMTP_HOST",
    "SENDGRID_SMTP_PORT",
    "SENDGRID_SMTP_USERNAME",
    "SENDGRID_API_KEY",
)
SECRET_ENV_NAMES = (
    "SENDGRID_API_KEY",
    "SPRING_MAIL_PASSWORD",
    "SPRING_DATASOURCE_PASSWORD",
    "STRICT_POSTGRES_PASSWORD",
    "POSTGRES_PASSWORD",
    "DATABASE_URL",
)
STAGE_RE = re.compile(r"^[A-Za-z0-9_]{1,64}$")
FILE_RE = re.compile(r"^strict-automatic-[0-9]{8}T[0-9]{6}Z\.dump$")
TIME_RE = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")
HOST_RE = re.compile(r"^[A-Za-z0-9._-]{1,253}$")
EMAIL_RE = re.compile(r"^[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")
UNIT = "strict-db-backup.service"


class ConfigError(Exception):
    def __init__(self, stage):
        super().__init__(stage)
        self.stage = stage


class RedactingWriter:
    def __init__(self, raw, secrets):
        self.raw = raw
        self.secrets = [item for item in secrets if item]

    def write(self, text):
        for secret in self.secrets:
            text = text.replace(secret, "[redacted]")
        self.raw.write(text)
        return len(text)

    def flush(self):
        self.raw.flush()


def fail(stage):
    sys.stdout.write(f"notify=failed stage={stage}\n")
    raise SystemExit(1)


def assert_private(path, stage):
    try:
        info = os.lstat(path)
    except OSError:
        raise ConfigError(stage)
    if not stat.S_ISREG(info.st_mode) or stat.S_ISLNK(info.st_mode):
        raise ConfigError(stage)
    if stat.S_IMODE(info.st_mode) & 0o077:
        raise ConfigError("env_mode")


def read_allowed(path, allowed, stage):
    assert_private(path, stage)
    values = {key: "" for key in allowed}
    flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, flags)
    except OSError:
        raise ConfigError(stage)
    try:
        with os.fdopen(descriptor, "r", encoding="utf-8") as handle:
            descriptor = -1
            for raw in handle:
                line = raw.strip().strip("\r")
                if not line or line.startswith("#"):
                    continue
                if line.startswith("export "):
                    line = line[7:].lstrip()
                if "=" not in line:
                    raise ConfigError(stage)
                key, value = line.split("=", 1)
                key = key.strip()
                if key not in values:
                    continue
                values[key] = value.strip().strip("\r")
    except ConfigError:
        raise
    except Exception:
        raise ConfigError(stage)
    finally:
        if descriptor >= 0:
            os.close(descriptor)
    return values


def required(values, names, stage):
    for name in names:
        if not values.get(name):
            raise ConfigError(stage)
    return values


def message_fields():
    stage = os.environ.get("STRICT_BACKUP_ALERT_STAGE", "")
    if not STAGE_RE.fullmatch(stage):
        stage = "unknown"
    filename = os.environ.get("STRICT_BACKUP_ALERT_FILE", "")
    if filename and not FILE_RE.fullmatch(filename):
        filename = ""
    timestamp = os.environ.get("STRICT_BACKUP_ALERT_TIMESTAMP", "")
    if not TIME_RE.fullmatch(timestamp):
        raise ConfigError("timestamp")
    server = os.environ.get("STRICT_BACKUP_ALERT_SERVER", "")
    if not HOST_RE.fullmatch(server):
        raise ConfigError("server")
    if os.environ.get("STRICT_BACKUP_ALERT_ENVIRONMENT") != "production":
        raise ConfigError("environment")
    if os.environ.get("STRICT_BACKUP_ALERT_UNIT") != UNIT:
        raise ConfigError("unit")
    return stage, filename, timestamp, server


def build_message(recipient, stage, filename, timestamp, server):
    lines = [
        "Environment: production",
        f"Server: {server}",
        f"Timestamp: {timestamp}",
        "Backup status: FAILED",
        f"Failure stage: {stage}",
    ]
    if filename:
        lines.append(f"Backup file: {filename}")
    lines.append(f"Systemd unit: {UNIT}")
    message = EmailMessage()
    message["From"] = SENDER
    message["To"] = recipient
    message["Date"] = formatdate(localtime=False)
    message["Subject"] = "[STRICT PROD] PostgreSQL backup FAILED"
    message.set_content("\n".join(lines) + "\n", cte="7bit")
    return message


def send(message, smtp_values):
    host = smtp_values["SENDGRID_SMTP_HOST"]
    if not HOST_RE.fullmatch(host):
        raise ConfigError("credentials")
    try:
        port = int(smtp_values["SENDGRID_SMTP_PORT"])
    except ValueError:
        raise ConfigError("credentials")
    allow_test_port = os.environ.get("STRICT_BACKUP_ALERT_ALLOW_TEST_PORT") == "1"
    if port != 587 and not allow_test_port:
        raise ConfigError("smtp_port")
    if port <= 0 or port > 65535:
        raise ConfigError("credentials")

    context = ssl.create_default_context()
    ca_file = os.environ.get("STRICT_BACKUP_ALERT_CA_FILE", "")
    if ca_file:
        context.load_verify_locations(cafile=ca_file)

    try:
        smtp = smtplib.SMTP(host, port, timeout=20)
    except OSError:
        fail("smtp_connect")
    try:
        smtp.ehlo()
        code, _response = smtp.starttls(context=context)
        sys.stdout.write(f"smtp_starttls={code}\n")
        if code != 220:
            fail("smtp_starttls")
        smtp.ehlo()
        try:
            code, _response = smtp.login(
                smtp_values["SENDGRID_SMTP_USERNAME"],
                smtp_values["SENDGRID_API_KEY"],
            )
        except smtplib.SMTPAuthenticationError as exc:
            sys.stdout.write(f"smtp_auth={exc.smtp_code}\n")
            fail("smtp_auth")
        sys.stdout.write(f"smtp_auth={code}\n")
        if code != 235:
            fail("smtp_auth")
        try:
            refused = smtp.send_message(message)
        except smtplib.SMTPSenderRefused as exc:
            sys.stdout.write(f"smtp_mail_from={exc.smtp_code}\n")
            fail("smtp_sender")
        except smtplib.SMTPRecipientsRefused as exc:
            codes = [item[0] for item in exc.recipients.values()]
            sys.stdout.write(f"smtp_rcpt={codes[0] if codes else 550}\n")
            fail("smtp_recipient")
        except smtplib.SMTPDataError as exc:
            sys.stdout.write(f"smtp_data={exc.smtp_code}\n")
            fail("smtp_data")
        if refused:
            fail("smtp_recipient")
        sys.stdout.write("smtp_mail_from=250\n")
        sys.stdout.write("smtp_rcpt=250\n")
        sys.stdout.write("smtp_data=250\n")
        sys.stdout.write("notify=sent\n")
    finally:
        try:
            smtp.quit()
        except Exception:
            pass


def main():
    for name in SECRET_ENV_NAMES:
        if os.environ.get(name):
            fail("secret_in_environment")

    puff_path = os.environ.get("STRICT_BACKUP_PUFF_ENV", "/etc/puff/puff-backend-prod.env")
    alert_path = os.environ.get("STRICT_BACKUP_ALERT_ENV", "/etc/strict/strict-backup-alert.env")
    try:
        smtp_values = required(read_allowed(puff_path, ALLOWED_SMTP, "credentials"), ALLOWED_SMTP, "credentials")
        alert_values = required(
            read_allowed(alert_path, ("STRICT_BACKUP_ALERT_TO",), "recipient"),
            ("STRICT_BACKUP_ALERT_TO",),
            "recipient",
        )
        recipient = alert_values["STRICT_BACKUP_ALERT_TO"]
        if not EMAIL_RE.fullmatch(recipient):
            raise ConfigError("recipient")
        if recipient.casefold() == SENDER.casefold():
            raise ConfigError("sender_equals_recipient")
        stage, filename, timestamp, server = message_fields()
        secret = smtp_values["SENDGRID_API_KEY"]
        sys.stdout = RedactingWriter(sys.stdout, (secret,))
        sys.stderr = RedactingWriter(sys.stderr, (secret,))
        mail = build_message(recipient, stage, filename, timestamp, server)
        send(mail, smtp_values)
    except ConfigError as exc:
        fail(exc.stage)
    except SystemExit:
        raise
    except Exception:
        fail("internal")


if __name__ == "__main__":
    sys.tracebacklimit = 0
    main()
