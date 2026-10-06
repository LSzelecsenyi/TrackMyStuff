package app.mymusclemap.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.TargetWeightDirection
import app.mymusclemap.domain.achievements.TargetWeightGoalFacts
import app.mymusclemap.domain.achievements.TargetWeightProgressEvaluator
import app.mymusclemap.testString
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class TargetWeightScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeLossTargetShowsNeutralProgress() {
        val progress = TargetWeightProgressEvaluator.progress(
            TargetWeightGoalFacts(
                id = 1,
                targetKg = 78.0,
                baselineKg = 87.0,
                direction = TargetWeightDirection.LOSS,
                active = true
            ),
            80.0
        )!!
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                WeightDetailsScreen(
                    state = WeightDetailsUiState(
                        showTarget = true,
                        target = TargetWeightCard(
                            targetKg = 78.0,
                            progress = progress,
                            waitingForBaseline = false
                        )
                    ),
                    onBack = {},
                    onAddToday = {},
                    onChartRangeSelected = {},
                    onEditorDateChange = {},
                    onEditorWeightChange = {},
                    onSave = {},
                    onDismissEditor = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                    onMessageConsumed = {}
                )
            }
        }
        composeRule.onNodeWithTag("target-weight-section").assertIsDisplayed()
        composeRule.onNodeWithText(UiFormatters.weightKg(78.0)).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.target_weight_current, UiFormatters.weightKg(80.0))
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(
                R.string.target_weight_progress_count,
                UiFormatters.weightValue(7.0),
                UiFormatters.weightValue(9.0)
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.target_weight_to_go, UiFormatters.weightKg(2.0))
        ).assertIsDisplayed()
    }

    @Test
    fun emptyTargetOffersSetAndRemoveRetiresTheRequest() {
        var opened = 0
        var cleared = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                WeightDetailsScreen(
                    state = WeightDetailsUiState(showTarget = true, target = null),
                    onBack = {},
                    onAddToday = {},
                    onChartRangeSelected = {},
                    onEditorDateChange = {},
                    onEditorWeightChange = {},
                    onSave = {},
                    onDismissEditor = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                    onMessageConsumed = {},
                    onOpenTarget = { opened += 1 },
                    onClearTarget = { cleared += 1 }
                )
            }
        }
        composeRule.onNodeWithTag("target-weight-set").performClick()
        assertEquals(1, opened)
        assertEquals(0, cleared)
    }
}
