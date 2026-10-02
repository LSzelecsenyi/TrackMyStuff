package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WeeklyWorkoutGoalDao {
    @Query("SELECT * FROM weekly_workout_goals ORDER BY effectiveWeekStart ASC, id ASC")
    abstract fun observeAll(): Flow<List<WeeklyWorkoutGoalEntity>>

    @Query("SELECT * FROM weekly_workout_goals ORDER BY effectiveWeekStart ASC, id ASC")
    abstract suspend fun getAll(): List<WeeklyWorkoutGoalEntity>

    @Query("DELETE FROM weekly_workout_goals")
    abstract suspend fun deleteAll()

    @Insert
    abstract suspend fun insertAll(rows: List<WeeklyWorkoutGoalEntity>)

    @Transaction
    open suspend fun replaceAll(rows: List<WeeklyWorkoutGoalEntity>) {
        deleteAll()
        if (rows.isNotEmpty()) {
            insertAll(rows)
        }
    }
}
