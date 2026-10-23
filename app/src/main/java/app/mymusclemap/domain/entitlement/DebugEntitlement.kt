package app.mymusclemap.domain.entitlement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Debug-only view of the entitlement used by the running process.
 * Release builds keep [InactiveDebugEntitlementGate], which cannot grant access.
 */
enum class DebugEntitlementMode {
    Real,
    ForcePro,
    ForceFree;

    fun stored(): String = when (this) {
        Real -> "REAL"
        ForcePro -> "FORCE_PRO"
        ForceFree -> "FORCE_FREE"
    }

    companion object {
        fun fromStored(value: String?): DebugEntitlementMode = when (value) {
            "FORCE_PRO" -> ForcePro
            "FORCE_FREE" -> ForceFree
            else -> Real
        }
    }
}

data class DebugEntitlementControl(
    val mode: DebugEntitlementMode = DebugEntitlementMode.Real,
    /** FORCE_FREE is stored for the next process and is not in the live stream yet. */
    val pendingFreeOnNextLaunch: Boolean = false
)

/**
 * Applied after [EntitlementResolver] and before feature code sees [EffectiveEntitlement].
 * [select] and [armFreeOnNextLaunch] are no-ops on the release implementation.
 */
interface DebugEntitlementGate {
    val control: StateFlow<DebugEntitlementControl>

    fun apply(actual: EffectiveEntitlement): EffectiveEntitlement

    fun select(mode: DebugEntitlementMode)

    /**
     * Persists a next-launch FREE override without publishing it to the live stream.
     * Returns false when the current effective entitlement is not PRO.
     */
    fun armFreeOnNextLaunch(effectiveGrantsPro: Boolean): Boolean
}

object InactiveDebugEntitlementGate : DebugEntitlementGate {
    private val none = MutableStateFlow(DebugEntitlementControl())
    override val control: StateFlow<DebugEntitlementControl> = none

    override fun apply(actual: EffectiveEntitlement): EffectiveEntitlement = actual

    override fun select(mode: DebugEntitlementMode) = Unit

    override fun armFreeOnNextLaunch(effectiveGrantsPro: Boolean): Boolean = false
}

data class DeveloperEntitlementPanel(
    val mode: DebugEntitlementMode,
    val pendingFreeOnNextLaunch: Boolean,
    val actualKnown: Boolean,
    val effectiveKnown: Boolean,
    val actualGrantsPro: Boolean?,
    val effectiveGrantsPro: Boolean?,
    val phase: FreeAccessPhase?,
    val periodId: Long?,
    val selectionConfirmed: Boolean,
    val selectedPlanCount: Int,
    val selectedExerciseCount: Int,
    val lockedPlanCount: Int,
    val lockedExerciseCount: Int,
    val freeAccessLoaded: Boolean
) {
    val canArmFreeOnNextLaunch: Boolean
        get() = effectiveKnown && effectiveGrantsPro == true && !pendingFreeOnNextLaunch
}

fun buildDeveloperEntitlementPanel(
    control: DebugEntitlementControl,
    actualKnown: Boolean,
    effectiveKnown: Boolean,
    actual: EffectiveEntitlement?,
    effective: EffectiveEntitlement?,
    decision: FreeAccessDecision
): DeveloperEntitlementPanel {
    val loaded = decision.loaded
    return DeveloperEntitlementPanel(
        mode = control.mode,
        pendingFreeOnNextLaunch = control.pendingFreeOnNextLaunch,
        actualKnown = actualKnown,
        effectiveKnown = effectiveKnown,
        actualGrantsPro = if (actualKnown) actual?.grantsPro else null,
        effectiveGrantsPro = if (effectiveKnown) effective?.grantsPro else null,
        phase = if (loaded) decision.state.phase else null,
        periodId = if (loaded) decision.state.periodId else null,
        selectionConfirmed = loaded && decision.state.phase == FreeAccessPhase.Confirmed,
        selectedPlanCount = if (loaded) decision.state.selectedPlanIds.size else 0,
        selectedExerciseCount = if (loaded) decision.state.selectedCustomExerciseIds.size else 0,
        lockedPlanCount = if (loaded) decision.lockedPlanIds.size else 0,
        lockedExerciseCount = if (loaded) decision.lockedCustomExerciseIds.size else 0,
        freeAccessLoaded = loaded
    )
}
