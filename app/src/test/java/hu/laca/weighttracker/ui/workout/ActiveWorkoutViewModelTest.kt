package hu.laca.weighttracker.ui.workout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.local.WeightDatabase
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import hu.laca.weighttracker.domain.workout.WorkoutFocusTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ActiveWorkoutViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private val today = LocalDate.parse("2026-09-16")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        exercises = ExerciseRepository(
            database.exerciseDao(),
            clock,
            database.workoutTemplateDao(),
            database.workoutSessionDao()
        )
        templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock,
            database.workoutSessionDao()
        )
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            FixedDateProvider(today)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private var sessionId = 0L

    @Test
    fun keszPersistsEditedValuesAndCompletesWithoutSecondSave() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val exerciseId = viewModel.loaded().aggregate!!.exercises[0].exercise.id
        val remainingId = viewModel.loaded().aggregate!!.exercises[0].sets[1].id
        viewModel.onReps(first.id, "7")
        viewModel.completeSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                state.focusEvent != null && first.id !in state.completingSetIds
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        val completed = stored.exercises[0].sets[0]
        assertEquals(7, completed.actualReps)
        assertEquals(SessionSetStatus.COMPLETED, completed.status)
        assertEquals(SessionSetStatus.PENDING, stored.exercises[0].sets[1].status)
        val focus = viewModel.uiState.value.focusEvent!!.target as WorkoutFocusTarget.Set
        assertEquals(remainingId, focus.setId)
        assertEquals(exerciseId, focus.exerciseId)
        assertEquals(exerciseId, viewModel.uiState.value.currentExerciseId)
        assertEquals(remainingId, viewModel.uiState.value.currentSetId)
        assertEquals(remainingId, viewModel.uiState.value.focusedSetId)
        assertTrue(exerciseId in viewModel.uiState.value.expandedExerciseIds)
        assertTrue(viewModel.uiState.value.dirtySetIds.isEmpty())
    }

    @Test
    fun plusIncreasesRepsByOneInDraft() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.stepReps(first.id, 1)
        val state = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "9" }
        }
        assertEquals("9", state.drafts[first.id]!!.repsText)
    }

    @Test
    fun minusDecreasesRepsByOneInDraft() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.stepReps(first.id, -1)
        val state = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "7" }
        }
        assertEquals("7", state.drafts[first.id]!!.repsText)
    }

    @Test
    fun minusAtMinimumKeepsValidCompletedReps() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.onReps(first.id, "1")
        awaitReal { viewModel.uiState.first { it.drafts[first.id]?.repsText == "1" } }
        viewModel.stepReps(first.id, -1)
        val minState = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "1" }
        }
        assertEquals("1", minState.drafts[first.id]!!.repsText)
        viewModel.onReps(first.id, "")
        awaitReal { viewModel.uiState.first { it.drafts[first.id]?.repsText == "" } }
        viewModel.stepReps(first.id, -1)
        val emptyState = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "" }
        }
        assertEquals("", emptyState.drafts[first.id]!!.repsText)
    }

    @Test
    fun plusOnEmptyRepsUsesMinimumValidValue() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.onReps(first.id, "")
        awaitReal { viewModel.uiState.first { it.drafts[first.id]?.repsText == "" } }
        viewModel.stepReps(first.id, 1)
        val state = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "1" }
        }
        assertEquals("1", state.drafts[first.id]!!.repsText)
    }

    @Test
    fun steppedRepsPersistThroughExistingCompleteFlow() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val next = viewModel.loaded().aggregate!!.exercises[0].sets[1]
        viewModel.stepReps(first.id, 1)
        viewModel.completeSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                first.id !in state.completingSetIds &&
                    state.currentSetId == next.id
            }
        }
        val stored = sessions.getAggregate(sessionId)!!.exercises[0].sets[0]
        assertEquals(SessionSetStatus.COMPLETED, stored.status)
        assertEquals(9, stored.actualReps)
    }

    @Test
    fun completeSetSavesCompletedWithoutImeAction() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val next = viewModel.loaded().aggregate!!.exercises[0].sets[1]
        viewModel.completeSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                first.id !in state.completingSetIds &&
                    state.currentSetId == next.id
            }
        }
        val stored = sessions.getAggregate(sessionId)!!.exercises[0].sets[0]
        assertEquals(SessionSetStatus.COMPLETED, stored.status)
        assertEquals(8, stored.actualReps)
        assertEquals(next.id, viewModel.uiState.value.currentSetId)
        assertEquals(next.id, (viewModel.uiState.value.focusEvent!!.target as WorkoutFocusTarget.Set).setId)
    }

    @Test
    fun skipDoesNotRequireImeAndAdvancesWithoutFinishingWorkout() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val next = viewModel.loaded().aggregate!!.exercises[0].sets[1]
        viewModel.skipSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                first.id !in state.completingSetIds &&
                    state.currentSetId == next.id
            }
        }
        assertEquals(SessionSetStatus.SKIPPED, sessions.getAggregate(sessionId)!!.exercises[0].sets[0].status)
        assertEquals(next.id, viewModel.uiState.value.currentSetId)
        assertEquals(false, viewModel.uiState.value.finished)
    }

    @Test
    fun invalidValuesDoNotComplete() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.onReps(first.id, "0")
        viewModel.completeSet(first.id)
        val state = awaitReal {
            viewModel.uiState.first { first.id !in it.completingSetIds && it.setErrors[first.id].orEmpty().isNotEmpty() }
        }
        assertEquals(SessionSetStatus.PENDING, sessions.getAggregate(sessionId)!!.exercises[0].sets[0].status)
        assertNull(state.focusEvent)
        assertEquals(first.id, state.currentSetId)
        assertNull(state.focusedSetId)
    }

    @Test
    fun doubleTapDoesNotDuplicateCompletion() = runTest {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.onReps(first.id, "6")
        viewModel.completeSet(first.id)
        viewModel.completeSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                first.id !in state.completingSetIds &&
                    state.aggregate?.exercises?.get(0)?.sets?.get(0)?.status == SessionSetStatus.COMPLETED
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        assertEquals(1, stored.exercises[0].sets.count { it.status == SessionSetStatus.COMPLETED })
        assertEquals(4, stored.exercises.flatMap { it.sets }.size)
        assertEquals(6, stored.exercises[0].sets[0].actualReps)
        assertTrue(viewModel.uiState.value.completingSetIds.isEmpty())
    }

    @Test
    fun lastSetOfExerciseMovesHighlightToNextPendingExercise() = runTest {
        val viewModel = startTwoExercises()
        val loaded = viewModel.loaded().aggregate!!
        val firstExerciseId = loaded.exercises[0].exercise.id
        loaded.exercises[0].sets.forEach { set ->
            viewModel.onReps(set.id, "8")
            viewModel.completeSet(set.id)
            awaitReal {
                viewModel.uiState.first { state ->
                    set.id !in state.completingSetIds &&
                        state.aggregate?.exercises?.get(0)?.sets?.any {
                            it.id == set.id && it.status == SessionSetStatus.COMPLETED
                        } == true
                }
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        val second = stored.exercises[1]
        assertTrue(stored.exercises[0].sets.all { it.status == SessionSetStatus.COMPLETED })
        val focus = viewModel.uiState.value.focusEvent!!.target as WorkoutFocusTarget.Set
        assertEquals(second.sets.first().id, focus.setId)
        assertEquals(second.exercise.id, focus.exerciseId)
        assertEquals(second.exercise.id, viewModel.uiState.value.currentExerciseId)
        assertTrue(second.exercise.id in viewModel.uiState.value.expandedExerciseIds)
        assertEquals(second.sets.first().id, viewModel.uiState.value.focusedSetId)
        assertEquals(second.sets.first().id, viewModel.uiState.value.currentSetId)
        assertTrue(viewModel.uiState.value.currentExerciseId != firstExerciseId)
    }

    @Test
    fun skippedExerciseIsBypassed() = runTest {
        val viewModel = startTwoExercises()
        val loaded = viewModel.loaded().aggregate!!
        loaded.exercises[1].sets.forEach { set ->
            viewModel.skipSet(set.id)
            awaitReal {
                viewModel.uiState.first { state ->
                    state.aggregate?.exercises?.get(1)?.sets?.any {
                        it.id == set.id && it.status == SessionSetStatus.SKIPPED
                    } == true
                }
            }
        }
        loaded.exercises[0].sets.forEach { set ->
            viewModel.completeSet(set.id)
            awaitReal {
                viewModel.uiState.first { state ->
                    set.id !in state.completingSetIds &&
                        state.aggregate?.exercises?.get(0)?.sets?.any {
                            it.id == set.id && it.status == SessionSetStatus.COMPLETED
                        } == true
                }
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        assertTrue(stored.exercises[1].sets.all { it.status == SessionSetStatus.SKIPPED })
        assertTrue(stored.exercises[0].sets.all { it.status == SessionSetStatus.COMPLETED })
        assertEquals(WorkoutFocusTarget.Finish, viewModel.uiState.value.focusEvent!!.target)
        assertNull(viewModel.uiState.value.currentExerciseId)
        assertNull(viewModel.uiState.value.currentSetId)
    }

    @Test
    fun finalPendingSetFocusesFinishAndClearsHighlight() = runTest {
        val viewModel = startSingleSet()
        val set = viewModel.loaded().aggregate!!.exercises[0].sets.single()
        viewModel.completeSet(set.id)
        awaitReal {
            viewModel.uiState.first { state ->
                state.focusEvent?.target == WorkoutFocusTarget.Finish &&
                    state.currentExerciseId == null &&
                    state.aggregate?.exercises?.get(0)?.sets?.single()?.status == SessionSetStatus.COMPLETED
            }
        }
        assertEquals(SessionSetStatus.COMPLETED, sessions.getAggregate(sessionId)!!.exercises[0].sets.single().status)
        assertEquals(WorkoutFocusTarget.Finish, viewModel.uiState.value.focusEvent!!.target)
        assertNull(viewModel.uiState.value.currentExerciseId)
        assertNull(viewModel.uiState.value.currentSetId)
        assertNull(viewModel.uiState.value.focusedSetId)
        assertEquals(
            hu.laca.weighttracker.domain.workout.SessionStatus.IN_PROGRESS,
            sessions.getAggregate(sessionId)!!.session.status
        )
        assertEquals(false, viewModel.uiState.value.finished)
    }

    @Test
    fun skipAdvancesToNextPendingSetInSameExercise() = runTest {
        val viewModel = startTwoExercises()
        val loaded = viewModel.loaded().aggregate!!
        val first = loaded.exercises[0].sets[0]
        val next = loaded.exercises[0].sets[1]
        val exerciseId = loaded.exercises[0].exercise.id
        viewModel.skipSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                state.focusEvent != null && first.id !in state.completingSetIds
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        assertEquals(SessionSetStatus.SKIPPED, stored.exercises[0].sets[0].status)
        assertEquals(SessionSetStatus.PENDING, stored.exercises[0].sets[1].status)
        val focus = viewModel.uiState.value.focusEvent!!.target as WorkoutFocusTarget.Set
        assertEquals(next.id, focus.setId)
        assertEquals(exerciseId, focus.exerciseId)
        assertEquals(exerciseId, viewModel.uiState.value.currentExerciseId)
        assertTrue(exerciseId in viewModel.uiState.value.expandedExerciseIds)
        assertEquals(next.id, viewModel.uiState.value.focusedSetId)
        assertEquals(next.id, viewModel.uiState.value.currentSetId)
    }

    @Test
    fun repositoryErrorKeepsCurrentSetActive() = runTest {
        val viewModel = startSingleSet()
        val set = viewModel.loaded().aggregate!!.exercises[0].sets.single()
        viewModel.confirmFinish(true)
        awaitReal { viewModel.uiState.first { it.finished } }
        viewModel.completeSet(set.id)
        val state = awaitReal {
            viewModel.uiState.first { snapshot ->
                set.id !in snapshot.completingSetIds &&
                    snapshot.message == ActiveWorkoutMessage.SaveFailed
            }
        }
        assertEquals(ActiveWorkoutMessage.SaveFailed, state.message)
        assertNull(state.focusEvent)
        assertEquals(SessionSetStatus.SKIPPED, sessions.getAggregate(sessionId)!!.exercises[0].sets.single().status)
    }

    @Test
    fun confirmAbandonDeletesSessionWithoutAbandonedJournalEntry() = runTest {
        val viewModel = startSingleSet()
        viewModel.loaded()
        viewModel.confirmAbandon()
        awaitReal { viewModel.uiState.first { it.abandoned } }
        assertNull(sessions.getAggregate(sessionId))
        assertNull(sessions.observeInProgress().first())
        assertTrue(sessions.observeSummaries().first().isEmpty())
        assertTrue(viewModel.uiState.value.discarding)
    }

    @Test
    fun dismissAbandonLeavesActiveSessionUnchanged() = runTest {
        val viewModel = startSingleSet()
        viewModel.loaded()
        viewModel.requestAbandon()
        assertTrue(viewModel.uiState.value.confirmAbandon)
        viewModel.dismissAbandon()
        assertFalse(viewModel.uiState.value.confirmAbandon)
        assertFalse(viewModel.uiState.value.discarding)
        assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
        assertEquals(1, sessions.getAggregate(sessionId)!!.exercises.single().sets.size)
    }

    @Test
    fun doubleConfirmAbandonDoesNotRestartDelete() = runTest {
        val viewModel = startSingleSet()
        viewModel.loaded()
        viewModel.confirmAbandon()
        viewModel.confirmAbandon()
        awaitReal { viewModel.uiState.first { it.abandoned } }
        assertNull(sessions.getAggregate(sessionId))
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
    }

    @Test
    fun consumeFocusEventDoesNotReplay() = runTest {
        val viewModel = startSingleSet()
        val set = viewModel.loaded().aggregate!!.exercises[0].sets.single()
        viewModel.completeSet(set.id)
        awaitReal { viewModel.uiState.first { it.focusEvent != null } }
        viewModel.consumeFocusEvent()
        awaitReal { viewModel.uiState.first { it.focusEvent == null } }
        viewModel.consumeFocusEvent()
        assertNull(viewModel.uiState.value.focusEvent)
    }

    private suspend fun startTwoExercises(): ActiveWorkoutViewModel {
        val pull = savePull()
        val dip = saveDip()
        val templateId = saveTemplate("Push A", listOf(pull to twoSets(), dip to twoSets()))
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        sessionId = started.sessionId
        return active(started.sessionId)
    }

    private suspend fun startSingleSet(): ActiveWorkoutViewModel {
        val pull = savePull()
        val templateId = saveTemplate(
            "Push A",
            listOf(pull to listOf(PlannedSetDraft(-1, "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)))
        )
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        sessionId = started.sessionId
        return active(started.sessionId)
    }

    private fun active(sessionId: Long): ActiveWorkoutViewModel {
        return ActiveWorkoutViewModel(
            SavedStateHandle(mapOf(ActiveWorkoutViewModel.SESSION_ID to sessionId)),
            sessions
        )
    }

    private suspend fun ActiveWorkoutViewModel.loaded(): ActiveWorkoutUiState {
        return awaitReal {
            uiState.first { !it.loading && it.aggregate != null && it.drafts.isNotEmpty() }
        }
    }

    private suspend fun <T> awaitReal(block: suspend () -> T): T {
        return withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { block() }
        }
    }

    private suspend fun savePull(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Húzódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS,
                secondaryMuscles = listOf(MuscleGroup.BICEPS)
            )
        ) as ExerciseSaveResult.Created).id
    }

    private suspend fun saveDip(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Tolódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.TRICEPS
            )
        ) as ExerciseSaveResult.Created).id
    }

    private fun twoSets(): List<PlannedSetDraft> {
        return List(2) { index ->
            PlannedSetDraft(
                localId = -(index + 1L),
                minRepsText = "8",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
            )
        }
    }

    private suspend fun saveTemplate(
        name: String,
        items: List<Pair<Long, List<PlannedSetDraft>>>
    ): Long {
        val draft = TemplateDraft(
            name = name,
            exercises = items.mapIndexed { index, item ->
                TemplateExerciseDraft(
                    localId = -(index + 1L),
                    exerciseId = item.first,
                    sets = item.second
                )
            }
        )
        return (templates.save(draft) as TemplateSaveResult.Created).id
    }
}
