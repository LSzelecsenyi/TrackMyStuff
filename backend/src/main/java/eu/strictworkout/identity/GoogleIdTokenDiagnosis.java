package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.StringJoiner;

/**
 * Non-secret description of why a Google ID token was rejected.
 * The token, subject, email, and name are never included.
 * A parsed payload is not treated as authenticated.
 */
final class GoogleIdTokenDiagnosis {

    private final String reason;
    private final String expectedAudience;
    private final String actualAudience;
    private final String issuer;
    private final String expiresAt;
    private final String expired;
    private final String signature;

    private GoogleIdTokenDiagnosis(
            String reason,
            String expectedAudience,
            String actualAudience,
            String issuer,
            String expiresAt,
            String expired,
            String signature
    ) {
        this.reason = reason;
        this.expectedAudience = expectedAudience;
        this.actualAudience = actualAudience;
        this.issuer = issuer;
        this.expiresAt = expiresAt;
        this.expired = expired;
        this.signature = signature;
    }

    static GoogleIdTokenDiagnosis unparseable() {
        return new GoogleIdTokenDiagnosis("unparseable", "not-checked", "not-checked", "not-checked", "not-checked", "not-checked", "not-checked");
    }

    static GoogleIdTokenDiagnosis clientIdMissing() {
        return new GoogleIdTokenDiagnosis("client-id-missing", "absent", "not-checked", "not-checked", "not-checked", "not-checked", "not-checked");
    }

    static GoogleIdTokenDiagnosis fromClaims(
            String reason,
            Collection<String> expectedAudience,
            GoogleIdToken.Payload payload,
            boolean expired,
            String signature
    ) {
        return new GoogleIdTokenDiagnosis(
                reason,
                audience(expectedAudience),
                audience(payload.getAudience()),
                text(payload.getIssuer()),
                expiresAt(payload.getExpirationTimeSeconds()),
                Boolean.toString(expired),
                signature
        );
    }

    static GoogleIdTokenDiagnosis rejected(GoogleIdTokenVerifier verifier, GoogleIdToken token) {
        GoogleIdToken.Payload payload = token.getPayload();
        long now = verifier.getClock().currentTimeMillis();
        long skew = verifier.getAcceptableTimeSkewSeconds();
        boolean issuerOk = token.verifyIssuer(verifier.getIssuers());
        boolean audienceOk = token.verifyAudience(verifier.getAudience());
        boolean expired = !token.verifyExpirationTime(now, skew);
        boolean timeOk = token.verifyTime(now, skew);
        String signature = signature(verifier, token);
        String reason = reason(issuerOk, audienceOk, expired, timeOk, signature);
        return new GoogleIdTokenDiagnosis(
                reason,
                audience(verifier.getAudience()),
                audience(payload.getAudience()),
                text(payload.getIssuer()),
                expiresAt(payload.getExpirationTimeSeconds()),
                Boolean.toString(expired),
                signature
        );
    }

    static String reason(boolean issuerOk, boolean audienceOk, boolean expired, boolean timeOk, String signature) {
        if (!issuerOk) {
            return "issuer-mismatch";
        }
        if (!audienceOk) {
            return "audience-mismatch";
        }
        if (expired) {
            return "expired";
        }
        if (!timeOk) {
            return "time-invalid";
        }
        if ("unavailable".equals(signature)) {
            return "certificates-unavailable";
        }
        if (!"valid".equals(signature)) {
            return "signature-mismatch";
        }
        return "rejected";
    }

    String logMessage() {
        return "Google ID token rejected: reason=" + reason
                + " expectedAudience=" + expectedAudience
                + " actualAudience=" + actualAudience
                + " issuer=" + issuer
                + " expiresAt=" + expiresAt
                + " expired=" + expired
                + " signature=" + signature;
    }

    private static String signature(GoogleIdTokenVerifier verifier, GoogleIdToken token) {
        try {
            for (PublicKey key : verifier.getPublicKeysManager().getPublicKeys()) {
                if (token.verifySignature(key)) {
                    return "valid";
                }
            }
            return "invalid";
        } catch (GeneralSecurityException | IOException ex) {
            return "unavailable";
        }
    }

    private static String audience(Object audience) {
        if (audience == null) {
            return "absent";
        }
        if (audience instanceof Collection<?> values) {
            StringJoiner joiner = new StringJoiner(",");
            for (Object value : values) {
                joiner.add(String.valueOf(value));
            }
            String joined = joiner.toString();
            return joined.isBlank() ? "absent" : joined;
        }
        String text = String.valueOf(audience).trim();
        return text.isEmpty() ? "absent" : text;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return "absent";
        }
        return value;
    }

    private static String expiresAt(Long epochSeconds) {
        if (epochSeconds == null) {
            return "absent";
        }
        return Instant.ofEpochSecond(epochSeconds).toString();
    }
}
