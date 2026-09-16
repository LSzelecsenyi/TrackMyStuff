package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.R
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.ActualSetDraft
import hu.laca.weighttracker.domain.workout.ActualSetLogic
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.SessionExercise
import hu.laca.weighttracker.domain.workout.SessionExerciseItem
import hu.laca.weighttracker.domain.workout.SessionSet
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.WorkoutSession
import hu.laca.weighttracker.domain.workout.WorkoutSessionAggregate
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class ActiveWorkoutScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentExerciseAccessibilityLabelIsAktualisGyakorlat() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Aktuális gyakorlat", context.getString(R.string.active_exercise_state))
    }

    @Test
    fun currentExerciseHasAccessibilityStateAtNordWidth() {
        render(state = twoExerciseState(), width = 360.dp, fontScale = 1f)
        composeRule.onNodeWithTag(workoutExerciseKey(10L)).assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Aktuális gyakorlat")).assertIsDisplayed()
        composeRule.onNodeWithTag("complete-set").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(WORKOUT_FINISH_KEY).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun highlightAndActionsStayUsableAtFontScale13() {
        render(state = twoExerciseState(), width = 360.dp, fontScale = 1.3f)
        composeRule.onNodeWithTag(workoutExerciseKey(10L)).assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Aktuális gyakorlat")).assertIsDisplayed()
        composeRule.onNodeWithTag("complete-set").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Húzódzkodás").assertIsDisplayed()
        composeRule.onNodeWithText("Tolódzkodás").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun completedWorkoutHasNoCurrentExercise() {
        render(state = completedState(), width = 360.dp, fontScale = 1f)
        composeRule.onNodeWithTag(workoutExerciseKey(10L)).assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Aktuális gyakorlat")).assertDoesNotExist()
        composeRule.onNodeWithTag("complete-set").assertDoesNotExist()
        composeRule.onNodeWithTag(WORKOUT_FINISH_KEY).performScrollTo().assertIsDisplayed()
    }

    private fun render(state: ActiveWorkoutUiState, width: Dp, fontScale: Float) {
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
                        ActiveWorkoutScreen(
                            state = state,
                            onBack = {},
                            onReps = { _, _ -> },
                            onLoadKind = { _, _ -> },
                            onWeight = { _, _ -> },
                            onMinutes = { _, _ -> },
                            onSeconds = { _, _ -> },
                            onDistance = { _, _ -> },
                            onDistanceUnit = { _, _ -> },
                            onComplete = {},
                            onSkip = {},
                            onUndoSkip = {},
                            onAddExtra = {},
                            onRemoveExtra = {},
                            onRequestFinish = {},
                            onDismissFinish = {},
                            onConfirmFinish = {},
                            onRequestAbandon = {},
                            onDismissAbandon = {},
                            onConfirmAbandon = {},
                            onFinished = {},
                            onAbandoned = {},
                            onMessageConsumed = {},
                            onFocusConsumed = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun twoExerciseState(): ActiveWorkoutUiState {
        val pending = set(1L, 10L, 0, SessionSetStatus.PENDING)
        val completed = set(3L, 11L, 0, SessionSetStatus.COMPLETED)
        val first = item(10L, "Húzódzkodás", 0, listOf(pending))
        val second = item(11L, "Tolódzkodás", 1, listOf(completed))
        val aggregate = WorkoutSessionAggregate(session(), listOf(first, second))
        return ActiveWorkoutUiState(
            loading = false,
            aggregate = aggregate,
            currentExerciseId = 10L,
            drafts = mapOf(
                pending.id to ActualSetLogic.draftFromSet(pending),
                completed.id to ActualSetLogic.draftFromSet(completed)
            )
        )
    }

    private fun completedState(): ActiveWorkoutUiState {
        val done = set(1L, 10L, 0, SessionSetStatus.COMPLETED)
        val first = item(10L, "Húzódzkodás", 0, listOf(done))
        val aggregate = WorkoutSessionAggregate(session(), listOf(first))
        return ActiveWorkoutUiState(
            loading = false,
            aggregate = aggregate,
            currentExerciseId = null,
            drafts = mapOf(done.id to ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        )
    }

    private fun session(): WorkoutSession {
        return WorkoutSession(
            id = 1L,
            templateId = 1L,
            templateName = "Push A",
            status = SessionStatus.IN_PROGRESS,
            workoutDate = LocalDate.parse("2026-09-16"),
            startedAt = 1_000L,
            finishedAt = null,
            abandonedAt = null,
            notes = null,
            bodyWeightKg = null,
            bodyWeightSource = BodyWeightSource.UNKNOWN,
            bodyWeightSourceDate = null,
            createdAt = 1_000L,
            updatedAt = 1_000L
        )
    }

    private fun item(
        id: Long,
        name: String,
        position: Int,
        sets: List<SessionSet>
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = id,
                sessionId = 1L,
                exerciseId = id,
                position = position,
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = sets
        )
    }

    private fun set(
        id: Long,
        exerciseId: Long,
        position: Int,
        status: SessionSetStatus
    ): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = exerciseId,
            position = position,
            plannedMinReps = 8,
            plannedMaxReps = 8,
            plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            plannedWeightKg = null,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = 8,
            actualLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            actualWeightKg = null,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) 2_000L else null,
            addedDuringWorkout = false
        )
    }
}
