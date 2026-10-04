package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import java.io.IOException;
import java.security.GeneralSecurityException;

final class GoogleCertIdTokenChecker implements GoogleIdTokenChecker {

    private final GoogleIdTokenVerifier verifier;

    GoogleCertIdTokenChecker(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public GoogleIdToken.Payload verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            return token == null ? null : token.getPayload();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            throw new UnverifiedIdentityException();
        }
    }
}
