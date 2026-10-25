package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Test

class ProBenefitsAccessTest {
    @Test
    fun comparisonOrderPutsImmediateValueFirstAndIncludesTimers() {
        assertEquals(
            listOf(
                ProComparisonRow.WorkoutPlans,
                ProComparisonRow.CustomExercises,
                ProComparisonRow.TimedSetsAndTimers,
                ProComparisonRow.Statistics,
                ProComparisonRow.Reports,
                ProComparisonRow.WorkoutScheduling,
                ProComparisonRow.Achievements,
                ProComparisonRow.BodyMeasurements,
                ProComparisonRow.ProgressPhotos,
                ProComparisonRow.ExternalWorkouts
            ),
            ProComparisonRow.order
        )
    }

    @Test
    fun freeAccessDoesNotClaimPro() {
        val access = proBenefitsAccess(free())
        assertEquals(ProAccessLevel.Free, access.level)
        assertEquals(ProAccessSource.None, access.source)
    }

    @Test
    fun playSubscriptionIsTheAccessSourceOnlyWhenItGrantsPro() {
        val access = proBenefitsAccess(
            free().copy(tier = EntitlementTier.Pro, subscriptionValid = true)
        )
        assertEquals(ProAccessLevel.Pro, access.level)
        assertEquals(ProAccessSource.GooglePlaySubscription, access.source)
    }

    @Test
    fun founderAndTrialAreNotDescribedAsASubscription() {
        val founder = proBenefitsAccess(
            free().copy(tier = EntitlementTier.Pro, founderProActive = true)
        )
        val lifetime = proBenefitsAccess(
            free().copy(tier = EntitlementTier.Pro, founderLifetime = true)
        )
        val trial = proBenefitsAccess(
            free().copy(tier = EntitlementTier.Pro, promotionalProActive = true)
        )
        val welcome = proBenefitsAccess(
            free().copy(tier = EntitlementTier.Pro, welcomeBackActive = true)
        )
        assertEquals(ProAccessSource.Founder, founder.source)
        assertEquals(ProAccessSource.Founder, lifetime.source)
        assertEquals(ProAccessSource.Trial, trial.source)
        assertEquals(ProAccessSource.Promotional, welcome.source)
        assertEquals(ProAccessLevel.Pro, trial.level)
    }

    @Test
    fun forceProWithoutASubscriptionIsProAndDoesNotClaimPlay() {
        val actual = free()
        val effective = actual.copy(tier = EntitlementTier.Pro, temporaryTesterPro = true)
        val access = proBenefitsAccess(effective, actual, revealDebugOverride = true)
        assertEquals(ProAccessLevel.Pro, access.level)
        assertEquals(ProAccessSource.DebugOverride, access.source)
    }

    @Test
    fun forceFreeKeepsEffectiveAccessFreeEvenWhenASubscriptionExists() {
        val actual = free().copy(tier = EntitlementTier.Pro, subscriptionValid = true)
        val effective = free()
        val access = proBenefitsAccess(effective, actual, revealDebugOverride = true)
        assertEquals(ProAccessLevel.Free, access.level)
        assertEquals(ProAccessSource.DebugOverride, access.source)
    }

    @Test
    fun releaseDoesNotRevealADebugOverride() {
        val actual = free().copy(tier = EntitlementTier.Pro, subscriptionValid = true)
        val access = proBenefitsAccess(free(), actual, revealDebugOverride = false)
        assertEquals(ProAccessLevel.Free, access.level)
        assertEquals(ProAccessSource.None, access.source)
    }

    private fun free(): EffectiveEntitlement {
        return EffectiveEntitlement(
            tier = EntitlementTier.Free,
            subscriptionValid = false,
            founderLifetime = false,
            temporaryTesterPro = false
        )
    }
}
