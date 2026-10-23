package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class EntitlementComposerOverrideTest {
    private val now = Instant.parse("2026-10-21T08:00:00Z")
    private val paidUntil = now.plusSeconds(3_600)

    @Test
    fun identityOverlayUsesTheActualEntitlementAndLeavesBillingUntouched() {
        val composer = composer { it }
        val actual = composer.resolveActual()
        assertTrue(actual.grantsPro)
        assertTrue(actual.subscriptionValid)
        assertEquals(actual, composer.resolve())
        assertEquals(paidUntil, composer.sources().subscription.paidUntilInclusive)
        assertTrue(composer.policy().workoutTimers())
    }

    @Test
    fun effectiveOverlayDoesNotRewriteSources() {
        val composer = composer { actual ->
            actual.copy(
                tier = EntitlementTier.Free,
                subscriptionValid = false,
                founderLifetime = false,
                temporaryTesterPro = false,
                founderProActive = false,
                promotionalProActive = false,
                welcomeBackActive = false
            )
        }
        assertTrue(composer.resolveActual().subscriptionValid)
        assertTrue(composer.resolveActual().grantsPro)
        assertFalse(composer.resolve().grantsPro)
        assertFalse(composer.resolve().subscriptionValid)
        assertFalse(composer.policy().workoutTimers())
        assertFalse(composer.policy().workoutPlans(4).canCreate)
        assertFalse(composer.policy().customExercises(6).canCreate)
        assertEquals(paidUntil, composer.sources().subscription.paidUntilInclusive)
        val entitlements = PolicyBackedEntitlements({ composer.policy() })
        assertFalse(entitlements.hasAccess(AppFeature.WorkoutTimers))
        assertFalse(entitlements.hasAccess(AppFeature.UnlimitedWorkoutPlans))
        assertFalse(entitlements.hasAccess(AppFeature.UnlimitedCustomExercises))
    }

    @Test
    fun inactiveGateCannotGrantAndIgnoresSelection() {
        val free = EffectiveEntitlement(
            tier = EntitlementTier.Free,
            subscriptionValid = false,
            founderLifetime = false,
            temporaryTesterPro = false
        )
        val gate = InactiveDebugEntitlementGate
        assertSame(free, gate.apply(free))
        gate.select(DebugEntitlementMode.ForcePro)
        gate.select(DebugEntitlementMode.ForceFree)
        assertFalse(gate.armFreeOnNextLaunch(true))
        assertEquals(DebugEntitlementMode.Real, gate.control.value.mode)
        assertFalse(gate.control.value.pendingFreeOnNextLaunch)
        assertFalse(gate.apply(free).grantsPro)
    }

    private fun composer(
        apply: (EffectiveEntitlement) -> EffectiveEntitlement
    ): EntitlementComposer {
        return EntitlementComposer(
            subscriptionProvider = object : SubscriptionEntitlementProvider {
                override fun current() = SubscriptionEntitlement(paidUntilInclusive = paidUntil)
            },
            founderLifetimeProvider = InactiveFounderLifetimeProvider,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            applyEffective = apply
        )
    }
}
