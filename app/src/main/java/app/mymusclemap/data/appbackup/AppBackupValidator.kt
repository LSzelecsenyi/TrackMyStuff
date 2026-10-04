package app.mymusclemap.data.appbackup

import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.MuscleRole
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import java.time.DayOfWeek
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeParseException

object AppBackupValidator {
    private val BODY_CODE = Regex("""[A-Z][A-Z0-9_]{0,63}""")

    fun validate(snapshot: AppBackupSnapshot): List<AppBackupError> {
        val errors = mutableListOf<AppBackupError>()
        val tables = snapshot.tables
        requireUnique(tables.weightMeasurements.map { it.id }, "weight_measurements.id", errors)
        requireUnique(tables.weightMeasurements.map { it.date }, "weight_measurements.date", errors)
        requireUnique(tables.bodyMeasurements.map { it.id }, "body_measurements.id", errors)
        requireUnique(
            tables.bodyMeasurements.map { it.type to it.date },
            "body_measurements.type_date",
            errors
        )
        requireUnique(
            tables.bodyMeasurements.mapNotNull { row ->
                row.externalId?.let { row.source to it }
            },
            "body_measurements.source_externalId",
            errors
        )
        requireUnique(tables.weeklyWorkoutGoals.map { it.id }, "weekly_workout_goals.id", errors)
        requireUnique(
            tables.weeklyWorkoutGoals.map { it.effectiveWeekStart },
            "weekly_workout_goals.effectiveWeekStart",
            errors
        )
        tables.weeklyWorkoutGoals.forEach { row ->
            validateWeeklyGoal(row, errors)
        }
        requireUnique(tables.exercises.map { it.id }, "exercises.id", errors)
        requireUnique(tables.exercises.map { it.normalizedName }, "exercises.normalizedName", errors)
        requireUnique(
            tables.exerciseMuscles.map { it.exerciseId to it.muscleGroup },
            "exercise_muscles",
            errors
        )
        requireUnique(tables.workoutTemplates.map { it.id }, "workout_templates.id", errors)
        requireUnique(
            tables.workoutTemplates.map { it.normalizedName },
            "workout_templates.normalizedName",
            errors
        )
        requireUnique(tables.workoutTemplateExercises.map { it.id }, "workout_template_exercises.id", errors)
        requireUnique(
            tables.workoutTemplateExercises.map { it.templateId to it.position },
            "workout_template_exercises.position",
            errors
        )
        requireUnique(tables.workoutTemplateSets.map { it.id }, "workout_template_sets.id", errors)
        requireUnique(
            tables.workoutTemplateSets.map { it.templateExerciseId to it.position },
            "workout_template_sets.position",
            errors
        )
        requireUnique(tables.scheduledWorkouts.map { it.id }, "scheduled_workouts.id", errors)
        requireUnique(
            tables.scheduledWorkouts
                .filter { it.cancelledAt == null && it.templateId != null }
                .map { it.scheduledDate to it.templateId },
            "scheduled_workouts.date_template",
            errors
        )
        requireUnique(tables.workoutSessions.map { it.id }, "workout_sessions.id", errors)
        requireUnique(tables.workoutSessions.map { it.clientWorkoutId }, "workout_sessions.clientWorkoutId", errors)
        requireUnique(
            tables.workoutSessions.mapNotNull { it.importFingerprint },
            "workout_sessions.importFingerprint",
            errors
        )
        requireUnique(
            tables.workoutSessions.mapNotNull { it.scheduledWorkoutId },
            "workout_sessions.scheduledWorkoutId",
            errors
        )
        requireUnique(tables.workoutSessionExercises.map { it.id }, "workout_session_exercises.id", errors)
        requireUnique(
            tables.workoutSessionExercises.map { it.sessionId to it.position },
            "workout_session_exercises.position",
            errors
        )
        requireUnique(
            tables.workoutSessionExerciseMuscles.map { it.sessionExerciseId to it.muscleGroup },
            "workout_session_exercise_muscles",
            errors
        )
        requireUnique(tables.workoutSessionSets.map { it.id }, "workout_session_sets.id", errors)
        requireUnique(
            tables.workoutSessionSets.map { it.sessionExerciseId to it.position },
            "workout_session_sets.position",
            errors
        )

        tables.weightMeasurements.forEach { requireIsoDate(it.date, "weight_measurements.date", errors) }
        tables.bodyMeasurements.forEach { row ->
            if (!row.type.matches(BODY_CODE)) {
                errors += AppBackupError(AppBackupErrorCode.InvalidValue, "body_measurements.type")
            }
            requireIsoDate(row.date, "body_measurements.date", errors)
            if (!row.value.isFinite()) {
                errors += AppBackupError(AppBackupErrorCode.InvalidValue, "body_measurements.value")
            }
            if (!row.source.matches(BODY_CODE)) {
                errors += AppBackupError(AppBackupErrorCode.InvalidValue, "body_measurements.source")
            }
            if (row.externalId != null && row.externalId.isBlank()) {
                errors += AppBackupError(AppBackupErrorCode.InvalidValue, "body_measurements.externalId")
            }
        }
        tables.scheduledWorkouts.forEach {
            requireIsoDate(it.scheduledDate, "scheduled_workouts.scheduledDate", errors)
            requireIsoDate(it.originalScheduledDate, "scheduled_workouts.originalScheduledDate", errors)
            if (it.templateName.isBlank()) {
                errors += AppBackupError(AppBackupErrorCode.InvalidValue, "scheduled_workouts.templateName")
            }
        }
        tables.workoutSessions.forEach {
            requireIsoDate(it.workoutDate, "workout_sessions.workoutDate", errors)
            it.bodyWeightSourceDate?.let { date ->
                requireIsoDate(date, "workout_sessions.bodyWeightSourceDate", errors)
            }
        }

        tables.exercises.forEach { exercise ->
            requireEnum(exercise.category, ExerciseCategory.entries, "exercises.category", errors)
            requireEnum(exercise.movementPattern, MovementPattern.entries, "exercises.movementPattern", errors)
            requireEnum(exercise.measurementType, MeasurementType.entries, "exercises.measurementType", errors)
            requireEnum(exercise.resistanceBasis, ResistanceBasis.entries, "exercises.resistanceBasis", errors)
            requireEnum(
                exercise.weightInterpretation,
                WeightInterpretation.entries,
                "exercises.weightInterpretation",
                errors
            )
        }
        tables.exerciseMuscles.forEach { muscle ->
            requireEnum(muscle.muscleGroup, MuscleGroup.entries, "exercise_muscles.muscleGroup", errors)
            requireEnum(muscle.role, MuscleRole.entries, "exercise_muscles.role", errors)
        }
        tables.workoutTemplateSets.forEach { set ->
            requireEnum(set.loadKind, PlannedLoadKind.entries, "workout_template_sets.loadKind", errors)
        }
        tables.workoutSessions.forEach { session ->
            requireEnum(session.status, SessionStatus.entries, "workout_sessions.status", errors)
            requireEnum(
                session.bodyWeightSource,
                BodyWeightSource.entries,
                "workout_sessions.bodyWeightSource",
                errors
            )
        }
        tables.workoutSessionExercises.forEach { exercise ->
            requireEnum(exercise.category, ExerciseCategory.entries, "workout_session_exercises.category", errors)
            requireEnum(
                exercise.movementPattern,
                MovementPattern.entries,
                "workout_session_exercises.movementPattern",
                errors
            )
            requireEnum(
                exercise.measurementType,
                MeasurementType.entries,
                "workout_session_exercises.measurementType",
                errors
            )
            requireEnum(
                exercise.resistanceBasis,
                ResistanceBasis.entries,
                "workout_session_exercises.resistanceBasis",
                errors
            )
            requireEnum(
                exercise.weightInterpretation,
                WeightInterpretation.entries,
                "workout_session_exercises.weightInterpretation",
                errors
            )
            requireEnum(
                exercise.primaryMuscle,
                MuscleGroup.entries,
                "workout_session_exercises.primaryMuscle",
                errors
            )
        }
        tables.workoutSessionExerciseMuscles.forEach { muscle ->
            requireEnum(
                muscle.muscleGroup,
                MuscleGroup.entries,
                "workout_session_exercise_muscles.muscleGroup",
                errors
            )
            requireEnum(muscle.role, MuscleRole.entries, "workout_session_exercise_muscles.role", errors)
        }
        tables.workoutSessionSets.forEach { set ->
            requireEnum(set.plannedLoadKind, PlannedLoadKind.entries, "workout_session_sets.plannedLoadKind", errors)
            set.actualLoadKind?.let {
                requireEnum(it, PlannedLoadKind.entries, "workout_session_sets.actualLoadKind", errors)
            }
            requireEnum(set.status, SessionSetStatus.entries, "workout_session_sets.status", errors)
        }

        val exerciseIds = tables.exercises.map { it.id }.toSet()
        val templateIds = tables.workoutTemplates.map { it.id }.toSet()
        val templateExerciseIds = tables.workoutTemplateExercises.map { it.id }.toSet()
        val scheduledIds = tables.scheduledWorkouts.map { it.id }.toSet()
        val sessionIds = tables.workoutSessions.map { it.id }.toSet()
        val sessionExerciseIds = tables.workoutSessionExercises.map { it.id }.toSet()

        tables.exerciseMuscles.forEach { muscle ->
            if (muscle.exerciseId !in exerciseIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "exercise_muscles.exerciseId")
            }
        }
        tables.workoutTemplateExercises.forEach { row ->
            if (row.templateId !in templateIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "workout_template_exercises.templateId")
            }
            if (row.exerciseId !in exerciseIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "workout_template_exercises.exerciseId")
            }
        }
        tables.workoutTemplateSets.forEach { row ->
            if (row.templateExerciseId !in templateExerciseIds) {
                errors += AppBackupError(
                    AppBackupErrorCode.MissingRelation,
                    "workout_template_sets.templateExerciseId"
                )
            }
        }
        tables.scheduledWorkouts.forEach { row ->
            val templateId = row.templateId
            if (templateId != null && templateId !in templateIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "scheduled_workouts.templateId")
            }
        }
        tables.workoutSessions.forEach { session ->
            val templateId = session.templateId
            if (templateId != null && templateId !in templateIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "workout_sessions.templateId")
            }
            val scheduledId = session.scheduledWorkoutId
            if (scheduledId != null && scheduledId !in scheduledIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "workout_sessions.scheduledWorkoutId")
            }
            val inProgress = session.status == SessionStatus.IN_PROGRESS.name
            val locked = session.activeLock == 1
            if (inProgress != locked || (session.activeLock != null && session.activeLock != 1)) {
                errors += AppBackupError(AppBackupErrorCode.InvalidValue, "workout_sessions.activeLock")
            }
        }
        val activeLocks = tables.workoutSessions.count { it.activeLock == 1 }
        if (activeLocks > 1) {
            errors += AppBackupError(AppBackupErrorCode.DuplicateKey, "workout_sessions.activeLock")
        }
        tables.workoutSessionExercises.forEach { row ->
            if (row.sessionId !in sessionIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "workout_session_exercises.sessionId")
            }
            if (row.exerciseId !in exerciseIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "workout_session_exercises.exerciseId")
            }
        }
        tables.workoutSessionExerciseMuscles.forEach { row ->
            if (row.sessionExerciseId !in sessionExerciseIds) {
                errors += AppBackupError(
                    AppBackupErrorCode.MissingRelation,
                    "workout_session_exercise_muscles.sessionExerciseId"
                )
            }
        }
        tables.workoutSessionSets.forEach { row ->
            if (row.sessionExerciseId !in sessionExerciseIds) {
                errors += AppBackupError(AppBackupErrorCode.MissingRelation, "workout_session_sets.sessionExerciseId")
            }
        }
        if (tables.replacesProgressPhotos) {
            requireUnique(tables.progressPhotos.map { it.id }, "progress_photos.id", errors)
            requireUnique(tables.progressPhotos.map { it.fileName }, "progress_photos.fileName", errors)
            if (tables.progressPhotos.size > AppBackupFormat.MAX_PROGRESS_PHOTOS) {
                errors += AppBackupError(AppBackupErrorCode.FileTooLarge, "progress_photos")
            }
            tables.progressPhotos.forEach { row ->
                requireIsoDate(row.date, "progress_photos.date", errors)
                if (!ProgressPhotoStore.isPortableFileName(row.fileName)) {
                    errors += AppBackupError(AppBackupErrorCode.InvalidValue, "progress_photos.fileName")
                }
                if (row.createdAt < 0 || row.updatedAt < 0) {
                    errors += AppBackupError(AppBackupErrorCode.InvalidValue, "progress_photos.timestamp")
                }
            }
        }
        return errors.distinct()
    }

    private fun validateWeeklyGoal(row: WeeklyWorkoutGoalEntity, errors: MutableList<AppBackupError>) {
        val date = runCatching { LocalDate.parse(row.effectiveWeekStart) }.getOrNull()
        if (date == null || date.dayOfWeek != DayOfWeek.MONDAY) {
            errors += AppBackupError(AppBackupErrorCode.InvalidValue, "weekly_workout_goals.effectiveWeekStart")
        }
        val workouts = row.workoutsPerWeek
        if (workouts != null && workouts !in WeeklyGoalLogic.MIN_GOAL..WeeklyGoalLogic.MAX_GOAL) {
            errors += AppBackupError(AppBackupErrorCode.InvalidValue, "weekly_workout_goals.workoutsPerWeek")
        }
        if (workouts == null && row.graceWeek) {
            errors += AppBackupError(AppBackupErrorCode.InvalidValue, "weekly_workout_goals.graceWeek")
        }
    }

    private fun requireIsoDate(value: String, field: String, errors: MutableList<AppBackupError>) {
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            errors += AppBackupError(AppBackupErrorCode.InvalidValue, field)
        } catch (_: DateTimeException) {
            errors += AppBackupError(AppBackupErrorCode.InvalidValue, field)
        }
    }

    private fun <E : Enum<E>> requireEnum(
        value: String,
        entries: List<E>,
        field: String,
        errors: MutableList<AppBackupError>
    ) {
        if (entries.none { it.name == value }) {
            errors += AppBackupError(AppBackupErrorCode.InvalidValue, field)
        }
    }

    private fun <T> requireUnique(values: List<T>, field: String, errors: MutableList<AppBackupError>) {
        if (values.size != values.toSet().size) {
            errors += AppBackupError(AppBackupErrorCode.DuplicateKey, field)
        }
    }
}
