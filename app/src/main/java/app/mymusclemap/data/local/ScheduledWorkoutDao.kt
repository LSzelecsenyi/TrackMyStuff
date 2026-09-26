package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SCHEDULED_WORKOUT_SELECT = """
        SELECT
            sw.id AS id,
            sw.scheduledDate AS scheduledDate,
            sw.originalScheduledDate AS originalScheduledDate,
            sw.templateId AS templateId,
            sw.createdAt AS createdAt,
            sw.cancelledAt AS cancelledAt,
            COALESCE(t.name, sw.templateName) AS templateName,
            t.archived AS templateArchived,
            (
                SELECT COUNT(*) FROM workout_template_exercises e
                WHERE e.templateId = sw.templateId
            ) AS exerciseCount,
            (
                SELECT COUNT(*) FROM workout_template_sets s
                INNER JOIN workout_template_exercises e ON e.id = s.templateExerciseId
                WHERE e.templateId = sw.templateId
            ) AS plannedSetCount,
            sess.id AS sessionId,
            sess.status AS sessionStatus
        FROM scheduled_workouts sw
        LEFT JOIN workout_templates t ON t.id = sw.templateId
        LEFT JOIN workout_sessions sess ON sess.scheduledWorkoutId = sw.id
        """

private const val ACTIVE_OCCURRENCE = " sw.cancelledAt IS NULL "

data class ScheduledWorkoutQueryRow(
    val id: Long,
    val scheduledDate: String,
    val originalScheduledDate: String,
    val templateId: Long?,
    val createdAt: Long,
    val cancelledAt: Long?,
    val templateName: String,
    val templateArchived: Boolean?,
    val exerciseCount: Int,
    val plannedSetCount: Int,
    val sessionId: Long?,
    val sessionStatus: String?
)

@Dao
interface ScheduledWorkoutDao {
    @Query(SCHEDULED_WORKOUT_SELECT + " WHERE sw.id = :id AND" + ACTIVE_OCCURRENCE + " LIMIT 1")
    fun observeById(id: Long): Flow<ScheduledWorkoutQueryRow?>

    @Query(SCHEDULED_WORKOUT_SELECT + " WHERE sw.id = :id AND" + ACTIVE_OCCURRENCE + " LIMIT 1")
    suspend fun getById(id: Long): ScheduledWorkoutQueryRow?

    @Query("SELECT * FROM scheduled_workouts WHERE id = :id LIMIT 1")
    suspend fun getEntity(id: Long): ScheduledWorkoutEntity?

    @Query(
        """
        SELECT * FROM scheduled_workouts
        WHERE scheduledDate = :scheduledDate
          AND templateId = :templateId
          AND cancelledAt IS NULL
        LIMIT 1
        """
    )
    suspend fun findActiveByDateAndTemplate(scheduledDate: String, templateId: Long): ScheduledWorkoutEntity?

    @Query(
        SCHEDULED_WORKOUT_SELECT + """
        WHERE sw.scheduledDate = :date AND""" + ACTIVE_OCCURRENCE + """
        ORDER BY sw.scheduledDate ASC, sw.createdAt ASC, sw.id ASC
        """
    )
    fun observeOnDate(date: String): Flow<List<ScheduledWorkoutQueryRow>>

    @Query(
        SCHEDULED_WORKOUT_SELECT + """
        WHERE sw.scheduledDate BETWEEN :startInclusive AND :endInclusive AND""" + ACTIVE_OCCURRENCE + """
        ORDER BY sw.scheduledDate ASC, sw.createdAt ASC, sw.id ASC
        """
    )
    fun observeBetween(startInclusive: String, endInclusive: String): Flow<List<ScheduledWorkoutQueryRow>>

    @Query(
        SCHEDULED_WORKOUT_SELECT + """
        WHERE sw.scheduledDate >= :fromInclusive AND""" + ACTIVE_OCCURRENCE + """
        ORDER BY sw.scheduledDate ASC, sw.createdAt ASC, sw.id ASC
        """
    )
    fun observeUpcoming(fromInclusive: String): Flow<List<ScheduledWorkoutQueryRow>>

    @Query(
        SCHEDULED_WORKOUT_SELECT + """
        WHERE""" + ACTIVE_OCCURRENCE + """
        ORDER BY sw.scheduledDate ASC, sw.createdAt ASC, sw.id ASC
        """
    )
    fun observeAll(): Flow<List<ScheduledWorkoutQueryRow>>

    @Query(
        """
        SELECT COUNT(*) FROM scheduled_workouts
        WHERE templateId = :templateId AND cancelledAt IS NULL
        """
    )
    suspend fun countActiveByTemplate(templateId: Long): Int

    @Query(
        """
        SELECT DISTINCT templateId FROM scheduled_workouts
        WHERE cancelledAt IS NULL AND templateId IS NOT NULL
        """
    )
    fun observeTemplateIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ScheduledWorkoutEntity): Long

    @Update
    suspend fun update(entity: ScheduledWorkoutEntity)

    @Query(
        """
        UPDATE scheduled_workouts
        SET cancelledAt = :cancelledAt
        WHERE templateId = :templateId
          AND cancelledAt IS NULL
          AND scheduledDate >= :fromDate
          AND id NOT IN (
              SELECT scheduledWorkoutId FROM workout_sessions
              WHERE scheduledWorkoutId IS NOT NULL
          )
        """
    )
    suspend fun cancelUnlinkedFrom(templateId: Long, fromDate: String, cancelledAt: Long): Int

    @Query("DELETE FROM scheduled_workouts WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
