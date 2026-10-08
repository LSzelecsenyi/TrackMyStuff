package app.mymusclemap.ui.workout

import android.content.Context
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
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class WorkoutCompletionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private val today = LocalDate.parse("2026-10-08")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
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

    @Test
    fun completedSessionLoadsNameOrderAndSkipsWithoutAnActiveWorkout() = runTest {
        val pull = saveExercise(
            name = "Ring Dips",
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE
        )
        val dip = saveExercise(
            name = "Weighted Dips",
            measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.TOTAL
        )
        val templateId = saveTemplate(
            "Push",
            listOf(
                pull to oneSet(PlannedLoadKind.BODYWEIGHT_ONLY),
                dip to oneSet(PlannedLoadKind.ADDED_WEIGHT, "20")
            )
        )
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val active = sessions.getAggregate(started.sessionId)!!
        sessions.completeSet(
            active.exercises[0].sets[0].id,
            ActualSetDraft(repsText = "12", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        sessions.skipSet(active.exercises[1].sets[0].id)
        sessions.addExtraSet(active.exercises[1].exercise.id)
        val withExtra = sessions.getAggregate(started.sessionId)!!
        sessions.completeSet(
            withExtra.exercises[1].sets.last().id,
            ActualSetDraft(repsText = "5", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "25")
        )
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = true))
        val stored = sessions.getAggregate(started.sessionId)!!
        val viewModel = WorkoutCompletionViewModel(sessions, Dispatchers.Unconfined)
        viewModel.load(stored.session.clientWorkoutId)
        val ready = viewModel.state.first { it is WorkoutSummaryUiState.Ready } as WorkoutSummaryUiState.Ready
        assertEquals(SessionStatus.COMPLETED, stored.session.status)
        assertEquals("Push", ready.summary.workoutName)
        assertEquals(listOf("Ring Dips", "Weighted Dips"), ready.summary.exercises.map { it.name })
        assertEquals(listOf(12), ready.summary.exercises[0].completedSets.map { it.actualReps })
        assertEquals(listOf(5), ready.summary.exercises[1].completedSets.map { it.actualReps })
        assertEquals(2, ready.summary.exerciseCount)
        assertEquals(2, ready.summary.completedSetCount)
        assertEquals(ElapsedTime.forSession(stored.session), ready.summary.durationMillis)
        assertEquals(125.0, ready.summary.volumeKg!!, 0.0)
    }

    @Test
    fun aMissingCompletedSessionStaysUnavailable() = runTest {
        val viewModel = WorkoutCompletionViewModel(sessions, Dispatchers.Unconfined)
        viewModel.load("missing-workout")
        val state = viewModel.state.first { it is WorkoutSummaryUiState.Unavailable }
        assertTrue(state is WorkoutSummaryUiState.Unavailable)
        viewModel.load("")
        assertTrue(viewModel.state.value is WorkoutSummaryUiState.Unavailable)
    }

    private suspend fun saveExercise(
        name: String,
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        interpretation: WeightInterpretation
    ): Long {
        return (exercises.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                measurementType = measurement,
                resistanceBasis = resistance,
                weightInterpretation = interpretation,
                primaryMuscle = MuscleGroup.CHEST
            )
        ) as ExerciseSaveResult.Created).id
    }

    private fun oneSet(kind: PlannedLoadKind, weight: String = ""): List<PlannedSetDraft> {
        return listOf(
            PlannedSetDraft(
                localId = -1L,
                minRepsText = "8",
                loadKind = kind,
                weightText = weight
            )
        )
    }

    private suspend fun saveTemplate(
        name: String,
        items: List<Pair<Long, List<PlannedSetDraft>>>
    ): Long {
        return (templates.save(
            TemplateDraft(
                name = name,
                exercises = items.mapIndexed { index, item ->
                    TemplateExerciseDraft(
                        localId = -(index + 1L),
                        exerciseId = item.first,
                        sets = item.second
                    )
                }
            )
        ) as TemplateSaveResult.Created).id
    }
}
