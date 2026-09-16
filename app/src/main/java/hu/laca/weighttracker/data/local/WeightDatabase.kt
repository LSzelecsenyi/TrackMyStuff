package hu.laca.weighttracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WeightMeasurementEntity::class,
        ExerciseEntity::class,
        ExerciseMuscleEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutTemplateExerciseEntity::class,
        WorkoutTemplateSetEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSessionExerciseEntity::class,
        WorkoutSessionExerciseMuscleEntity::class,
        WorkoutSessionSetEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class WeightDatabase : RoomDatabase() {
    abstract fun weightMeasurementDao(): WeightMeasurementDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        fun create(context: Context): WeightDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WeightDatabase::class.java,
                "weight_tracker.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }
    }
}
