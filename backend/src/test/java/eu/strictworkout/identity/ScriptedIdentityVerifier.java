package eu.strictworkout.identity;

import java.util.concurrent.ConcurrentHashMap;

public final class ScriptedIdentityVerifier implements ExternalIdentityVerifier {

    private final ConcurrentHashMap<String, VerifiedExternalIdentity> accepted = new ConcurrentHashMap<>();

    public void accept(String idToken, String subject, String email, boolean emailVerified) {
        accepted.put(idToken, new VerifiedExternalIdentity(IdentityProvider.GOOGLE, subject, email, emailVerified, null));
    }

    @Override
    public VerifiedExternalIdentity verify(String idToken) {
        VerifiedExternalIdentity identity = accepted.get(idToken);
        if (identity == null) {
            throw new UnverifiedIdentityException();
        }
        return identity;
    }
}
