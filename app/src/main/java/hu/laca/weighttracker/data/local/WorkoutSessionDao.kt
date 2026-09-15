package hu.laca.weighttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE workoutDate BETWEEN :startInclusive AND :endInclusive
        ORDER BY workoutDate DESC, startedAt DESC, id DESC
        """
    )
    abstract fun observeBetween(startInclusive: String, endInclusive: String): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT workoutDate AS date, COUNT(*) AS completedCount
        FROM workout_sessions
        WHERE status = 'COMPLETED' AND workoutDate BETWEEN :startInclusive AND :endInclusive
        GROUP BY workoutDate
        """
    )
    abstract fun observeCompletedCountsBetween(
        startInclusive: String,
        endInclusive: String
    ): Flow<List<WorkoutDateCount>>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE workoutDate = :date
        ORDER BY startedAt DESC, id DESC
        """
    )
    abstract fun observeByWorkoutDate(date: String): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'COMPLETED'
        ORDER BY finishedAt DESC, startedAt DESC, id DESC
        LIMIT 1
        """
    )
    abstract fun observeLatestCompleted(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS'")
    abstract fun observeInProgress(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    abstract fun observeById(id: Long): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    abstract suspend fun getInProgress(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session_exercises WHERE sessionId = :sessionId ORDER BY position ASC, id ASC")
    abstract fun observeExercises(sessionId: Long): Flow<List<WorkoutSessionExerciseEntity>>

    @Query("SELECT * FROM workout_session_exercises WHERE sessionId = :sessionId ORDER BY position ASC, id ASC")
    abstract suspend fun getExercises(sessionId: Long): List<WorkoutSessionExerciseEntity>

    @Query("SELECT * FROM workout_session_exercises ORDER BY sessionId ASC, position ASC, id ASC")
    abstract fun observeAllExercises(): Flow<List<WorkoutSessionExerciseEntity>>

    @Query("SELECT * FROM workout_session_sets ORDER BY sessionExerciseId ASC, position ASC, id ASC")
    abstract fun observeAllSets(): Flow<List<WorkoutSessionSetEntity>>

    @Query("SELECT * FROM workout_session_exercise_muscles")
    abstract fun observeAllMuscles(): Flow<List<WorkoutSessionExerciseMuscleEntity>>

    @Query("SELECT * FROM workout_session_sets WHERE sessionExerciseId = :sessionExerciseId ORDER BY position ASC, id ASC")
    abstract suspend fun getSets(sessionExerciseId: Long): List<WorkoutSessionSetEntity>

    @Query("SELECT * FROM workout_session_sets WHERE id = :id LIMIT 1")
    abstract suspend fun getSet(id: Long): WorkoutSessionSetEntity?

    @Query("SELECT * FROM workout_session_exercises WHERE id = :id LIMIT 1")
    abstract suspend fun getExercise(id: Long): WorkoutSessionExerciseEntity?

    @Query("SELECT * FROM workout_session_exercise_muscles WHERE sessionExerciseId = :sessionExerciseId")
    abstract suspend fun getMuscles(sessionExerciseId: Long): List<WorkoutSessionExerciseMuscleEntity>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE templateId = :templateId")
    abstract suspend fun countTemplateReferences(templateId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_session_exercises WHERE exerciseId = :exerciseId")
    abstract suspend fun countExerciseReferences(exerciseId: Long): Int

    @Query("SELECT DISTINCT templateId FROM workout_sessions")
    abstract fun observeReferencedTemplateIds(): Flow<List<Long>>

    @Query("SELECT DISTINCT exerciseId FROM workout_session_exercises")
    abstract fun observeReferencedExerciseIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSession(entity: WorkoutSessionEntity): Long

    @Update
    abstract suspend fun updateSession(entity: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertExercise(entity: WorkoutSessionExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMuscles(entities: List<WorkoutSessionExerciseMuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSet(entity: WorkoutSessionSetEntity): Long

    @Update
    abstract suspend fun updateSet(entity: WorkoutSessionSetEntity)

    @Query("DELETE FROM workout_session_sets WHERE id = :id")
    abstract suspend fun deleteSet(id: Long)

    @Query("UPDATE workout_session_sets SET position = :position WHERE id = :id")
    abstract suspend fun updateSetPosition(id: Long, position: Int)

    @Transaction
    open suspend fun insertAggregate(
        session: WorkoutSessionEntity,
        exercises: List<Triple<WorkoutSessionExerciseEntity, List<WorkoutSessionExerciseMuscleEntity>, List<WorkoutSessionSetEntity>>>
    ): Long {
        val sessionId = insertSession(session)
        exercises.forEachIndexed { exercisePosition, (exercise, muscles, sets) ->
            val exerciseId = insertExercise(
                exercise.copy(id = 0L, sessionId = sessionId, position = exercisePosition)
            )
            if (muscles.isNotEmpty()) {
                insertMuscles(muscles.map { it.copy(sessionExerciseId = exerciseId) })
            }
            sets.forEachIndexed { setPosition, set ->
                insertSet(set.copy(id = 0L, sessionExerciseId = exerciseId, position = setPosition))
            }
        }
        return sessionId
    }
}
