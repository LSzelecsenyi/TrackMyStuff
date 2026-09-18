package hu.laca.weighttracker.ui.history

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
import hu.laca.weighttracker.domain.journal.WorkoutJournalEntry
import hu.laca.weighttracker.domain.workout.FinishWorkoutResult
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import hu.laca.weighttracker.ui.components.UserMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class HistoryWorkoutDeleteViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.parse("2026-09-16")
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private lateinit var database: WeightDatabase
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository

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
            dateProvider
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun confirmDeleteRemovesWorkoutAndShowsSnackbar() = runTest {
        val sessionId = completeWorkout()
        val viewModel = history()
        viewModel.uiState.first { state ->
            !state.loading && state.timeline.entries.any { it is WorkoutJournalEntry }
        }
        viewModel.requestDeleteWorkout(sessionId)
        assertEquals(sessionId, viewModel.uiState.value.pendingWorkoutDelete?.session?.id)
        viewModel.confirmDeleteWorkout()
        val state = viewModel.uiState.first { snapshot ->
            snapshot.userMessage == UserMessage.WorkoutDeleted &&
                snapshot.timeline.entries.none { it is WorkoutJournalEntry }
        }
        assertNull(state.pendingWorkoutDelete)
        assertFalse(state.deletingWorkout)
        assertEquals(UserMessage.WorkoutDeleted, state.userMessage)
        assertTrue(state.timeline.entries.none { it is WorkoutJournalEntry })
        assertNull(sessions.getAggregate(sessionId))
    }

    @Test
    fun dismissDeleteLeavesWorkoutAndJournalUnchanged() = runTest {
        val sessionId = completeWorkout()
        val viewModel = history()
        viewModel.uiState.first { !it.loading && it.timeline.entries.any { entry -> entry is WorkoutJournalEntry } }
        viewModel.requestDeleteWorkout(sessionId)
        viewModel.dismissDeleteWorkout()
        assertNull(viewModel.uiState.value.pendingWorkoutDelete)
        assertNotNull(sessions.getAggregate(sessionId))
        assertEquals(1, viewModel.uiState.value.timeline.entries.filterIsInstance<WorkoutJournalEntry>().size)
    }

    @Test
    fun doubleTapStartsOnlyOneDelete() = runTest {
        val sessionId = completeWorkout()
        val viewModel = history()
        viewModel.uiState.first { !it.loading && it.timeline.entries.any { it is WorkoutJournalEntry } }
        viewModel.requestDeleteWorkout(sessionId)
        viewModel.confirmDeleteWorkout()
        viewModel.confirmDeleteWorkout()
        viewModel.uiState.first { it.userMessage == UserMessage.WorkoutDeleted }
        assertNull(sessions.getAggregate(sessionId))
        assertEquals(UserMessage.WorkoutDeleted, viewModel.uiState.value.userMessage)
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
    }

    private fun history(): HistoryViewModel {
        return HistoryViewModel(
            WeightRepository(database.weightMeasurementDao(), Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)),
            sessions,
            dateProvider,
            SavedStateHandle()
        )
    }

    private suspend fun completeWorkout(): Long {
        val pull = (exercises.save(
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
        val templateId = (templates.save(
            TemplateDraft(
                name = "Push A",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = pull,
                        sets = listOf(PlannedSetDraft(-1L, "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
                    )
                )
            )
        ) as TemplateSaveResult.Created).id
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = true))
        return started.sessionId
    }
}
