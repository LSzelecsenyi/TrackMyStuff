package eu.strictworkout.entitlement;

import eu.strictworkout.founder.FounderStatus;
import eu.strictworkout.founder.TemporaryPro;

public record EffectiveEntitlement(
        EntitlementAccess access,
        boolean founderLifetime,
        boolean temporaryFounderPro,
        boolean founderProActive
) {
    /**
     * {@code legacyLifetimeGrant} is an existing {@code FOUNDER_LIFETIME} row and stays permanent.
     * {@code founderProGrantActive} is a {@code FOUNDER_PRO} row whose {@code expires_at} is still ahead.
     * Neither source creates a subscription.
     */
    public static EffectiveEntitlement resolve(
            FounderStatus founderStatus,
            boolean legacyLifetimeGrant,
            boolean founderProGrantActive,
            boolean otherProSource
    ) {
        boolean lifetime = legacyLifetimeGrant;
        boolean founderPro = !lifetime && founderProGrantActive;
        boolean temporary = !lifetime && !founderPro && founderStatus != null && TemporaryPro.active(founderStatus);
        boolean pro = lifetime || founderPro || temporary || otherProSource;
        return new EffectiveEntitlement(
                pro ? EntitlementAccess.PRO : EntitlementAccess.FREE,
                lifetime,
                temporary,
                founderPro
        );
    }

    public static EffectiveEntitlement resolve(
            FounderStatus founderStatus,
            boolean legacyLifetimeGrant,
            boolean otherProSource
    ) {
        return resolve(founderStatus, legacyLifetimeGrant, false, otherProSource);
    }

    public static EffectiveEntitlement resolve(FounderStatus founderStatus, boolean legacyLifetimeGrant) {
        return resolve(founderStatus, legacyLifetimeGrant, false, false);
    }
}
