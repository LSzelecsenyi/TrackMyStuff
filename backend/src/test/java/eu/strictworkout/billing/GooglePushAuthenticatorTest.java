package eu.strictworkout.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GooglePushAuthenticatorTest {

    @Test
    void audienceWithoutTheServiceAccountEmailStaysClosed() {
        GooglePushAuthenticator authenticator = new GooglePushAuthenticator(
                "https://api.strictworkout.eu/api/v1/billing/rtdn",
                ""
        );

        assertFalse(authenticator.configured());
        assertFalse(authenticator.verify("Bearer not-a-real-token"));
    }

    @Test
    void aConfiguredPushEndpointStillRejectsAMissingCredential() {
        GooglePushAuthenticator authenticator = new GooglePushAuthenticator(
                "https://api.strictworkout.eu/api/v1/billing/rtdn",
                "play-pubsub@example.iam.gserviceaccount.com"
        );

        assertTrue(authenticator.configured());
        assertFalse(authenticator.verify(null));
        assertFalse(authenticator.verify("Bearer "));
        assertFalse(authenticator.verify("Token abc"));
    }
}
