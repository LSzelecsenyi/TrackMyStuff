"""Local tests for the Play API probe. They do not contact Google or production."""

from __future__ import annotations

import base64
import hashlib
import io
import json
import os
import random
import tempfile
import unittest
from contextlib import redirect_stdout

import play_api_probe as probe

SHA256_DIGESTINFO = bytes.fromhex("3031300d060960864801650304020105000420")
EMAIL = "probe@example.iam.gserviceaccount.com"


class FakeHttp:
    def __init__(
        self,
        oauth_status=200,
        oauth_body=None,
        play_status=400,
        oauth_error=None,
        play_error=None,
        play_body=None,
        list_status=None,
        list_body=None,
        list_error=None,
    ):
        self.oauth_status = oauth_status
        self.oauth_body = oauth_body if oauth_body is not None else b'{"access_token":"ya29.fake-test-token","expires_in":3600}'
        self.play_status = play_status
        self.play_body = play_body if play_body is not None else b'{"error":"should-not-be-printed","access_token":"ya29.LEAK"}'
        self.list_status = self.play_status if list_status is None else list_status
        self.list_body = self.play_body if list_body is None else list_body
        self.oauth_error = oauth_error
        self.play_error = play_error
        self.list_error = list_error
        self.play_called = False
        self.list_called = False
        self.posts = 0
        self.form = None
        self.play_url = None
        self.list_url = None
        self.authorizations = []

    def post_form(self, url, form, timeout):
        self.posts += 1
        self.form = dict(form)
        self.oauth_url = url
        if self.oauth_error is not None:
            raise self.oauth_error
        return self.oauth_status, self.oauth_body

    def get(self, url, headers, timeout):
        authorization = headers.get("Authorization", "")
        self.authorizations.append(authorization)
        self.authorization_is_bearer = authorization.startswith("Bearer ") and len(authorization) > len("Bearer ")
        if url == _subscriptions_url():
            self.list_called = True
            self.list_url = url
            if self.list_error is not None:
                raise self.list_error
            return self.list_status, self.list_body
        self.play_called = True
        self.play_url = url
        if self.play_error is not None:
            raise self.play_error
        return self.play_status, self.play_body


class ProbeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.public_exponent = 65537
        cls.modulus, cls.private_exponent = _rsa_pair(1024, cls.public_exponent)
        cls.pem = _pkcs8_pem(cls.modulus, cls.public_exponent, cls.private_exponent)

    def test_oauth_success_and_play_invalid_token(self):
        for status in (400, 404):
            with self.subTest(status=status):
                http = FakeHttp(play_status=status)
                report = self._run(http)
                self.assertEqual(report.readable, "YES")
                self.assertEqual(report.oauth_status, "200")
                self.assertEqual(report.oauth_result, "SUCCESS")
                self.assertEqual(report.play_status, str(status))
                self.assertEqual(report.play_result, "INVALID_TOKEN")
                self.assertTrue(http.play_called)
                self.assertTrue(http.authorization_is_bearer)
                self.assertEqual(http.oauth_url, probe.TOKEN_URL)
                self.assertEqual(http.play_url, probe.PLAY_URL.format(package=probe.PACKAGE, token=probe.FAKE_TOKEN))
                self._assert_jwt(http.form)

    def test_oauth_http_failure_does_not_call_play(self):
        for status, body in (
            (400, b'{"error":"invalid_grant","error_description":"secret-detail"}'),
            (401, b'{"error":"unauthorized"}'),
        ):
            with self.subTest(status=status):
                http = FakeHttp(oauth_status=status, oauth_body=body)
                report = self._run(http)
                self.assertEqual(report.oauth_status, str(status))
                self.assertEqual(report.oauth_result, "FAILURE")
                self.assertEqual(report.play_status, "NOT_CALLED")
                self.assertEqual(report.play_result, "OTHER")
                self.assertFalse(http.play_called)
                self.assertFalse(http.list_called)
                self.assertEqual(report.list_status, "NOT_CALLED")
                self.assertEqual(report.list_result, "OTHER")
                self.assertEqual(http.posts, 1)
                self.assertNotIn("secret-detail", report.text())

    def test_oauth_200_without_access_token_is_failure(self):
        http = FakeHttp(oauth_body=b'{"token_type":"Bearer"}')
        report = self._run(http)
        self.assertEqual(report.oauth_status, "200")
        self.assertEqual(report.oauth_result, "FAILURE")
        self.assertFalse(http.play_called)

    def test_play_permission_denied(self):
        for status in (401, 403):
            with self.subTest(status=status):
                report = self._run(FakeHttp(play_status=status))
                self.assertEqual(report.oauth_result, "SUCCESS")
                self.assertEqual(report.play_status, str(status))
                self.assertEqual(report.play_result, "PERMISSION_DENIED")

    def test_structured_play_401_error(self):
        body = json.dumps({
            "error": {
                "code": 401,
                "status": "PERMISSION_DENIED",
                "message": "The current user has insufficient permissions to perform the requested operation.",
                "errors": [{
                    "domain": "androidpublisher",
                    "reason": "permissionDenied",
                    "message": "raw-errors-message-should-not-print",
                }],
                "details": [{
                    "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                    "reason": "PERMISSION_DENIED",
                    "metadata": {"token": "ya29.DETAILTOKEN"},
                }],
            }
        }).encode("utf-8")
        text = self._run(FakeHttp(play_status=401, play_body=body)).text()
        self.assertIn("Google Play API HTTP status: 401", text)
        self.assertIn("Google Play API result: PERMISSION_DENIED", text)
        self.assertIn("Google error.status: PERMISSION_DENIED", text)
        self.assertIn(
            "Google error.message: The current user has insufficient permissions to perform the requested operation.",
            text,
        )
        self.assertIn("Google error.errors.reason: permissionDenied", text)
        self.assertIn("Google error.details.reason: PERMISSION_DENIED", text)
        self.assertNotIn("raw-errors-message-should-not-print", text)
        self.assertNotIn("ya29.DETAILTOKEN", text)
        self.assertNotIn("type.googleapis.com", text)
        self.assertNotIn("{", text)
        self.assertNotIn("}", text)

    def test_malformed_play_error_responses(self):
        bodies = (
            b"",
            b"not-json",
            b"{",
            b'{"error":"plain-error-text"}',
            b'{"error":[1,2,3]}',
            b'{"nope":true}',
            b"\xff\xfe",
            b'{"error":{"status":"PERMISSION_DENIED"}}' + b" " * 9000,
        )
        for body in bodies:
            with self.subTest(body=body[:24]):
                text = self._run(FakeHttp(play_status=401, play_body=body)).text()
                self.assertIn("Google error.status: NONE", text)
                self.assertIn("Google error.message: NONE", text)
                self.assertNotIn("errors.reason", text)
                self.assertNotIn("details.reason", text)
                self.assertNotIn("plain-error-text", text)
                self.assertNotIn("not-json", text)
                self.assertNotIn("{", text)

    def test_play_error_message_redacts_sensitive_values(self):
        access_token = "ya29.super-secret-token"
        jwt = "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ4In0.c2lnbmF0dXJldmFsdWU"
        pem = "-----BEGIN PRIVATE KEY-----\nMIIEsecretkeymaterial\n-----END PRIVATE KEY-----"
        message = (
            "denied "
            + access_token
            + " Authorization: Bearer "
            + access_token
            + " "
            + jwt
            + " "
            + probe.FAKE_TOKEN
            + " "
            + pem
        )
        body = json.dumps({
            "error": {
                "status": "UNAUTHENTICATED",
                "message": message,
                "errors": [{"reason": access_token}, {"reason": "authError"}],
                "details": [{"reason": jwt}],
            }
        }).encode("utf-8")
        text = self._run(FakeHttp(play_status=401, play_body=body)).text()
        self.assertNotIn(access_token, text)
        self.assertNotIn("super-secret-token", text)
        self.assertNotIn(jwt, text)
        self.assertNotIn(probe.FAKE_TOKEN, text)
        self.assertNotIn("MIIEsecretkeymaterial", text)
        self.assertNotIn("BEGIN PRIVATE KEY", text)
        self.assertNotIn("Bearer", text)
        self.assertIn("Google error.status: UNAUTHENTICATED", text)
        self.assertIn("Google error.errors.reason: authError", text)
        self.assertNotIn("Google error.details.reason", text)
        self.assertTrue(
            "Google error.message: OMITTED" in text or "[REDACTED]" in text
        )
        unsafe = json.dumps({
            "error": {
                "status": "UNAUTHENTICATED",
                "message": "failed \u0001 " + access_token,
            }
        }).encode("utf-8")
        omitted = self._run(FakeHttp(play_status=401, play_body=unsafe)).text()
        self.assertIn("Google error.message: OMITTED", omitted)
        self.assertNotIn(access_token, omitted)
        self.assertNotIn("super-secret-token", omitted)

    def test_play_error_missing_fields(self):
        body = json.dumps({
            "error": {
                "code": 401,
                "errors": [{"domain": "global"}],
                "details": [{}],
            }
        }).encode("utf-8")
        text = self._run(FakeHttp(play_status=401, play_body=body)).text()
        self.assertIn("Google error.status: NONE", text)
        self.assertIn("Google error.message: NONE", text)
        self.assertNotIn("errors.reason", text)
        self.assertNotIn("details.reason", text)
        self.assertNotIn("global", text)
        self.assertNotIn("code", text)

    def test_play_other_status(self):
        report = self._run(FakeHttp(play_status=500))
        self.assertEqual(report.play_result, "OTHER")
        self.assertEqual(report.play_status, "500")

    def test_oauth_timeout(self):
        report = self._run(FakeHttp(oauth_error=probe.TransportTimeout()))
        self.assertEqual(report.readable, "YES")
        self.assertEqual(report.oauth_status, "NONE")
        self.assertEqual(report.oauth_result, "FAILURE")
        self.assertEqual(report.play_status, "NOT_CALLED")
        self.assertEqual(report.play_result, "OTHER")

    def test_play_timeout(self):
        report = self._run(FakeHttp(play_error=probe.TransportTimeout(), list_status=200, list_body=b'{"subscriptions":[{"productId":"strict_pro"}]}'))
        self.assertEqual(report.oauth_result, "SUCCESS")
        self.assertEqual(report.play_status, "NONE")
        self.assertEqual(report.play_result, "UNAVAILABLE")
        self.assertTrue(report.list_status == "200")
        self.assertEqual(report.list_result, "SUCCESS")
        self.assertNotIn("strict_pro", report.text())

    def test_both_endpoints_status_classes(self):
        catalog = json.dumps({
            "subscriptions": [{"productId": "strict_pro", "basePlans": [{"basePlanId": "monthly"}]}],
            "nextPageToken": "ya29.page-token-secret",
        }).encode("utf-8")
        purchase_ok = b'{"subscriptionState":"SUBSCRIPTION_STATE_ACTIVE","latestOrderId":"GPA.1234-5678"}'
        success = self._run(FakeHttp(play_status=200, play_body=purchase_ok, list_status=200, list_body=catalog))
        success_text = success.text()
        self.assertEqual(success.play_status, "200")
        self.assertEqual(success.play_result, "OTHER")
        self.assertEqual(success.list_status, "200")
        self.assertEqual(success.list_result, "SUCCESS")
        self.assertNotIn("strict_pro", success_text)
        self.assertNotIn("monthly", success_text)
        self.assertNotIn("GPA.1234-5678", success_text)
        self.assertNotIn("ya29", success_text)
        self.assertNotIn("nextPageToken", success_text)
        self.assertNotIn("{", success_text)
        for status in (400, 404):
            with self.subTest(status=status):
                report = self._run(FakeHttp(play_status=status, list_status=status, list_body=b""))
                self.assertEqual(report.play_result, "INVALID_TOKEN")
                self.assertEqual(report.list_status, str(status))
                self.assertEqual(report.list_result, "OTHER")

    def test_purchase_401_does_not_skip_subscription_list(self):
        play_body = json.dumps({
            "error": {
                "status": "PERMISSION_DENIED",
                "message": "The current user has insufficient permissions to perform the requested operation.",
                "errors": [{"reason": "permissionDenied"}],
            }
        }).encode("utf-8")
        catalog = b'{"subscriptions":[{"productId":"strict_pro"}]}'
        http = FakeHttp(play_status=401, play_body=play_body, list_status=200, list_body=catalog)
        report = self._run(http)
        text = report.text()
        self.assertEqual(http.posts, 1)
        self.assertEqual(len(http.authorizations), 2)
        self.assertEqual(http.authorizations[0], http.authorizations[1])
        self.assertTrue(http.play_called)
        self.assertTrue(http.list_called)
        self.assertEqual(http.play_url, probe.PLAY_URL.format(package=probe.PACKAGE, token=probe.FAKE_TOKEN))
        self.assertEqual(http.list_url, _subscriptions_url())
        self.assertEqual(report.play_result, "PERMISSION_DENIED")
        self.assertEqual(report.list_status, "200")
        self.assertEqual(report.list_result, "SUCCESS")
        self.assertIn("Google error.errors.reason: permissionDenied", text)
        self.assertNotIn("Subscription list Google error", text)
        self.assertNotIn("strict_pro", text)
        self.assertNotIn("ya29", text)

    def test_both_endpoints_permission_denied(self):
        play_body = json.dumps({
            "error": {"status": "PERMISSION_DENIED", "message": "purchase denied", "errors": [{"reason": "permissionDenied"}]}
        }).encode("utf-8")
        list_body = json.dumps({
            "error": {"status": "PERMISSION_DENIED", "message": "catalog denied", "errors": [{"reason": "permissionDenied"}]}
        }).encode("utf-8")
        text = self._run(FakeHttp(play_status=401, play_body=play_body, list_status=401, list_body=list_body)).text()
        self.assertIn("Google Play API HTTP status: 401", text)
        self.assertIn("Google Play API result: PERMISSION_DENIED", text)
        self.assertIn("Subscription list HTTP status: 401", text)
        self.assertIn("Subscription list result: PERMISSION_DENIED", text)
        self.assertIn("Subscription list Google error.status: PERMISSION_DENIED", text)
        self.assertIn("Subscription list Google error.message: catalog denied", text)
        self.assertIn("Subscription list Google error.errors.reason: permissionDenied", text)

    def test_subscription_list_403(self):
        report = self._run(FakeHttp(play_status=400, list_status=403, list_body=b""))
        self.assertEqual(report.play_result, "INVALID_TOKEN")
        self.assertEqual(report.list_status, "403")
        self.assertEqual(report.list_result, "PERMISSION_DENIED")

    def test_subscription_list_timeout(self):
        http = FakeHttp(play_status=400, list_error=probe.TransportTimeout(), list_body=b"")
        report = self._run(http)
        self.assertTrue(http.play_called)
        self.assertTrue(http.list_called)
        self.assertEqual(report.play_result, "INVALID_TOKEN")
        self.assertEqual(report.list_status, "NONE")
        self.assertEqual(report.list_result, "UNAVAILABLE")

    def test_subscription_list_redacts_sensitive_errors(self):
        access_token = "ya29.list-secret-token"
        jwt = "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ4In0.c2lnbmF0dXJldmFsdWU"
        pem = "-----BEGIN PRIVATE KEY-----\nMIIElistsecret\n-----END PRIVATE KEY-----"
        message = "catalog " + access_token + " Bearer " + access_token + " " + jwt + " " + probe.FAKE_TOKEN + " " + pem
        list_body = json.dumps({
            "error": {
                "status": "PERMISSION_DENIED",
                "message": message,
                "errors": [{"reason": access_token}, {"reason": "permissionDenied"}],
                "details": [{"reason": jwt}, {"reason": "PERMISSION_DENIED"}],
            }
        }).encode("utf-8")
        text = self._run(FakeHttp(play_status=401, play_body=b"", list_status=401, list_body=list_body)).text()
        self.assertNotIn(access_token, text)
        self.assertNotIn("list-secret-token", text)
        self.assertNotIn(jwt, text)
        self.assertNotIn(probe.FAKE_TOKEN, text)
        self.assertNotIn("MIIElistsecret", text)
        self.assertNotIn("BEGIN PRIVATE KEY", text)
        self.assertNotIn("Bearer", text)
        self.assertIn("Subscription list Google error.status: PERMISSION_DENIED", text)
        self.assertIn("Subscription list Google error.errors.reason: permissionDenied", text)
        self.assertIn("Subscription list Google error.details.reason: PERMISSION_DENIED", text)
        self.assertTrue("OMITTED" in text or "[REDACTED]" in text)

    def test_play_transport_failure(self):
        report = self._run(FakeHttp(play_error=probe.TransportFailed()))
        self.assertEqual(report.play_result, "UNAVAILABLE")
        self.assertEqual(report.play_status, "NONE")

    def test_missing_credential_file(self):
        report = probe.diagnose(os.path.join(tempfile.gettempdir(), "missing-play-credential.json"), FakeHttp())
        self.assertEqual(report.readable, "NO")
        self.assertEqual(report.oauth_result, "FAILURE")
        self.assertEqual(report.play_status, "NOT_CALLED")

    def test_malformed_credentials(self):
        cases = (
            b"{",
            b'{"client_email":"probe@example.iam.gserviceaccount.com"}',
            b'{"client_email":"","private_key":""}',
            b'{"client_email":"probe@example.iam.gserviceaccount.com","private_key":"not-a-key"}',
        )
        for raw in cases:
            with self.subTest(raw=raw[:24]):
                report = self._run(FakeHttp(), raw)
                self.assertEqual(report.readable, "YES")
                self.assertEqual(report.oauth_status, "NONE")
                self.assertEqual(report.oauth_result, "FAILURE")
                self.assertEqual(report.play_status, "NOT_CALLED")

    def test_stdout_does_not_contain_secrets(self):
        http = FakeHttp()
        path = self._write(self._credential())
        try:
            buffer = io.StringIO()
            with redirect_stdout(buffer):
                report = probe.diagnose(path, http, now=1_700_000_000)
                probe.sys.stdout.write(report.text() + "\n")
            printed = buffer.getvalue()
        finally:
            os.remove(path)
        self.assertNotIn("ya29", printed)
        self.assertNotIn("LEAK", printed)
        self.assertNotIn("should-not-be-printed", printed)
        self.assertNotIn("BEGIN PRIVATE KEY", printed)
        self.assertNotIn("Bearer", printed)
        self.assertNotIn(EMAIL, printed)
        self.assertIn("Google Play API result: INVALID_TOKEN", printed)

    def test_signature_is_pkcs1_sha256(self):
        message = b"abc"
        signature = probe.rsa_sha256_sign(self.modulus, self.private_exponent, message)
        size = (self.modulus.bit_length() + 7) // 8
        self.assertEqual(len(signature), size)
        encoded = pow(int.from_bytes(signature, "big"), self.public_exponent, self.modulus).to_bytes(size, "big")
        self.assertEqual(encoded[:2], b"\x00\x01")
        separator = encoded.index(b"\x00", 2)
        digest_info = encoded[separator + 1:]
        self.assertEqual(digest_info[:19], SHA256_DIGESTINFO)
        self.assertEqual(digest_info[19:], hashlib.sha256(message).digest())

    def _run(self, http: FakeHttp, raw: bytes | None = None) -> probe.Report:
        path = self._write(raw if raw is not None else self._credential())
        try:
            return probe.diagnose(path, http, now=1_700_000_000)
        finally:
            os.remove(path)

    def _credential(self) -> bytes:
        body = {"type": "service_account", "client_email": EMAIL, "private_key": self.pem}
        return json.dumps(body).encode("utf-8")

    def _write(self, raw: bytes) -> str:
        handle = tempfile.NamedTemporaryFile(delete=False)
        handle.write(raw)
        handle.close()
        return handle.name

    def _assert_jwt(self, form: dict):
        self.assertEqual(form["grant_type"], "urn:ietf:params:oauth:grant-type:jwt-bearer")
        assertion = form["assertion"]
        header_part, payload_part, signature_part = assertion.split(".")
        self.assertNotIn("=", assertion)
        self.assertNotIn("+", assertion)
        self.assertNotIn("/", assertion)
        header = json.loads(_b64url_decode(header_part))
        payload = json.loads(_b64url_decode(payload_part))
        self.assertEqual(header, {"alg": "RS256", "typ": "JWT"})
        self.assertEqual(payload["iss"], EMAIL)
        self.assertEqual(payload["scope"], probe.SCOPE)
        self.assertEqual(payload["aud"], probe.TOKEN_URL)
        self.assertEqual(payload["iat"], 1_700_000_000)
        self.assertEqual(payload["exp"], 1_700_003_600)
        signature = _b64url_decode(signature_part)
        size = (self.modulus.bit_length() + 7) // 8
        encoded = pow(int.from_bytes(signature, "big"), self.public_exponent, self.modulus).to_bytes(size, "big")
        signing_input = (header_part + "." + payload_part).encode("ascii")
        self.assertEqual(encoded, probe._emsa_pkcs1_sha256(signing_input, size))


