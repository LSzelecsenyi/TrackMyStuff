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

    @Query("SELECT workoutDate AS date, templateName AS name FROM workout_sessions WHERE status = 'COMPLETED'")
    abstract suspend fun getCompletedDateNames(): List<ImportedWorkoutName>

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

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY workoutDate DESC, id DESC")
    abstract fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT e.* FROM workout_session_exercises e
        INNER JOIN workout_sessions s ON s.id = e.sessionId
        WHERE s.status = 'COMPLETED'
        ORDER BY e.sessionId ASC, e.position ASC, e.id ASC
        """
    )
    abstract fun observeCompletedExercises(): Flow<List<WorkoutSessionExerciseEntity>>

    @Query(
        """
        SELECT st.* FROM workout_session_sets st
        INNER JOIN workout_session_exercises e ON e.id = st.sessionExerciseId
        INNER JOIN workout_sessions s ON s.id = e.sessionId
        WHERE s.status = 'COMPLETED'
        ORDER BY st.sessionExerciseId ASC, st.position ASC, st.id ASC
        """
    )
    abstract fun observeCompletedSets(): Flow<List<WorkoutSessionSetEntity>>

    @Query(
        """
        SELECT m.* FROM workout_session_exercise_muscles m
        INNER JOIN workout_session_exercises e ON e.id = m.sessionExerciseId
        INNER JOIN workout_sessions s ON s.id = e.sessionId
        WHERE s.status = 'COMPLETED'
        """
    )
    abstract fun observeCompletedMuscles(): Flow<List<WorkoutSessionExerciseMuscleEntity>>

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

    @Query("SELECT DISTINCT templateId FROM workout_sessions WHERE templateId IS NOT NULL")
    abstract fun observeReferencedTemplateIds(): Flow<List<Long>>

    @Query("SELECT importFingerprint FROM workout_sessions WHERE importFingerprint IS NOT NULL")
    abstract suspend fun getImportFingerprints(): List<String>

    @Query(
        """
        SELECT importFingerprint FROM workout_sessions
        WHERE importFingerprint IN (:fingerprints)
        """
    )
    abstract suspend fun findExistingImportFingerprints(fingerprints: List<String>): List<String>

    @Query("SELECT DISTINCT exerciseId FROM workout_session_exercises")
    abstract fun observeReferencedExerciseIds(): Flow<List<Long>>

    @Query("SELECT * FROM scheduled_workouts WHERE id = :id LIMIT 1")
    abstract suspend fun getScheduledWorkout(id: Long): ScheduledWorkoutEntity?

    @Query("SELECT id FROM workout_sessions WHERE scheduledWorkoutId = :scheduledWorkoutId LIMIT 1")
    abstract suspend fun getSessionIdByScheduledWorkoutId(scheduledWorkoutId: Long): Long?

    @Query("SELECT * FROM workout_templates WHERE id = :id LIMIT 1")
    abstract suspend fun getTemplate(id: Long): WorkoutTemplateEntity?

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

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    abstract suspend fun deleteSessionById(id: Long): Int

    @Transaction
    open suspend fun deleteSessionAggregate(id: Long): Int {
        return deleteSessionById(id)
    }

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

    @Transaction
    open suspend fun insertImportedAggregates(
        sessions: List<Pair<WorkoutSessionEntity, List<Triple<WorkoutSessionExerciseEntity, List<WorkoutSessionExerciseMuscleEntity>, List<WorkoutSessionSetEntity>>>>>
    ): List<Long> {
        return sessions.map { (session, exercises) -> insertAggregate(session, exercises) }
    }

    @Transaction
    open suspend fun insertStartedSession(
        session: WorkoutSessionEntity,
        exercises: List<Triple<WorkoutSessionExerciseEntity, List<WorkoutSessionExerciseMuscleEntity>, List<WorkoutSessionSetEntity>>>,
        scheduledWorkoutId: Long?,
        expectedTemplateId: Long,
        todayIso: String
    ): InsertStartedSessionResult {
        if (getInProgress() != null) {
            return InsertStartedSessionResult.AlreadyActive
        }
        val template = getTemplate(expectedTemplateId) ?: return InsertStartedSessionResult.TemplateNotFound
        if (template.archived) {
            return InsertStartedSessionResult.TemplateArchived
        }
        if (scheduledWorkoutId != null) {
            val scheduled = getScheduledWorkout(scheduledWorkoutId)
                ?: return InsertStartedSessionResult.ScheduleNotFound
            if (scheduled.templateId != expectedTemplateId) {
                return InsertStartedSessionResult.ScheduleTemplateMismatch
            }
            if (scheduled.scheduledDate != todayIso) {
                return InsertStartedSessionResult.ScheduleNotOnToday
            }
            if (getSessionIdByScheduledWorkoutId(scheduledWorkoutId) != null) {
                return InsertStartedSessionResult.ScheduleAlreadyStarted
            }
        }
        val sessionId = insertAggregate(
            session.copy(scheduledWorkoutId = scheduledWorkoutId),
            exercises
        )
        return InsertStartedSessionResult.Inserted(sessionId)
    }
}

sealed class InsertStartedSessionResult {
    data class Inserted(val sessionId: Long) : InsertStartedSessionResult()
    data object AlreadyActive : InsertStartedSessionResult()
    data object TemplateNotFound : InsertStartedSessionResult()
    data object TemplateArchived : InsertStartedSessionResult()
    data object ScheduleNotFound : InsertStartedSessionResult()
    data object ScheduleTemplateMismatch : InsertStartedSessionResult()
    data object ScheduleAlreadyStarted : InsertStartedSessionResult()
    data object ScheduleNotOnToday : InsertStartedSessionResult()
}
