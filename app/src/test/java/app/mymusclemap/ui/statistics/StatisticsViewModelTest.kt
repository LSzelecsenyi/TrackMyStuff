package app.mymusclemap.ui.statistics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.FakeWeightMeasurementDao
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
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
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
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class StatisticsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 9, 16)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
            WeightRepository(FakeWeightMeasurementDao(), clock),
            clock,
            dateProvider
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun givenNoWorkoutsThenDashboardIsEmpty() = runTest {
        val viewModel = StatisticsViewModel(sessions, dateProvider)
        val state = viewModel.uiState.first { !it.loading }
        assertFalse(state.dashboard.hasCompletedWorkouts)
        assertEquals(0, state.dashboard.completedWorkoutCount)
    }

    @Test
    fun givenInProgressWorkoutThenStatisticsStayEmpty() = runTest {
        val templateId = saveTemplate()
        assertTrue(sessions.start(templateId) is StartWorkoutResult.Started)
        val viewModel = StatisticsViewModel(sessions, dateProvider)
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(0, state.dashboard.completedWorkoutCount)
        assertTrue(sessions.observeCompletedAggregates().first().isEmpty())
    }

    @Test
    fun givenCompletedWorkoutThenConsistencyUsesPersistedSession() = runTest {
        val templateId = saveTemplate()
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val aggregate = sessions.getAggregate(started.sessionId)!!
        val firstSet = aggregate.exercises.single().sets.first()
        sessions.completeSet(
            firstSet.id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        sessions.finish(started.sessionId, skipRemaining = true)
        val viewModel = StatisticsViewModel(sessions, dateProvider)
        val state = viewModel.uiState.first { !it.loading && it.dashboard.hasCompletedWorkouts }
        assertEquals(1, state.dashboard.completedWorkoutCount)
        assertEquals(1, state.dashboard.workoutsLast7Days)
        assertNull(state.dashboard.weightedLoad)
        assertEquals(MuscleGroup.LATS, state.dashboard.recentlyTrainedMuscles.single().muscle)
        assertEquals(1, state.dashboard.recentlyTrainedMuscles.single().completedSetCount)
        assertEquals(1, sessions.observeCompletedAggregates().first().size)
    }

    private suspend fun saveTemplate(): Long {
        val exerciseId = (exercises.save(
            ExerciseDraft(
                name = "Pull-up",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS
            )
        ) as ExerciseSaveResult.Created).id
        return (templates.save(
            TemplateDraft(
                name = "Pull",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = exerciseId,
                        sets = List(2) { index ->
                            PlannedSetDraft(
                                localId = -(index + 1L),
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        }
                    )
                )
            )
        ) as TemplateSaveResult.Created).id
    }
}
