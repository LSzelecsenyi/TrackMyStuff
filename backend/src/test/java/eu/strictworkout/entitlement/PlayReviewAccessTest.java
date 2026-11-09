package eu.strictworkout.entitlement;

import eu.strictworkout.founder.FounderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayReviewAccessTest {

    @Test
    void matchesOnlyTheVerifiedReviewerAddress() {
        assertTrue(PlayReviewAccess.matchesVerifiedEmail(PlayReviewAccess.EMAIL));
        assertTrue(PlayReviewAccess.matchesVerifiedEmail("U0652739323@ID.GLE"));
        assertTrue(PlayReviewAccess.matchesVerifiedEmail("  u0652739323@id.gle  "));
        assertFalse(PlayReviewAccess.matchesVerifiedEmail(null));
        assertFalse(PlayReviewAccess.matchesVerifiedEmail(""));
        assertFalse(PlayReviewAccess.matchesVerifiedEmail("   "));
        assertFalse(PlayReviewAccess.matchesVerifiedEmail("someone@id.gle"));
        assertFalse(PlayReviewAccess.matchesVerifiedEmail("u0652739323@id.gle.invalid"));
    }

    @Test
    void reviewerWithoutFounderGrantIsReportedAsPro() {
        EffectiveEntitlement free = EffectiveEntitlement.resolve(null, false, false, true);
        assertEquals(EntitlementAccess.PRO, free.access());
        assertFalse(free.temporaryFounderPro());
        assertTrue(EntitlementController.temporaryFounderPro(free, true));
    }

    @Test
    void otherUsersKeepTheirResolvedEntitlement() {
        EffectiveEntitlement free = EffectiveEntitlement.resolve(FounderStatus.ACTIVE_FREE, false, false, false);
        assertEquals(EntitlementAccess.FREE, free.access());
        assertFalse(EntitlementController.temporaryFounderPro(free, false));

        EffectiveEntitlement temporary = EffectiveEntitlement.resolve(FounderStatus.ACTIVE_PRO, false);
        assertTrue(EntitlementController.temporaryFounderPro(temporary, false));

        EffectiveEntitlement lifetime = EffectiveEntitlement.resolve(FounderStatus.APPROVED, true, true, true);
        assertTrue(lifetime.founderLifetime());
        assertFalse(EntitlementController.temporaryFounderPro(lifetime, true));
    }
}
