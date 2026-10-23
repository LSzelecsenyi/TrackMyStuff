package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FreeAccessIntroTest {
    private val now = Instant.parse("2026-06-01T00:00:00Z")
    private val earlier = Instant.parse("2026-05-01T00:00:00Z")
    private val later = Instant.parse("2026-07-01T00:00:00Z")

    @Test
    fun aFirstFreePeriodWithoutAPriorGrantDoesNotClaimThatProEnded() {
        assertEquals(
            FreeAccessIntro.WithinFreeLimits,
            freeAccessIntro(
                phase = FreeAccessPhase.Required,
                periodId = 1L,
                priorProGrantEnded = false
            )
        )
    }

    @Test
    fun aLaterRequiredPeriodWasOpenedByLeavingPro() {
        assertEquals(
            FreeAccessIntro.ProEnded,
            freeAccessIntro(
                phase = FreeAccessPhase.Required,
                periodId = 2L,
                priorProGrantEnded = false
            )
        )
    }

    @Test
    fun anExpiredGrantOnTheFirstPeriodIsADowngrade() {
        val sources = EntitlementSources(
            subscription = SubscriptionEntitlement(paidUntilInclusive = earlier)
        )
        assertTrue(priorProGrantEnded(sources, now, actualGrantsPro = false, effectiveGrantsPro = false))
        assertEquals(
            FreeAccessIntro.ProEnded,
            freeAccessIntro(FreeAccessPhase.Required, periodId = 1L, priorProGrantEnded = true)
        )
    }

    @Test
    fun anActiveGrantIsNotTreatedAsEnded() {
        val sources = EntitlementSources(
            subscription = SubscriptionEntitlement(paidUntilInclusive = later),
            promotionalPro = PromotionalProEntitlement(expiresAt = later),
            welcomeBack = PromotionalProEntitlement(expiresAt = later),
            backendFounder = BackendFounderEntitlement(founderProExpiresAt = later)
        )
        assertFalse(priorProGrantEnded(sources, now, actualGrantsPro = false, effectiveGrantsPro = false))
    }

    @Test
    fun expiredTrialWelcomeBackAndFounderProCountAsEnded() {
        assertTrue(
            priorProGrantEnded(
                EntitlementSources(promotionalPro = PromotionalProEntitlement(expiresAt = earlier)),
                now,
                actualGrantsPro = false,
                effectiveGrantsPro = false
            )
        )
        assertTrue(
            priorProGrantEnded(
                EntitlementSources(welcomeBack = PromotionalProEntitlement(expiresAt = earlier)),
                now,
                actualGrantsPro = false,
                effectiveGrantsPro = false
            )
        )
        assertTrue(
            priorProGrantEnded(
                EntitlementSources(backendFounder = BackendFounderEntitlement(founderProExpiresAt = earlier)),
                now,
                actualGrantsPro = false,
                effectiveGrantsPro = false
            )
        )
    }

    @Test
    fun forcingFreeOverStillValidProIsADowngrade() {
        assertTrue(
            priorProGrantEnded(
                EntitlementSources(),
                now,
                actualGrantsPro = true,
                effectiveGrantsPro = false
            )
        )
        assertFalse(
            priorProGrantEnded(
                EntitlementSources(),
                now,
                actualGrantsPro = false,
                effectiveGrantsPro = false
            )
        )
    }

    @Test
    fun aPeriodThatDoesNotRequireSelectionKeepsTheLimitCopy() {
        assertEquals(
            FreeAccessIntro.WithinFreeLimits,
            freeAccessIntro(FreeAccessPhase.Confirmed, periodId = 4L, priorProGrantEnded = true)
        )
    }
}
