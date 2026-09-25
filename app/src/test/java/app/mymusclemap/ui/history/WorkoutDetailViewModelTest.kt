package app.mymusclemap.ui.history

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.journal.WorkoutSetCopy
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.ui.components.UserMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class WorkoutDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private val today = LocalDate.parse("2026-09-15")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        val sessionClock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
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
            sessionClock,
            FixedDateProvider(today)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun invalidSessionIdProducesMissingState() = runTest {
        val missing = detail(-1L).uiState.first { !it.loading }
        assertTrue(missing.missing)
        assertNull(missing.aggregate)
        val unknown = detail(99L).uiState.first { !it.loading }
        assertTrue(unknown.missing)
    }

    @Test
    fun inProgressSessionIsNotShownAsHistoryDetail() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        val state = detail(started.sessionId).uiState.first { !it.loading }
        assertEquals(started.sessionId, state.activeSessionId)
        assertNull(state.aggregate)
        assertFalse(state.missing)
    }

    @Test
    fun completedDetailUsesSnapshotsAndSetOrder() = runTest {
        WeightRepository(database.weightMeasurementDao(), Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC))
            .save(today, 88.3)
        val pull = savePull()
        val dip = saveDip()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets(), dip to fourSets()))
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        val aggregate = sessions.getAggregate(started.sessionId)!!
        val first = aggregate.exercises[0].sets
        sessions.completeSet(
            first[0].id,
            ActualSetDraft(repsText = "7", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "15")
        )
        sessions.skipSet(first[1].id)
        sessions.addExtraSet(aggregate.exercises[0].exercise.id)
        val extra = sessions.getAggregate(started.sessionId)!!.exercises[0].sets.last()
        sessions.completeSet(
            extra.id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = true))
        templates.save(
            TemplateDraft(
                id = templateId,
                name = "Push B",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1,
                        exerciseId = pull,
                        sets = listOf(PlannedSetDraft(-1, "6", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "10"))
                    )
                )
            )
        )
        exercises.save(
            ExerciseDraft(
                id = pull,
                name = "Húzódzkodás plusz",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = listOf(MuscleGroup.TRICEPS)
            )
        )
        val state = detail(started.sessionId).uiState.first { !it.loading && it.aggregate != null }
        val session = state.aggregate!!.session
        assertEquals("Push A", session.templateName)
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(88.3, session.bodyWeightKg!!, 0.0)
        assertEquals(BodyWeightSource.MEASURED_SAME_DAY, session.bodyWeightSource)
        assertEquals(today, session.bodyWeightSourceDate)
        assertEquals(listOf("Húzódzkodás", "Tolódzkodás"), state.aggregate.exercises.map { it.exercise.name })
        assertEquals(listOf(0, 1), state.aggregate.exercises.map { it.exercise.position })
        assertEquals(MuscleGroup.LATS, state.aggregate.exercises[0].exercise.primaryMuscle)
        val sets = state.aggregate.exercises[0].sets
        assertEquals(listOf(0, 1, 2, 3, 4), sets.map { it.position })
        assertEquals(SessionSetStatus.COMPLETED, sets[0].status)
        assertEquals(7, sets[0].actualReps)
        assertEquals(SessionSetStatus.SKIPPED, sets[1].status)
        assertTrue(sets.last().addedDuringWorkout)
        assertEquals(2, state.progress.completed)
        assertTrue(state.progress.skipped > 0)
        val firstDisplay = state.setDisplays[sets[0].id]!!
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        assertEquals("7 reps · +15 kg", firstDisplay.performed)
        assertTrue(firstDisplay.valuesDiffer)
        assertEquals(
            "8 reps · bodyweight",
            WorkoutSetCopy.display(resources, sets.last(), state.aggregate.exercises[0].exercise).performed
        )
        assertFalse(WorkoutDetailViewModel::class.java.declaredMethods.map { it.name }.any { name ->
            name.contains("complete", ignoreCase = true) ||
                name.contains("finish", ignoreCase = true) ||
                (name.contains("edit", ignoreCase = true) && !name.contains("delete", ignoreCase = true)) ||
                name.contains("reopen", ignoreCase = true)
        })
    }

    @Test
    fun missingBodyWeightSnapshotStaysAbsent() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        sessions.finish(started.sessionId, skipRemaining = true)
        val state = detail(started.sessionId).uiState.first { !it.loading && it.aggregate != null }
        assertNull(state.aggregate!!.session.bodyWeightKg)
        assertEquals(BodyWeightSource.UNKNOWN, state.aggregate.session.bodyWeightSource)
    }

    @Test
    fun dismissDeleteKeepsTheCompletedWorkout() = runTest {
        val sessionId = completePushA()
        val viewModel = detail(sessionId)
        viewModel.uiState.first { !it.loading && it.aggregate != null }
        viewModel.requestDeleteWorkout()
        assertTrue(viewModel.uiState.value.confirmDelete)
        viewModel.dismissDeleteWorkout()
        assertFalse(viewModel.uiState.value.confirmDelete)
        assertFalse(viewModel.uiState.value.deleting)
        assertFalse(viewModel.uiState.value.deleted)
        assertEquals(SessionStatus.COMPLETED, sessions.getAggregate(sessionId)!!.session.status)
    }

    @Test
    fun confirmDeleteRemovesAggregateAndMarksDeleted() = runTest {
        val sessionId = completePushA()
        val viewModel = detail(sessionId)
        viewModel.uiState.first { !it.loading && it.aggregate != null }
        viewModel.requestDeleteWorkout()
        viewModel.confirmDeleteWorkout()
        val state = viewModel.uiState.first { it.deleted }
        assertTrue(state.deleted)
        assertNull(sessions.getAggregate(sessionId))
    }

    @Test
    fun doubleConfirmDeleteStartsOnlyOneRepositoryDelete() = runTest {
        val sessionId = completePushA()
        val viewModel = detail(sessionId)
        viewModel.uiState.first { !it.loading && it.aggregate != null }
        viewModel.confirmDeleteWorkout()
        viewModel.confirmDeleteWorkout()
        viewModel.uiState.first { it.deleted }
        assertNull(sessions.getAggregate(sessionId))
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
    }

    @Test
    fun repositoryFailureKeepsTheUserOnTheDetailScreen() = runTest {
        val sessionId = completePushA()
        val viewModel = detail(sessionId)
        viewModel.uiState.first { !it.loading && it.aggregate != null }
        viewModel.requestDeleteWorkout()
        assertEquals(
            app.mymusclemap.domain.workout.DeleteWorkoutResult.Deleted,
            sessions.deleteWorkout(sessionId)
        )
        viewModel.confirmDeleteWorkout()
        val state = viewModel.uiState.first { it.userMessage == UserMessage.WorkoutDeleteFailed }
        assertEquals(UserMessage.WorkoutDeleteFailed, state.userMessage)
        assertFalse(state.deleted)
        assertFalse(state.deleting)
    }

    private fun detail(sessionId: Long): WorkoutDetailViewModel {
        return WorkoutDetailViewModel(
            SavedStateHandle(mapOf(WorkoutDetailViewModel.SESSION_ID to sessionId)),
            sessions,
            ApplicationProvider.getApplicationContext<Context>().resources
        )
    }

    private suspend fun completePushA(): Long {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = true))
        return started.sessionId
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

    private fun fourSets(): List<PlannedSetDraft> {
        return List(4) { index ->
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
