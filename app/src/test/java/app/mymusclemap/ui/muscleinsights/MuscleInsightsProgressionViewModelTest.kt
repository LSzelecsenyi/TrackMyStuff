package app.mymusclemap.ui.muscleinsights

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.FakeWeightMeasurementDao
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import app.mymusclemap.domain.performance.ExerciseProgressionDemo
import app.mymusclemap.domain.performance.RecordedPerformanceOutcome
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class MuscleInsightsProgressionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 16)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: WeightDatabase
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var exercises: ExerciseRepository

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
    fun demoIgnoresUserAggregatesAndPeriodsKeepTheirOwnCurrentOccurrence() {
        val user = ExerciseProgressionDemo.aggregates(today, ZoneOffset.UTC).map { aggregate ->
            aggregate.copy(
                exercises = aggregate.exercises.map { item ->
                    item.copy(exercise = item.exercise.copy(name = "Secret press"))
                }
            )
        }
        val demo = resolveExerciseProgression(
            demo = true,
            aggregates = user,
            muscle = MuscleGroup.CHEST,
            period = MuscleInsightsPeriod.Weeks4,
            today = today
        )
        assertTrue(demo.exercises.none { it.displayName == "Secret press" })
        assertEquals(
            ExerciseProgressionDemo.summarize(MuscleGroup.CHEST, MuscleInsightsPeriod.Weeks4, today),
            demo
        )
        val all = resolveExerciseProgression(
            demo = true,
            aggregates = emptyList(),
            muscle = MuscleGroup.CHEST,
            period = MuscleInsightsPeriod.All,
            today = today
        )
        assertTrue(all.exercises.any { it.displayName == "Archive press" })
        assertTrue(demo.exercises.none { it.displayName == "Archive press" })
        val lats = resolveExerciseProgression(
            demo = true,
            aggregates = user,
            muscle = MuscleGroup.LATS,
            period = MuscleInsightsPeriod.Weeks4,
            today = today
        )
        assertEquals(listOf("Weighted pull-up"), lats.exercises.map { it.displayName })
        assertTrue(lats.exercises.none { it.displayName == "Bench press" })
    }

    @Test
    fun entitlementSwitchReplacesDemoResultsWithTheSignedInHistory() = runTest {
        val exerciseId = saveExercise()
        insertPress(exerciseId, daysAgo = 14, reps = 8, finishedAt = 1_000L)
        insertPress(exerciseId, daysAgo = 2, reps = 10, finishedAt = 2_000L)
        val access = MutableAccess(advanced = false)
        val viewModel = MuscleInsightsViewModel(
            sessionRepository = sessions,
            dateProvider = dateProvider,
            entitlements = access,
            savedStateHandle = SavedStateHandle()
        )
        val demo = viewModel.uiState.first { !it.loading && it.demo }
        assertTrue(demo.progression.exercises.any { it.displayName == "Incline bench press" })
        assertTrue(demo.progression.exercises.none { it.displayName == "Secret press" })

        access.advanced = true
        val personal = viewModel.uiState.first { !it.loading && !it.demo && it.progression.exercises.isNotEmpty() }
        assertEquals(listOf("Secret press"), personal.progression.exercises.map { it.displayName })
        assertEquals(RecordedPerformanceOutcome.IMPROVED, personal.progression.exercises.single().outcome)
        assertFalse(personal.progression.exercises.any { it.displayName == "Incline bench press" })

        viewModel.onMuscleSelected(MuscleGroup.LATS)
        val lats = viewModel.uiState.first { it.muscle == MuscleGroup.LATS && !it.demo }
        assertTrue(lats.progression.exercises.isEmpty())
        assertEquals(MuscleInsightsPeriod.Weeks4, lats.period)

        viewModel.onMuscleSelected(MuscleGroup.CHEST)
        viewModel.onPeriodSelected(MuscleInsightsPeriod.All)
        val all = viewModel.uiState.first { it.period == MuscleInsightsPeriod.All && it.muscle == MuscleGroup.CHEST }
        assertEquals("Secret press", all.progression.exercises.single().displayName)
        assertEquals(MuscleGroup.CHEST, all.muscle)
    }

    private suspend fun saveExercise(): Long {
        val saved = exercises.save(
            ExerciseDraft(
                name = "Secret press",
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = WeightInterpretation.TOTAL,
                primaryMuscle = MuscleGroup.CHEST
            )
        ) as ExerciseSaveResult.Created
        return saved.id
    }

    private suspend fun insertPress(exerciseId: Long, daysAgo: Long, reps: Int, finishedAt: Long) {
        database.workoutSessionDao().insertAggregate(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Test",
                status = SessionStatus.COMPLETED.name,
                workoutDate = today.minusDays(daysAgo).toString(),
                startedAt = finishedAt - 10,
                finishedAt = finishedAt,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN.name,
                bodyWeightSourceDate = null,
                createdAt = finishedAt,
                updatedAt = finishedAt,
                activeLock = null,
                clientWorkoutId = "press-$finishedAt"
            ),
            listOf(
                Triple(
                    WorkoutSessionExerciseEntity(
                        sessionId = 0L,
                        exerciseId = exerciseId,
                        position = 0,
                        name = "Secret press",
                        category = ExerciseCategory.STRENGTH.name,
                        movementPattern = MovementPattern.HORIZONTAL_PUSH.name,
                        measurementType = MeasurementType.REPETITIONS_AND_WEIGHT.name,
                        resistanceBasis = ResistanceBasis.EXTERNAL.name,
                        weightInterpretation = WeightInterpretation.TOTAL.name,
                        primaryMuscle = MuscleGroup.CHEST.name,
                        notes = null
                    ),
                    emptyList(),
                    listOf(
                        WorkoutSessionSetEntity(
                            sessionExerciseId = 0L,
                            position = 0,
                            plannedMinReps = reps,
                            plannedMaxReps = reps,
                            plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                            plannedWeightKg = 80.0,
                            plannedDurationSeconds = null,
                            plannedDistanceMeters = null,
                            actualReps = reps,
                            actualLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                            actualWeightKg = 80.0,
                            actualDurationSeconds = null,
                            actualDistanceMeters = null,
                            status = SessionSetStatus.COMPLETED.name,
                            completedAt = finishedAt,
                            addedDuringWorkout = false
                        )
                    )
                )
            )
        )
    }

    private class MutableAccess(advanced: Boolean) : FeatureEntitlements {
        private val generation = MutableStateFlow(0)
        var advanced: Boolean = advanced
            set(value) {
                field = value
                generation.value = generation.value + 1
            }

        override fun hasAccess(feature: AppFeature): Boolean {
            return feature != AppFeature.AdvancedMuscleAnalytics || this.advanced
        }

        override fun changes(): Flow<Int> = generation
    }
}
