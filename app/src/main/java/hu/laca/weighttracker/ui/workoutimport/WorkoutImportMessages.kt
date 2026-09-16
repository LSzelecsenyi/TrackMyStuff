package hu.laca.weighttracker.ui.workoutimport

import android.content.res.Resources
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportError
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportErrorCode
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportWarning
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportWarningCode

object WorkoutImportMessages {
    fun error(resources: Resources, error: WorkoutImportError): String {
        val row = error.rowNumber ?: 0
        val name = displayName(error.incomingExerciseName)
        return when (error.code) {
            WorkoutImportErrorCode.EmptyFile -> resources.getString(R.string.workout_import_error_empty)
            WorkoutImportErrorCode.HeaderOnly -> resources.getString(R.string.workout_import_error_header_only)
            WorkoutImportErrorCode.InvalidUtf8 -> resources.getString(R.string.workout_import_error_utf8)
            WorkoutImportErrorCode.FileTooLarge -> resources.getString(R.string.workout_import_error_too_large)
            WorkoutImportErrorCode.TooManyRows -> resources.getString(R.string.workout_import_error_too_many_rows)
            WorkoutImportErrorCode.TooManyWorkouts -> resources.getString(R.string.workout_import_error_too_many_workouts)
            WorkoutImportErrorCode.TooManyExercises -> resources.getString(R.string.workout_import_error_too_many_exercises)
            WorkoutImportErrorCode.TooManySets -> resources.getString(R.string.workout_import_error_too_many_sets)
            WorkoutImportErrorCode.FieldTooLong -> resources.getString(R.string.workout_import_error_field_too_long, row)
            WorkoutImportErrorCode.MalformedQuote -> resources.getString(R.string.workout_import_error_malformed_quote, row)
            WorkoutImportErrorCode.MissingHeader -> resources.getString(R.string.workout_import_error_missing_header)
            WorkoutImportErrorCode.InvalidHeader -> resources.getString(R.string.workout_import_error_invalid_header)
            WorkoutImportErrorCode.DuplicateHeader -> resources.getString(R.string.workout_import_error_duplicate_header)
            WorkoutImportErrorCode.ExtraColumns -> resources.getString(R.string.workout_import_error_extra_columns, row)
            WorkoutImportErrorCode.MissingColumns -> resources.getString(R.string.workout_import_error_missing_columns, row)
            WorkoutImportErrorCode.UnsupportedVersion -> resources.getString(R.string.workout_import_error_unsupported_version)
            WorkoutImportErrorCode.MissingRequired -> resources.getString(R.string.workout_import_error_missing_required, row)
            WorkoutImportErrorCode.InvalidInteger -> resources.getString(R.string.workout_import_error_invalid_integer, row)
            WorkoutImportErrorCode.InvalidDecimal -> resources.getString(R.string.workout_import_error_invalid_decimal, row)
            WorkoutImportErrorCode.InvalidPrecision -> resources.getString(R.string.workout_import_error_invalid_precision, row)
            WorkoutImportErrorCode.OutOfRange -> resources.getString(R.string.workout_import_error_out_of_range, row)
            WorkoutImportErrorCode.InvalidDate -> resources.getString(R.string.workout_import_error_invalid_date, row)
            WorkoutImportErrorCode.FutureDate -> resources.getString(R.string.workout_import_error_future_date)
            WorkoutImportErrorCode.InvalidTimestamp -> resources.getString(R.string.workout_import_error_invalid_timestamp, row)
            WorkoutImportErrorCode.TimestampDateMismatch -> resources.getString(R.string.workout_import_error_timestamp_mismatch, row)
            WorkoutImportErrorCode.FinishBeforeStart -> resources.getString(R.string.workout_import_error_finish_before_start, row)
            WorkoutImportErrorCode.InvalidStatus -> resources.getString(R.string.workout_import_error_invalid_status, row)
            WorkoutImportErrorCode.InvalidLoadKind -> resources.getString(R.string.workout_import_error_invalid_load, row)
            WorkoutImportErrorCode.InvalidDistanceUnit -> resources.getString(R.string.workout_import_error_invalid_distance_unit, row)
            WorkoutImportErrorCode.DistanceUnitWithoutDistance ->
                resources.getString(R.string.workout_import_error_distance_unit_without, row)
            WorkoutImportErrorCode.MissingDistanceUnit ->
                resources.getString(R.string.workout_import_error_missing_distance_unit, row)
            WorkoutImportErrorCode.WeightIncompatibleWithLoad ->
                resources.getString(R.string.workout_import_error_weight_incompatible, row)
            WorkoutImportErrorCode.SkippedHasActuals ->
                resources.getString(R.string.workout_import_error_skipped_actuals, row)
            WorkoutImportErrorCode.CompletedMissingLoadKind ->
                resources.getString(R.string.workout_import_error_missing_load, row)
            WorkoutImportErrorCode.InconsistentWorkoutField ->
                resources.getString(R.string.workout_import_error_inconsistent_workout)
            WorkoutImportErrorCode.ConflictingExerciseName ->
                resources.getString(R.string.workout_import_error_conflicting_exercise)
            WorkoutImportErrorCode.NonContiguousExerciseIndex ->
                resources.getString(R.string.workout_import_error_exercise_index)
            WorkoutImportErrorCode.DuplicateSetIndex -> resources.getString(R.string.workout_import_error_duplicate_set)
            WorkoutImportErrorCode.NonContiguousSetIndex -> resources.getString(R.string.workout_import_error_set_index)
            WorkoutImportErrorCode.EmptyWorkout -> resources.getString(R.string.workout_import_error_empty_workout)
            WorkoutImportErrorCode.NoCompletedSets -> resources.getString(R.string.workout_import_error_no_completed)
            WorkoutImportErrorCode.DuplicateRecord -> resources.getString(R.string.workout_import_error_duplicate_record)
            WorkoutImportErrorCode.UnresolvedExercise ->
                resources.getString(R.string.workout_import_error_unresolved, name)
            WorkoutImportErrorCode.AmbiguousCatalogMatch ->
                resources.getString(R.string.workout_import_error_ambiguous, name)
            WorkoutImportErrorCode.StaleManualMapping ->
                resources.getString(R.string.workout_import_error_stale_mapping, name)
            WorkoutImportErrorCode.ConflictingManualMapping ->
                resources.getString(R.string.workout_import_error_conflicting_mapping, name)
            WorkoutImportErrorCode.IncompatibleMeasurement ->
                resources.getString(R.string.workout_import_error_incompatible_measurement, name)
            WorkoutImportErrorCode.MissingRequiredActual -> missingActual(resources, error, row, name)
            WorkoutImportErrorCode.ForbiddenActual ->
                resources.getString(R.string.workout_import_error_forbidden_actual, row)
            WorkoutImportErrorCode.IncompatibleLoadKind -> incompatibleLoad(resources, error, name)
            WorkoutImportErrorCode.MissingLoadWeight ->
                resources.getString(R.string.workout_import_error_missing_weight, row)
            WorkoutImportErrorCode.ForbiddenLoadWeight ->
                resources.getString(R.string.workout_import_error_forbidden_weight, row)
            WorkoutImportErrorCode.InvalidCatalogMuscle ->
                resources.getString(R.string.workout_import_error_invalid_muscle)
        }
    }

