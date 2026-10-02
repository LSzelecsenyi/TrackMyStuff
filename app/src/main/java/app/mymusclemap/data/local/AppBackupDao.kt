package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.mymusclemap.data.appbackup.AppBackupTables

@Dao
abstract class AppBackupDao {
    @Query("SELECT * FROM weight_measurements ORDER BY id ASC")
    abstract suspend fun getWeightMeasurements(): List<WeightMeasurementEntity>

    @Query("SELECT * FROM body_measurements ORDER BY id ASC")
    abstract suspend fun getBodyMeasurements(): List<BodyMeasurementEntity>

    @Query("SELECT * FROM exercises ORDER BY id ASC")
    abstract suspend fun getExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise_muscles ORDER BY exerciseId ASC, muscleGroup ASC")
    abstract suspend fun getExerciseMuscles(): List<ExerciseMuscleEntity>

    @Query("SELECT * FROM workout_templates ORDER BY id ASC")
    abstract suspend fun getWorkoutTemplates(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_template_exercises ORDER BY id ASC")
    abstract suspend fun getWorkoutTemplateExercises(): List<WorkoutTemplateExerciseEntity>

    @Query("SELECT * FROM workout_template_sets ORDER BY id ASC")
    abstract suspend fun getWorkoutTemplateSets(): List<WorkoutTemplateSetEntity>

    @Query("SELECT * FROM scheduled_workouts ORDER BY id ASC")
    abstract suspend fun getScheduledWorkouts(): List<ScheduledWorkoutEntity>

    @Query("SELECT * FROM workout_sessions ORDER BY id ASC")
    abstract suspend fun getWorkoutSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_session_exercises ORDER BY id ASC")
    abstract suspend fun getWorkoutSessionExercises(): List<WorkoutSessionExerciseEntity>

    @Query("SELECT * FROM workout_session_exercise_muscles ORDER BY sessionExerciseId ASC, muscleGroup ASC")
    abstract suspend fun getWorkoutSessionExerciseMuscles(): List<WorkoutSessionExerciseMuscleEntity>

    @Query("SELECT * FROM workout_session_sets ORDER BY id ASC")
    abstract suspend fun getWorkoutSessionSets(): List<WorkoutSessionSetEntity>

    @Query("DELETE FROM workout_session_sets")
    abstract suspend fun deleteWorkoutSessionSets()

    @Query("DELETE FROM workout_session_exercise_muscles")
    abstract suspend fun deleteWorkoutSessionExerciseMuscles()

    @Query("DELETE FROM workout_session_exercises")
    abstract suspend fun deleteWorkoutSessionExercises()

    @Query("DELETE FROM workout_sessions")
    abstract suspend fun deleteWorkoutSessions()

    @Query("DELETE FROM scheduled_workouts")
    abstract suspend fun deleteScheduledWorkouts()

    @Query("DELETE FROM workout_template_sets")
    abstract suspend fun deleteWorkoutTemplateSets()

    @Query("DELETE FROM workout_template_exercises")
    abstract suspend fun deleteWorkoutTemplateExercises()

    @Query("DELETE FROM workout_templates")
    abstract suspend fun deleteWorkoutTemplates()

    @Query("DELETE FROM exercise_muscles")
    abstract suspend fun deleteExerciseMuscles()

    @Query("DELETE FROM exercises")
    abstract suspend fun deleteExercises()

    @Query("DELETE FROM weight_measurements")
    abstract suspend fun deleteWeightMeasurements()

    @Query("DELETE FROM body_measurements")
    abstract suspend fun deleteBodyMeasurements()

    @Query("SELECT * FROM weekly_workout_goals ORDER BY effectiveWeekStart ASC, id ASC")
    abstract suspend fun getWeeklyWorkoutGoals(): List<WeeklyWorkoutGoalEntity>

    @Query("DELETE FROM weekly_workout_goals")
    abstract suspend fun deleteWeeklyWorkoutGoals()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWeightMeasurements(rows: List<WeightMeasurementEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertBodyMeasurements(rows: List<BodyMeasurementEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertExerciseMuscles(rows: List<ExerciseMuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWorkoutTemplates(rows: List<WorkoutTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWorkoutTemplateExercises(rows: List<WorkoutTemplateExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWorkoutTemplateSets(rows: List<WorkoutTemplateSetEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertScheduledWorkouts(rows: List<ScheduledWorkoutEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWorkoutSessions(rows: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWorkoutSessionExercises(rows: List<WorkoutSessionExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWorkoutSessionExerciseMuscles(rows: List<WorkoutSessionExerciseMuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWorkoutSessionSets(rows: List<WorkoutSessionSetEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWeeklyWorkoutGoals(rows: List<WeeklyWorkoutGoalEntity>)

    @Query("SELECT * FROM progress_photos ORDER BY id ASC")
    abstract suspend fun getProgressPhotos(): List<ProgressPhotoEntity>

    @Query("DELETE FROM progress_photos")
    abstract suspend fun deleteProgressPhotos()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertProgressPhotos(rows: List<ProgressPhotoEntity>)

    @Transaction
    open suspend fun loadTables(): AppBackupTables {
        return AppBackupTables(
            weightMeasurements = getWeightMeasurements(),
            exercises = getExercises(),
            exerciseMuscles = getExerciseMuscles(),
            workoutTemplates = getWorkoutTemplates(),
            workoutTemplateExercises = getWorkoutTemplateExercises(),
            workoutTemplateSets = getWorkoutTemplateSets(),
            scheduledWorkouts = getScheduledWorkouts(),
            workoutSessions = getWorkoutSessions(),
            workoutSessionExercises = getWorkoutSessionExercises(),
            workoutSessionExerciseMuscles = getWorkoutSessionExerciseMuscles(),
            workoutSessionSets = getWorkoutSessionSets(),
            bodyMeasurements = getBodyMeasurements(),
            weeklyWorkoutGoals = getWeeklyWorkoutGoals(),
            progressPhotos = getProgressPhotos()
        )
    }

    @Transaction
    open suspend fun replaceAll(tables: AppBackupTables) {
        deleteWorkoutSessionSets()
        deleteWorkoutSessionExerciseMuscles()
        deleteWorkoutSessionExercises()
        deleteWorkoutSessions()
        deleteScheduledWorkouts()
        deleteWorkoutTemplateSets()
        deleteWorkoutTemplateExercises()
        deleteWorkoutTemplates()
        deleteExerciseMuscles()
        deleteExercises()
        deleteBodyMeasurements()
        deleteWeeklyWorkoutGoals()
        deleteWeightMeasurements()
        if (tables.weightMeasurements.isNotEmpty()) {
            insertWeightMeasurements(tables.weightMeasurements)
        }
        if (tables.bodyMeasurements.isNotEmpty()) {
            insertBodyMeasurements(tables.bodyMeasurements)
        }
        if (tables.exercises.isNotEmpty()) {
            insertExercises(tables.exercises)
        }
        if (tables.exerciseMuscles.isNotEmpty()) {
            insertExerciseMuscles(tables.exerciseMuscles)
        }
        if (tables.workoutTemplates.isNotEmpty()) {
            insertWorkoutTemplates(tables.workoutTemplates)
        }
        if (tables.workoutTemplateExercises.isNotEmpty()) {
            insertWorkoutTemplateExercises(tables.workoutTemplateExercises)
        }
        if (tables.workoutTemplateSets.isNotEmpty()) {
            insertWorkoutTemplateSets(tables.workoutTemplateSets)
        }
        if (tables.scheduledWorkouts.isNotEmpty()) {
            insertScheduledWorkouts(tables.scheduledWorkouts)
        }
        if (tables.workoutSessions.isNotEmpty()) {
            insertWorkoutSessions(tables.workoutSessions)
        }
        if (tables.workoutSessionExercises.isNotEmpty()) {
            insertWorkoutSessionExercises(tables.workoutSessionExercises)
        }
        if (tables.workoutSessionExerciseMuscles.isNotEmpty()) {
            insertWorkoutSessionExerciseMuscles(tables.workoutSessionExerciseMuscles)
        }
        if (tables.workoutSessionSets.isNotEmpty()) {
            insertWorkoutSessionSets(tables.workoutSessionSets)
        }
        if (tables.weeklyWorkoutGoals.isNotEmpty()) {
            insertWeeklyWorkoutGoals(tables.weeklyWorkoutGoals)
        }
        if (tables.replacesProgressPhotos) {
            deleteProgressPhotos()
            if (tables.progressPhotos.isNotEmpty()) {
                insertProgressPhotos(tables.progressPhotos)
            }
        }
    }
}
