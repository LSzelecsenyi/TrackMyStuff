package hu.laca.weighttracker.ui.exercises

import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseFieldError
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation

fun ExerciseCategory.labelRes(): Int {
    return when (this) {
        ExerciseCategory.STRENGTH -> R.string.exercise_category_strength
        ExerciseCategory.CARDIO -> R.string.exercise_category_cardio
        ExerciseCategory.STATIC_HOLD -> R.string.exercise_category_static
        ExerciseCategory.MOBILITY -> R.string.exercise_category_mobility
        ExerciseCategory.SKILL -> R.string.exercise_category_skill
    }
}

fun MovementPattern.labelRes(): Int {
    return when (this) {
        MovementPattern.VERTICAL_PUSH -> R.string.exercise_pattern_vertical_push
        MovementPattern.HORIZONTAL_PUSH -> R.string.exercise_pattern_horizontal_push
        MovementPattern.VERTICAL_PULL -> R.string.exercise_pattern_vertical_pull
        MovementPattern.HORIZONTAL_PULL -> R.string.exercise_pattern_horizontal_pull
        MovementPattern.SQUAT -> R.string.exercise_pattern_squat
        MovementPattern.HIP_HINGE -> R.string.exercise_pattern_hip_hinge
        MovementPattern.LUNGE -> R.string.exercise_pattern_lunge
        MovementPattern.CORE -> R.string.exercise_pattern_core
        MovementPattern.CARRY -> R.string.exercise_pattern_carry
        MovementPattern.ISOLATION -> R.string.exercise_pattern_isolation
        MovementPattern.CARDIO -> R.string.exercise_pattern_cardio
        MovementPattern.MOBILITY -> R.string.exercise_pattern_mobility
        MovementPattern.OTHER -> R.string.exercise_pattern_other
    }
}

fun MeasurementType.labelRes(): Int {
    return when (this) {
        MeasurementType.REPETITIONS -> R.string.exercise_measure_reps
        MeasurementType.REPETITIONS_AND_WEIGHT -> R.string.exercise_measure_reps_weight
        MeasurementType.DURATION -> R.string.exercise_measure_duration
        MeasurementType.DURATION_AND_WEIGHT -> R.string.exercise_measure_duration_weight
        MeasurementType.DISTANCE_AND_DURATION -> R.string.exercise_measure_distance_duration
        MeasurementType.COMPLETION_ONLY -> R.string.exercise_measure_completion
    }
}

fun ResistanceBasis.labelRes(): Int {
    return when (this) {
        ResistanceBasis.BODYWEIGHT -> R.string.exercise_resistance_bodyweight
        ResistanceBasis.EXTERNAL -> R.string.exercise_resistance_external
        ResistanceBasis.NONE -> R.string.exercise_resistance_none
    }
}

fun WeightInterpretation.labelRes(): Int {
    return when (this) {
        WeightInterpretation.TOTAL -> R.string.exercise_weight_total
        WeightInterpretation.PER_SIDE -> R.string.exercise_weight_per_side
        WeightInterpretation.NOT_APPLICABLE -> R.string.exercise_weight_na
    }
}

fun MuscleGroup.labelRes(): Int {
    return when (this) {
        MuscleGroup.CHEST -> R.string.muscle_chest
        MuscleGroup.LATS -> R.string.muscle_lats
        MuscleGroup.UPPER_BACK -> R.string.muscle_upper_back
        MuscleGroup.LOWER_BACK -> R.string.muscle_lower_back
        MuscleGroup.FRONT_DELTOID -> R.string.muscle_front_delt
        MuscleGroup.SIDE_DELTOID -> R.string.muscle_side_delt
        MuscleGroup.REAR_DELTOID -> R.string.muscle_rear_delt
        MuscleGroup.BICEPS -> R.string.muscle_biceps
        MuscleGroup.TRICEPS -> R.string.muscle_triceps
        MuscleGroup.FOREARMS -> R.string.muscle_forearms
        MuscleGroup.ABS -> R.string.muscle_abs
        MuscleGroup.OBLIQUES -> R.string.muscle_obliques
        MuscleGroup.GLUTES -> R.string.muscle_glutes
        MuscleGroup.QUADRICEPS -> R.string.muscle_quads
        MuscleGroup.HAMSTRINGS -> R.string.muscle_hamstrings
        MuscleGroup.ADDUCTORS -> R.string.muscle_adductors
        MuscleGroup.CALVES -> R.string.muscle_calves
        MuscleGroup.NECK -> R.string.muscle_neck
        MuscleGroup.FULL_BODY -> R.string.muscle_full_body
        MuscleGroup.CARDIOVASCULAR -> R.string.muscle_cardio
    }
}

fun ExerciseFieldError.labelRes(): Int {
    return when (this) {
        ExerciseFieldError.NameBlank -> R.string.error_exercise_name_blank
        ExerciseFieldError.NameTooLong -> R.string.error_exercise_name_length
        ExerciseFieldError.NotesTooLong -> R.string.error_exercise_notes_length
        ExerciseFieldError.PrimaryAlsoSecondary -> R.string.error_exercise_primary_secondary
        ExerciseFieldError.WeightInterpretationRequired -> R.string.error_exercise_weight_required
    }
}

fun CatalogMessage.labelRes(): Int {
    return when (this) {
        CatalogMessage.Saved -> R.string.message_exercise_saved
        CatalogMessage.Updated -> R.string.message_exercise_updated
        CatalogMessage.DuplicateName -> R.string.error_exercise_duplicate
        CatalogMessage.Archived -> R.string.message_exercise_archived
        CatalogMessage.Restored -> R.string.message_exercise_restored
        CatalogMessage.Deleted -> R.string.message_exercise_deleted
        CatalogMessage.DeleteBlocked -> R.string.message_exercise_delete_blocked
    }
}
