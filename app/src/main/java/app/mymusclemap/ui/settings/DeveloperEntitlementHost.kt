package app.mymusclemap.ui.settings

import androidx.compose.runtime.staticCompositionLocalOf
import app.mymusclemap.domain.entitlement.DebugEntitlementControl
import app.mymusclemap.domain.entitlement.DebugEntitlementMode
import app.mymusclemap.domain.entitlement.DeveloperEntitlementPanel
import app.mymusclemap.domain.entitlement.FreeAccessDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Debug builds read this from Settings. Release builds ship an empty section
 * and never call [onSelect].
 */
data class DeveloperEntitlementHost(
    val control: StateFlow<DebugEntitlementControl>,
    val revisions: Flow<Int>,
    val decisions: Flow<FreeAccessDecision>,
    val panel: () -> DeveloperEntitlementPanel,
    val onSelect: (DebugEntitlementMode) -> Unit,
    val onArmNextLaunch: () -> Unit
)

val LocalDeveloperEntitlement = staticCompositionLocalOf<DeveloperEntitlementHost?> { null }
