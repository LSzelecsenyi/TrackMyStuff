package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;

final class GoogleCertIdTokenChecker implements GoogleIdTokenChecker {

    private static final Logger log = LoggerFactory.getLogger(GoogleCertIdTokenChecker.class);

    private final GoogleIdTokenVerifier verifier;

    GoogleCertIdTokenChecker(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public GoogleIdToken.Payload verify(String idToken) {
        GoogleIdToken parsed;
        try {
            parsed = GoogleIdToken.parse(verifier.getJsonFactory(), idToken);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn(GoogleIdTokenDiagnosis.unparseable().logMessage());
            throw new UnverifiedIdentityException();
        }
        boolean accepted;
        try {
            accepted = verifier.verify(parsed);
        } catch (GeneralSecurityException | IOException ex) {
            log.warn(GoogleIdTokenDiagnosis.rejected(verifier, parsed).logMessage());
            throw new UnverifiedIdentityException();
        }
        if (!accepted) {
            log.warn(GoogleIdTokenDiagnosis.rejected(verifier, parsed).logMessage());
            throw new UnverifiedIdentityException();
        }
        return parsed.getPayload();
    }
}
