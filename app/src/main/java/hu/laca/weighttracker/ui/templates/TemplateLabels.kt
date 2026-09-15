package hu.laca.weighttracker.ui.templates

import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.TemplateFieldError

fun PlannedLoadKind.labelRes(): Int {
    return when (this) {
        PlannedLoadKind.BODYWEIGHT_ONLY -> R.string.load_bodyweight
        PlannedLoadKind.ADDED_WEIGHT -> R.string.load_added
        PlannedLoadKind.ASSISTANCE -> R.string.load_assistance
        PlannedLoadKind.EXTERNAL_WEIGHT -> R.string.load_external
        PlannedLoadKind.NONE -> R.string.load_none
    }
}

fun TemplateFieldError.labelRes(): Int {
    return when (this) {
        TemplateFieldError.NameBlank -> R.string.error_template_name_blank
        TemplateFieldError.NameTooLong -> R.string.error_template_name_length
        TemplateFieldError.NotesTooLong -> R.string.error_template_notes_length
        TemplateFieldError.RepsRequired -> R.string.error_template_reps_required
        TemplateFieldError.RepsMalformed -> R.string.error_template_reps_malformed
        TemplateFieldError.RepsNotPositive -> R.string.error_template_reps_positive
        TemplateFieldError.RepsMaxLessThanMin -> R.string.error_template_reps_range
        TemplateFieldError.RepsTooLarge -> R.string.error_template_reps_large
        TemplateFieldError.LoadIncompatible -> R.string.error_template_load
        TemplateFieldError.WeightRequired -> R.string.error_template_weight_required
        TemplateFieldError.WeightMalformed -> R.string.error_template_weight_malformed
        TemplateFieldError.WeightNotPositive -> R.string.error_template_weight_positive
        TemplateFieldError.WeightTooLarge -> R.string.error_template_weight_large
        TemplateFieldError.DurationRequired -> R.string.error_template_duration_required
        TemplateFieldError.DurationMalformed -> R.string.error_template_duration_malformed
        TemplateFieldError.DurationNotPositive -> R.string.error_template_duration_positive
        TemplateFieldError.DistanceRequired -> R.string.error_template_distance_required
        TemplateFieldError.DistanceMalformed -> R.string.error_template_distance_malformed
        TemplateFieldError.DistanceNotPositive -> R.string.error_template_distance_positive
        TemplateFieldError.CompletionSingleSet -> R.string.error_template_completion_sets
    }
}

fun TemplateMessage.labelRes(): Int {
    return when (this) {
        TemplateMessage.Saved -> R.string.message_template_saved
        TemplateMessage.Updated -> R.string.message_template_updated
        TemplateMessage.Archived -> R.string.message_template_archived
        TemplateMessage.Restored -> R.string.message_template_restored
        TemplateMessage.Deleted -> R.string.message_template_deleted
        TemplateMessage.DeleteBlocked -> R.string.template_delete_blocked_body
    }
}
