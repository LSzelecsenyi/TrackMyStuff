package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TargetWeightGoalDao {
    @Query("SELECT * FROM target_weight_goals WHERE retiredAt IS NULL ORDER BY id DESC LIMIT 1")
    abstract fun observeActive(): Flow<TargetWeightGoalEntity?>

    @Query("SELECT * FROM target_weight_goals WHERE retiredAt IS NULL ORDER BY id DESC LIMIT 1")
    abstract suspend fun active(): TargetWeightGoalEntity?

    @Insert
    abstract suspend fun insert(row: TargetWeightGoalEntity): Long

    @Query(
        """
        UPDATE target_weight_goals
        SET retiredAt = :retiredAt, updatedAt = :updatedAt
        WHERE id = :id AND retiredAt IS NULL
        """
    )
    abstract suspend fun retire(id: Long, retiredAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE target_weight_goals
        SET baselineKg = :baselineKg, direction = :direction, updatedAt = :updatedAt
        WHERE id = :id AND baselineKg IS NULL
        """
    )
    abstract suspend fun captureBaseline(
        id: Long,
        baselineKg: Double,
        direction: String,
        updatedAt: Long
    )
}
