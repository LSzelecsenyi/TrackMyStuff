package app.mymusclemap

import android.content.Context
import app.mymusclemap.domain.entitlement.DebugEntitlementGate
import app.mymusclemap.domain.entitlement.DebugEntitlementPreferenceStore
import app.mymusclemap.domain.entitlement.StoredDebugEntitlementGate

/** Debug builds persist REAL / FORCE_PRO / FORCE_FREE outside billing storage. */
object DebugEntitlementGateSelection {
    fun open(context: Context, onLiveChanged: () -> Unit): DebugEntitlementGate {
        return StoredDebugEntitlementGate(
            store = DebugEntitlementPreferenceStore(context),
            onLiveChanged = onLiveChanged
        )
    }
}
