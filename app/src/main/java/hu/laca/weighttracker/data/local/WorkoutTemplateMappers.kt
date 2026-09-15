package hu.laca.weighttracker.data.local

import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.WorkoutTemplate
import hu.laca.weighttracker.domain.workout.WorkoutTemplateExercise
import hu.laca.weighttracker.domain.workout.WorkoutTemplateSet

fun WorkoutTemplateEntity.toModel(): WorkoutTemplate {
    return WorkoutTemplate(
        id = id,
        name = name,
        normalizedName = normalizedName,
        notes = notes,
        archived = archived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun WorkoutTemplateExerciseEntity.toModel(): WorkoutTemplateExercise {
    return WorkoutTemplateExercise(
        id = id,
        templateId = templateId,
        exerciseId = exerciseId,
        position = position,
        notes = notes,
        createdAt = createdAt
    )
}

fun WorkoutTemplateSetEntity.toModel(): WorkoutTemplateSet {
    return WorkoutTemplateSet(
        id = id,
        templateExerciseId = templateExerciseId,
        position = position,
        minReps = minReps,
        maxReps = maxReps,
        loadKind = runCatching { PlannedLoadKind.valueOf(loadKind) }
            .getOrDefault(PlannedLoadKind.NONE),
        weightKg = weightKg,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters
    )
}
