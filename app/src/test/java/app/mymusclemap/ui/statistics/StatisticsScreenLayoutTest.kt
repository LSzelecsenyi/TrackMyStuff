package app.mymusclemap.ui.statistics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.statistics.ExerciseBest
import app.mymusclemap.domain.statistics.ExerciseHistoryKind
import app.mymusclemap.domain.statistics.ExerciseHistoryPoint
import app.mymusclemap.domain.statistics.ExerciseProgressSummary
import app.mymusclemap.domain.statistics.MuscleRestSummary
import app.mymusclemap.domain.statistics.MuscleTrainingCount
import app.mymusclemap.domain.statistics.PlanAdherence
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.statistics.TrainingActivity
import app.mymusclemap.domain.statistics.TrainingStatistics
import app.mymusclemap.domain.statistics.TrainingVolume
import app.mymusclemap.testQuantity
import app.mymusclemap.testString
import app.mymusclemap.ui.pro.PRO_INFO_BODY
import app.mymusclemap.ui.pro.PRO_INFO_HIGHLIGHTS
import app.mymusclemap.ui.pro.PRO_INFO_SHEET
import app.mymusclemap.ui.pro.PRO_INFO_TITLE
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class StatisticsScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun givenNoCompletedWorkoutsThenEmptyStateStillShowsAdherenceAndRange() {
        render(StatisticsUiState(loading = false, dashboard = TrainingStatistics()))
        composeRule.onNodeWithTag(STATISTICS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_RANGE).assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_empty_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_ACTIVITY).assertDoesNotExist()
        composeRule.onNodeWithTag(STATISTICS_VOLUME).assertDoesNotExist()
        composeRule.onNodeWithTag(STATISTICS_ADHERENCE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_adherence_empty)).assertIsDisplayed()
    }

    @Test
    fun givenOneWorkoutThenSummarySectionsAreShownWithoutTrends() {
        render(
            StatisticsUiState(
                loading = false,
                dashboard = TrainingStatistics(
                    activity = TrainingActivity(1, 2, 1, 3_600_000L),
                    volume = TrainingVolume(totalKg = 400.0, completedSetCount = 1),
                    muscleDistribution = listOf(MuscleTrainingCount(MuscleGroup.CHEST, 2, 1, today)),
                    exercises = listOf(exercise("Bench press", 80.0, 80.0, historySize = 1))
                )
            )
        )
        composeRule.onNodeWithTag(STATISTICS_ACTIVITY).assertIsDisplayed()
        composeRule.onNodeWithText(testQuantity(R.plurals.statistics_workouts, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_trend_insufficient))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_rest_empty))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Bench press").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_SEE_MUSCLES).assertDoesNotExist()
    }

    @Test
    fun givenCompletedWorkoutsThenDashboardSectionsAreShown() {
        render(populated())
        composeRule.onNodeWithTag(STATISTICS_ACTIVITY).assertIsDisplayed()
        composeRule.onNodeWithText(testQuantity(R.plurals.statistics_workouts, 2)).assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_ADHERENCE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_adherence_percent, 83)).assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_VOLUME).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_volume_scope)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bench press").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_MUSCLES).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.muscle_chest)).onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_REST).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun givenNoWeightedSetsThenVolumeIsScopedEmpty() {
        render(
            StatisticsUiState(
                loading = false,
                dashboard = TrainingStatistics(activity = TrainingActivity(1, 1, 1))
            )
        )
        composeRule.onNodeWithText(testString(R.string.statistics_volume_unavailable))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun givenMoreMusclesThanSummaryThenSeeDetailsFiresOnce() {
        val opened = intArrayOf(0)
        render(
            populated(
                muscles = listOf(
                    MuscleTrainingCount(MuscleGroup.CHEST, 8, 2, today),
                    MuscleTrainingCount(MuscleGroup.LATS, 6, 2, today),
                    MuscleTrainingCount(MuscleGroup.BICEPS, 4, 2, today),
                    MuscleTrainingCount(MuscleGroup.TRICEPS, 2, 1, today)
                )
            ),
            onOpenMuscles = { opened[0] += 1 }
        )
        composeRule.onNodeWithTag(STATISTICS_SEE_MUSCLES).performScrollTo().performClick()
        assertEquals(1, opened[0])
    }

    @Test
    fun givenExerciseRowWhenTappedThenCallbackRunsOnce() {
        val opened = longArrayOf(0L)
        render(populated(), onOpenExercise = { opened[0] = it })
        composeRule.onNodeWithTag("statistics-exercise-1").performScrollTo().performClick()
        assertEquals(1L, opened[0])
    }

    @Test
    fun givenThirtyDayRangeThenProInfoIsNotShown() {
        render(populated())
        composeRule.onNodeWithTag(STATISTICS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-range-30d").assertIsSelected()
        composeRule.onNodeWithTag(PRO_INFO_SHEET).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertDoesNotExist()
    }

    @Test
    fun givenLockedProRangeThenProInfoShowsExistingTrainingHistoryCopy() {
        render(
            populated().copy(lockedFeature = AppFeature.AdvancedStatistics)
        )
        composeRule.onNodeWithTag("statistics-range-30d").assertIsSelected()
        composeRule.onNodeWithTag("statistics-range-3m").assertIsNotSelected()
        composeRule.onNodeWithTag("statistics-range-6m").assertIsNotSelected()
        composeRule.onNodeWithTag("statistics-range-1y").assertIsNotSelected()
        composeRule.onNodeWithTag("statistics-range-all").assertIsNotSelected()
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_BODY).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_HIGHLIGHTS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_range_3m)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_range_6m)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_range_1y)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_range_all)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_title)).assertDoesNotExist()
        composeRule.onNodeWithText(
            testString(
                R.string.pro_info_feature_body,
                testString(R.string.pro_feature_advanced_statistics)
            )
        ).assertDoesNotExist()
    }

    @Test
    fun givenEntitledLongerRangeThenProInfoIsNotShown() {
        render(populated().copy(range = StatisticsRange.All))
        composeRule.onNodeWithTag("statistics-range-all").assertIsSelected()
        composeRule.onNodeWithTag("statistics-range-30d").assertIsNotSelected()
        composeRule.onNodeWithTag(PRO_INFO_SHEET).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertDoesNotExist()
    }

    @Test
    fun givenRangeChipWhenTappedThenCallbackReceivesThatRange() {
        val selected = arrayOfNulls<StatisticsRange>(1)
        render(populated(), onRangeSelected = { selected[0] = it })
        composeRule.onNodeWithTag("statistics-range-3m").performClick()
        assertEquals(StatisticsRange.Months3, selected[0])
    }

    @Test
    fun givenBackWhenTappedThenCallbackRunsOnce() {
        val backs = intArrayOf(0)
        render(populated(), onBack = { backs[0] += 1 })
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertEquals(1, backs[0])
    }

    private fun populated(
        muscles: List<MuscleTrainingCount> = listOf(MuscleTrainingCount(MuscleGroup.CHEST, 4, 2, today))
    ): StatisticsUiState {
        return StatisticsUiState(
            loading = false,
            range = StatisticsRange.Days30,
            dashboard = TrainingStatistics(
                range = StatisticsRange.Days30,
                activity = TrainingActivity(2, 8, 2, 3_600_000L),
                adherence = PlanAdherence(plannedCount = 12, completedCount = 10, missedCount = 2),
                volume = TrainingVolume(
                    totalKg = 800.0,
                    completedSetCount = 4,
                    trend = listOf(
                        SeriesPoint(today.minusWeeks(1), 400.0),
                        SeriesPoint(today, 400.0)
                    )
                ),
                muscleDistribution = muscles,
                restBetweenSessions = listOf(
                    MuscleRestSummary(MuscleGroup.CHEST, 4, 2.8, 1, 6, today)
                ),
                exercises = listOf(
                    exercise("Bench press", 80.0, 75.0, historySize = 2)
                )
            )
        )
    }

    private fun exercise(
        name: String,
        bestKg: Double,
        recentKg: Double,
        historySize: Int
    ): ExerciseProgressSummary {
        val history = List(historySize) { index ->
            ExerciseHistoryPoint(today.minusWeeks((historySize - 1 - index).toLong()), recentKg)
        }
        return ExerciseProgressSummary(
            exerciseId = 1L,
            name = name,
            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
            lastTrained = today,
            best = ExerciseBest.WeightedSet(bestKg, bestKg, 5, false),
            recent = ExerciseBest.WeightedSet(recentKg, recentKg, 4, false),
            history = history,
            historyKind = ExerciseHistoryKind.EFFECTIVE_KG
        )
    }

    private fun render(
        state: StatisticsUiState,
        onBack: () -> Unit = {},
        onRangeSelected: (StatisticsRange) -> Unit = {},
        onOpenMuscles: () -> Unit = {},
        onOpenExercise: (Long) -> Unit = {}
    ) {
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
                        StatisticsScreen(
                            state = state,
                            onBack = onBack,
                            onRangeSelected = onRangeSelected,
                            onOpenMuscleDistribution = onOpenMuscles,
                            onOpenExercise = onOpenExercise
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
