package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC, id ASC")
    abstract fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC, id ASC")
    abstract fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE archived = 1 ORDER BY name COLLATE NOCASE ASC, id ASC")
    abstract fun observeArchived(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise_muscles")
    abstract fun observeMuscles(): Flow<List<ExerciseMuscleEntity>>

    @Query("SELECT COUNT(*) FROM exercises WHERE archived = 0")
    abstract fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM exercises WHERE archived = 1")
    abstract fun observeArchivedCount(): Flow<Int>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC, id ASC")
    abstract suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise_muscles")
    abstract suspend fun getAllMuscles(): List<ExerciseMuscleEntity>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercise_muscles WHERE exerciseId = :exerciseId")
    abstract suspend fun getMuscles(exerciseId: Long): List<ExerciseMuscleEntity>

    @Query(
        "SELECT id FROM exercises WHERE normalizedName = :normalizedName AND id != :exceptId LIMIT 1"
    )
    abstract suspend fun findIdByNormalizedName(normalizedName: String, exceptId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: ExerciseEntity): Long

    @Update
    abstract suspend fun update(entity: ExerciseEntity)

    @Query("DELETE FROM exercises WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM exercise_muscles WHERE exerciseId = :exerciseId")
    abstract suspend fun deleteMuscles(exerciseId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMuscles(muscles: List<ExerciseMuscleEntity>)

    @Transaction
    open suspend fun saveWithMuscles(entity: ExerciseEntity, muscles: List<ExerciseMuscleEntity>): Long {
        val id = if (entity.id == 0L) {
            insert(entity)
        } else {
            update(entity)
            entity.id
        }
        deleteMuscles(id)
        if (muscles.isNotEmpty()) {
            insertMuscles(muscles.map { it.copy(exerciseId = id) })
        }
        return id
    }
}
