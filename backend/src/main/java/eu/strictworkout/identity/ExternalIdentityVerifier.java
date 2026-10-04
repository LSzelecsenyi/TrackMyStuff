package eu.strictworkout.identity;

public interface ExternalIdentityVerifier {

    VerifiedExternalIdentity verify(String idToken);
}
