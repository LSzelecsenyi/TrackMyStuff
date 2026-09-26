package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureEntitlementsTest {
    @Test
    fun openProviderGrantsEveryCapability() {
        AppFeature.entries.forEach { feature ->
            assertTrue(OpenFeatureEntitlements.hasAccess(feature))
        }
    }

    @Test
    fun selectiveProviderUnlocksOnlyGrantedCapabilities() {
        val entitlements = SelectiveFeatureEntitlements(
            setOf(AppFeature.AdvancedStatistics, AppFeature.AdvancedPlanning)
        )
        assertTrue(entitlements.hasAccess(AppFeature.AdvancedStatistics))
        assertTrue(entitlements.hasAccess(AppFeature.AdvancedPlanning))
        assertFalse(entitlements.hasAccess(AppFeature.AdvancedMuscleAnalytics))
        assertFalse(entitlements.hasAccess(AppFeature.UnlimitedWorkoutPlans))
    }

    @Test
    fun emptySelectionLocksEveryCapability() {
        val entitlements = SelectiveFeatureEntitlements(emptySet())
        AppFeature.entries.forEach { feature ->
            assertFalse(entitlements.hasAccess(feature))
        }
    }

    @Test
    fun runInvokesAllowedWhenCapabilityIsUnlocked() {
        val allowed = intArrayOf(0)
        val locked = intArrayOf(0)
        ProAccess.run(
            entitlements = OpenFeatureEntitlements,
            feature = AppFeature.UnlimitedWorkoutPlans,
            onLocked = { locked[0] += 1 },
            onAllowed = { allowed[0] += 1 }
        )
        assertEquals(1, allowed[0])
        assertEquals(0, locked[0])
    }

    @Test
    fun runInvokesLockedWhenCapabilityIsLocked() {
        val allowed = intArrayOf(0)
        val locked = intArrayOf(0)
        ProAccess.run(
            entitlements = SelectiveFeatureEntitlements(emptySet()),
            feature = AppFeature.AdvancedMuscleAnalytics,
            onLocked = { locked[0] += 1 },
            onAllowed = { allowed[0] += 1 }
        )
        assertEquals(0, allowed[0])
        assertEquals(1, locked[0])
    }
}
