package hu.laca.weighttracker.data.local

import hu.laca.weighttracker.domain.exercise.ExerciseEnumCodec
import hu.laca.weighttracker.domain.exercise.MuscleRole
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.SessionExercise
import hu.laca.weighttracker.domain.workout.SessionSet
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.WorkoutSession
import java.time.LocalDate

fun WorkoutSessionEntity.toModel(): WorkoutSession {
    return WorkoutSession(
        id = id,
        templateId = templateId,
        templateName = templateName,
        status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.ABANDONED),
        workoutDate = LocalDate.parse(workoutDate),
        startedAt = startedAt,
        finishedAt = finishedAt,
        abandonedAt = abandonedAt,
        notes = notes,
        bodyWeightKg = bodyWeightKg,
        bodyWeightSource = runCatching { BodyWeightSource.valueOf(bodyWeightSource) }
            .getOrDefault(BodyWeightSource.UNKNOWN),
        bodyWeightSourceDate = bodyWeightSourceDate?.let(LocalDate::parse),
        createdAt = createdAt,
        updatedAt = updatedAt,
        importFingerprint = importFingerprint,
        scheduledWorkoutId = scheduledWorkoutId
    )
}

fun WorkoutSessionExerciseEntity.toModel(
    muscles: List<WorkoutSessionExerciseMuscleEntity>
): SessionExercise {
    val secondary = muscles
        .filter { ExerciseEnumCodec.role(it.role) == MuscleRole.SECONDARY }
        .mapNotNull { ExerciseEnumCodec.muscle(it.muscleGroup) }
        .distinct()
    return SessionExercise(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        position = position,
        name = name,
        category = ExerciseEnumCodec.category(category),
        movementPattern = ExerciseEnumCodec.movement(movementPattern),
        measurementType = ExerciseEnumCodec.measurement(measurementType),
        resistanceBasis = ExerciseEnumCodec.resistance(resistanceBasis),
        weightInterpretation = ExerciseEnumCodec.weight(weightInterpretation),
        primaryMuscle = ExerciseEnumCodec.muscleOrFallback(primaryMuscle),
        secondaryMuscles = secondary,
        notes = notes
    )
}

fun WorkoutSessionSetEntity.toModel(): SessionSet {
    return SessionSet(
        id = id,
        sessionExerciseId = sessionExerciseId,
        position = position,
        plannedMinReps = plannedMinReps,
        plannedMaxReps = plannedMaxReps,
        plannedLoadKind = runCatching { PlannedLoadKind.valueOf(plannedLoadKind) }
            .getOrDefault(PlannedLoadKind.NONE),
        plannedWeightKg = plannedWeightKg,
        plannedDurationSeconds = plannedDurationSeconds,
        plannedDistanceMeters = plannedDistanceMeters,
        actualReps = actualReps,
        actualLoadKind = actualLoadKind?.let { runCatching { PlannedLoadKind.valueOf(it) }.getOrNull() },
        actualWeightKg = actualWeightKg,
        actualDurationSeconds = actualDurationSeconds,
        actualDistanceMeters = actualDistanceMeters,
        status = runCatching { SessionSetStatus.valueOf(status) }.getOrDefault(SessionSetStatus.PENDING),
        completedAt = completedAt,
        addedDuringWorkout = addedDuringWorkout
    )
}

fun SessionStatus.activeLock(): Int? = if (this == SessionStatus.IN_PROGRESS) 1 else null

data class WorkoutDateCount(
    val date: String,
    val completedCount: Int
)

data class ImportedWorkoutName(
    val date: String,
    val name: String
)
