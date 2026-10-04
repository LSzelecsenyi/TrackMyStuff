package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleIdTokenDiagnosisTest {

    @Test
    void audienceMismatchIsReportedBeforeSignature() {
        assertEquals(
                "audience-mismatch",
                GoogleIdTokenDiagnosis.reason(true, false, false, false, "valid")
        );
    }

    @Test
    void issuerMismatchIsReportedBeforeAudience() {
        assertEquals(
                "issuer-mismatch",
                GoogleIdTokenDiagnosis.reason(false, false, false, false, "invalid")
        );
    }

    @Test
    void expiredTokenIsRejectedWhenAudienceAndIssuerMatch() {
        assertEquals(
                "expired",
                GoogleIdTokenDiagnosis.reason(true, true, true, false, "valid")
        );
    }

    @Test
    void signatureMismatchIsRejectedWhenClaimsMatch() {
        assertEquals(
                "signature-mismatch",
                GoogleIdTokenDiagnosis.reason(true, true, false, true, "invalid")
        );
    }

    @Test
    void certificateFailureIsDistinctFromAnInvalidSignature() {
        assertEquals(
                "certificates-unavailable",
                GoogleIdTokenDiagnosis.reason(true, true, false, true, "unavailable")
        );
    }

    @Test
    void diagnosticLineKeepsAudienceIssuerAndExpiryAndOmitsIdentityClaims() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("should-not-log-subject");
        payload.setEmail("should-not-log@example.com");
        payload.setIssuer("https://accounts.google.com");
        payload.setAudience("actual-client-id.apps.googleusercontent.com");
        payload.setExpirationTimeSeconds(1_700_000_000L);
        String line = GoogleIdTokenDiagnosis.fromClaims(
                "audience-mismatch",
                List.of("592826281810-f28rkidokm93lff117o1bn4d3ogidti6.apps.googleusercontent.com"),
                payload,
                false,
                "valid"
        ).logMessage();
        assertTrue(line.contains("reason=audience-mismatch"));
        assertTrue(line.contains("expectedAudience=592826281810-f28rkidokm93lff117o1bn4d3ogidti6.apps.googleusercontent.com"));
        assertTrue(line.contains("actualAudience=actual-client-id.apps.googleusercontent.com"));
        assertTrue(line.contains("issuer=https://accounts.google.com"));
        assertTrue(line.contains("expiresAt=2023-11-14T22:13:20Z"));
        assertTrue(line.contains("expired=false"));
        assertTrue(line.contains("signature=valid"));
        assertFalse(line.contains("should-not-log-subject"));
        assertFalse(line.contains("should-not-log@example.com"));
        assertFalse(line.contains("eyJ"));
    }

    @Test
    void unparseableAndMissingClientIdDoNotInventClaims() {
        assertEquals(
                "Google ID token rejected: reason=unparseable expectedAudience=not-checked actualAudience=not-checked issuer=not-checked expiresAt=not-checked expired=not-checked signature=not-checked",
                GoogleIdTokenDiagnosis.unparseable().logMessage()
        );
        assertTrue(GoogleIdTokenDiagnosis.clientIdMissing().logMessage().contains("reason=client-id-missing"));
    }

    @Test
    void blankClientConfigurationRejectsEveryToken() {
        assertThrows(UnverifiedIdentityException.class, () -> new RejectingIdentityVerifier().verify("header.payload.signature"));
    }
}
