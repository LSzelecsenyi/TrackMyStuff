package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus

/**
 * Conservative kilogram-volume rules for Advanced Statistics.
 *
 * Volume is [effectiveLoadKg] × [SessionSet.actualReps] only when:
 * - the set is [SessionSetStatus.COMPLETED]
 * - the exercise records reps and weight
 * - actual reps and weight are both present and positive
 * - the actual load is external or added weight
 *
 * PER_SIDE records are doubled so the figure is total external load, matching how
 * the journal labels those sets. Bodyweight-only, assistance, and duration-plus-weight
 * sets are omitted rather than inventing a kilogram total.
 */
object WorkoutSetVolume {
    fun isCompleted(set: SessionSet): Boolean = set.status == SessionSetStatus.COMPLETED

    fun effectiveLoadKg(set: SessionSet, interpretation: WeightInterpretation): Double? {
        if (!isCompleted(set)) return null
        val kind = set.actualLoadKind ?: return null
        val weight = set.actualWeightKg ?: return null
        if (weight <= 0.0) return null
        return when (kind) {
            PlannedLoadKind.EXTERNAL_WEIGHT -> if (interpretation == WeightInterpretation.PER_SIDE) {
                weight * 2.0
            } else {
                weight
            }
            PlannedLoadKind.ADDED_WEIGHT -> weight
            PlannedLoadKind.BODYWEIGHT_ONLY,
            PlannedLoadKind.ASSISTANCE,
            PlannedLoadKind.NONE -> null
        }
    }

    fun isVolumeEligible(exercise: SessionExercise, set: SessionSet): Boolean {
        if (exercise.measurementType != MeasurementType.REPETITIONS_AND_WEIGHT) return false
        val reps = set.actualReps ?: return false
        if (reps <= 0) return false
        return effectiveLoadKg(set, exercise.weightInterpretation) != null
    }

    fun volumeKg(exercise: SessionExercise, set: SessionSet): Double? {
        if (!isVolumeEligible(exercise, set)) return null
        val load = effectiveLoadKg(set, exercise.weightInterpretation) ?: return null
        val reps = set.actualReps ?: return null
        return load * reps
    }
}
