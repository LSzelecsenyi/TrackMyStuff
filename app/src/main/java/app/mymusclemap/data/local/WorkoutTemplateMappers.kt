package app.mymusclemap.data.local

import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.WorkoutTemplate
import app.mymusclemap.domain.workout.WorkoutTemplateExercise
import app.mymusclemap.domain.workout.WorkoutTemplateSet

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
