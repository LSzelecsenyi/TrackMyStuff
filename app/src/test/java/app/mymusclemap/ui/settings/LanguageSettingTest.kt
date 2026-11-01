package app.mymusclemap.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.domain.locale.AppLanguage
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguageSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aHungarianDeviceShowsMagyarAndEnglish() {
        var selected = AppLanguage.HU
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                AppearancePreferencesScreen(
                    state = SettingsUiState(),
                    onBack = {},
                    showLanguageSetting = true,
                    selectedLanguage = selected,
                    onLanguageSelected = { selected = it }
                )
            }
        }
        composeRule.onNodeWithTag(SETTINGS_LANGUAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Magyar").assertIsDisplayed()
        composeRule.onNodeWithText("English").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_LANGUAGE_EN).performScrollTo().performClick()
        assertEquals(AppLanguage.EN, selected)
    }

    @Test
    fun aNonHungarianDeviceHidesTheLanguageSetting() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                AppearancePreferencesScreen(
                    state = SettingsUiState(),
                    onBack = {}
                )
            }
        }
        composeRule.onAllNodesWithTag(SETTINGS_LANGUAGE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SETTINGS_LANGUAGE_HU).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SETTINGS_LANGUAGE_EN).assertCountEquals(0)
    }
}
