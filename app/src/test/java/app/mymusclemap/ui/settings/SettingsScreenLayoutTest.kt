package app.mymusclemap.ui.settings

import app.mymusclemap.R
import app.mymusclemap.testString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.theme.PaletteDraftLogic
import app.mymusclemap.domain.theme.PaletteSession
import app.mymusclemap.domain.theme.PaletteSessionLogic
import app.mymusclemap.domain.theme.PaletteType
import app.mymusclemap.domain.theme.SeedField
import app.mymusclemap.domain.theme.ThemeMode
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.components.STRICT_WORDMARK_DARK
import app.mymusclemap.ui.components.STRICT_WORDMARK_LIGHT
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class SettingsScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lockScreenCompletionIsOffByDefaultAndDoesNotExplainUntilRequested() {
        var enabled: Boolean? = null
        render(onLockScreenSetCompletionChange = { enabled = it })
        composeRule.onNodeWithTag(SETTINGS_LOCK_SCREEN_SETS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.settings_lock_screen_sets_body)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.settings_lock_screen_sets_permission_reason)).assertCountEquals(0)
        composeRule.onNodeWithTag(SETTINGS_LOCK_SCREEN_SETS).performClick()
        assertEquals(true, enabled)
    }

    @Test
    fun lockScreenEnableExplanationCanBeDismissedWithoutConfirming() {
        var confirmed = false
        var dismissed = false
        render(
            lockScreenEnablePrompt = LockScreenEnablePrompt.RequestPermission,
            onConfirmLockScreenEnable = { confirmed = true },
            onDismissLockScreenEnable = { dismissed = true }
        )
        composeRule.onNodeWithText(testString(R.string.settings_lock_screen_sets_explain), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.settings_lock_screen_sets_permission_reason), substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_LOCK_SCREEN_NOT_NOW).performClick()
        assertTrue(dismissed)
        assertFalse(confirmed)
    }

    @Test
    fun weeklyGoalRowShowsNotSetWhenNothingIsConfigured() {
        render()
        composeRule.onNodeWithTag(SETTINGS_WEEKLY_GOAL).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.weekly_goal_not_set)).assertIsDisplayed()
    }

    @Test
    fun weeklyGoalRowShowsAPendingNextMondayChange() {
        val today = LocalDate.of(2026, 3, 12)
        val status = WeeklyGoalLogic.evaluate(
            listOf(
                WeeklyGoalRevision(LocalDate.of(2026, 3, 9), 3, graceWeek = true),
                WeeklyGoalRevision(LocalDate.of(2026, 3, 16), 5, graceWeek = false)
            ),
            emptyMap(),
            today
        )
        render(state = SettingsUiState(weeklyGoal = status))
        composeRule.onNodeWithText(testString(R.string.weekly_goal_pending_summary, 5))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun weeklyGoalRowShowsThatADisableStartsNextMonday() {
        val status = WeeklyGoalLogic.evaluate(
            listOf(
                WeeklyGoalRevision(LocalDate.of(2026, 3, 9), 4, graceWeek = false),
                WeeklyGoalRevision(LocalDate.of(2026, 3, 16), null, graceWeek = false)
            ),
            emptyMap(),
            LocalDate.of(2026, 3, 12)
        )
        render(state = SettingsUiState(weeklyGoal = status))
        composeRule.onNodeWithText(testString(R.string.weekly_goal_turns_off_next_monday))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun givenSystemThemeWhenScreenAppearsThenSystemRowIsSelected() {
        render()
        composeRule.onNodeWithTag(SETTINGS_THEME_SYSTEM).assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_LIGHT).assertIsNotSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_DARK).assertIsNotSelected()
        composeRule.onNodeWithText(testString(R.string.theme_title).uppercase()).assertIsDisplayed()
    }

    @Test
    fun givenAnotherAppearanceModeWhenRowTappedThenExactlyOneModeIsSelected() {
        renderInteractive()
        composeRule.onNodeWithTag(SETTINGS_THEME_LIGHT).performClick()
        composeRule.onNodeWithTag(SETTINGS_THEME_LIGHT).assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_SYSTEM).assertIsNotSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_DARK).assertIsNotSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_DARK).performClick()
        composeRule.onNodeWithTag(SETTINGS_THEME_DARK).assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_SYSTEM).assertIsNotSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_LIGHT).assertIsNotSelected()
    }

    @Test
    fun givenDefaultPaletteThenCustomEditorIsHidden() {
        render()
        composeRule.onNodeWithText(testString(R.string.theme_system)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.palette_default)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.palette_default_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.palette_custom_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PALETTE_DEFAULT).assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_PALETTE_CUSTOM).assertIsNotSelected()
        composeRule.onAllNodesWithTag(SETTINGS_PREVIEW).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SETTINGS_EDITOR).assertCountEquals(0)
        composeRule.onAllNodesWithTag(settingsColorRowTag(SeedField.Background)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SETTINGS_SAVE_BAR).assertCountEquals(0)
    }

    @Test
    fun givenCustomPaletteThenPreviewAndColorRowsAppear() {
        render(state = customState())
        composeRule.onNodeWithTag(SETTINGS_PALETTE_CUSTOM).assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_PREVIEW).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.theme_preview_title).uppercase()).assertIsDisplayed()
        SeedField.editorFields.forEach { field ->
            composeRule.onNodeWithTag(settingsColorRowTag(field)).assertIsDisplayed()
        }
        composeRule.onAllNodesWithTag(settingsColorRowTag(SeedField.Tertiary)).assertCountEquals(0)
        composeRule.onNodeWithTag(SETTINGS_SAVE_BAR).assertIsDisplayed()
    }

    @Test
    fun givenUnsavedHexWhenChangedThenPreviewUpdatesWithoutSaving() {
        val saves = intArrayOf(0)
        renderInteractive(initial = customState(), onSaveDraft = { saves[0] += 1 })
        composeRule.onNodeWithTag(settingsPreviewPrimaryTag("#1548C6")).assertIsDisplayed()
        composeRule.onNodeWithTag(settingsHexTag(SeedField.Primary))
            .performTextReplacement("#8A2BE2")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(settingsPreviewPrimaryTag("#8A2BE2")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(settingsPreviewPrimaryTag("#1548C6")).assertCountEquals(0)
        assertEquals(0, saves[0])
    }

    @Test
    fun givenCancelOrBackWithoutSaveThenUnsavedColorsAreNotPersisted() {
        val saves = intArrayOf(0)
        val cancels = intArrayOf(0)
        val backs = intArrayOf(0)
        renderInteractive(
            initial = customState(),
            onSaveDraft = { saves[0] += 1 },
            onCancelDraft = { cancels[0] += 1 },
            onBack = { backs[0] += 1 }
        )
        composeRule.onNodeWithTag(settingsHexTag(SeedField.Primary))
            .performTextReplacement("#8A2BE2")
        composeRule.onNodeWithTag(SETTINGS_CANCEL).performClick()
        composeRule.onNodeWithTag(settingsPreviewPrimaryTag("#1548C6")).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertEquals(0, saves[0])
        assertEquals(1, cancels[0])
        assertEquals(1, backs[0])
    }

    @Test
    fun givenValidChangeWhenSaveTappedTwiceThenSaveRunsOnce() {
        val saves = intArrayOf(0)
        render(state = customState(), onSaveDraft = { saves[0] += 1 })
        composeRule.onNodeWithTag(SETTINGS_SAVE).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(SETTINGS_SAVE).performClick()
        assertEquals(1, saves[0])
    }

    @Test
    fun givenInvalidHexThenSaveIsDisabledAndErrorIsShown() {
        val saves = intArrayOf(0)
        val invalid = PaletteDraftLogic.updateField(
            PaletteDraftLogic.fromSeeds(ThemeSeeds.copyOfFactoryLight(), ThemeSeeds.copyOfFactoryDark()),
            SeedField.Primary,
            "#ZZZZZZ"
        )
        render(
            state = customState().copy(draft = invalid),
            onSaveDraft = { saves[0] += 1 }
        )
        composeRule.onNodeWithTag(SETTINGS_SAVE).assertIsNotEnabled()
        composeRule.onNodeWithText(testString(R.string.error_color_hex))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_SAVE).performClick()
        assertEquals(0, saves[0])
    }

    @Test
    fun givenLightDarkEditorSwitchThenBothDraftsStay() {
        renderInteractive(initial = customState())
        composeRule.onNodeWithTag(settingsHexTag(SeedField.Primary))
            .performTextReplacement("#8A2BE2")
        composeRule.onNodeWithTag(SETTINGS_EDITOR_DARK).performClick()
        composeRule.onNodeWithTag(SETTINGS_EDITOR_DARK).assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_EDITOR_LIGHT).assertIsNotSelected()
        composeRule.onNodeWithTag(settingsHexTag(SeedField.Primary))
            .performTextReplacement("#BA81EE")
        composeRule.onNodeWithTag(SETTINGS_EDITOR_LIGHT).performClick()
        composeRule.onNodeWithTag(settingsHexTag(SeedField.Primary)).assertIsDisplayed()
        composeRule.onNodeWithText("#8A2BE2").assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_EDITOR_DARK).performClick()
        composeRule.onNodeWithText("#BA81EE").assertIsDisplayed()
    }

    @Test
    fun givenSwatchTapThenExistingColorPickerOpensForThatField() {
        render(state = customState())
        composeRule.onNodeWithTag(settingsSwatchTag(SeedField.Background)).performClick()
        composeRule.onNodeWithText(testString(R.string.color_preview_hint)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.color_background)).assertCountEquals(2)
        composeRule.onNodeWithText(testString(R.string.color_hex_code)).assertIsDisplayed()
    }

    @Test
    fun settingsExposeFullBackupAndNotBodyWeightCsvTransfer() {
        val exports = intArrayOf(0)
        val restores = intArrayOf(0)
        render(
            onAppBackupExportClick = { exports[0] += 1 },
            onRestoreClick = { restores[0] += 1 }
        )
        composeRule.onAllNodesWithTag("settings-export").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-import").assertCountEquals(0)
        composeRule.onAllNodesWithText("Export body-weight data").assertCountEquals(0)
        composeRule.onAllNodesWithText("Import body-weight data").assertCountEquals(0)
        composeRule.onNodeWithTag(SETTINGS_APP_BACKUP_EXPORT).performScrollTo().performClick()
        composeRule.onNodeWithTag(SETTINGS_APP_BACKUP_RESTORE).performScrollTo().performClick()
        assertEquals(1, exports[0])
        assertEquals(1, restores[0])
    }

    @Test
    fun givenNarrowWidthLargeFontAndFocusedHexThenActionsStayUsable() {
        render(
            state = customState(),
            width = 360.dp,
            height = 640.dp,
            fontScale = 1.3f
        )
        composeRule.onNodeWithTag(settingsHexTag(SeedField.Primary)).performClick()
        composeRule.onNodeWithTag(SETTINGS_SAVE_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_SAVE).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_CANCEL).assertIsDisplayed()
        val interactive = listOf(
            SETTINGS_THEME_SYSTEM,
            SETTINGS_THEME_LIGHT,
            SETTINGS_THEME_DARK,
            SETTINGS_PALETTE_DEFAULT,
            SETTINGS_PALETTE_CUSTOM,
            SETTINGS_EDITOR_LIGHT,
            SETTINGS_EDITOR_DARK,
            SETTINGS_GENERATE,
            SETTINGS_RESET,
            SETTINGS_SAVE,
            SETTINGS_CANCEL,
            settingsColorRowTag(SeedField.Background),
            settingsSwatchTag(SeedField.Background),
            settingsHexTag(SeedField.Primary)
        )
        interactive.forEach { tag ->
            ensureVisible(tag)
            assertMinTouch(tag)
        }
        val save = composeRule.onNodeWithTag(SETTINGS_SAVE_BAR).getBoundsInRoot()
        val hex = composeRule.onNodeWithTag(settingsHexTag(SeedField.Primary)).getBoundsInRoot()
        assertTrue("save bar should stay on screen: $save", save.bottom <= 640.dp + 1.dp)
        assertTrue(
            "save bar should not cover the focused hex field: hex=$hex save=$save",
            save.top >= hex.bottom - 1.dp || hex.bottom <= save.top
        )
    }

    @Test
    fun givenVisibleScreenThenLegacyMaterialChromeIsGone() {
        render(state = customState())
        composeRule.onNodeWithText(testString(R.string.theme_title).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.palette_title).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.theme_preview_title).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PREVIEW).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_EDITOR_MODE).assertIsDisplayed()
        composeRule.onAllNodesWithText("Export body-weight data").assertCountEquals(0)
        composeRule.onAllNodesWithText("Import body-weight data").assertCountEquals(0)
        composeRule.onNodeWithText(testString(R.string.app_backup_title).uppercase()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_export_app_backup)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_export_app_backup_subtitle)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_restore_app_backup)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_restore_app_backup_subtitle)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Export full backup").assertCountEquals(0)
        composeRule.onAllNodesWithText("Restore full backup").assertCountEquals(0)
        composeRule.onAllNodesWithText("My Muscle Map").assertCountEquals(0)
        composeRule.onAllNodesWithText("ABOUT MY MUSCLE MAP").assertCountEquals(0)
        composeRule.onNodeWithTag(SETTINGS_APP_BACKUP_EXPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_APP_BACKUP_RESTORE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.about_title).uppercase()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(STRICT_WORDMARK_LIGHT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.app_name)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.brand_tagline)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.about_product)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_HELP_TIPS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_SEND_FEEDBACK).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PRIVACY_POLICY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_SAVE).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_CANCEL).assertIsDisplayed()
        val save = composeRule.onNodeWithTag(SETTINGS_SAVE).getBoundsInRoot()
        val cancel = composeRule.onNodeWithTag(SETTINGS_CANCEL).getBoundsInRoot()
        assertTrue(
            "save and cancel should sit side by side, not stacked capsules",
            kotlin.math.abs(save.top.value - cancel.top.value) <= 8f
        )
        assertTrue(
            "save should not be a full-width capsule",
            save.right - save.left < 200.dp
        )
    }

    @Test
    fun aboutDarkThemeUsesTheDarkWordmark() {
        render(darkTheme = true)
        composeRule.onNodeWithText(testString(R.string.about_title).uppercase()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(STRICT_WORDMARK_DARK).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(STRICT_WORDMARK_LIGHT).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.brand_tagline)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("My Muscle Map").assertCountEquals(0)
    }

    @Test
    fun givenHelpAndTipsWhenTappedThenCallbackRunsOnce() {
        val opens = intArrayOf(0)
        render(onOpenHelp = { opens[0] += 1 })
        composeRule.onNodeWithTag(SETTINGS_HELP_TIPS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_help_tips)).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_HELP_TIPS).performClick()
        assertEquals(1, opens[0])
    }

    @Test
    fun givenFounderProgramHiddenThenAboutHasNoEnrollmentRow() {
        render(showFounderProgram = false)
        composeRule.onNodeWithTag("settings-founder-program").assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_open)).assertDoesNotExist()
    }

    @Test
    fun givenAboutSectionThenFounderProgramEntryOpensOnce() {
        val opens = intArrayOf(0)
        render(onOpenFounderProgram = { opens[0] += 1 })
        composeRule.onNodeWithTag("settings-founder-program").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_open)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_open_subtitle)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.founder_open)).assertCountEquals(1)
        val founder = composeRule.onNodeWithTag("settings-founder-program").getBoundsInRoot()
        val about = composeRule.onNodeWithText(testString(R.string.about_title).uppercase()).getBoundsInRoot()
        val palette = composeRule.onNodeWithText(testString(R.string.palette_title).uppercase()).getBoundsInRoot()
        assertTrue(palette.bottom < founder.top)
        assertTrue(founder.bottom < about.top)
        composeRule.onNodeWithTag("settings-founder-program").performClick()
        assertEquals(1, opens[0])
    }

    @Test
    fun givenFoundingMemberThenSettingsShowsMemberStatusOnce() {
        render(state = SettingsUiState(appVersionName = "0.1.0-debug"), showFounderBadge = true)
        composeRule.onNodeWithText(testString(R.string.founder_badge)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_member_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_open)).assertDoesNotExist()
        composeRule.onNodeWithText(
            testString(R.string.about_version_status, "0.1.0-debug", testString(R.string.founder_badge))
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.founder_member_subtitle)).assertCountEquals(1)
    }

    @Test
    fun lockScreenRowKeepsItsCopyClearOfTheSwitch() {
        render(width = 320.dp, fontScale = 1.3f)
        val title = composeRule.onNodeWithText(testString(R.string.settings_lock_screen_sets_title)).getBoundsInRoot()
        val body = composeRule.onNodeWithText(testString(R.string.settings_lock_screen_sets_body)).getBoundsInRoot()
        val toggle = composeRule.onNodeWithTag(SETTINGS_LOCK_SCREEN_SETS).getBoundsInRoot()
        assertTrue(title.right <= toggle.left)
        assertTrue(body.right <= toggle.left)
        assertTrue(toggle.left - maxOf(title.right, body.right) >= AppDimens.itemGap - 1.dp)
    }

    @Test
    fun givenAppVersionThenAboutSectionShowsDynamicVersion() {
        render(state = SettingsUiState(appVersionName = "9.9.9-debug"))
        composeRule.onNodeWithText(testString(R.string.about_title).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.about_version, "9.9.9-debug")).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_APP_VERSION).assertIsDisplayed()
    }

    @Test
    fun givenSendFeedbackWhenTappedThenCallbackRunsOnce() {
        val feedback = intArrayOf(0)
        render(onSendFeedback = { feedback[0] += 1 })
        composeRule.onNodeWithTag(SETTINGS_SEND_FEEDBACK).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_SEND_FEEDBACK).performClick()
        assertEquals(1, feedback[0])
    }

    @Test
    fun givenNullPrivacyUrlThenPrivacyRowOpensInAppPolicy() {
        val opens = intArrayOf(0)
        render(
            state = SettingsUiState(privacyPolicyUrl = null),
            onOpenPrivacyPolicy = { opens[0] += 1 }
        )
        composeRule.onNodeWithTag(SETTINGS_PRIVACY_POLICY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PRIVACY_POLICY).assertIsEnabled()
        composeRule.onNodeWithText(testString(R.string.action_privacy_policy_in_app_subtitle)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PRIVACY_POLICY).performClick()
        assertEquals(1, opens[0])
    }

    @Test
    fun givenPrivacyUrlThenPrivacyRowIsEnabledAndOpensOnce() {
        val opens = intArrayOf(0)
        render(
            state = SettingsUiState(privacyPolicyUrl = "https://example.com/privacy"),
            onOpenPrivacyPolicy = { opens[0] += 1 }
        )
        composeRule.onNodeWithTag(SETTINGS_PRIVACY_POLICY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PRIVACY_POLICY).assertIsEnabled()
        composeRule.onNodeWithText(testString(R.string.action_privacy_policy_subtitle)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PRIVACY_POLICY).performClick()
        assertEquals(1, opens[0])
    }

    @Test
    fun givenRestoreExplanationThenReplaceWarningIsShown() {
        render(state = SettingsUiState(showRestoreExplanation = true))
        composeRule.onAllNodesWithText(testString(R.string.restore_app_backup_title)).assertCountEquals(2)
        composeRule.onNodeWithText(testString(R.string.restore_app_backup_body)).assertIsDisplayed()
    }

    private fun ensureVisible(tag: String) {
        val node = composeRule.onNodeWithTag(tag)
        try {
            node.performScrollTo()
        } catch (_: AssertionError) {
            // Pinned chrome (save bar) is outside the scroll container.
        }
        node.assertIsDisplayed()
    }

    private fun assertMinTouch(tag: String) {
        val bounds = composeRule.onNodeWithTag(tag).getBoundsInRoot()
        assertTrue(
            "$tag height should be at least 48dp: $bounds",
            bounds.bottom - bounds.top >= 48.dp
        )
        assertTrue(
            "$tag width should be at least 48dp: $bounds",
            bounds.right - bounds.left >= 48.dp
        )
        assertTrue("$tag should stay within 360dp: $bounds", bounds.right <= 360.dp + 8.dp)
        assertTrue("$tag should not be clipped: $bounds", bounds.left >= (-8).dp)
    }

    private fun customState(): SettingsUiState {
        val light = ThemeSeeds.copyOfFactoryLight()
        val dark = ThemeSeeds.copyOfFactoryDark()
        return SettingsUiState(
            appearance = AppearanceSettings(
                mode = ThemeMode.System,
                paletteType = PaletteType.Custom,
                customLight = light,
                customDark = dark
            ),
            draft = PaletteDraftLogic.fromSeeds(light, dark)
        )
    }

    private fun renderInteractive(
        initial: SettingsUiState = SettingsUiState(),
        onSaveDraft: () -> Unit = {},
        onCancelDraft: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            var appearance by remember { mutableStateOf(initial.appearance) }
            var draft by remember { mutableStateOf(initial.draft) }
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                        SettingsScreen(
                            state = SettingsUiState(appearance = appearance, draft = draft),
                            onThemeSelected = { appearance = appearance.copy(mode = it) },
                            onSelectDefaultPalette = {
                                val next = PaletteSessionLogic.selectDefault(
                                    PaletteSession(appearance, draft)
                                )
                                appearance = next.appearance
                                draft = next.draft
                            },
                            onSelectCustomPalette = {
                                val next = PaletteSessionLogic.selectCustom(
                                    PaletteSession(appearance, draft)
                                )
                                appearance = next.appearance
                                draft = next.draft
                            },
                            onEditingDarkChange = { editingDark ->
                                draft = draft?.copy(editingDark = editingDark)
                            },
                            onDraftFieldChange = { field, raw ->
                                draft = draft?.let { PaletteDraftLogic.updateField(it, field, raw) }
                            },
                            onDraftColorPicked = { field, rgb ->
                                draft = draft?.let { PaletteDraftLogic.applyParsedColor(it, field, rgb) }
                            },
                            onGenerateDark = {
                                draft = draft?.let(PaletteDraftLogic::generateDark)
                            },
                            onSaveDraft = onSaveDraft,
                            onCancelDraft = {
                                val next = PaletteSessionLogic.cancelDraft(
                                    PaletteSession(appearance, draft)
                                )
                                draft = next.draft
                                onCancelDraft()
                            },
                            onResetCustomDraft = {
                                val next = PaletteSessionLogic.resetCustomDraft(
                                    PaletteSession(appearance, draft)
                                )
                                draft = next.draft
                            },
                            onAppBackupExportClick = {},
                            onRestoreClick = {},
                            onConfirmRestoreExplanation = {},
                            onDismissRestoreExplanation = {},
                            onDismissRestoreErrors = {},
                            onSendFeedback = {},
                            onOpenPrivacyPolicy = {},
                            onOpenHelp = {},
                            onMessageConsumed = {},
                            onBack = onBack
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun proPlanStaysAvailableWhenTheFounderEntryIsHidden() {
        var opens = 0
        render(showFounderProgram = false, onOpenProPlan = { opens += 1 })
        composeRule.onNodeWithTag(SETTINGS_PRO_PLAN).performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, opens)
        composeRule.onNodeWithText(testString(R.string.settings_pro_plan)).assertExists()
    }

    private fun render(
        state: SettingsUiState = SettingsUiState(),
        width: Dp = 360.dp,
        height: Dp = 2000.dp,
        fontScale: Float = 1f,
        onSaveDraft: () -> Unit = {},
        onAppBackupExportClick: () -> Unit = {},
        onRestoreClick: () -> Unit = {},
        onSendFeedback: () -> Unit = {},
        onOpenPrivacyPolicy: () -> Unit = {},
        onOpenHelp: () -> Unit = {},
        onOpenFounderProgram: () -> Unit = {},
        onOpenProPlan: () -> Unit = {},
        showFounderProgram: Boolean = true,
        showFounderBadge: Boolean = false,
        darkTheme: Boolean = false,
        onBack: () -> Unit = {},
        onLockScreenSetCompletionChange: (Boolean) -> Unit = {},
        lockScreenEnablePrompt: LockScreenEnablePrompt? = null,
        onConfirmLockScreenEnable: () -> Unit = {},
        onDismissLockScreenEnable: () -> Unit = {},
        accountName: String? = null,
        accountEmail: String? = null,
        onSignOut: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview(
                    seeds = if (darkTheme) ThemeSeeds.DefaultDark else ThemeSeeds.DefaultLight,
                    darkTheme = darkTheme
                ) {
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(height)
                            .fillMaxSize()
                    ) {
                        SettingsScreen(
                            state = state,
                            onThemeSelected = {},
                            onSelectDefaultPalette = {},
                            onSelectCustomPalette = {},
                            onEditingDarkChange = {},
                            onDraftFieldChange = { _, _ -> },
                            onDraftColorPicked = { _, _ -> },
                            onGenerateDark = {},
                            onSaveDraft = onSaveDraft,
                            onCancelDraft = {},
                            onResetCustomDraft = {},
                            onAppBackupExportClick = onAppBackupExportClick,
                            onRestoreClick = onRestoreClick,
                            onConfirmRestoreExplanation = {},
                            onDismissRestoreExplanation = {},
                            onDismissRestoreErrors = {},
                            onSendFeedback = onSendFeedback,
                            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                            onOpenHelp = onOpenHelp,
                            onOpenFounderProgram = onOpenFounderProgram,
                            onOpenProPlan = onOpenProPlan,
                            showFounderProgram = showFounderProgram,
                            showFounderBadge = showFounderBadge,
                            onMessageConsumed = {},
                            onBack = onBack,
                            onLockScreenSetCompletionChange = onLockScreenSetCompletionChange,
                            lockScreenEnablePrompt = lockScreenEnablePrompt,
                            onConfirmLockScreenEnable = onConfirmLockScreenEnable,
                            onDismissLockScreenEnable = onDismissLockScreenEnable,
                            accountName = accountName,
                            accountEmail = accountEmail,
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun accountSectionShowsTheSignedInGoogleAccountAndConfirmsSignOut() {
        var signedOut = 0
        render(accountName = "Ada", accountEmail = "ada@example.com", onSignOut = { signedOut += 1 })
        composeRule.onNodeWithTag(SETTINGS_ACCOUNT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ada").assertIsDisplayed()
        composeRule.onNodeWithText("ada@example.com").assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_SIGN_OUT).performScrollTo().performClick()
        composeRule.onNodeWithText(testString(R.string.settings_sign_out_title)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.settings_sign_out))[1].performClick()
        assertEquals(1, signedOut)
    }
}
