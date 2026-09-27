package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {
    @Query("SELECT * FROM body_measurements ORDER BY date ASC, id ASC")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE type = :type ORDER BY date ASC, id ASC")
    fun observeByType(type: String): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements ORDER BY date ASC, id ASC")
    suspend fun getAll(): List<BodyMeasurementEntity>

    @Query("SELECT * FROM body_measurements WHERE type = :type ORDER BY date ASC, id ASC")
    suspend fun getByType(type: String): List<BodyMeasurementEntity>

    @Query(
        "SELECT * FROM body_measurements WHERE type = :type AND date = :date ORDER BY id DESC LIMIT 1"
    )
    suspend fun getByTypeAndDate(type: String, date: String): BodyMeasurementEntity?

    @Query(
        """
        SELECT * FROM body_measurements
        WHERE type = :type AND date <= :today
        ORDER BY date DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun latestOnOrBefore(type: String, today: String): BodyMeasurementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: BodyMeasurementEntity): Long

    @Update
    suspend fun update(entity: BodyMeasurementEntity)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)
}
