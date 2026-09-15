package hu.laca.weighttracker.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [WeightMeasurementEntity::class],
    version = 1,
    exportSchema = true
)
abstract class WeightDatabase : RoomDatabase() {
    abstract fun weightMeasurementDao(): WeightMeasurementDao

    companion object {
        fun create(context: Context): WeightDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WeightDatabase::class.java,
                "weight_tracker.db"
            ).build()
        }
    }
}
