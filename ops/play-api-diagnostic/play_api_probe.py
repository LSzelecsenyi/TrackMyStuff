#!/usr/bin/env python3
"""Google Play Developer API connectivity probe.

Signs a service-account JWT in memory and prints only HTTP status classes.
It does not acknowledge a purchase, write a token, or print credential material.
"""

from __future__ import annotations

import hashlib
import json
import re
import socket
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

TOKEN_URL = "https://oauth2.googleapis.com/token"
PLAY_URL = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}/purchases/subscriptionsv2/tokens/{token}"
SUBSCRIPTIONS_URL = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}/subscriptions"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
PACKAGE = "com.strictworkout.app"
FAKE_TOKEN = "strict-connectivity-test"
DEFAULT_CREDENTIAL = "/etc/strict/play-service-account.json"
TIMEOUT_SECONDS = 20
MAX_CREDENTIAL_BYTES = 1_000_000
MAX_ERROR_BODY_BYTES = 8_192
MAX_ERROR_MESSAGE_CHARS = 200
JWT_HEADER = b'{"alg":"RS256","typ":"JWT"}'
_SAFE_CODE = re.compile(r"^[A-Za-z][A-Za-z0-9_]{0,63}$")
_PEM = re.compile(
    r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.*?-----END [A-Z0-9 ]*PRIVATE KEY-----",
    re.DOTALL | re.IGNORECASE,
)
_MESSAGE_REDACTIONS = (
    re.compile(r"(?i)\bauthorization\s*:\s*(?:bearer\s+)?\S+"),
    re.compile(r"(?i)\bbearer\s+\S+"),
    re.compile(r"\bya29\.[A-Za-z0-9._\-]+"),
    re.compile(r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}"),
    re.compile(r"(?i)\"private_key\"\s*:\s*\"(?:\\.|[^\"])*\""),
    re.compile(r"(?i)(tokens/)[^\s/\"']+"),
    re.compile(r"[A-Za-z0-9+/_-]{24,}={0,2}"),
)
_STILL_SENSITIVE = re.compile(
    r"ya29\.|BEGIN |PRIVATE KEY|Bearer |eyJ[A-Za-z0-9_-]{8,}\.|\"private_key\"",
    re.IGNORECASE,
)
SHA256_DIGESTINFO = bytes.fromhex("3031300d060960864801650304020105000420")


class CredentialUnreadable(Exception):
    pass


class CredentialMalformed(Exception):
    pass


class TransportTimeout(Exception):
    pass


class TransportFailed(Exception):
    pass


@dataclass(frozen=True)
class Report:
    readable: str
    oauth_status: str
    oauth_result: str
    play_status: str
    play_result: str
    play_error_lines: tuple[str, ...] = ()
    list_status: str = "NOT_CALLED"
    list_result: str = "OTHER"
    list_error_lines: tuple[str, ...] = ()

    def text(self) -> str:
        lines = [
            f"Credential file readable: {self.readable}",
            f"OAuth HTTP status: {self.oauth_status}",
            f"OAuth result: {self.oauth_result}",
            f"Google Play API HTTP status: {self.play_status}",
            f"Google Play API result: {self.play_result}",
        ]
        lines.extend(self.play_error_lines)
        lines.append(f"Subscription list HTTP status: {self.list_status}")
        lines.append(f"Subscription list result: {self.list_result}")
        lines.extend(self.list_error_lines)
        return "\n".join(lines)


