package eu.strictworkout.identity;

public record VerifiedExternalIdentity(
        IdentityProvider provider,
        String subject,
        String email,
        boolean emailVerified,
        String displayName
) {
}
