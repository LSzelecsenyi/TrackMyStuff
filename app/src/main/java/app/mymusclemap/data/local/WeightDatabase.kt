package app.mymusclemap.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WeightMeasurementEntity::class,
        BodyMeasurementEntity::class,
        ExerciseEntity::class,
        ExerciseMuscleEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutTemplateExerciseEntity::class,
        WorkoutTemplateSetEntity::class,
        ScheduledWorkoutEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSessionExerciseEntity::class,
        WorkoutSessionExerciseMuscleEntity::class,
        WorkoutSessionSetEntity::class,
        ProgressPhotoEntity::class,
        WeeklyWorkoutGoalEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class WeightDatabase : RoomDatabase() {
    abstract fun weightMeasurementDao(): WeightMeasurementDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun scheduledWorkoutDao(): ScheduledWorkoutDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun appBackupDao(): AppBackupDao
    abstract fun progressPhotoDao(): ProgressPhotoDao
    abstract fun weeklyWorkoutGoalDao(): WeeklyWorkoutGoalDao

    companion object {
        fun create(context: Context): WeightDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WeightDatabase::class.java,
                "weight_tracker.db"
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13
                )
                .build()
        }
    }
}
