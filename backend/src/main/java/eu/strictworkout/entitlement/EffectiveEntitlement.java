package eu.strictworkout.entitlement;

import eu.strictworkout.founder.FounderStatus;
import eu.strictworkout.founder.TemporaryPro;

public record EffectiveEntitlement(
        EntitlementAccess access,
        boolean founderLifetime,
        boolean temporaryFounderPro
) {
    public static EffectiveEntitlement resolve(
            FounderStatus founderStatus,
            boolean founderLifetimeGrant,
            boolean otherProSource
    ) {
        boolean lifetime = founderLifetimeGrant;
        boolean temporary = !lifetime && founderStatus != null && TemporaryPro.active(founderStatus);
        boolean pro = lifetime || temporary || otherProSource;
        return new EffectiveEntitlement(pro ? EntitlementAccess.PRO : EntitlementAccess.FREE, lifetime, temporary);
    }

    public static EffectiveEntitlement resolve(FounderStatus founderStatus, boolean founderLifetimeGrant) {
        return resolve(founderStatus, founderLifetimeGrant, false);
    }
}
