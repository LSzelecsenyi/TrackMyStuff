package hu.laca.weighttracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WeightMeasurementEntity::class,
        ExerciseEntity::class,
        ExerciseMuscleEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class WeightDatabase : RoomDatabase() {
    abstract fun weightMeasurementDao(): WeightMeasurementDao
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        fun create(context: Context): WeightDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WeightDatabase::class.java,
                "weight_tracker.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
