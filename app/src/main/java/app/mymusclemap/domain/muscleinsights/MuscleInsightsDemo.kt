package app.mymusclemap.domain.muscleinsights

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.statistics.StatisticsRange
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Fixed sample history relative to [today]. It does not read user workouts.
 * The same dates are used from the Overview action and from Statistics.
 */
object MuscleInsightsDemo {
    fun preview(today: LocalDate, now: LocalDateTime, zone: ZoneId): MuscleInsightsPreview {
        return index(today, zone)
            .preview(today, now, StatisticsRange.Days30)
            .copy(demo = true)
    }

    fun insight(
        muscle: MuscleGroup,
        today: LocalDate,
        now: LocalDateTime,
        zone: ZoneId,
        period: MuscleInsightsPeriod
    ): MuscleInsight {
        return index(today, zone).insight(muscle, today, now, period)
    }

    fun index(today: LocalDate, zone: ZoneId): MuscleInsightsLogic.InsightIndex {
        return MuscleInsightsLogic.index(aggregates(today, zone), zone)
    }

    fun aggregates(today: LocalDate, zone: ZoneId): List<WorkoutSessionAggregate> {
        return listOf(
            workout(
                id = 1L,
                date = today.minusDays(2),
                zone = zone,
                exerciseId = 1L,
                name = "Bench press",
                primary = MuscleGroup.CHEST,
                secondary = listOf(MuscleGroup.BICEPS),
                setCount = 4,
                firstSetId = 1L
            ),
            workout(
                id = 2L,
                date = today.minusDays(9),
                zone = zone,
                exerciseId = 1L,
                name = "Bench press",
                primary = MuscleGroup.CHEST,
                secondary = emptyList(),
                setCount = 3,
                firstSetId = 10L
            ),
            workout(
                id = 3L,
                date = today.minusDays(16),
                zone = zone,
                exerciseId = 1L,
                name = "Bench press",
                primary = MuscleGroup.CHEST,
                secondary = emptyList(),
                setCount = 3,
                firstSetId = 20L
            ),
            workout(
                id = 4L,
                date = today.minusDays(23),
                zone = zone,
                exerciseId = 1L,
                name = "Bench press",
                primary = MuscleGroup.CHEST,
                secondary = emptyList(),
                setCount = 3,
                firstSetId = 30L
            ),
            workout(
                id = 5L,
                date = today.minusDays(30),
                zone = zone,
                exerciseId = 1L,
                name = "Bench press",
                primary = MuscleGroup.CHEST,
                secondary = emptyList(),
                setCount = 3,
                firstSetId = 40L
            ),
            workout(
                id = 6L,
                date = today.minusDays(40),
                zone = zone,
                exerciseId = 2L,
                name = "Pull-up",
                primary = MuscleGroup.LATS,
                secondary = emptyList(),
                setCount = 2,
                firstSetId = 50L
            ),
            workout(
                id = 7L,
                date = today.minusDays(10),
                zone = zone,
                exerciseId = 3L,
                name = "Squat",
                primary = MuscleGroup.QUADRICEPS,
                secondary = emptyList(),
                setCount = 2,
                firstSetId = 60L
            )
        )
    }

    private fun workout(
        id: Long,
        date: LocalDate,
        zone: ZoneId,
        exerciseId: Long,
        name: String,
        primary: MuscleGroup,
        secondary: List<MuscleGroup>,
        setCount: Int,
        firstSetId: Long
    ): WorkoutSessionAggregate {
        val finished = date.atTime(LocalTime.of(18, 0)).atZone(zone).toInstant().toEpochMilli()
        val sets = List(setCount) { index ->
            set(firstSetId + index, finished)
        }
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
                bodyWeightSource = BodyWeightSource.MANUAL,
                bodyWeightSourceDate = null,
                createdAt = finished,
                updatedAt = finished
            ),
            exercises = listOf(
                SessionExerciseItem(
                    exercise = SessionExercise(
                        id = id * 100,
                        sessionId = id,
                        exerciseId = exerciseId,
                        position = 0,
                        name = name,
                        category = ExerciseCategory.STRENGTH,
                        movementPattern = MovementPattern.OTHER,
                        measurementType = MeasurementType.REPETITIONS,
                        resistanceBasis = ResistanceBasis.EXTERNAL,
                        weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                        primaryMuscle = primary,
                        secondaryMuscles = secondary,
                        notes = null
                    ),
                    sets = sets
                )
            )
        )
    }

    private fun set(id: Long, completedAt: Long): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = id,
            position = id.toInt(),
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
            status = SessionSetStatus.COMPLETED,
            completedAt = completedAt,
            addedDuringWorkout = false
        )
    }
}
