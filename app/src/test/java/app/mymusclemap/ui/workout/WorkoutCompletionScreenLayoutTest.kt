package app.mymusclemap.ui.workout

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.HighlightMeasure
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.achievements.PerformanceHighlight
import app.mymusclemap.domain.achievements.PerformanceRecordKind
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.WorkoutSummary
import app.mymusclemap.domain.workout.WorkoutSummaryExercise
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class WorkoutCompletionScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successfulSummaryAndActionsAreReadableAtFontScale13() {
        var left = 0
        render(
            ready(exercises = 4, sets = 14, durationMillis = 3_020_000L, name = "Push"),
            width = 360.dp,
            fontScale = 1.3f,
            onLeave = { left += 1 }
        )
        composeRule.onNodeWithText(testString(R.string.workout_complete_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Push").assertIsDisplayed()
        composeRule.onNodeWithText("50:20").assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("14").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.workout_complete_mark_a11y)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(WORKOUT_COMPLETE_BACK).assertCountEquals(1)
        composeRule.onAllNodesWithTag(WORKOUT_COMPLETE_HEATMAP).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.workout_complete_see_trained)).assertCountEquals(0)
        val title = composeRule.onNodeWithTag(WORKOUT_COMPLETE_TITLE).getBoundsInRoot()
        val stats = composeRule.onNodeWithTag(WORKOUT_COMPLETE_SUMMARY).getBoundsInRoot()
        val action = composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).getBoundsInRoot()
        val mark = composeRule.onNodeWithTag(WORKOUT_COMPLETE_MARK).getBoundsInRoot()
        assertTrue("title=$title stats=$stats", title.bottom <= stats.top + 1.dp)
        assertTrue("stats=$stats action=$action", stats.bottom <= action.top + 1.dp)
        assertTrue("mark=$mark title=$title", mark.bottom <= title.top + 1.dp)
        assertTrue(action.right - action.left >= 48.dp)
        assertTrue(action.bottom - action.top >= 48.dp)
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).performClick()
        assertEquals(1, left)
    }

    @Test
    fun reducedMotionShowsStableEndStateAndKeepsNavigation() {
        var left = 0
        render(
            ready(1, 1, 80_000L),
            width = 360.dp,
            fontScale = 1f,
            onLeave = { left += 1 }
        )
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_MARK).assertIsDisplayed()
        composeRule.onNodeWithText("1:20").assertIsDisplayed()
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).performClick()
        assertEquals(1, left)
    }

    @Test
    fun completionHasOneExitAndDoesNotLeaveUntilTheUserAsks() {
        var left = 0
        render(
            ready(2, 8, 600_000L),
            width = 360.dp,
            fontScale = 1f,
            onLeave = { left += 1 }
        )
        composeRule.onAllNodesWithTag(WORKOUT_COMPLETE_BACK).assertCountEquals(1)
        composeRule.onAllNodesWithText(testString(R.string.workout_complete_see_trained)).assertCountEquals(0)
        assertEquals(0, left)
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).performClick()
        assertEquals(1, left)
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).performClick()
        assertEquals(1, left)
    }

    @Test
    fun workoutCelebrationsStayUntilTheUserLeaves() {
        var left = 0
        render(
            ready(1, 3, 60_000L, name = "First workout"),
            width = 360.dp,
            fontScale = 1f,
            onLeave = { left += 1 },
            celebrations = listOf(firstStep(), thirtyWorkouts())
        )
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_CELEBRATIONS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_first_step_name)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_first_step_requirement)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_workouts_name, 30)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_workouts_requirement, 30)).assertIsDisplayed()
        composeRule.onNodeWithTag("workout-complete-badge-${AchievementId.FIRST_WORKOUT.name}").assertIsDisplayed()
        composeRule.onNodeWithTag("workout-complete-badge-${AchievementId.WORKOUTS_30.name}").assertIsDisplayed()
        assertEquals(0, left)
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).performClick()
        assertEquals(1, left)
    }

    @Test
    fun performedSetsKeepMeasurementOrderAndOmitMeaninglessVolume() {
        val summary = WorkoutSummary(
            workoutName = "Mixed",
            durationMillis = 90_000L,
            exercises = listOf(
                WorkoutSummaryExercise(
                    name = "Ring Dips",
                    completedSets = listOf(performed(id = 2, position = 1, reps = 12, kind = PlannedLoadKind.BODYWEIGHT_ONLY)),
                    interpretation = WeightInterpretation.NOT_APPLICABLE
                ),
                WorkoutSummaryExercise(
                    name = "Plank",
                    completedSets = listOf(
                        performed(id = 4, position = 0, reps = null, kind = PlannedLoadKind.NONE, duration = 45)
                    ),
                    interpretation = WeightInterpretation.NOT_APPLICABLE
                )
            ),
            volumeKg = null,
            highlights = emptyList()
        )
        render(WorkoutSummaryUiState.Ready(summary), width = 360.dp, fontScale = 1f, onLeave = {})
        val exercises = composeRule.onNodeWithTag(WORKOUT_COMPLETE_EXERCISES).getBoundsInRoot()
        composeRule.onNodeWithText("Ring Dips").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Plank").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.set_copy_reps, "12"), substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.set_copy_seconds, 45), substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("workout-complete-volume").assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.workout_summary_highlights)).assertCountEquals(0)
        assertTrue(exercises.top > composeRule.onNodeWithTag(WORKOUT_COMPLETE_SUMMARY).getBoundsInRoot().top)
    }

    @Test
    fun highlightsShowPreviousAndNewValues() {
        val summary = WorkoutSummary(
            workoutName = "Strength",
            durationMillis = 60_000L,
            exercises = listOf(
                WorkoutSummaryExercise(
                    name = "Weighted Dips",
                    completedSets = listOf(
                        performed(
                            id = 1,
                            position = 0,
                            reps = 5,
                            kind = PlannedLoadKind.ADDED_WEIGHT,
                            weight = 25.0
                        )
                    ),
                    interpretation = WeightInterpretation.TOTAL
                )
            ),
            volumeKg = 125.0,
            highlights = listOf(
                PerformanceHighlight(
                    kind = PerformanceRecordKind.WEIGHT,
                    exerciseName = "Weighted Dips",
                    previous = 20.0,
                    current = 25.0,
                    measure = HighlightMeasure.ADDED_KG
                )
            )
        )
        render(WorkoutSummaryUiState.Ready(summary), width = 360.dp, fontScale = 1f, onLeave = {})
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_HIGHLIGHTS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.workout_summary_weight_pr)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Weighted Dips").assertCountEquals(2)
        composeRule.onNodeWithText(
            testString(R.string.workout_summary_previous, testString(R.string.set_copy_added_weight, "20"))
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.workout_summary_new, testString(R.string.set_copy_added_weight, "25"))
        ).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.set_copy_weight, "125")).assertIsDisplayed()
    }

    @Test
    fun loadingDoesNotInventValuesOrLeave() {
        var left = 0
        render(WorkoutSummaryUiState.Loading, width = 360.dp, fontScale = 1f, onLeave = { left += 1 })
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_LOADING).assertIsDisplayed()
        composeRule.onAllNodesWithTag(WORKOUT_COMPLETE_SUMMARY).assertCountEquals(0)
        assertEquals(0, left)
    }

    @Test
    fun aMissingSessionDoesNotInventValuesOrLeave() {
        var left = 0
        render(WorkoutSummaryUiState.Unavailable, width = 360.dp, fontScale = 1f, onLeave = { left += 1 })
        composeRule.onNodeWithText(testString(R.string.workout_summary_unavailable)).assertIsDisplayed()
        composeRule.onAllNodesWithTag("workout-complete-exercise-count").assertCountEquals(0)
        assertEquals(0, left)
    }

    @Test
    fun backFromTheFinishDialogDoesNotLeave() {
        var left = 0
        var pressedEarly = false
        val dispatcher = AtomicReference<OnBackPressedDispatcher?>(null)
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                val owner = LocalOnBackPressedDispatcherOwner.current
                SideEffect { dispatcher.set(owner?.onBackPressedDispatcher) }
                WorkoutCompletionScreen(
                    state = ready(1, 3, 60_000L),
                    onLeave = { left += 1 },
                    playAnimation = false,
                    celebrations = listOf(thirtyWorkouts())
                )
                SideEffect {
                    if (!pressedEarly) {
                        pressedEarly = true
                        owner?.onBackPressedDispatcher?.onBackPressed()
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, left)
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_SCREEN).assertIsDisplayed()
        composeRule.runOnIdle { dispatcher.get()!!.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(1, left)
        composeRule.runOnIdle { dispatcher.get()!!.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(1, left)
    }

    private fun firstStep(): PendingCelebration.JourneyUnlocked {
        return PendingCelebration.JourneyUnlocked(
            achievementId = AchievementId.FIRST_WORKOUT,
            triggerClientWorkoutId = "cw-1",
            acknowledgement = CelebrationAcknowledgement(achievementId = AchievementId.FIRST_WORKOUT.name)
        )
    }

    private fun thirtyWorkouts(): PendingCelebration.WorkoutCountUnlocked {
        return PendingCelebration.WorkoutCountUnlocked(
            achievementId = AchievementId.WORKOUTS_30,
            threshold = 30,
            triggerClientWorkoutId = "cw-30",
            acknowledgement = CelebrationAcknowledgement(achievementId = AchievementId.WORKOUTS_30.name)
        )
    }

    private fun ready(
        exercises: Int,
        sets: Int,
        durationMillis: Long,
        name: String? = null
    ): WorkoutSummaryUiState.Ready {
        val counts = if (exercises == 0) {
            emptyList()
        } else {
            val base = sets / exercises
            val extra = sets % exercises
            List(exercises) { index -> base + if (index < extra) 1 else 0 }
        }
        return WorkoutSummaryUiState.Ready(
            WorkoutSummary(
                workoutName = name,
                durationMillis = durationMillis,
                exercises = counts.mapIndexed { index, count ->
                    WorkoutSummaryExercise(
                        name = "Exercise ${index + 1}",
                        completedSets = List(count) { position ->
                            performed(id = index * 100L + position, position = position, reps = null, kind = PlannedLoadKind.NONE)
                        },
                        interpretation = WeightInterpretation.NOT_APPLICABLE
                    )
                },
                volumeKg = null,
                highlights = emptyList()
            )
        )
    }

    private fun performed(
        id: Long,
        position: Int,
        reps: Int?,
        kind: PlannedLoadKind,
        weight: Double? = null,
        duration: Int? = null
    ): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = 1,
            position = position,
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = kind,
            plannedWeightKg = weight,
            plannedDurationSeconds = duration,
            plannedDistanceMeters = null,
            actualReps = reps,
            actualLoadKind = kind,
            actualWeightKg = weight,
            actualDurationSeconds = duration,
            actualDistanceMeters = null,
            status = SessionSetStatus.COMPLETED,
            completedAt = 1L,
            addedDuringWorkout = false
        )
    }

    private fun render(
        state: WorkoutSummaryUiState,
        width: Dp,
        fontScale: Float,
        onLeave: () -> Unit,
        playAnimation: Boolean = false,
        celebrations: List<PendingCelebration> = emptyList()
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
                            .fillMaxSize()
                    ) {
                        WorkoutCompletionScreen(
                            state = state,
                            onLeave = onLeave,
                            playAnimation = playAnimation,
                            celebrations = celebrations
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
