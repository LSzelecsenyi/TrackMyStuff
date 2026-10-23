package app.mymusclemap.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.DebugEntitlementMode
import app.mymusclemap.domain.entitlement.DeveloperEntitlementPanel
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.components.CompactEditorDivider
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val DEBUG_ENTITLEMENT_SECTION = "debug-entitlement-section"
internal const val DEBUG_ENTITLEMENT_REAL = "debug-entitlement-real"
internal const val DEBUG_ENTITLEMENT_PRO = "debug-entitlement-pro"
internal const val DEBUG_ENTITLEMENT_FREE = "debug-entitlement-free"
internal const val DEBUG_ENTITLEMENT_NEXT_LAUNCH = "debug-entitlement-next-launch"
internal const val DEBUG_ENTITLEMENT_DIAGNOSTICS = "debug-entitlement-diagnostics"

@Composable
fun DeveloperToolsSection() {
    val host = LocalDeveloperEntitlement.current ?: return
    val control by host.control.collectAsStateWithLifecycle()
    val revision by host.revisions.collectAsStateWithLifecycle(initialValue = 0)
    val decision by host.decisions.collectAsStateWithLifecycle(
        initialValue = FreeAccessLogic.unknown()
    )
    val panel = remember(control, revision, decision) { host.panel() }
    CompactEditorDivider()
    DeveloperEntitlementControls(
        panel = panel,
        onSelect = host.onSelect,
        onArmNextLaunch = host.onArmNextLaunch
    )
}

@Composable
internal fun DeveloperEntitlementControls(
    panel: DeveloperEntitlementPanel,
    onSelect: (DebugEntitlementMode) -> Unit,
    onArmNextLaunch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(DEBUG_ENTITLEMENT_SECTION)) {
        Text(
            text = stringResource(R.string.debug_entitlement_banner),
            style = AppTypeTokens.sectionKicker,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        CompactEditorSection(
            title = stringResource(R.string.debug_tools_title).uppercase(AppLocale.UI)
        ) {
            Text(
                text = stringResource(R.string.debug_entitlement_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Column(modifier = Modifier.selectableGroup()) {
                ModeRow(
                    label = stringResource(R.string.debug_entitlement_real),
                    selected = panel.mode == DebugEntitlementMode.Real,
                    testTag = DEBUG_ENTITLEMENT_REAL,
                    onClick = { onSelect(DebugEntitlementMode.Real) }
                )
                ModeRow(
                    label = stringResource(R.string.debug_entitlement_force_pro),
                    selected = panel.mode == DebugEntitlementMode.ForcePro,
                    testTag = DEBUG_ENTITLEMENT_PRO,
                    onClick = { onSelect(DebugEntitlementMode.ForcePro) }
                )
                ModeRow(
                    label = stringResource(R.string.debug_entitlement_force_free),
                    selected = panel.mode == DebugEntitlementMode.ForceFree,
                    testTag = DEBUG_ENTITLEMENT_FREE,
                    onClick = { onSelect(DebugEntitlementMode.ForceFree) }
                )
            }
            Spacer(Modifier.height(AppDimens.itemGap))
            Column(modifier = Modifier.testTag(DEBUG_ENTITLEMENT_DIAGNOSTICS)) {
                Diagnostic(stringResource(R.string.debug_entitlement_mode, modeLabel(panel.mode)))
                Diagnostic(
                    stringResource(
                        R.string.debug_entitlement_actual,
                        tierLabel(panel.actualKnown, panel.actualGrantsPro)
                    )
                )
                Diagnostic(
                    stringResource(
                        R.string.debug_entitlement_effective,
                        tierLabel(panel.effectiveKnown, panel.effectiveGrantsPro)
                    )
                )
                if (!panel.freeAccessLoaded || panel.phase == null || panel.periodId == null) {
                    Diagnostic(stringResource(R.string.debug_entitlement_not_loaded))
                } else {
                    Diagnostic(stringResource(R.string.debug_entitlement_phase, phaseLabel(panel.phase)))
                    Diagnostic(stringResource(R.string.debug_entitlement_period, panel.periodId))
                    Diagnostic(
                        stringResource(
                            R.string.debug_entitlement_confirmed,
                            stringResource(
                                if (panel.selectionConfirmed) {
                                    R.string.debug_entitlement_yes
                                } else {
                                    R.string.debug_entitlement_no
                                }
                            )
                        )
                    )
                    Diagnostic(stringResource(R.string.debug_entitlement_selected_plans, panel.selectedPlanCount))
                    Diagnostic(
                        stringResource(
                            R.string.debug_entitlement_selected_exercises,
                            panel.selectedExerciseCount
                        )
                    )
                    Diagnostic(stringResource(R.string.debug_entitlement_locked_plans, panel.lockedPlanCount))
                    Diagnostic(
                        stringResource(
                            R.string.debug_entitlement_locked_exercises,
                            panel.lockedExerciseCount
                        )
                    )
                }
                if (panel.pendingFreeOnNextLaunch) {
                    Diagnostic(stringResource(R.string.debug_entitlement_pending))
                }
            }
            TextButton(
                onClick = onArmNextLaunch,
                enabled = panel.canArmFreeOnNextLaunch,
                modifier = Modifier
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(DEBUG_ENTITLEMENT_NEXT_LAUNCH)
            ) {
                Text(stringResource(R.string.debug_entitlement_next_launch))
            }
        }
    }
}

@Composable
private fun ModeRow(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun Diagnostic(text: String) {
    Text(
        text = text,
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun modeLabel(mode: DebugEntitlementMode): String {
    return stringResource(
        when (mode) {
            DebugEntitlementMode.Real -> R.string.debug_entitlement_real
            DebugEntitlementMode.ForcePro -> R.string.debug_entitlement_force_pro
            DebugEntitlementMode.ForceFree -> R.string.debug_entitlement_force_free
        }
    )
}

@Composable
private fun tierLabel(known: Boolean, grantsPro: Boolean?): String {
    if (!known || grantsPro == null) return stringResource(R.string.debug_entitlement_tier_unknown)
    return stringResource(
        if (grantsPro) R.string.debug_entitlement_tier_pro else R.string.debug_entitlement_tier_free
    )
}

@Composable
private fun phaseLabel(phase: FreeAccessPhase): String {
    return stringResource(
        when (phase) {
            FreeAccessPhase.NotRequired -> R.string.debug_entitlement_phase_not_required
            FreeAccessPhase.Required -> R.string.debug_entitlement_phase_required
            FreeAccessPhase.Confirmed -> R.string.debug_entitlement_phase_confirmed
        }
    )
}
