package eu.strictworkout.billing;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.util.Collections;

final class GooglePushAuthenticator {

    private final String audience;
    private final String expectedEmail;

    GooglePushAuthenticator(String audience, String expectedEmail) {
        this.audience = audience == null ? "" : audience.trim();
        this.expectedEmail = expectedEmail == null ? "" : expectedEmail.trim();
    }

    /**
     * Both the push audience and the Pub/Sub service-account email are required.
     * An audience alone would accept any Google-signed token for that audience.
     */
    boolean configured() {
        return !audience.isEmpty() && !expectedEmail.isEmpty();
    }

    boolean verify(String authorization) {
        if (!configured() || authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        String jwt = authorization.substring("Bearer ".length()).trim();
        if (jwt.isEmpty()) {
            return false;
        }
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance()
            ).setAudience(Collections.singletonList(audience)).build();
            GoogleIdToken token = verifier.verify(jwt);
            if (token == null) {
                return false;
            }
            return expectedEmail.equals(token.getPayload().getEmail());
        } catch (Exception error) {
            return false;
        }
    }
}