    fun warning(resources: Resources, warning: WorkoutImportWarning): String {
        val name = displayName(warning.incomingExerciseName)
        return when (warning.code) {
            WorkoutImportWarningCode.ArchivedExercise ->
                resources.getString(R.string.workout_import_warning_archived, name)
            WorkoutImportWarningCode.ManualAliasMapping ->
                resources.getString(R.string.workout_import_warning_manual, name)
            WorkoutImportWarningCode.MultipleIncomingNamesMapped ->
                resources.getString(R.string.workout_import_warning_multiple_aliases)
            WorkoutImportWarningCode.BodyWeightMismatch ->
                resources.getString(R.string.workout_import_warning_bw_mismatch)
            WorkoutImportWarningCode.PreviousMeasurementFallback ->
                resources.getString(R.string.workout_import_warning_bw_previous)
            WorkoutImportWarningCode.UnknownBodyWeight ->
                resources.getString(R.string.workout_import_warning_bw_unknown)
            WorkoutImportWarningCode.SameDateAndName ->
                resources.getString(R.string.workout_import_warning_same_date_name)
        }
    }

    fun bodyWeightSourceRes(source: BodyWeightSource): Int {
        return when (source) {
            BodyWeightSource.MANUAL -> R.string.workout_import_bw_file
            BodyWeightSource.MEASURED_SAME_DAY -> R.string.workout_import_bw_same_day
            BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT -> R.string.workout_import_bw_previous
            BodyWeightSource.UNKNOWN -> R.string.workout_import_bw_unavailable
        }
    }

    private fun missingActual(
        resources: Resources,
        error: WorkoutImportError,
        row: Int,
        name: String
    ): String {
        if (error.detail == MeasurementType.DISTANCE_AND_DURATION.name && name.isNotBlank()) {
            return resources.getString(R.string.workout_import_error_needs_distance_duration, name)
        }
        return when (error.field) {
            "reps" -> resources.getString(R.string.workout_import_error_missing_reps, row)
            "duration_seconds" -> resources.getString(R.string.workout_import_error_missing_duration, row)
            "distance" -> resources.getString(R.string.workout_import_error_missing_distance, row)
            else -> resources.getString(R.string.workout_import_error_generic)
        }
    }

    private fun incompatibleLoad(resources: Resources, error: WorkoutImportError, name: String): String {
        val load = error.detail
        return if (load == PlannedLoadKind.BODYWEIGHT_ONLY.name ||
            load == PlannedLoadKind.ADDED_WEIGHT.name ||
            load == PlannedLoadKind.ASSISTANCE.name
        ) {
            resources.getString(R.string.workout_import_error_incompatible_load, name)
        } else {
            resources.getString(R.string.workout_import_error_incompatible_load_generic, name)
        }
    }

    private fun displayName(value: String?): String {
        return value?.takeIf { it.isNotBlank() } ?: ""
    }
}
