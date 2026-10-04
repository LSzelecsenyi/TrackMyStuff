package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleIdentityVerifierTest {

    @Test
    void verifiedEmailIsKeptAndUnverifiedEmailIsDropped() {
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier(token -> payload("sub-1", "person@example.com", true));
        VerifiedExternalIdentity verified = verifier.verify("header.payload.sig");
        assertEquals(IdentityProvider.GOOGLE, verified.provider());
        assertEquals("sub-1", verified.subject());
        assertEquals("person@example.com", verified.email());
        assertTrue(verified.emailVerified());

        GoogleIdentityVerifier unverified = new GoogleIdentityVerifier(token -> payload("sub-1", "person@example.com", false));
        VerifiedExternalIdentity dropped = unverified.verify("header.payload.sig");
        assertEquals("sub-1", dropped.subject());
        assertNull(dropped.email());
        assertFalse(dropped.emailVerified());
    }

    @Test
    void missingEmailStillVerifiesTheSubject() {
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier(token -> payload("sub-2", null, false));
        VerifiedExternalIdentity verified = verifier.verify("header.payload.sig");
        assertEquals("sub-2", verified.subject());
        assertNull(verified.email());
    }

    @Test
    void invalidPayloadIsRejected() {
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier(token -> null);
        assertThrows(UnverifiedIdentityException.class, () -> verifier.verify("not-a-google-token"));
        assertThrows(UnverifiedIdentityException.class, () -> verifier.verify(" "));
    }

    @Test
    void blankClientConfigurationRejectsEveryToken() {
        ExternalIdentityVerifier verifier = new RejectingIdentityVerifier();
        assertThrows(UnverifiedIdentityException.class, () -> verifier.verify("anything"));
    }

    private static GoogleIdToken.Payload payload(String subject, String email, boolean emailVerified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(subject);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        return payload;
    }
}
