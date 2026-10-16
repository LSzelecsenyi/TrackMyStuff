package app.mymusclemap.domain.performance

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Fixed sample comparisons for the Free Muscle Insights preview.
 * These aggregates are never written to a workout repository.
 */
object ExerciseProgressionDemo {
    fun summarize(
        muscle: MuscleGroup,
        period: MuscleInsightsPeriod,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): MuscleExercisePerformance {
        return ExercisePerformanceComparisonLogic.summarizeMuscle(
            aggregates(today, zone),
            muscle,
            period,
            today
        )
    }

    fun aggregates(today: LocalDate, zone: ZoneId): List<WorkoutSessionAggregate> {
        return listOf(
            weighted(1L, today, zone, 2, 101L, "Bench press", MuscleGroup.CHEST, 80.0, 10),
            weighted(2L, today, zone, 14, 101L, "Bench press", MuscleGroup.CHEST, 80.0, 8),
            mixed(3L, today, zone, 3, 102L, "Incline bench press", 60.0 to 12, 80.0 to 5),
            mixed(4L, today, zone, 16, 102L, "Incline bench press", 60.0 to 10, 80.0 to 8),
            weighted(5L, today, zone, 120, 103L, "Archive press", MuscleGroup.CHEST, 70.0, 6),
            added(6L, today, zone, 4, 201L, "Weighted pull-up", MuscleGroup.LATS, 15.0, 8),
            added(7L, today, zone, 18, 201L, "Weighted pull-up", MuscleGroup.LATS, 10.0, 8),
            hold(8L, today, zone, 1, 301L, "Plank", MuscleGroup.ABS, 60),
            hold(9L, today, zone, 12, 301L, "Plank", MuscleGroup.ABS, 45),
            weighted(10L, today, zone, 5, 401L, "Back squat", MuscleGroup.QUADRICEPS, 100.0, 5),
            weighted(11L, today, zone, 15, 401L, "Back squat", MuscleGroup.QUADRICEPS, 100.0, 8)
        )
    }

    private fun weighted(
        id: Long,
        today: LocalDate,
        zone: ZoneId,
        daysAgo: Long,
        exerciseId: Long,
        name: String,
        muscle: MuscleGroup,
        kilograms: Double,
        reps: Int
    ): WorkoutSessionAggregate {
        return session(
            id = id,
            today = today,
            zone = zone,
            daysAgo = daysAgo,
            exerciseId = exerciseId,
            name = name,
            muscle = muscle,
            measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistance = ResistanceBasis.EXTERNAL,
            interpretation = WeightInterpretation.TOTAL,
            sets = listOf(set(reps = reps, weight = kilograms, kind = PlannedLoadKind.EXTERNAL_WEIGHT))
        )
    }

    private fun mixed(
        id: Long,
        today: LocalDate,
        zone: ZoneId,
        daysAgo: Long,
        exerciseId: Long,
        name: String,
        first: Pair<Double, Int>,
        second: Pair<Double, Int>
    ): WorkoutSessionAggregate {
        return session(
            id = id,
            today = today,
            zone = zone,
            daysAgo = daysAgo,
            exerciseId = exerciseId,
            name = name,
            muscle = MuscleGroup.CHEST,
            measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistance = ResistanceBasis.EXTERNAL,
            interpretation = WeightInterpretation.TOTAL,
            sets = listOf(
                set(reps = first.second, weight = first.first, kind = PlannedLoadKind.EXTERNAL_WEIGHT),
                set(reps = second.second, weight = second.first, kind = PlannedLoadKind.EXTERNAL_WEIGHT)
            )
        )
    }

    private fun added(
        id: Long,
        today: LocalDate,
        zone: ZoneId,
        daysAgo: Long,
        exerciseId: Long,
        name: String,
        muscle: MuscleGroup,
        kilograms: Double,
        reps: Int
    ): WorkoutSessionAggregate {
        return session(
            id = id,
            today = today,
            zone = zone,
            daysAgo = daysAgo,
            exerciseId = exerciseId,
            name = name,
            muscle = muscle,
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(set(reps = reps, weight = kilograms, kind = PlannedLoadKind.ADDED_WEIGHT))
        )
    }

    private fun hold(
        id: Long,
        today: LocalDate,
        zone: ZoneId,
        daysAgo: Long,
        exerciseId: Long,
        name: String,
        muscle: MuscleGroup,
        seconds: Int
    ): WorkoutSessionAggregate {
        return session(
            id = id,
            today = today,
            zone = zone,
            daysAgo = daysAgo,
            exerciseId = exerciseId,
            name = name,
            muscle = muscle,
            measurement = MeasurementType.DURATION,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(set(reps = null, weight = null, kind = PlannedLoadKind.BODYWEIGHT_ONLY, duration = seconds))
        )
    }

    private fun session(
        id: Long,
        today: LocalDate,
        zone: ZoneId,
        daysAgo: Long,
        exerciseId: Long,
        name: String,
        muscle: MuscleGroup,
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        interpretation: WeightInterpretation,
        sets: List<SessionSet>
    ): WorkoutSessionAggregate {
        val date = today.minusDays(daysAgo)
        val finished = date.atTime(LocalTime.of(18, 0)).atZone(zone).toInstant().toEpochMilli()
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = id,
                templateId = null,
                templateName = "Demo",
                status = SessionStatus.COMPLETED,
                workoutDate = date,
                startedAt = finished - 3_600_000L,
                finishedAt = finished,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = finished,
                updatedAt = finished
            ),
            exercises = listOf(
                SessionExerciseItem(
                    exercise = SessionExercise(
                        id = id,
                        sessionId = id,
                        exerciseId = exerciseId,
                        position = 0,
                        name = name,
                        category = ExerciseCategory.STRENGTH,
                        movementPattern = MovementPattern.OTHER,
                        measurementType = measurement,
                        resistanceBasis = resistance,
                        weightInterpretation = interpretation,
                        primaryMuscle = muscle,
                        secondaryMuscles = emptyList(),
                        notes = null
                    ),
                    sets = sets
                )
            )
        )
    }

    private fun set(
        reps: Int?,
        weight: Double?,
        kind: PlannedLoadKind,
        duration: Int? = null
    ): SessionSet {
        return SessionSet(
            id = 1L,
            sessionExerciseId = 0L,
            position = 0,
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = kind,
            plannedWeightKg = weight,
            plannedDurationSeconds = duration,
            plannedDistanceMeters = null,
            actualReps = reps,
            actualLoadKind = kind,
            actualWeightKg = weight,
            actualDurationSeconds = duration,
            actualDistanceMeters = null,
            status = SessionSetStatus.COMPLETED,
            completedAt = 1L,
            addedDuringWorkout = false
        )
    }
}
