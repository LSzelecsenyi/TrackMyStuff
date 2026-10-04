package eu.strictworkout.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RejectingIdentityVerifier implements ExternalIdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(RejectingIdentityVerifier.class);

    @Override
    public VerifiedExternalIdentity verify(String idToken) {
        log.warn(GoogleIdTokenDiagnosis.clientIdMissing().logMessage());
        throw new UnverifiedIdentityException();
    }
}
