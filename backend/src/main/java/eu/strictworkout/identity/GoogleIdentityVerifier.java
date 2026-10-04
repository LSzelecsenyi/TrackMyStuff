package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

final class GoogleIdentityVerifier implements ExternalIdentityVerifier {

    private final GoogleIdTokenChecker tokens;

    GoogleIdentityVerifier(GoogleIdTokenChecker tokens) {
        this.tokens = tokens;
    }

    @Override
    public VerifiedExternalIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new UnverifiedIdentityException();
        }
        GoogleIdToken.Payload payload;
        try {
            payload = tokens.verify(idToken);
        } catch (RuntimeException ex) {
            throw new UnverifiedIdentityException();
        }
        if (payload == null || payload.getSubject() == null || payload.getSubject().isBlank()) {
            throw new UnverifiedIdentityException();
        }
        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
        String email = emailVerified ? payload.getEmail() : null;
        if (email != null && email.isBlank()) {
            email = null;
            emailVerified = false;
        }
        return new VerifiedExternalIdentity(IdentityProvider.GOOGLE, payload.getSubject(), email, email != null && emailVerified);
    }
}
