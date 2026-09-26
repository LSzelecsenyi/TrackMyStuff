package app.mymusclemap.ui.statistics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
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
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.statistics.ExerciseBest
import app.mymusclemap.domain.statistics.ExerciseHistoryKind
import app.mymusclemap.domain.statistics.ExerciseHistoryPoint
import app.mymusclemap.domain.statistics.ExerciseProgressSummary
import app.mymusclemap.domain.statistics.MuscleTrainingCount
import app.mymusclemap.domain.statistics.TrainingStatistics
import app.mymusclemap.domain.statistics.WeightedLoadVolume
import app.mymusclemap.domain.statistics.WeeklyVolumePoint
import app.mymusclemap.testQuantity
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
@Config(qualifiers = "w360dp-h2000dp")
class StatisticsScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun givenNoCompletedWorkoutsThenEmptyStateIsShown() {
        render(StatisticsUiState(loading = false, dashboard = TrainingStatistics()))
        composeRule.onNodeWithTag(STATISTICS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_empty_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_CONSISTENCY).assertDoesNotExist()
        composeRule.onNodeWithTag(STATISTICS_VOLUME).assertDoesNotExist()
    }

    @Test
    fun givenCompletedWorkoutsThenDashboardSectionsAreShown() {
        render(populated())
        composeRule.onNodeWithTag(STATISTICS_CONSISTENCY).assertIsDisplayed()
        composeRule.onNodeWithText(testQuantity(R.plurals.statistics_workouts, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(testQuantity(R.plurals.statistics_workouts, 4)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_frequency_value, 1.0)).assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_VOLUME).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_volume_scope)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bench press").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_best_weight, "80.0 kg", 5))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_best_weight, "75.0 kg", 4))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(STATISTICS_MUSCLES).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.muscle_chest)).onFirst().assertIsDisplayed()
    }

    @Test
    fun givenNoWeightedSetsThenVolumeIsScopedEmpty() {
        render(
            StatisticsUiState(
                loading = false,
                dashboard = TrainingStatistics(completedWorkoutCount = 1, workoutsLast7Days = 1)
            )
        )
        composeRule.onNodeWithText(testString(R.string.statistics_volume_unavailable))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_progress_empty))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun givenBackWhenTappedThenCallbackRunsOnce() {
        val backs = intArrayOf(0)
        render(populated(), onBack = { backs[0] += 1 })
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertEquals(1, backs[0])
    }

    private fun populated(): StatisticsUiState {
        return StatisticsUiState(
            loading = false,
            dashboard = TrainingStatistics(
                completedWorkoutCount = 2,
                workoutsLast7Days = 2,
                trainingDaysLast7Days = 2,
                workoutsLast30Days = 4,
                trainingDaysLast30Days = 4,
                recentWeeklyFrequency = 1.0,
                weightedLoad = WeightedLoadVolume(800.0, 800.0, 4, 4),
                weeklyVolume = listOf(
                    WeeklyVolumePoint(today.minusWeeks(1), 400.0),
                    WeeklyVolumePoint(today, 400.0)
                ),
                exerciseProgress = listOf(
                    ExerciseProgressSummary(
                        exerciseId = 1L,
                        name = "Bench press",
                        measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                        lastTrained = today,
                        best = ExerciseBest.WeightedSet(80.0, 80.0, 5, false),
                        recent = ExerciseBest.WeightedSet(75.0, 75.0, 4, false),
                        history = listOf(
                            ExerciseHistoryPoint(today.minusWeeks(1), 70.0),
                            ExerciseHistoryPoint(today, 80.0)
                        ),
                        historyKind = ExerciseHistoryKind.EFFECTIVE_KG
                    )
                ),
                recentlyTrainedMuscles = listOf(
                    MuscleTrainingCount(MuscleGroup.CHEST, 4, 2, today)
                ),
                mostTrainedMuscles = listOf(
                    MuscleTrainingCount(MuscleGroup.CHEST, 4, 2, today)
                )
            )
        )
    }

    private fun render(state: StatisticsUiState, onBack: () -> Unit = {}) {
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
                        StatisticsScreen(state = state, onBack = onBack)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