def diagnose(path: str, http: "HttpClient", now: int | None = None) -> Report:
    try:
        email, modulus, private_exponent = load_rsa(path)
    except CredentialUnreadable:
        return _not_called(readable="NO")
    except CredentialMalformed:
        return _not_called(readable="YES")

    issued_at = int(time.time()) if now is None else int(now)
    try:
        assertion = sign_assertion(email, modulus, private_exponent, issued_at)
    except CredentialMalformed:
        return _not_called(readable="YES")
    finally:
        del private_exponent
        del modulus
        del email

    try:
        oauth_status, oauth_body = http.post_form(
            TOKEN_URL,
            {
                "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion": assertion,
            },
            TIMEOUT_SECONDS,
        )
    except TransportTimeout:
        return _not_called(readable="YES")
    except TransportFailed:
        return _not_called(readable="YES")
    finally:
        del assertion

    if not _oauth_succeeded(oauth_status, oauth_body):
        return Report("YES", str(oauth_status), "FAILURE", "NOT_CALLED", "OTHER")

    package = urllib.parse.quote(PACKAGE, safe="")
    play_url = PLAY_URL.format(package=package, token=urllib.parse.quote(FAKE_TOKEN, safe=""))
    list_url = SUBSCRIPTIONS_URL.format(package=package)
    access_token = _oauth_access_token(oauth_body)
    del oauth_body
    try:
        play_status, play_result, play_errors = _read_only_get(
            http, play_url, access_token, _play_result, ""
        )
        list_status, list_result, list_errors = _read_only_get(
            http, list_url, access_token, _list_result, "Subscription list "
        )
        return Report(
            "YES",
            str(oauth_status),
            "SUCCESS",
            play_status,
            play_result,
            play_errors,
            list_status,
            list_result,
            list_errors,
        )
    finally:
        del access_token


def load_rsa(path: str) -> tuple[str, int, int]:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            raw = handle.read(MAX_CREDENTIAL_BYTES)
            if handle.read(1):
                raise CredentialMalformed
    except (OSError, UnicodeError):
        raise CredentialUnreadable from None
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        raise CredentialMalformed from None
    finally:
        del raw
    if not isinstance(parsed, dict):
        raise CredentialMalformed
    email = parsed.get("client_email")
    pem = parsed.get("private_key")
    del parsed
    if not isinstance(email, str) or email == "" or not isinstance(pem, str) or pem == "":
        raise CredentialMalformed
    modulus, private_exponent = rsa_private_components(pem)
    del pem
    return email, modulus, private_exponent


def sign_assertion(email: str, modulus: int, private_exponent: int, issued_at: int) -> str:
    claims = json.dumps(
        {
            "iss": email,
            "scope": SCOPE,
            "aud": TOKEN_URL,
            "iat": issued_at,
            "exp": issued_at + 3600,
        },
        separators=(",", ":"),
    ).encode("ascii")
    signing_input = b64url(JWT_HEADER) + "." + b64url(claims)
    signature = rsa_sha256_sign(modulus, private_exponent, signing_input.encode("ascii"))
    return signing_input + "." + b64url(signature)


def rsa_private_components(pem: str) -> tuple[int, int]:
    if "BEGIN PRIVATE KEY" not in pem or "END PRIVATE KEY" not in pem:
        raise CredentialMalformed
    lines = []
    for line in pem.splitlines():
        stripped = line.strip()
        if stripped == "" or stripped.startswith("-----"):
            continue
        lines.append(stripped)
    try:
        der = _b64decode("".join(lines))
    except Exception:
        raise CredentialMalformed from None
    return _pkcs8_rsa(der)


def rsa_sha256_sign(modulus: int, private_exponent: int, message: bytes) -> bytes:
    size = (modulus.bit_length() + 7) // 8
    encoded = _emsa_pkcs1_sha256(message, size)
    signature = pow(int.from_bytes(encoded, "big"), private_exponent, modulus)
    return signature.to_bytes(size, "big")


def _emsa_pkcs1_sha256(message: bytes, size: int) -> bytes:
    digest_info = SHA256_DIGESTINFO + hashlib.sha256(message).digest()
    padding_len = size - len(digest_info) - 3
    if padding_len < 8:
        raise CredentialMalformed
    return b"\x00\x01" + (b"\xff" * padding_len) + b"\x00" + digest_info


