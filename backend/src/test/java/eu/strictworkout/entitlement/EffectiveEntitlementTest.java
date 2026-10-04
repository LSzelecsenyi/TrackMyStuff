package eu.strictworkout.entitlement;

import eu.strictworkout.founder.FounderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectiveEntitlementTest {

    @Test
    void founderLifetimeIsTheGrantAndSupersedesTemporaryPro() {
        EffectiveEntitlement lifetime = EffectiveEntitlement.resolve(FounderStatus.APPROVED, true);
        assertEquals(EntitlementAccess.PRO, lifetime.access());
        assertTrue(lifetime.founderLifetime());
        assertFalse(lifetime.temporaryFounderPro());

        EffectiveEntitlement approvedWithoutGrant = EffectiveEntitlement.resolve(FounderStatus.APPROVED, false);
        assertEquals(EntitlementAccess.FREE, approvedWithoutGrant.access());
        assertFalse(approvedWithoutGrant.founderLifetime());
    }

    @Test
    void temporaryProFollowsActiveAndPendingOnly() {
        assertTrue(EffectiveEntitlement.resolve(FounderStatus.ACTIVE_PRO, false).temporaryFounderPro());
        assertTrue(EffectiveEntitlement.resolve(FounderStatus.PENDING_APPROVAL, false).temporaryFounderPro());
        assertEquals(EntitlementAccess.PRO, EffectiveEntitlement.resolve(FounderStatus.PENDING_APPROVAL, false).access());
        assertFalse(EffectiveEntitlement.resolve(FounderStatus.ACTIVE_FREE, false).temporaryFounderPro());
        assertFalse(EffectiveEntitlement.resolve(FounderStatus.EXPIRED, false).temporaryFounderPro());
        assertFalse(EffectiveEntitlement.resolve(FounderStatus.REJECTED, false).temporaryFounderPro());
        assertEquals(EntitlementAccess.FREE, EffectiveEntitlement.resolve(null, false).access());
    }

    @Test
    void anotherProSourceGrantsProWithoutFounderLifetime() {
        EffectiveEntitlement subscription = EffectiveEntitlement.resolve(FounderStatus.EXPIRED, false, true);
        assertEquals(EntitlementAccess.PRO, subscription.access());
        assertFalse(subscription.founderLifetime());
        assertFalse(subscription.temporaryFounderPro());
    }
}
