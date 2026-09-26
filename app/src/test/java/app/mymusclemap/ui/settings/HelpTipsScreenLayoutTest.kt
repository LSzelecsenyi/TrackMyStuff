package app.mymusclemap.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class HelpTipsScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenHelpScreenThenAllTopicsAndHeatmapBandsAreShown() {
        render()
        composeRule.onNodeWithTag(HELP_ROOT).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.help_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(HELP_TOPIC_WORKOUT).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.help_workout_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(HELP_TOPIC_HEATMAP).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.help_heatmap_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_band_today)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_band_recent)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_band_days_3_4)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_band_days_5_6)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_band_old)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_band_inactive)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_band_never)).assertIsDisplayed()
        composeRule.onNodeWithTag(HELP_TOPIC_WEIGHT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.help_weight_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(HELP_TOPIC_CALENDAR).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.help_calendar_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(HELP_TOPIC_BACKUP).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.help_backup_body)).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.help_workout_title).uppercase(AppLocale.UI)
        ).assertIsDisplayed()
    }

    @Test
    fun givenBackWhenTappedThenCallbackRunsOnce() {
        val backs = intArrayOf(0)
        render(onBack = { backs[0] += 1 })
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertEquals(1, backs[0])
    }

    private fun render(onBack: () -> Unit = {}) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .height(2000.dp)
                            .fillMaxSize()
                    ) {
                        HelpTipsScreen(onBack = onBack)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
