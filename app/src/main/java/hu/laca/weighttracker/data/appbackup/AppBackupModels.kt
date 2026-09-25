package hu.laca.weighttracker.data.appbackup

import hu.laca.weighttracker.data.local.ExerciseEntity
import hu.laca.weighttracker.data.local.ExerciseMuscleEntity
import hu.laca.weighttracker.data.local.ScheduledWorkoutEntity
import hu.laca.weighttracker.data.local.WeightMeasurementEntity
import hu.laca.weighttracker.data.local.WorkoutSessionEntity
import hu.laca.weighttracker.data.local.WorkoutSessionExerciseEntity
import hu.laca.weighttracker.data.local.WorkoutSessionExerciseMuscleEntity
import hu.laca.weighttracker.data.local.WorkoutSessionSetEntity
import hu.laca.weighttracker.data.local.WorkoutTemplateEntity
import hu.laca.weighttracker.data.local.WorkoutTemplateExerciseEntity
import hu.laca.weighttracker.data.local.WorkoutTemplateSetEntity
import java.time.Instant

object AppBackupFormat {
    const val FORMAT = "my-muscle-map-backup"
    const val FORMAT_VERSION = 1
    const val SCHEMA_VERSION = 6
    const val MAX_UTF8_BYTES = 16 * 1024 * 1024

    const val TABLE_WEIGHT_MEASUREMENTS = "weight_measurements"
    const val TABLE_EXERCISES = "exercises"
    const val TABLE_EXERCISE_MUSCLES = "exercise_muscles"
    const val TABLE_WORKOUT_TEMPLATES = "workout_templates"
    const val TABLE_WORKOUT_TEMPLATE_EXERCISES = "workout_template_exercises"
    const val TABLE_WORKOUT_TEMPLATE_SETS = "workout_template_sets"
    const val TABLE_SCHEDULED_WORKOUTS = "scheduled_workouts"
    const val TABLE_WORKOUT_SESSIONS = "workout_sessions"
    const val TABLE_WORKOUT_SESSION_EXERCISES = "workout_session_exercises"
    const val TABLE_WORKOUT_SESSION_EXERCISE_MUSCLES = "workout_session_exercise_muscles"
    const val TABLE_WORKOUT_SESSION_SETS = "workout_session_sets"

    val TABLE_NAMES: List<String> = listOf(
        TABLE_WEIGHT_MEASUREMENTS,
        TABLE_EXERCISES,
        TABLE_EXERCISE_MUSCLES,
        TABLE_WORKOUT_TEMPLATES,
        TABLE_WORKOUT_TEMPLATE_EXERCISES,
        TABLE_WORKOUT_TEMPLATE_SETS,
        TABLE_SCHEDULED_WORKOUTS,
        TABLE_WORKOUT_SESSIONS,
        TABLE_WORKOUT_SESSION_EXERCISES,
        TABLE_WORKOUT_SESSION_EXERCISE_MUSCLES,
        TABLE_WORKOUT_SESSION_SETS
    )
}

data class AppBackupSource(
    val applicationId: String,
    val versionName: String
)

data class AppBackupTables(
    val weightMeasurements: List<WeightMeasurementEntity>,
    val exercises: List<ExerciseEntity>,
    val exerciseMuscles: List<ExerciseMuscleEntity>,
    val workoutTemplates: List<WorkoutTemplateEntity>,
    val workoutTemplateExercises: List<WorkoutTemplateExerciseEntity>,
    val workoutTemplateSets: List<WorkoutTemplateSetEntity>,
    val scheduledWorkouts: List<ScheduledWorkoutEntity>,
    val workoutSessions: List<WorkoutSessionEntity>,
    val workoutSessionExercises: List<WorkoutSessionExerciseEntity>,
    val workoutSessionExerciseMuscles: List<WorkoutSessionExerciseMuscleEntity>,
    val workoutSessionSets: List<WorkoutSessionSetEntity>
)

data class AppBackupSnapshot(
    val formatVersion: Int,
    val schemaVersion: Int,
    val exportedAt: Instant,
    val source: AppBackupSource?,
    val tables: AppBackupTables,
    val settings: Map<String, String>
)

data class AppBackupError(
    val code: AppBackupErrorCode,
    val detail: String? = null
)

enum class AppBackupErrorCode {
    EmptyFile,
    FileTooLarge,
    InvalidJson,
    InvalidFormat,
    UnsupportedFormatVersion,
    UnsupportedSchemaVersion,
    MissingField,
    InvalidType,
    InvalidValue,
    DuplicateKey,
    MissingRelation
}

sealed class AppBackupParseResult {
    data class Success(val snapshot: AppBackupSnapshot) : AppBackupParseResult()
    data class Failure(val errors: List<AppBackupError>) : AppBackupParseResult()
}

sealed class AppBackupRestoreResult {
    data object Success : AppBackupRestoreResult()
    data class Invalid(val errors: List<AppBackupError>) : AppBackupRestoreResult()
}