def _pkcs8_rsa(der: bytes) -> tuple[int, int]:
    try:
        tag, outer, _rest = _read_tlv(der, 0)
        if tag != 0x30:
            raise CredentialMalformed
        index = 0
        _tag, _value, index = _read_tlv(outer, index)
        _tag, _value, index = _read_tlv(outer, index)
        octet_tag, octet, _index = _read_tlv(outer, index)
        if octet_tag != 0x04:
            raise CredentialMalformed
        rsa_tag, rsa, _rest = _read_tlv(octet, 0)
        if rsa_tag != 0x30:
            raise CredentialMalformed
        cursor = 0
        _tag, _version, cursor = _read_tlv(rsa, cursor)
        _tag, modulus, cursor = _read_tlv(rsa, cursor)
        _tag, _exponent, cursor = _read_tlv(rsa, cursor)
        _tag, private_exponent, _cursor = _read_tlv(rsa, cursor)
        return _der_integer(modulus), _der_integer(private_exponent)
    except CredentialMalformed:
        raise
    except Exception:
        raise CredentialMalformed from None


def _read_tlv(buf: bytes, index: int) -> tuple[int, bytes, int]:
    if index >= len(buf):
        raise CredentialMalformed
    tag = buf[index]
    index += 1
    length, index = _read_len(buf, index)
    end = index + length
    if end > len(buf):
        raise CredentialMalformed
    return tag, buf[index:end], end


def _read_len(buf: bytes, index: int) -> tuple[int, int]:
    if index >= len(buf):
        raise CredentialMalformed
    first = buf[index]
    index += 1
    if first < 0x80:
        return first, index
    count = first & 0x7F
    if count == 0 or count > 4 or index + count > len(buf):
        raise CredentialMalformed
    length = int.from_bytes(buf[index:index + count], "big")
    index += count
    return length, index


def _der_integer(content: bytes) -> int:
    if content == b"":
        raise CredentialMalformed
    return int.from_bytes(content, "big")


def _b64decode(value: str) -> bytes:
    import base64

    pad = "=" * ((4 - len(value) % 4) % 4)
    return base64.b64decode(value + pad, validate=True)


def b64url(value: bytes) -> str:
    import base64

    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _oauth_succeeded(status: int, body: bytes) -> bool:
    return 200 <= status < 300 and _oauth_access_token(body) != ""


def _oauth_access_token(body: bytes) -> str:
    try:
        parsed = json.loads(body.decode("utf-8"))
    except Exception:
        return ""
    if not isinstance(parsed, dict):
        return ""
    token = parsed.get("access_token")
    del parsed
    if not isinstance(token, str):
        return ""
    return token


def _play_result(status: int) -> str:
    if status in (400, 404):
        return "INVALID_TOKEN"
    if status in (401, 403):
        return "PERMISSION_DENIED"
    return "OTHER"


def _list_result(status: int) -> str:
    if 200 <= status < 300:
        return "SUCCESS"
    if status in (401, 403):
        return "PERMISSION_DENIED"
    return "OTHER"


def _read_only_get(http, url: str, access_token: str, classify, error_prefix: str):
    try:
        status, body = http.get(
            url,
            {"Authorization": "Bearer " + access_token},
            TIMEOUT_SECONDS,
        )
    except TransportTimeout:
        return "NONE", "UNAVAILABLE", ()
    except TransportFailed:
        return "NONE", "UNAVAILABLE", ()
    try:
        if status < 400:
            return str(status), classify(status), ()
        lines = tuple(error_prefix + line for line in play_error_lines(body))
        return str(status), classify(status), lines
    finally:
        del body


def play_error_lines(body: bytes) -> tuple[str, ...]:
    try:
        return _play_error_lines(body)
    except Exception:
        return ("Google error.status: NONE", "Google error.message: OMITTED")


def _play_error_lines(body: bytes) -> tuple[str, ...]:
    error = _error_object(body)
    if error is None:
        return ("Google error.status: NONE", "Google error.message: NONE")
    lines = [
        "Google error.status: " + (_safe_code(error.get("status")) or "NONE"),
        "Google error.message: " + _safe_message(error.get("message")),
    ]
    error_reasons = _reason_values(error.get("errors"))
    detail_reasons = _reason_values(error.get("details"))
    if error_reasons:
        lines.append("Google error.errors.reason: " + ",".join(error_reasons))
    if detail_reasons:
        lines.append("Google error.details.reason: " + ",".join(detail_reasons))
    return tuple(lines)


