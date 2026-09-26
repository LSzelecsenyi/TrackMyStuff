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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.statistics.ExerciseBest
import app.mymusclemap.domain.statistics.ExerciseHistoryKind
import app.mymusclemap.domain.statistics.ExerciseHistoryPoint
import app.mymusclemap.domain.statistics.ExerciseProgressSummary
import app.mymusclemap.domain.statistics.MuscleRestSummary
import app.mymusclemap.domain.statistics.MuscleTrainingCount
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
class StatisticsDetailScreensLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun muscleDetailsShowTheCompleteRankedListAndBack() {
        val backs = intArrayOf(0)
        composeRule.setContent {
            themed {
                StatisticsMuscleDistributionScreen(
                    muscles = listOf(
                        MuscleTrainingCount(MuscleGroup.CHEST, 8, 2, today),
                        MuscleTrainingCount(MuscleGroup.LATS, 3, 1, today)
                    ),
                    onBack = { backs[0] += 1 }
                )
            }
        }
        composeRule.onNodeWithTag(STATISTICS_MUSCLE_DETAILS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_chest)).assertIsDisplayed()
        composeRule.onNodeWithText(testQuantity(R.plurals.statistics_muscle_sets, 8)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertEquals(1, backs[0])
    }

    @Test
    fun restDetailsShowObservedSpacingNotRecoveryCopy() {
        composeRule.setContent {
            themed {
                StatisticsRestScreen(
                    rest = listOf(
                        MuscleRestSummary(MuscleGroup.BICEPS, 9, 2.8, 1, 6, today)
                    ),
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag(STATISTICS_REST_DETAILS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_rest_scope)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_biceps).uppercase(java.util.Locale.ENGLISH))
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_rest_average)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_rest_days_value, 2.8)).assertIsDisplayed()
    }

    @Test
    fun exerciseListOpensAnExerciseAndOneObservationHidesDuplicateBest() {
        val opened = longArrayOf(0L)
        composeRule.setContent {
            themed {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(2000.dp)
                        .fillMaxSize()
                ) {
                    StatisticsExerciseListScreen(
                        exercises = listOf(oneObservation()),
                        onBack = {},
                        onOpenExercise = { opened[0] = it }
                    )
                }
            }
        }
        composeRule.onNodeWithTag(STATISTICS_EXERCISE_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-exercise-all-4").performClick()
        assertEquals(4L, opened[0])
    }

    @Test
    fun exerciseDetailWithOneObservationShowsLatestAndInsufficientHistory() {
        composeRule.setContent {
            themed {
                StatisticsExerciseDetailScreen(summary = oneObservation(), onBack = {})
            }
        }
        composeRule.onNodeWithTag(STATISTICS_EXERCISE_DETAIL).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_latest)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_best)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.statistics_progress_insufficient)).assertIsDisplayed()
    }

    @Test
    fun completionOnlyDetailShowsSessionCountInsteadOfBestLatestPair() {
        composeRule.setContent {
            themed {
                StatisticsExerciseDetailScreen(
                    summary = ExerciseProgressSummary(
                        exerciseId = 9L,
                        name = "Stretch",
                        measurementType = MeasurementType.COMPLETION_ONLY,
                        lastTrained = today,
                        best = ExerciseBest.Completions(2),
                        recent = ExerciseBest.Completions(1),
                        history = listOf(
                            ExerciseHistoryPoint(today.minusDays(4), 1.0),
                            ExerciseHistoryPoint(today, 1.0)
                        ),
                        historyKind = ExerciseHistoryKind.COMPLETIONS
                    ),
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.statistics_rest_sessions)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_best)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.statistics_latest)).assertDoesNotExist()
    }

    private fun oneObservation(): ExerciseProgressSummary {
        return ExerciseProgressSummary(
            exerciseId = 4L,
            name = "Overhead Press",
            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
            lastTrained = today,
            best = ExerciseBest.WeightedSet(40.0, 40.0, 5, false),
            recent = ExerciseBest.WeightedSet(40.0, 40.0, 5, false),
            history = listOf(ExerciseHistoryPoint(today, 40.0)),
            historyKind = ExerciseHistoryKind.EFFECTIVE_KG
        )
    }

    @androidx.compose.runtime.Composable
    private fun themed(content: @androidx.compose.runtime.Composable () -> Unit) {
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
                    content()
                }
            }
        }
    }
}
