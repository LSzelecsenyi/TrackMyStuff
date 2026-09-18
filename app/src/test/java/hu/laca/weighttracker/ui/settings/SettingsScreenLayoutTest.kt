package hu.laca.weighttracker.ui.settings

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
import hu.laca.weighttracker.domain.theme.AppearanceSettings
import hu.laca.weighttracker.domain.theme.PaletteDraftLogic
import hu.laca.weighttracker.domain.theme.PaletteSession
import hu.laca.weighttracker.domain.theme.PaletteSessionLogic
import hu.laca.weighttracker.domain.theme.PaletteType
import hu.laca.weighttracker.domain.theme.SeedField
import hu.laca.weighttracker.domain.theme.ThemeMode
import hu.laca.weighttracker.domain.theme.ThemeSeeds
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class SettingsScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenSystemThemeWhenScreenAppearsThenSystemRowIsSelected() {
        render()
        composeRule.onNodeWithTag(SETTINGS_THEME_SYSTEM).assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_LIGHT).assertIsNotSelected()
        composeRule.onNodeWithTag(SETTINGS_THEME_DARK).assertIsNotSelected()
        composeRule.onNodeWithText("MEGJELENÉS").assertIsDisplayed()
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
        composeRule.onNodeWithText("ELŐNÉZET").assertIsDisplayed()
        SeedField.entries.forEach { field ->
            composeRule.onNodeWithTag(settingsColorRowTag(field)).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(SETTINGS_SAVE_BAR).assertIsDisplayed()
    }

    @Test
    fun givenUnsavedHexWhenChangedThenPreviewUpdatesWithoutSaving() {
        val saves = intArrayOf(0)
        renderInteractive(initial = customState(), onSaveDraft = { saves[0] += 1 })
        composeRule.onNodeWithTag(settingsPreviewPrimaryTag("#2457C5")).assertIsDisplayed()
        composeRule.onNodeWithTag(settingsHexTag(SeedField.Primary))
            .performTextReplacement("#8A2BE2")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(settingsPreviewPrimaryTag("#8A2BE2")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(settingsPreviewPrimaryTag("#2457C5")).assertCountEquals(0)
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
        composeRule.onNodeWithTag(settingsPreviewPrimaryTag("#2457C5")).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Vissza").performClick()
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
        composeRule.onNodeWithText("A színkód formátuma #RRGGBB legyen, például #2457C5.")
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
        composeRule.onNodeWithText("Élő előnézet").assertIsDisplayed()
        composeRule.onAllNodesWithText("Háttér").assertCountEquals(2)
        composeRule.onNodeWithText("HEX színkód").assertIsDisplayed()
    }

    @Test
    fun givenExportOrImportRowWhenTappedThenExistingSafFlowStartsOnce() {
        val exports = intArrayOf(0)
        val imports = intArrayOf(0)
        render(
            onExportClick = { exports[0] += 1 },
            onImportClick = { imports[0] += 1 }
        )
        composeRule.onNodeWithText("ADATOK").assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_EXPORT).performClick()
        composeRule.onNodeWithTag(SETTINGS_IMPORT).performClick()
        assertEquals(1, exports[0])
        assertEquals(1, imports[0])
        assertEquals(0, composeRule.onAllNodesWithText("Edzés CSV").fetchSemanticsNodes().size)
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
            SETTINGS_EXPORT,
            SETTINGS_IMPORT,
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
        composeRule.onNodeWithText("MEGJELENÉS").assertIsDisplayed()
        composeRule.onNodeWithText("SZÍNPALETTA").assertIsDisplayed()
        composeRule.onNodeWithText("ELŐNÉZET").assertIsDisplayed()
        composeRule.onNodeWithText("ADATOK").assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PREVIEW).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_EDITOR_MODE).assertIsDisplayed()
        composeRule.onAllNodesWithText("Mentés és visszaállítás").fetchSemanticsNodes().let { nodes ->
            assertEquals(0, nodes.size)
        }
        composeRule.onAllNodesWithText("Adatok exportálása").fetchSemanticsNodes().let { nodes ->
            assertEquals(0, nodes.size)
        }
        composeRule.onNodeWithText("Testsúlyadatok exportálása").assertIsDisplayed()
        composeRule.onNodeWithText("Testsúlyadatok importálása").assertIsDisplayed()
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
                            onExportClick = {},
                            onImportClick = {},
                            onConfirmImportExplanation = {},
                            onDismissImportExplanation = {},
                            onDismissImportErrors = {},
                            onMessageConsumed = {},
                            onBack = onBack
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun render(
        state: SettingsUiState = SettingsUiState(),
        width: Dp = 360.dp,
        height: Dp = 2000.dp,
        fontScale: Float = 1f,
        onSaveDraft: () -> Unit = {},
        onExportClick: () -> Unit = {},
        onImportClick: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
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
                            onExportClick = onExportClick,
                            onImportClick = onImportClick,
                            onConfirmImportExplanation = {},
                            onDismissImportExplanation = {},
                            onDismissImportErrors = {},
                            onMessageConsumed = {},
                            onBack = onBack
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
