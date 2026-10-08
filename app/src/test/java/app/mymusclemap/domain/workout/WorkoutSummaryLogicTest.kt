package app.mymusclemap.domain.workout

import app.mymusclemap.domain.achievements.PerformanceRecordEvaluator
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WorkoutSummaryLogicTest {
    @Test
    fun summaryKeepsStoredOrderAndDropsSkippedSets() {
        val aggregate = session(
            name = "Push",
            startedAt = 1_000L,
            finishedAt = 91_000L,
            items = listOf(
                exercise(
                    id = 2,
                    position = 1,
                    name = "Plank",
                    measurement = MeasurementType.DURATION,
                    resistance = ResistanceBasis.NONE,
                    interpretation = WeightInterpretation.NOT_APPLICABLE,
                    sets = listOf(set(id = 5, position = 0, status = SessionSetStatus.COMPLETED, duration = 45))
                ),
                exercise(
                    id = 1,
                    position = 0,
                    name = "Weighted Dips",
                    measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
                    resistance = ResistanceBasis.EXTERNAL,
                    interpretation = WeightInterpretation.TOTAL,
                    sets = listOf(
                        set(id = 3, position = 2, status = SessionSetStatus.COMPLETED, reps = 6, weight = 20.0),
                        set(id = 1, position = 0, status = SessionSetStatus.COMPLETED, reps = 8, weight = 20.0),
                        set(id = 2, position = 1, status = SessionSetStatus.SKIPPED, reps = 8, weight = 20.0),
                        set(id = 4, position = 3, status = SessionSetStatus.PENDING, reps = 8, weight = 20.0)
                    )
                )
            )
        )
        val summary = WorkoutSummaryLogic.from(aggregate)!!
        assertEquals("Push", summary.workoutName)
        assertEquals(90_000L, summary.durationMillis)
        assertEquals(listOf("Weighted Dips", "Plank"), summary.exercises.map { it.name })
        assertEquals(listOf(1L, 3L), summary.exercises[0].completedSets.map { it.id })
        assertEquals(2, summary.exerciseCount)
        assertEquals(3, summary.completedSetCount)
        assertEquals(280.0, summary.volumeKg!!, 0.0)
        assertEquals(280.0, PerformanceRecordEvaluator.workoutVolumeKg(aggregate)!!, 0.0)
    }

    @Test
    fun bodyweightAndDistanceDoNotInventVolume() {
        val aggregate = session(
            name = "Conditioning",
            startedAt = 0L,
            finishedAt = 60_000L,
            items = listOf(
                exercise(
                    id = 1,
                    position = 0,
                    name = "Pull-up",
                    measurement = MeasurementType.REPETITIONS,
                    resistance = ResistanceBasis.BODYWEIGHT,
                    interpretation = WeightInterpretation.NOT_APPLICABLE,
                    sets = listOf(
                        set(
                            id = 1,
                            position = 0,
                            status = SessionSetStatus.COMPLETED,
                            reps = 8,
                            kind = PlannedLoadKind.BODYWEIGHT_ONLY
                        )
                    )
                ),
                exercise(
                    id = 2,
                    position = 1,
                    name = "Run",
                    measurement = MeasurementType.DISTANCE_AND_DURATION,
                    resistance = ResistanceBasis.NONE,
                    interpretation = WeightInterpretation.NOT_APPLICABLE,
                    sets = listOf(
                        set(
                            id = 2,
                            position = 0,
                            status = SessionSetStatus.COMPLETED,
                            kind = PlannedLoadKind.NONE,
                            distance = 5_000.0,
                            duration = 1_500
                        )
                    )
                )
            )
        )
        val summary = WorkoutSummaryLogic.from(aggregate)!!
        assertNull(summary.volumeKg)
        assertEquals(2, summary.exerciseCount)
        assertEquals(2, summary.completedSetCount)
    }

    @Test
    fun anUnfinishedSessionHasNoSummary() {
        val aggregate = session(
            name = "Push",
            startedAt = 1L,
            finishedAt = null,
            status = SessionStatus.IN_PROGRESS,
            items = emptyList()
        )
        assertNull(WorkoutSummaryLogic.from(aggregate))
    }

    private fun session(
        name: String,
        startedAt: Long,
        finishedAt: Long?,
        items: List<SessionExerciseItem>,
        status: SessionStatus = SessionStatus.COMPLETED
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = 7,
                templateId = 1,
                templateName = name,
                status = status,
                workoutDate = LocalDate.of(2026, 10, 8),
                startedAt = startedAt,
                finishedAt = finishedAt,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = startedAt,
                updatedAt = finishedAt ?: startedAt,
                clientWorkoutId = "cw-summary"
            ),
            exercises = items
        )
    }

    private fun exercise(
        id: Long,
        position: Int,
        name: String,
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        interpretation: WeightInterpretation,
        sets: List<SessionSet>
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = id,
                sessionId = 7,
                exerciseId = id,
                position = position,
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.OTHER,
                measurementType = measurement,
                resistanceBasis = resistance,
                weightInterpretation = interpretation,
                primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = sets
        )
    }

    private fun set(
        id: Long,
        position: Int,
        status: SessionSetStatus,
        reps: Int? = null,
        weight: Double? = null,
        kind: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        duration: Int? = null,
        distance: Double? = null
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
            plannedDistanceMeters = distance,
            actualReps = reps,
            actualLoadKind = kind,
            actualWeightKg = weight,
            actualDurationSeconds = duration,
            actualDistanceMeters = distance,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) 1L else null,
            addedDuringWorkout = false
        )
    }
}
