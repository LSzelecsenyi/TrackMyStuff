package eu.strictworkout.identity;

final class RejectingIdentityVerifier implements ExternalIdentityVerifier {

    @Override
    public VerifiedExternalIdentity verify(String idToken) {
        throw new UnverifiedIdentityException();
    }
}
