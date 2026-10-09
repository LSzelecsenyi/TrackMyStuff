package app.mymusclemap.data.appbackup

import app.mymusclemap.data.local.AchievementStateEntity
import app.mymusclemap.data.local.BodyMeasurementEntity
import app.mymusclemap.data.local.ExerciseEntity
import app.mymusclemap.data.local.ExerciseMuscleEntity
import app.mymusclemap.data.local.ProgressEventEntity
import app.mymusclemap.data.local.ProgressPhotoEntity
import app.mymusclemap.data.local.ScheduledWorkoutEntity
import app.mymusclemap.data.local.TargetWeightGoalEntity
import app.mymusclemap.data.local.UnlockedAchievementEntity
import app.mymusclemap.data.local.WeightMeasurementEntity
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseMuscleEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.data.local.WorkoutTemplateEntity
import app.mymusclemap.data.local.WorkoutTemplateExerciseEntity
import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.data.local.WorkoutTemplateSetEntity
import java.time.Instant

/**
 * ZIP backup of the open account database. The archive has no account id and no
 * entitlement grants. Restore replaces that signed-in account only, after the
 * existing destructive-replace warning. A legacy archive is accepted the same way.
 */
object AppBackupFormat {
    const val FORMAT = "my-muscle-map-backup"
    const val FORMAT_VERSION = 1
    /** Structured JSON still written by the legacy exporter and accepted from old files. */
    const val SCHEMA_VERSION = 8
    /** First archive schema that replaces the progress-photo library. */
    const val ARCHIVE_PHOTO_SCHEMA_VERSION = 9
    /** Archive schema that first replaces achievement rows. */
    const val ARCHIVE_ACHIEVEMENT_SCHEMA_VERSION = 10
    /** Structured JSON stored inside a photo archive. Not accepted as a loose JSON file. */
    const val ARCHIVE_DATA_SCHEMA_VERSION = 11
    const val CONTAINER_VERSION = 1
    const val MIN_SUPPORTED_SCHEMA_VERSION = 6
    const val MAX_UTF8_BYTES = 16 * 1024 * 1024
    const val MAX_ARCHIVE_ENTRIES = 2_050
    const val MAX_PROGRESS_PHOTOS = 2_000
    const val MAX_PROGRESS_PHOTO_BYTES = 12 * 1024 * 1024
    const val MAX_PROGRESS_PHOTO_BYTES_TOTAL = 512L * 1024 * 1024
    const val MAX_PROGRESS_PHOTO_EDGE = 8_000

    const val ARCHIVE_MANIFEST = "manifest.json"
    const val ARCHIVE_DATA = "data.json"
    const val ARCHIVE_PHOTO_DIRECTORY = "progress_photos/"

    const val TABLE_WEIGHT_MEASUREMENTS = "weight_measurements"
    const val TABLE_BODY_MEASUREMENTS = "body_measurements"
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
    const val TABLE_WEEKLY_WORKOUT_GOALS = "weekly_workout_goals"
    const val TABLE_PROGRESS_PHOTOS = "progress_photos"
    const val TABLE_UNLOCKED_ACHIEVEMENTS = "unlocked_achievements"
    const val TABLE_PROGRESS_EVENTS = "progress_events"
    const val TABLE_ACHIEVEMENT_STATE = "achievement_state"
    const val TABLE_TARGET_WEIGHT_GOALS = "target_weight_goals"

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
    val workoutSessionSets: List<WorkoutSessionSetEntity>,
    val bodyMeasurements: List<BodyMeasurementEntity> = emptyList(),
    val weeklyWorkoutGoals: List<WeeklyWorkoutGoalEntity> = emptyList(),
    val progressPhotos: List<ProgressPhotoEntity> = emptyList(),
    val replacesProgressPhotos: Boolean = false,
    val unlockedAchievements: List<UnlockedAchievementEntity> = emptyList(),
    val progressEvents: List<ProgressEventEntity> = emptyList(),
    val achievementState: List<AchievementStateEntity> = emptyList(),
    val replacesAchievements: Boolean = false,
    val targetWeightGoals: List<TargetWeightGoalEntity> = emptyList(),
    val replacesTargetWeightGoals: Boolean = false
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
    MissingRelation,
    UnsupportedContainerVersion,
    CorruptBackup,
    IncompleteBackup,
    InvalidPhoto,
    RestoreFailed
}

sealed class AppBackupWriteResult {
    data class Written(val skippedMissingPhotos: Int) : AppBackupWriteResult()
    data object Failed : AppBackupWriteResult()
}

sealed class AppBackupParseResult {
    data class Success(val snapshot: AppBackupSnapshot) : AppBackupParseResult()
    data class Failure(val errors: List<AppBackupError>) : AppBackupParseResult()
}

sealed class AppBackupRestoreResult {
    data object Success : AppBackupRestoreResult()
    data class Invalid(val errors: List<AppBackupError>) : AppBackupRestoreResult()
}
