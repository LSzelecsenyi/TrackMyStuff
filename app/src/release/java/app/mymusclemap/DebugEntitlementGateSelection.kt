package app.mymusclemap

import android.content.Context
import app.mymusclemap.domain.entitlement.DebugEntitlementGate
import app.mymusclemap.domain.entitlement.InactiveDebugEntitlementGate

/**
 * Release and dev builds do not read debug entitlement preferences and cannot
 * grant or deny access through this type.
 */
object DebugEntitlementGateSelection {
    @Suppress("UNUSED_PARAMETER")
    fun open(context: Context, onLiveChanged: () -> Unit): DebugEntitlementGate {
        return InactiveDebugEntitlementGate
    }
}