def _error_object(body: bytes):
    if not isinstance(body, bytes) or body == b"" or len(body) > MAX_ERROR_BODY_BYTES:
        return None
    try:
        parsed = json.loads(body.decode("utf-8"))
    except Exception:
        return None
    if not isinstance(parsed, dict):
        return None
    error = parsed.get("error")
    if not isinstance(error, dict):
        return None
    return error


def _reason_values(items) -> tuple[str, ...]:
    if not isinstance(items, list):
        return ()
    found = []
    for item in items:
        if not isinstance(item, dict):
            continue
        code = _safe_code(item.get("reason"))
        if code and code not in found:
            found.append(code)
    return tuple(found)


def _safe_code(value) -> str | None:
    if not isinstance(value, str) or _SAFE_CODE.fullmatch(value) is None:
        return None
    lowered = value.lower()
    if "ya29" in lowered or "bearer" in lowered or "private" in lowered or lowered == FAKE_TOKEN:
        return None
    return value


def _safe_message(value) -> str:
    if not isinstance(value, str) or value.strip() == "":
        return "NONE"
    if any(ord(ch) < 9 or (13 < ord(ch) < 32) or ord(ch) == 127 for ch in value):
        return "OMITTED"
    text = _PEM.sub("[REDACTED]", value)
    text = text.replace("\r", " ").replace("\n", " ").replace("\t", " ")
    text = re.sub(re.escape(FAKE_TOKEN), "[REDACTED]", text)
    for pattern in _MESSAGE_REDACTIONS:
        text = pattern.sub("[REDACTED]", text)
    text = re.sub(r" {2,}", " ", text).strip()
    if (
        text == ""
        or len(text) > MAX_ERROR_MESSAGE_CHARS
        or _STILL_SENSITIVE.search(text)
        or FAKE_TOKEN in text
        or any(ord(ch) < 32 or ord(ch) == 127 for ch in text)
    ):
        return "OMITTED"
    return text


def _not_called(readable: str) -> Report:
    return Report(readable, "NONE", "FAILURE", "NOT_CALLED", "OTHER")


class HttpClient:
    def post_form(self, url: str, form: dict[str, str], timeout: int) -> tuple[int, bytes]:
        body = urllib.parse.urlencode(form).encode("ascii")
        return self._open(
            "POST",
            url,
            body,
            {"Content-Type": "application/x-www-form-urlencoded"},
            timeout,
        )

    def get(self, url: str, headers: dict[str, str], timeout: int) -> tuple[int, bytes]:
        return self._open("GET", url, None, headers, timeout)

    def _open(
        self,
        method: str,
        url: str,
        body: bytes | None,
        headers: dict[str, str],
        timeout: int,
    ) -> tuple[int, bytes]:
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        opener = urllib.request.build_opener(
            urllib.request.HTTPSHandler(context=ssl.create_default_context()),
            _NoRedirect(),
        )
        try:
            with opener.open(request, timeout=timeout) as response:
                return response.status, response.read(65536)
        except urllib.error.HTTPError as error:
            status = error.code
            try:
                payload = error.read(MAX_ERROR_BODY_BYTES)
            except Exception:
                payload = b""
            finally:
                error.close()
            if not isinstance(payload, bytes):
                payload = b""
            return status, payload[:MAX_ERROR_BODY_BYTES]
        except (TimeoutError, socket.timeout):
            raise TransportTimeout
        except urllib.error.URLError as error:
            reason = getattr(error, "reason", None)
            if isinstance(reason, (TimeoutError, socket.timeout)):
                raise TransportTimeout
            raise TransportFailed
        except ssl.SSLError:
            raise TransportFailed


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main(argv: list[str]) -> int:
    sys.tracebacklimit = 0
    path = DEFAULT_CREDENTIAL
    if len(argv) > 1:
        return 2
    try:
        report = diagnose(path, HttpClient())
    except Exception:
        report = _not_called(readable="NO")
    sys.stdout.write(report.text() + "\n")
    if report.play_result == "INVALID_TOKEN":
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
