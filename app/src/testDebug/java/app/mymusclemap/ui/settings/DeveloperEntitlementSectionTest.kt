package app.mymusclemap.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.domain.entitlement.DebugEntitlementControl
import app.mymusclemap.domain.entitlement.DebugEntitlementMode
import app.mymusclemap.domain.entitlement.EffectiveEntitlement
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FreeAccessDecision
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeAccessState
import app.mymusclemap.domain.entitlement.buildDeveloperEntitlementPanel
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import app.mymusclemap.R

@RunWith(RobolectricTestRunner::class)
class DeveloperEntitlementSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsHidesTheOverrideUntilADebugHostIsProvided() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                SettingsScreen(
                    state = SettingsUiState(),
                    onSelectDefaultPalette = {},
                    onSelectCustomPalette = {},
                    onEditingDarkChange = {},
                    onDraftFieldChange = { _, _ -> },
                    onDraftColorPicked = { _, _ -> },
                    onGenerateDark = {},
                    onSaveDraft = {},
                    onCancelDraft = {},
                    onResetCustomDraft = {},
                    onAppBackupExportClick = {},
                    onRestoreClick = {},
                    onConfirmRestoreExplanation = {},
                    onDismissRestoreExplanation = {},
                    onDismissRestoreErrors = {},
                    onSendFeedback = {},
                    onOpenPrivacyPolicy = {},
                    onOpenHelp = {},
                    onMessageConsumed = {},
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag(DEBUG_ENTITLEMENT_SECTION).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.debug_entitlement_title)).assertDoesNotExist()
    }

    @Test
    fun settingsShowsTheOverrideAndKeepsNextLaunchOffTheLiveSelection() {
        val control = MutableStateFlow(DebugEntitlementControl(mode = DebugEntitlementMode.ForcePro))
        val revisions = MutableStateFlow(0)
        val decisions = MutableStateFlow(confirmedDecision())
        var armed = false
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                CompositionLocalProvider(
                    LocalDeveloperEntitlement provides DeveloperEntitlementHost(
                        control = control,
                        revisions = revisions,
                        decisions = decisions,
                        panel = {
                            buildDeveloperEntitlementPanel(
                                control = control.value,
                                actualKnown = true,
                                effectiveKnown = true,
                                actual = pro(),
                                effective = if (control.value.mode == DebugEntitlementMode.ForceFree) {
                                    free()
                                } else {
                                    pro()
                                },
                                decision = decisions.value
                            )
                        },
                        onSelect = { mode ->
                            control.value = DebugEntitlementControl(mode = mode)
                            revisions.value += 1
                        },
                        onArmNextLaunch = {
                            armed = true
                            control.value = control.value.copy(pendingFreeOnNextLaunch = true)
                        }
                    )
                ) {
                    SettingsScreen(
                        state = SettingsUiState(),
                        onSelectDefaultPalette = {},
                        onSelectCustomPalette = {},
                        onEditingDarkChange = {},
                        onDraftFieldChange = { _, _ -> },
                        onDraftColorPicked = { _, _ -> },
                        onGenerateDark = {},
                        onSaveDraft = {},
                        onCancelDraft = {},
                        onResetCustomDraft = {},
                        onAppBackupExportClick = {},
                        onRestoreClick = {},
                        onConfirmRestoreExplanation = {},
                        onDismissRestoreExplanation = {},
                        onDismissRestoreErrors = {},
                        onSendFeedback = {},
                        onOpenPrivacyPolicy = {},
                        onOpenHelp = {},
                        onMessageConsumed = {},
                        onBack = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag(DEBUG_ENTITLEMENT_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.debug_entitlement_banner)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.debug_entitlement_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DEBUG_ENTITLEMENT_PRO).performScrollTo().assertIsSelected()
        composeRule.onNodeWithText(
            testString(
                R.string.debug_entitlement_effective,
                testString(R.string.debug_entitlement_tier_pro)
            )
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(
                R.string.debug_entitlement_confirmed,
                testString(R.string.debug_entitlement_yes)
            )
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.debug_entitlement_locked_plans, 1))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.debug_entitlement_locked_exercises, 1))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DEBUG_ENTITLEMENT_NEXT_LAUNCH).performScrollTo().assertIsEnabled().performClick()
        assertTrue(armed)
        composeRule.onNodeWithText(testString(R.string.debug_entitlement_pending)).assertIsDisplayed()
        composeRule.onNodeWithTag(DEBUG_ENTITLEMENT_NEXT_LAUNCH).assertIsNotEnabled()
        composeRule.onNodeWithTag(DEBUG_ENTITLEMENT_FREE).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(DebugEntitlementMode.ForceFree, control.value.mode)
        assertFalse(control.value.pendingFreeOnNextLaunch)
        composeRule.onNodeWithTag(DEBUG_ENTITLEMENT_FREE).assertIsSelected()
        composeRule.onNodeWithTag(DEBUG_ENTITLEMENT_NEXT_LAUNCH).assertIsNotEnabled()
    }

    private fun confirmedDecision(): FreeAccessDecision {
        val state = FreeAccessState(
            periodId = 3L,
            lastKnownPro = false,
            phase = FreeAccessPhase.Confirmed,
            selectedPlanIds = setOf(1L, 2L, 3L),
            selectedCustomExerciseIds = setOf(10L, 11L, 12L, 13L, 14L)
        )
        return FreeAccessLogic.decide(
            state,
            grantsPro = false,
            plans = (1L..4L).map { app.mymusclemap.domain.entitlement.FreeAccessSubject(it, it) },
            customExercises = (10L..15L).map { app.mymusclemap.domain.entitlement.FreeAccessSubject(it, it) }
        )
    }

    private fun pro() = EffectiveEntitlement(
        tier = EntitlementTier.Pro,
        subscriptionValid = true,
        founderLifetime = false,
        temporaryTesterPro = false
    )

    private fun free() = EffectiveEntitlement(
        tier = EntitlementTier.Free,
        subscriptionValid = false,
        founderLifetime = false,
        temporaryTesterPro = false
    )
}
