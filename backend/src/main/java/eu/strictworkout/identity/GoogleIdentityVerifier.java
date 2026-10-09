package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GoogleIdentityVerifier implements ExternalIdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdentityVerifier.class);

    private final GoogleIdTokenChecker tokens;

    GoogleIdentityVerifier(GoogleIdTokenChecker tokens) {
        this.tokens = tokens;
    }

    @Override
    public VerifiedExternalIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            log.warn("Google ID token rejected: reason=blank");
            throw new UnverifiedIdentityException();
        }
        GoogleIdToken.Payload payload;
        try {
            payload = tokens.verify(idToken);
        } catch (UnverifiedIdentityException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Google ID token rejected: reason=unexpected exception={}", ex.getClass().getSimpleName());
            throw new UnverifiedIdentityException();
        }
        if (payload == null || payload.getSubject() == null || payload.getSubject().isBlank()) {
            log.warn(
                    "Google ID token rejected: reason=subject-missing issuer={} audience={} expiresAt={}",
                    payload == null ? "absent" : text(payload.getIssuer()),
                    payload == null ? "absent" : audience(payload.getAudience()),
                    payload == null || payload.getExpirationTimeSeconds() == null
                            ? "absent"
                            : java.time.Instant.ofEpochSecond(payload.getExpirationTimeSeconds())
            );
            throw new UnverifiedIdentityException();
        }
        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
        String email = emailVerified ? payload.getEmail() : null;
        if (email != null && email.isBlank()) {
            email = null;
            emailVerified = false;
        }
        String name = payload.get("name") instanceof String value && !value.isBlank() ? value : null;
        return new VerifiedExternalIdentity(
                IdentityProvider.GOOGLE,
                payload.getSubject(),
                email,
                email != null && emailVerified,
                name
        );
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "absent" : value;
    }

    private static String audience(Object audience) {
        if (audience == null) {
            return "absent";
        }
        if (audience instanceof java.util.Collection<?> values) {
            return values.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("absent");
        }
        String text = String.valueOf(audience).trim();
        return text.isEmpty() ? "absent" : text;
    }
}
