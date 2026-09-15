package hu.laca.weighttracker.data.local

import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseEnumCodec
import hu.laca.weighttracker.domain.exercise.MuscleRole

fun ExerciseEntity.toModel(muscles: List<ExerciseMuscleEntity>): Exercise {
    val primary = muscles.firstOrNull { ExerciseEnumCodec.role(it.role) == MuscleRole.PRIMARY }
        ?: muscles.firstOrNull()
    val secondary = muscles
        .filter { ExerciseEnumCodec.role(it.role) == MuscleRole.SECONDARY }
        .mapNotNull { ExerciseEnumCodec.muscle(it.muscleGroup) }
        .distinct()
        .filterNot { it == ExerciseEnumCodec.muscleOrFallback(primary?.muscleGroup.orEmpty()) }
    return Exercise(
        id = id,
        name = name,
        normalizedName = normalizedName,
        category = ExerciseEnumCodec.category(category),
        movementPattern = ExerciseEnumCodec.movement(movementPattern),
        measurementType = ExerciseEnumCodec.measurement(measurementType),
        resistanceBasis = ExerciseEnumCodec.resistance(resistanceBasis),
        weightInterpretation = ExerciseEnumCodec.weight(weightInterpretation),
        primaryMuscle = ExerciseEnumCodec.muscleOrFallback(primary?.muscleGroup.orEmpty()),
        secondaryMuscles = secondary,
        notes = notes,
        archived = archived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
