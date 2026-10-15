package app.mymusclemap.ui.muscleinsights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.muscleinsights.BaselineCoverage
import app.mymusclemap.domain.muscleinsights.MuscleInsight
import app.mymusclemap.domain.muscleinsights.MuscleInsightTopic
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import app.mymusclemap.domain.muscleinsights.MuscleQuickInsight
import app.mymusclemap.domain.muscleinsights.StimulusAge
import app.mymusclemap.domain.muscleinsights.WeeklySetPoint
import app.mymusclemap.domain.musclemap.MuscleHeatmapEntry
import app.mymusclemap.domain.musclemap.MuscleRecencyBand
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class MuscleInsightsScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun demoStateShowsSampleLabelAndUpgradeDoesNotChangeTheMetrics() {
        val upgrades = intArrayOf(0)
        render(sampleState(demo = true), onUpgrade = { upgrades[0] += 1 })
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_DEMO).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_demo_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_UPGRADE).performClick()
        assertEquals(1, upgrades[0])
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_DEMO).assertIsDisplayed()
    }

    @Test
    fun personalStateDoesNotShowDemoData() {
        render(sampleState(demo = false))
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_DEMO).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_demo)).assertDoesNotExist()
    }

    @Test
    fun emptyMuscleShowsTheChartEmptyState() {
        render(
            MuscleInsightsUiState(
                loading = false,
                muscle = MuscleGroup.SERRATUS,
                insight = MuscleInsight.empty(MuscleGroup.SERRATUS)
            )
        )
        composeRule.onNodeWithText(testString(R.string.muscle_insights_chart_empty))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.muscle_insights_insufficient))
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun partialHistoryAppearsWithTheSameComparisonInEverySection() {
        render(sampleState(demo = false, coverage = BaselineCoverage.PARTIAL, partialHistoryDays = 25))
        composeRule.onAllNodesWithText(testString(R.string.muscle_insights_above))
            .assertCountEquals(3)
        composeRule.onAllNodesWithText(testString(R.string.muscle_insights_partial_history))
            .assertCountEquals(3)
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PARTIAL).assertIsDisplayed()
    }

    @Test
    fun fullCoverageDoesNotShowThePartialHistoryNote() {
        render(sampleState(demo = false, coverage = BaselineCoverage.FULL))
        composeRule.onNodeWithText(testString(R.string.muscle_insights_partial_history))
            .assertDoesNotExist()
        composeRule.onAllNodesWithText(testString(R.string.muscle_insights_above))
            .assertCountEquals(3)
    }

    @Test
    fun loadComparisonSheetSeparatesMeaningFromCalculation() {
        render(
            sampleState(
                demo = false,
                coverage = BaselineCoverage.PARTIAL,
                partialHistoryDays = 25
            ).copy(explanation = MuscleInsightTopic.RECENT_LOAD)
        )
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_MEANING).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_load_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_load_question)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_explain_how)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_partial_history_days, 25))
            .assertIsDisplayed()
    }

    @Test
    fun explanationSheetShowsTheCalculationAndDismisses() {
        val dismissed = intArrayOf(0)
        render(
            sampleState(demo = false).copy(explanation = MuscleInsightTopic.LAST_STIMULUS),
            onDismissExplanation = { dismissed[0] += 1 }
        )
        composeRule.onNodeWithText(testString(R.string.muscle_insights_explain_stimulus)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_close)).performClick()
        assertEquals(1, dismissed[0])
    }

    @Test
    fun darkThemeStillShowsTheInsightsTitle() {
        composeRule.setContent {
            WeightTrackerThemeForPreview(darkTheme = true) {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    MuscleInsightsScreen(
                        state = sampleState(demo = false),
                        onBack = {},
                        onMuscleSelected = {},
                        onPeriodSelected = {},
                        onExplain = {},
                        onDismissExplanation = {},
                        onUpgrade = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText(testString(R.string.muscle_insights_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_period_4w)).assertIsDisplayed()
    }

    @Test
    fun quickInsightsOpenTheSelectedMuscle() {
        val opened = arrayOfNulls<MuscleGroup>(1)
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(Modifier.fillMaxSize()) {
                    MuscleQuickInsightsSheet(
                        entry = MuscleHeatmapEntry(
                            group = MuscleGroup.LATS,
                            lastTrained = LocalDate.of(2026, 10, 13),
                            daysAgo = 2,
                            band = MuscleRecencyBand.DAYS_1_2
                        ),
                        insight = MuscleQuickInsight(
                            muscle = MuscleGroup.LATS,
                            lastStimulus = StimulusAge.Hours(30),
                            recentPrimarySets = 3
                        ),
                        onDismiss = {},
                        onOpenInsights = { opened[0] = it }
                    )
                }
            }
        }
        composeRule.onNodeWithTag(MUSCLE_QUICK_INSIGHTS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_recovery_short)).assertIsDisplayed()
        composeRule.onNodeWithTag(MUSCLE_QUICK_INSIGHTS_OPEN).performClick()
        assertEquals(MuscleGroup.LATS, opened[0])
    }

    private fun render(
        state: MuscleInsightsUiState,
        onUpgrade: () -> Unit = {},
        onDismissExplanation: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(
                    Modifier
                        .width(360.dp)
                        .height(2400.dp)
                        .fillMaxSize()
                ) {
                    MuscleInsightsScreen(
                        state = state,
                        onBack = {},
                        onMuscleSelected = {},
                        onPeriodSelected = {},
                        onExplain = {},
                        onDismissExplanation = onDismissExplanation,
                        onUpgrade = onUpgrade
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun sampleState(
        demo: Boolean,
        coverage: BaselineCoverage = BaselineCoverage.FULL,
        partialHistoryDays: Int? = null
    ): MuscleInsightsUiState {
        val today = LocalDate.of(2026, 10, 12)
        return MuscleInsightsUiState(
            loading = false,
            demo = demo,
            muscle = MuscleGroup.CHEST,
            period = MuscleInsightsPeriod.Weeks4,
            insight = MuscleInsight(
                muscle = MuscleGroup.CHEST,
                lastStimulus = StimulusAge.Hours(48),
                recentPrimarySets = 4,
                baselineWeeklySets = 3.0,
                comparison = app.mymusclemap.domain.muscleinsights.LoadComparison.ABOVE,
                coverage = coverage,
                partialHistoryDays = partialHistoryDays,
                weeklySets = listOf(
                    WeeklySetPoint(today.minusWeeks(1), 3),
                    WeeklySetPoint(today, 4)
                ),
                periodPrimarySets = 7,
                periodSecondarySets = 1,
                spacing = null
            )
        )
    }
}
