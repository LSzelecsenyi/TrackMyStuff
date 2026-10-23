package app.mymusclemap.domain.workout

import app.mymusclemap.domain.entitlement.FreeAccessDecision
import app.mymusclemap.domain.exercise.Exercise

object ExtraExerciseAccess {
    /**
     * Same rule as workout execution: archived exercises are excluded, starter exercises
     * stay available, and a custom exercise follows [FreeAccessDecision.customExerciseAccessible].
     */
    fun addable(exercise: Exercise, decision: FreeAccessDecision): Boolean {
        if (exercise.archived) return false
        if (!exercise.custom) return true
        return decision.customExerciseAccessible(exercise.id)
    }
}
