package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class InWorkoutExerciseHistoryRepositoryTest {
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
            FixedDateProvider(LocalDate.parse("2026-09-16"))
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun samePlanHistoryBeatsANewerOtherPlanAndIgnoresDrafts() = runTest {
        val exerciseId = saveBench()
        val samePlan = savePlan("Push A", exerciseId)
        val otherPlan = savePlan("Other", exerciseId)
        insertHistory(
            templateId = samePlan,
            templateName = "Old Push",
            startedAt = 100L,
            exerciseId = exerciseId,
            sets = listOf(
                set(status = SessionSetStatus.PENDING, actualReps = 99, plannedReps = 50, draft = "{\"reps\":\"3\"}"),
                set(status = SessionSetStatus.COMPLETED, actualReps = 8, plannedReps = 50, weight = 12.0),
                set(status = SessionSetStatus.SKIPPED, actualReps = null, plannedReps = 50)
            )
        )
        insertHistory(
            templateId = otherPlan,
            templateName = "Other",
            startedAt = 800L,
            exerciseId = exerciseId,
            sets = listOf(set(status = SessionSetStatus.COMPLETED, actualReps = 15, plannedReps = 15, weight = 40.0))
        )
        insertHistory(
            templateId = samePlan,
            templateName = "Abandoned",
            startedAt = 900L,
            exerciseId = exerciseId,
            status = SessionStatus.ABANDONED,
            sets = listOf(set(status = SessionSetStatus.COMPLETED, actualReps = 77, plannedReps = 77, weight = 1.0))
        )
        val current = start(samePlan)
        val aggregate = sessions.getAggregate(current)!!
        val history = sessions.loadPreviousExerciseHistory(aggregate, aggregate.exercises.single().exercise)
        assertEquals("Old Push", history!!.templateName)
        assertFalse(history.fromOtherPlan)
        assertEquals(listOf(1), history.sets.map { it.position })
        assertEquals(8, history.sets.single().reps)
        assertEquals(12.0, history.sets.single().weightKg!!, 0.0)
        assertNull(sessions.loadPreviousExerciseHistory(aggregate, aggregate.exercises.single().exercise)!!.sets
            .firstOrNull { it.reps == 99 || it.reps == 77 || it.reps == 15 })
    }

    @Test
    fun fallsBackWhenTheSamePlanHasNoCompletedSets() = runTest {
        val exerciseId = saveBench()
        val samePlan = savePlan("Push A", exerciseId)
        val otherPlan = savePlan("Other", exerciseId)
        insertHistory(
            templateId = otherPlan,
            templateName = "Evening",
            startedAt = 400L,
            exerciseId = exerciseId,
            sets = listOf(set(status = SessionSetStatus.COMPLETED, actualReps = 11, plannedReps = 8, weight = 20.0))
        )
        val current = start(samePlan)
        val aggregate = sessions.getAggregate(current)!!
        val history = sessions.loadPreviousExerciseHistory(aggregate, aggregate.exercises.single().exercise)
        assertEquals("Evening", history!!.templateName)
        assertTrue(history.fromOtherPlan)
        assertEquals(11, history.sets.single().reps)
    }

    @Test
    fun repeatedOccurrencesDoNotShareSets() = runTest {
        val exerciseId = saveBench()
        val plan = savePlan("Push A", exerciseId, copies = 2)
        insertHistory(
            templateId = plan,
            templateName = "Push A",
            startedAt = 200L,
            exerciseId = exerciseId,
            occurrences = listOf(
                listOf(set(status = SessionSetStatus.COMPLETED, actualReps = 5, plannedReps = 5, weight = 10.0)),
                listOf(set(status = SessionSetStatus.COMPLETED, actualReps = 12, plannedReps = 12, weight = 16.0))
            )
        )
        val current = start(plan)
        val aggregate = sessions.getAggregate(current)!!
        val first = sessions.loadPreviousExerciseHistory(aggregate, aggregate.exercises[0].exercise)!!
        val second = sessions.loadPreviousExerciseHistory(aggregate, aggregate.exercises[1].exercise)!!
        assertEquals(5, first.sets.single().reps)
        assertEquals(12, second.sets.single().reps)
        assertEquals(listOf(12), second.sets.map { it.reps })
    }

    @Test
    fun incompatibleMeasurementSnapshotIsNotReused() = runTest {
        val exerciseId = saveBench()
        val plan = savePlan("Push A", exerciseId)
        insertHistory(
            templateId = plan,
            templateName = "Old type",
            startedAt = 500L,
            exerciseId = exerciseId,
            measurement = MeasurementType.DURATION,
            resistance = ResistanceBasis.NONE,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(
                set(
                    status = SessionSetStatus.COMPLETED,
                    actualReps = null,
                    plannedReps = null,
                    kind = PlannedLoadKind.NONE,
                    duration = 90
                )
            )
        )
        insertHistory(
            templateId = plan,
            templateName = "Compatible",
            startedAt = 100L,
            exerciseId = exerciseId,
            sets = listOf(set(status = SessionSetStatus.COMPLETED, actualReps = 8, plannedReps = 8, weight = 12.0))
        )
        val current = start(plan)
        val aggregate = sessions.getAggregate(current)!!
        val history = sessions.loadPreviousExerciseHistory(aggregate, aggregate.exercises.single().exercise)
        assertEquals("Compatible", history!!.templateName)
        assertEquals(MeasurementType.REPETITIONS_AND_WEIGHT, history.measurementType)
        assertEquals(8, history.sets.single().reps)
    }

    private suspend fun saveBench(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Bench",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = WeightInterpretation.TOTAL,
                primaryMuscle = MuscleGroup.CHEST
            )
        ) as ExerciseSaveResult.Created).id
    }

    private suspend fun savePlan(name: String, exerciseId: Long, copies: Int = 1): Long {
        val draft = TemplateDraft(
            name = name,
            exercises = List(copies) { index ->
                TemplateExerciseDraft(
                    localId = -(index + 1L),
                    exerciseId = exerciseId,
                    sets = listOf(
                        PlannedSetDraft(
                            localId = -1L,
                            minRepsText = "8",
                            loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                            weightText = "10"
                        )
                    )
                )
            }
        )
        return (templates.save(draft) as TemplateSaveResult.Created).id
    }

    private suspend fun start(templateId: Long): Long {
        return (sessions.start(templateId) as StartWorkoutResult.Started).sessionId
    }

    private suspend fun insertHistory(
        templateId: Long?,
        templateName: String,
        startedAt: Long,
        exerciseId: Long,
        sets: List<WorkoutSessionSetEntity> = emptyList(),
        occurrences: List<List<WorkoutSessionSetEntity>>? = null,
        status: SessionStatus = SessionStatus.COMPLETED,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance: ResistanceBasis = ResistanceBasis.EXTERNAL,
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL
    ) {
        val groups = occurrences ?: listOf(sets)
        database.workoutSessionDao().insertAggregate(
            WorkoutSessionEntity(
                templateId = templateId,
                templateName = templateName,
                status = status.name,
                workoutDate = "2026-09-01",
                startedAt = startedAt,
                finishedAt = if (status == SessionStatus.COMPLETED) startedAt + 10 else null,
                abandonedAt = if (status == SessionStatus.ABANDONED) startedAt + 10 else null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN.name,
                bodyWeightSourceDate = null,
                createdAt = startedAt,
                updatedAt = startedAt,
                activeLock = null,
                clientWorkoutId = UUID.randomUUID().toString()
            ),
            groups.map { group ->
                Triple(
                    WorkoutSessionExerciseEntity(
                        sessionId = 0L,
                        exerciseId = exerciseId,
                        position = 0,
                        name = "Bench",
                        category = ExerciseCategory.STRENGTH.name,
                        movementPattern = MovementPattern.HORIZONTAL_PUSH.name,
                        measurementType = measurement.name,
                        resistanceBasis = resistance.name,
                        weightInterpretation = interpretation.name,
                        primaryMuscle = MuscleGroup.CHEST.name,
                        notes = null
                    ),
                    emptyList(),
                    group
                )
            }
        )
    }

    private fun set(
        status: SessionSetStatus,
        actualReps: Int?,
        plannedReps: Int?,
        weight: Double? = null,
        kind: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        draft: String? = null,
        duration: Int? = null
    ): WorkoutSessionSetEntity {
        val completed = status == SessionSetStatus.COMPLETED
        return WorkoutSessionSetEntity(
            sessionExerciseId = 0L,
            position = 0,
            plannedMinReps = plannedReps,
            plannedMaxReps = plannedReps,
            plannedLoadKind = kind.name,
            plannedWeightKg = weight,
            plannedDurationSeconds = duration,
            plannedDistanceMeters = null,
            actualReps = if (completed) actualReps else actualReps,
            actualLoadKind = if (completed) kind.name else null,
            actualWeightKg = if (completed) weight else null,
            actualDurationSeconds = if (completed) duration else null,
            actualDistanceMeters = null,
            status = status.name,
            completedAt = if (completed) 1L else null,
            addedDuringWorkout = false,
            draftPayload = draft
        )
    }
}
