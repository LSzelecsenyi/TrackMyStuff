package hu.laca.weighttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightMeasurementDao {
    @Query("SELECT * FROM weight_measurements ORDER BY date ASC, id ASC")
    fun observeAllAscending(): Flow<List<WeightMeasurementEntity>>

    @Query("SELECT * FROM weight_measurements WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): WeightMeasurementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WeightMeasurementEntity): Long

    @Update
    suspend fun update(entity: WeightMeasurementEntity)

    @Query("DELETE FROM weight_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)
}
