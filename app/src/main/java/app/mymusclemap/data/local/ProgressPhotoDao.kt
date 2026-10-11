package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressPhotoDao {
    @Query("SELECT * FROM progress_photos ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photos ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<ProgressPhotoEntity>

    @Query("SELECT * FROM progress_photos WHERE id = :id")
    suspend fun getById(id: Long): ProgressPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProgressPhotoEntity): Long

    @Query(
        """
        UPDATE progress_photos
        SET sourceUri = :sourceUri, sourceFingerprint = :sourceFingerprint, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateSource(id: Long, sourceUri: String?, sourceFingerprint: String?, updatedAt: Long)

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
