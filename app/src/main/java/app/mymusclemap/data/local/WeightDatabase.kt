package app.mymusclemap.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
        WeeklyWorkoutGoalEntity::class,
        UnlockedAchievementEntity::class,
        ProgressEventEntity::class,
        AchievementStateEntity::class,
        TargetWeightGoalEntity::class,
        FreeAccessStateEntity::class,
        FreeAccessPlanEntity::class,
        FreeAccessExerciseEntity::class
    ],
    version = 21,
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
    abstract fun achievementDao(): AchievementDao
    abstract fun targetWeightGoalDao(): TargetWeightGoalDao
    abstract fun freeAccessDao(): FreeAccessDao

    companion object {
        fun create(
            context: Context,
            name: String = "weight_tracker.db",
            allowMainThreadQueries: Boolean = false
        ): WeightDatabase {
            val appContext = context.applicationContext
            val location = if (AccountDatabaseLocation.isAccountDatabase(name)) {
                AccountDatabaseMigrator(appContext).migrateNamed(name)
                val file = AccountDatabaseLocation.accountFile(appContext, name)
                file.parentFile?.mkdirs()
                if (!file.exists()) {
                    // SQLiteOpenHelper will not create a database whose absolute path is outside
                    // the databases directory on every runtime. Create the empty file first.
                    SQLiteDatabase.openOrCreateDatabase(file, null).close()
                }
                file.absolutePath
            } else {
                name
            }
            val builder = Room.databaseBuilder(appContext, WeightDatabase::class.java, location)
            if (allowMainThreadQueries) {
                builder.allowMainThreadQueries()
            }
            return builder
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
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21
                )
                .build()
        }
    }
}
