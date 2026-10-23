package app.mymusclemap.domain.entitlement

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory view of an already resolved entitlement. Nothing here is written to
 * Play subscription storage, Founder grants, trials, or promotional records.
 */
internal object DebugEntitlementOverlay {
    fun apply(actual: EffectiveEntitlement, mode: DebugEntitlementMode): EffectiveEntitlement {
        return when (mode) {
            DebugEntitlementMode.Real -> actual
            DebugEntitlementMode.ForcePro -> forcePro(actual)
            DebugEntitlementMode.ForceFree -> forceFree(actual)
        }
    }

    fun isConsistent(entitlement: EffectiveEntitlement): Boolean {
        return entitlement.grantsPro == granting(entitlement) &&
            entitlement.tier == if (entitlement.grantsPro) EntitlementTier.Pro else EntitlementTier.Free
    }

    private fun forcePro(actual: EffectiveEntitlement): EffectiveEntitlement {
        if (actual.grantsPro && isConsistent(actual)) return actual
        val promoted = actual.copy(tier = EntitlementTier.Pro)
        return if (granting(promoted)) promoted else promoted.copy(temporaryTesterPro = true)
    }

    private fun forceFree(actual: EffectiveEntitlement): EffectiveEntitlement {
        val cleared = actual.copy(
            tier = EntitlementTier.Free,
            subscriptionValid = false,
            founderLifetime = false,
            temporaryTesterPro = false,
            founderProActive = false,
            promotionalProActive = false,
            welcomeBackActive = false
        )
        return if (cleared == actual) actual else cleared
    }

    private fun granting(entitlement: EffectiveEntitlement): Boolean {
        return entitlement.subscriptionValid ||
            entitlement.founderLifetime ||
            entitlement.founderProActive ||
            entitlement.temporaryTesterPro ||
            entitlement.promotionalProActive ||
            entitlement.welcomeBackActive
    }
}

internal interface DebugEntitlementStore {
    fun read(): DebugEntitlementControl
    fun write(control: DebugEntitlementControl)
}

internal class MemoryDebugEntitlementStore(
    initial: DebugEntitlementControl = DebugEntitlementControl()
) : DebugEntitlementStore {
    var value: DebugEntitlementControl = initial

    override fun read(): DebugEntitlementControl = value

    override fun write(control: DebugEntitlementControl) {
        value = control
    }
}

/**
 * Debug preference file. This is not the verified Play subscription DataStore.
 * [commit] is synchronous so a force-stop cannot drop the override.
 */
internal class DebugEntitlementPreferenceStore(context: Context) : DebugEntitlementStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): DebugEntitlementControl {
        return DebugEntitlementControl(
            mode = DebugEntitlementMode.fromStored(preferences.getString(KEY_MODE, null)),
            pendingFreeOnNextLaunch = preferences.getBoolean(KEY_PENDING, false)
        )
    }

    override fun write(control: DebugEntitlementControl) {
        val saved = preferences.edit()
            .putString(KEY_MODE, control.mode.stored())
            .putBoolean(KEY_PENDING, control.pendingFreeOnNextLaunch)
            .commit()
        check(saved) { "Debug entitlement override could not be saved." }
    }

    internal companion object {
        const val FILE = "strict_debug_entitlement_override"
        const val KEY_MODE = "mode"
        const val KEY_PENDING = "pending_free_next_launch"
    }
}

/**
 * Live mode is applied on the next [apply]. A pending next-launch FREE value
 * stays out of [apply] until the next process constructs this gate.
 */
internal class StoredDebugEntitlementGate(
    private val store: DebugEntitlementStore,
    private val onLiveChanged: () -> Unit
) : DebugEntitlementGate {
    private val lock = Any()
    private val states = MutableStateFlow(consumePending(store.read()))
    override val control: StateFlow<DebugEntitlementControl> = states

    override fun apply(actual: EffectiveEntitlement): EffectiveEntitlement {
        return DebugEntitlementOverlay.apply(actual, states.value.mode)
    }

    override fun select(mode: DebugEntitlementMode) {
        val publish = synchronized(lock) {
            val current = states.value
            if (current.mode == mode && !current.pendingFreeOnNextLaunch) {
                return
            }
            val next = DebugEntitlementControl(mode = mode, pendingFreeOnNextLaunch = false)
            store.write(next)
            states.value = next
            current.mode != mode
        }
        if (publish) onLiveChanged()
    }

    override fun armFreeOnNextLaunch(effectiveGrantsPro: Boolean): Boolean {
        if (!effectiveGrantsPro) return false
        synchronized(lock) {
            val current = states.value
            if (current.mode == DebugEntitlementMode.ForceFree) return false
            if (current.pendingFreeOnNextLaunch) return true
            val next = current.copy(pendingFreeOnNextLaunch = true)
            store.write(next)
            states.value = next
        }
        return true
    }

    private fun consumePending(current: DebugEntitlementControl): DebugEntitlementControl {
        if (!current.pendingFreeOnNextLaunch) return current
        val consumed = current.copy(
            mode = DebugEntitlementMode.ForceFree,
            pendingFreeOnNextLaunch = false
        )
        store.write(consumed)
        return consumed
    }
}
