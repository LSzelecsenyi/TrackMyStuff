package app.mymusclemap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.entitlement.DebugEntitlementMode
import app.mymusclemap.domain.entitlement.EffectiveEntitlement
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.InactiveDebugEntitlementGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugEntitlementGateReleaseTest {
    @Test
    fun releaseIgnoresStoredDebugStateAndCannotGrant() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "strict_debug_entitlement_override",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .putString("mode", "FORCE_PRO")
            .putBoolean("pending_free_next_launch", true)
            .commit()
        var events = 0
        val gate = DebugEntitlementGateSelection.open(context) { events += 1 }
        val free = EffectiveEntitlement(
            tier = EntitlementTier.Free,
            subscriptionValid = false,
            founderLifetime = false,
            temporaryTesterPro = false,
            founderRecognized = true
        )
        val pro = EffectiveEntitlement(
            tier = EntitlementTier.Pro,
            subscriptionValid = true,
            founderLifetime = false,
            temporaryTesterPro = false
        )
        assertSame(InactiveDebugEntitlementGate, gate)
        assertSame(free, gate.apply(free))
        assertSame(pro, gate.apply(pro))
        gate.select(DebugEntitlementMode.ForcePro)
        gate.select(DebugEntitlementMode.ForceFree)
        assertFalse(gate.armFreeOnNextLaunch(effectiveGrantsPro = true))
        assertEquals(0, events)
        assertEquals(DebugEntitlementMode.Real, gate.control.value.mode)
        assertFalse(gate.control.value.pendingFreeOnNextLaunch)
        assertFalse(gate.apply(free).grantsPro)
        assertTrue(gate.apply(free).founderRecognized)
        assertEquals("FORCE_PRO", preferences.getString("mode", null))
        assertTrue(preferences.getBoolean("pending_free_next_launch", false))
    }
}
