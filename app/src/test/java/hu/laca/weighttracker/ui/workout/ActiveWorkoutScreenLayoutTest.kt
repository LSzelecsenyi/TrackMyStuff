package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import hu.laca.weighttracker.domain.workout.WorkoutFocusTarget
import hu.laca.weighttracker.domain.workout.WorkoutSession
import hu.laca.weighttracker.domain.workout.WorkoutSessionAggregate
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun currentSetAccessibilityLabelsAreCorrect() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Aktuális", context.getString(R.string.active_exercise_badge))
        assertEquals("Sorozat befejezése", context.getString(R.string.action_complete_set_a11y))
        assertEquals("Sorozat kihagyása", context.getString(R.string.action_skip_set_a11y))
    }

    @Test
    fun listIndexIsResolvedFromStableExerciseIds() {
        val ids = listOf(10L, 11L, 14L)
        assertEquals(1, workoutListIndexFor(WorkoutFocusTarget.Set(99L, 10L), ids))
        assertEquals(2, workoutListIndexFor(WorkoutFocusTarget.Set(5L, 11L), ids))
        assertEquals(3, workoutListIndexFor(WorkoutFocusTarget.Set(1L, 14L), ids))
        assertEquals(4, workoutListIndexFor(WorkoutFocusTarget.Finish, ids))
        assertEquals(null, workoutListIndexFor(WorkoutFocusTarget.Set(1L, 99L), ids))
    }

    @Test
    fun scrollWaitsUntilCurrentSetAndExerciseAreReady() {
        val next = WorkoutFocusTarget.Set(2L, 11L)
        assertEquals(false, isWorkoutScrollReady(next, currentSetId = 1L, expandedExerciseIds = setOf(11L)))
        assertEquals(false, isWorkoutScrollReady(next, currentSetId = 2L, expandedExerciseIds = emptySet()))
        assertEquals(true, isWorkoutScrollReady(next, currentSetId = 2L, expandedExerciseIds = setOf(11L)))
        assertEquals(true, isWorkoutScrollReady(WorkoutFocusTarget.Finish, currentSetId = null, expandedExerciseIds = emptySet()))
        assertEquals(false, isWorkoutScrollReady(WorkoutFocusTarget.Finish, currentSetId = 2L, expandedExerciseIds = emptySet()))
        assertEquals("exercise-11", workoutScrollKey(next))
        assertEquals(WORKOUT_FINISH_KEY, workoutScrollKey(WorkoutFocusTarget.Finish))
        assertEquals(true, workoutTargetIsVisible(next, listOf("exercise-10", "exercise-11")))
        assertEquals(false, workoutTargetIsVisible(next, listOf("exercise-10")))
        assertEquals(true, workoutTargetIsVisible(WorkoutFocusTarget.Finish, listOf(WORKOUT_FINISH_KEY)))
    }

    @Test
    fun staleScrollGenerationIsRejected() {
        assertEquals(true, shouldApplyScrollEvent(4L, 4L))
        assertEquals(false, shouldApplyScrollEvent(3L, 4L))
        assertEquals(false, shouldApplyScrollEvent(4L, null))
    }

    @Test
    fun exactlyOneCurrentSetAndExerciseHasNoAktualisBadge() {
        render(state = mixedSetsState(), width = 360.dp, fontScale = 1f)
        composeRule.onAllNodesWithText("Aktuális").assertCountEquals(1)
        composeRule.onNode(hasStateDescription("Aktuális")).assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Aktuális gyakorlat")).assertDoesNotExist()
        composeRule.onNodeWithText("Folyamatban").assertDoesNotExist()
        composeRule.onNodeWithTag(workoutExerciseKey(10L)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sorozat befejezése").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sorozat kihagyása").assertIsDisplayed()
        composeRule.onAllNodesWithTag(SET_COMPLETE_ACTION).assertCountEquals(1)
        composeRule.onNodeWithText("Kész").assertIsDisplayed()
        composeRule.onNodeWithText("Kihagyva").assertIsDisplayed()
        composeRule.onNodeWithText("Szerkesztés").assertIsDisplayed()
        composeRule.onNodeWithText("Mentés").assertDoesNotExist()
        composeRule.onNodeWithTag(WORKOUT_FINISH_CTA_STRONG).assertDoesNotExist()
        composeRule.onNodeWithTag(WORKOUT_FINISH_CTA).assertIsDisplayed()
    }

    @Test
    fun completeAndSkipTouchTargetsAreAtLeast48Dp() {
        render(state = mixedSetsState(), width = 360.dp, fontScale = 1f)
        val complete = composeRule.onNodeWithTag(SET_COMPLETE_ACTION).getBoundsInRoot()
        val skip = composeRule.onNodeWithTag(SET_SKIP_ACTION).getBoundsInRoot()
        assertTrue(complete.right - complete.left >= 48.dp)
        assertTrue(complete.bottom - complete.top >= 48.dp)
        assertTrue(skip.right - skip.left >= 48.dp)
        assertTrue(skip.bottom - skip.top >= 48.dp)
    }

    @Test
    fun savingShowsProgressOnCompleteAction() {
        render(state = mixedSetsState(completingSetId = 2L), width = 360.dp, fontScale = 1f)
        composeRule.onNodeWithTag(SET_COMPLETE_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(SET_COMPLETE_ACTION).assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Aktuális")).assertIsDisplayed()
    }

    @Test
    fun skipOnDirtySetAsksForConfirmation() {
        render(state = mixedSetsState(dirtySetId = 2L), width = 360.dp, fontScale = 1f)
        composeRule.onNodeWithTag(SET_SKIP_ACTION).performClick()
        composeRule.onNodeWithText("Kihagyod a módosított sorozatot?").assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Aktuális")).assertIsDisplayed()
        composeRule.onNodeWithTag(SET_COMPLETE_ACTION).assertIsDisplayed()
    }

    @Test
    fun skipOnCleanSetDoesNotShowConfirmation() {
        render(state = mixedSetsState(), width = 360.dp, fontScale = 1f)
        composeRule.onNodeWithTag(SET_SKIP_ACTION).performClick()
        composeRule.onNodeWithText("Kihagyod a módosított sorozatot?").assertDoesNotExist()
    }

    @Test
    fun finishCtaIsStrongOnlyWhenEverySetIsResolved() {
        render(state = completedState(), width = 360.dp, fontScale = 1f)
        composeRule.onNodeWithTag(WORKOUT_FINISH_CTA_STRONG).assertIsDisplayed()
        composeRule.onNodeWithTag(SET_COMPLETE_ACTION).assertDoesNotExist()
        composeRule.onNodeWithText("Aktuális").assertDoesNotExist()
        composeRule.onNodeWithText("Teljesítve").assertIsDisplayed()
        composeRule.onNodeWithText("Szerkesztés").assertIsDisplayed()
    }

    @Test
    fun highlightAndActionsStayUsableAtFontScale13() {
        render(state = mixedSetsState(), width = 360.dp, fontScale = 1.3f)
        composeRule.onNodeWithTag(workoutExerciseKey(10L)).assertIsDisplayed()
        composeRule.onNodeWithText("Aktuális").assertIsDisplayed()
        composeRule.onNodeWithTag(SET_COMPLETE_ACTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SET_SKIP_ACTION).assertIsDisplayed()
        composeRule.onNodeWithText("Húzódzkodás").assertIsDisplayed()
        composeRule.onNodeWithTag(WORKOUT_FINISH_CTA).assertIsDisplayed()
        val complete = composeRule.onNodeWithTag(SET_COMPLETE_ACTION).getBoundsInRoot()
        assertTrue(complete.bottom - complete.top >= 48.dp)
    }

    @Test
    fun finishCtaStaysVisibleWithSystemInsetAndFontScale() {
        render(state = mixedSetsState(), width = 360.dp, fontScale = 1.3f)
        composeRule.onNodeWithTag(WORKOUT_FINISH_CTA).assertIsDisplayed()
        composeRule.onNodeWithTag(WORKOUT_FINISH_CTA_STRONG).assertDoesNotExist()
        composeRule.onNodeWithText("Aktuális").assertIsDisplayed()
    }

    @Test
    fun nextCurrentSetDoesNotAutoFocusAnyField() {
        render(
            state = mixedSetsState().copy(
                focusEvent = WorkoutFocusEvent(1L, WorkoutFocusTarget.Set(2L, 10L)),
                focusedSetId = 2L
            ),
            width = 360.dp,
            fontScale = 1f
        )
        composeRule.onNode(hasStateDescription("Aktuális")).assertIsDisplayed()
        composeRule.onAllNodesWithTag("set-numeric-field")[0].assertIsNotFocused()
        composeRule.onAllNodesWithTag("set-numeric-field")[1].assertIsNotFocused()
    }

    @Test
    fun completeActionClearsFieldFocusWithoutImeAction() {
        render(state = mixedSetsState(), width = 360.dp, fontScale = 1f)
        composeRule.onAllNodesWithTag("set-numeric-field")[0].performClick()
        composeRule.onNodeWithTag(SET_COMPLETE_ACTION).performClick()
        composeRule.onAllNodesWithTag("set-numeric-field").assertCountEquals(2)
        composeRule.onAllNodesWithTag("set-numeric-field")[0].assertIsNotFocused()
        composeRule.onAllNodesWithTag("set-numeric-field")[1].assertIsNotFocused()
        composeRule.onNode(hasStateDescription("Aktuális")).assertIsDisplayed()
    }

    @Test
    fun skipActionDoesNotLeaveFieldFocused() {
        render(state = mixedSetsState(), width = 360.dp, fontScale = 1f)
        composeRule.onAllNodesWithTag("set-numeric-field")[0].performClick()
        composeRule.onNodeWithTag(SET_SKIP_ACTION).performClick()
        composeRule.onAllNodesWithTag("set-numeric-field")[0].assertIsNotFocused()
        composeRule.onAllNodesWithTag("set-numeric-field")[1].assertIsNotFocused()
        composeRule.onNodeWithText("Kihagyod a módosított sorozatot?").assertDoesNotExist()
    }

    @Test
    fun lastSetFinishTargetShowsFinishWithoutFocusedField() {
        render(
            state = completedState().copy(
                focusEvent = WorkoutFocusEvent(8L, WorkoutFocusTarget.Finish)
            ),
            width = 360.dp,
            fontScale = 1f
        )
        composeRule.onNodeWithTag(WORKOUT_FINISH_KEY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(WORKOUT_FINISH_CTA_STRONG).assertIsDisplayed()
        composeRule.onAllNodesWithTag("set-numeric-field").assertCountEquals(0)
        composeRule.onNodeWithText("Aktuális").assertDoesNotExist()
    }

    @Test
    fun nextExerciseScrollIndexUsesStableExerciseId() {
        val ids = listOf(10L, 22L)
        assertEquals(2, workoutListIndexFor(WorkoutFocusTarget.Set(5L, 22L), ids))
        assertEquals(
            true,
            isWorkoutScrollReady(WorkoutFocusTarget.Set(5L, 22L), 5L, setOf(22L))
        )
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
                            onToggleExercise = {},
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

    private fun mixedSetsState(
        completingSetId: Long? = null,
        dirtySetId: Long? = null
    ): ActiveWorkoutUiState {
        val completed = set(1L, 10L, 0, SessionSetStatus.COMPLETED)
        val current = set(2L, 10L, 1, SessionSetStatus.PENDING)
        val later = set(3L, 10L, 2, SessionSetStatus.PENDING)
        val skipped = set(4L, 10L, 3, SessionSetStatus.SKIPPED)
        val first = item(10L, "Húzódzkodás", 0, listOf(completed, current, later, skipped))
        val aggregate = WorkoutSessionAggregate(session(), listOf(first))
        return ActiveWorkoutUiState(
            loading = false,
            aggregate = aggregate,
            currentExerciseId = 10L,
            currentSetId = current.id,
            focusedSetId = current.id,
            expandedExerciseIds = setOf(10L),
            completingSetIds = completingSetId?.let { setOf(it) }.orEmpty(),
            dirtySetIds = dirtySetId?.let { setOf(it) }.orEmpty(),
            drafts = mapOf(
                completed.id to ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY),
                current.id to ActualSetLogic.draftFromSet(current),
                later.id to ActualSetLogic.draftFromSet(later),
                skipped.id to ActualSetLogic.draftFromSet(skipped)
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
            currentSetId = null,
            expandedExerciseIds = setOf(10L),
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