def _subscriptions_url() -> str:
    return probe.SUBSCRIPTIONS_URL.format(package=probe.PACKAGE)


def _b64url_decode(value: str) -> bytes:
    pad = "=" * ((4 - len(value) % 4) % 4)
    return base64.urlsafe_b64decode(value + pad)


def _rsa_pair(bits: int, public_exponent: int) -> tuple[int, int]:
    prime_bits = bits // 2
    first = _prime(prime_bits)
    second = _prime(prime_bits)
    while second == first:
        second = _prime(prime_bits)
    modulus = first * second
    phi = (first - 1) * (second - 1)
    private_exponent = pow(public_exponent, -1, phi)
    return modulus, private_exponent


def _prime(bits: int) -> int:
    while True:
        candidate = random.getrandbits(bits) | 1 | (1 << (bits - 1))
        if _is_prime(candidate):
            return candidate


def _is_prime(number: int) -> bool:
    if number < 2:
        return False
    for small in (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31):
        if number % small == 0:
            return number == small
    decomposition = number - 1
    shifts = 0
    while decomposition % 2 == 0:
        decomposition //= 2
        shifts += 1
    for _round in range(16):
        base = random.randrange(2, number - 1)
        value = pow(base, decomposition, number)
        if value in (1, number - 1):
            continue
        for _shift in range(shifts - 1):
            value = pow(value, 2, number)
            if value == number - 1:
                break
        else:
            return False
    return True


