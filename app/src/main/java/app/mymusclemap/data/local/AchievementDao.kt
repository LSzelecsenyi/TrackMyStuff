package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AchievementDao {
    @Query("SELECT * FROM unlocked_achievements")
    abstract fun observeUnlocks(): Flow<List<UnlockedAchievementEntity>>

    @Query("SELECT * FROM unlocked_achievements")
    abstract suspend fun unlocks(): List<UnlockedAchievementEntity>

    @Query("SELECT * FROM progress_events ORDER BY id ASC")
    abstract fun observeEvents(): Flow<List<ProgressEventEntity>>

    @Query("SELECT * FROM progress_events ORDER BY id ASC")
    abstract suspend fun events(): List<ProgressEventEntity>

    @Query("SELECT * FROM achievement_state WHERE id = :id")
    abstract suspend fun state(id: Int = AchievementStateEntity.SINGLETON_ID): AchievementStateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertUnlocks(rows: List<UnlockedAchievementEntity>)

    @Query("DELETE FROM unlocked_achievements WHERE achievementId IN (:ids)")
    abstract suspend fun deleteUnlocks(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEvents(rows: List<ProgressEventEntity>)

    @Query("DELETE FROM progress_events WHERE dedupeKey IN (:keys)")
    abstract suspend fun deleteEvents(keys: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertState(row: AchievementStateEntity)

    @Query(
        """
        UPDATE unlocked_achievements
        SET celebratedAt = :celebratedAt
        WHERE achievementId IN (:ids) AND celebratedAt IS NULL
        """
    )
    abstract suspend fun acknowledgeUnlocks(ids: List<String>, celebratedAt: Long)

    @Query(
        """
        UPDATE progress_events
        SET celebratedAt = :celebratedAt
        WHERE dedupeKey IN (:keys) AND celebratedAt IS NULL
        """
    )
    abstract suspend fun acknowledgeEvents(keys: List<String>, celebratedAt: Long)
}
