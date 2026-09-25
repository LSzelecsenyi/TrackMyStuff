package app.mymusclemap.domain.musclemap

import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.MuscleGroup

object TemplateMuscleMapAssembler {
    fun assemble(exercises: List<Exercise>): TemplateMuscleMapState {
        val emphasis = linkedMapOf<MuscleGroup, TemplateMuscleEmphasis>()
        var hasFullBody = false
        var hasCardiovascular = false
        exercises.forEach { exercise ->
            when (exercise.primaryMuscle) {
                MuscleGroup.FULL_BODY -> hasFullBody = true
                MuscleGroup.CARDIOVASCULAR -> hasCardiovascular = true
                else -> emphasis[exercise.primaryMuscle] = TemplateMuscleEmphasis.PRIMARY
            }
            exercise.secondaryMuscles.forEach { group ->
                when (group) {
                    MuscleGroup.FULL_BODY -> hasFullBody = true
                    MuscleGroup.CARDIOVASCULAR -> hasCardiovascular = true
                    else -> if (emphasis[group] != TemplateMuscleEmphasis.PRIMARY) {
                        emphasis[group] = TemplateMuscleEmphasis.SECONDARY
                    }
                }
            }
        }
        return TemplateMuscleMapState(
            emphasis = emphasis,
            hasFullBody = hasFullBody,
            hasCardiovascular = hasCardiovascular
        )
    }
}