def _pkcs8_pem(modulus: int, public_exponent: int, private_exponent: int) -> str:
    prime = 65521
    rsa = b"".join(
        (
            _der_int(0),
            _der_int(modulus),
            _der_int(public_exponent),
            _der_int(private_exponent),
            _der_int(prime),
            _der_int(prime),
            _der_int(1),
            _der_int(1),
            _der_int(1),
        )
    )
    algorithm = bytes.fromhex("300d06092a864886f70d0101010500")
    outer = b"".join((_der_int(0), algorithm, _der_octet(_der_seq(rsa))))
    der = _der_seq(outer)
    encoded = base64.encodebytes(der).decode("ascii")
    return "-----BEGIN PRIVATE KEY-----\n" + encoded + "-----END PRIVATE KEY-----\n"


def _der_len(length: int) -> bytes:
    if length < 0x80:
        return bytes((length,))
    encoded = length.to_bytes((length.bit_length() + 7) // 8, "big")
    return bytes((0x80 | len(encoded),)) + encoded


def _der_tlv(tag: int, content: bytes) -> bytes:
    return bytes((tag,)) + _der_len(len(content)) + content


def _der_int(value: int) -> bytes:
    if value == 0:
        content = b"\x00"
    else:
        content = value.to_bytes((value.bit_length() + 7) // 8, "big")
        if content[0] & 0x80:
            content = b"\x00" + content
    return _der_tlv(0x02, content)


def _der_octet(content: bytes) -> bytes:
    return _der_tlv(0x04, content)


def _der_seq(content: bytes) -> bytes:
    return _der_tlv(0x30, content)


if __name__ == "__main__":
    unittest.main()
