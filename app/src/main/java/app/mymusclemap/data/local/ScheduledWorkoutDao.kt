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
            sw.templateId AS templateId,
            sw.createdAt AS createdAt,
            t.name AS templateName,
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
        INNER JOIN workout_templates t ON t.id = sw.templateId
        LEFT JOIN workout_sessions sess ON sess.scheduledWorkoutId = sw.id
        """

data class ScheduledWorkoutQueryRow(
    val id: Long,
    val scheduledDate: String,
    val templateId: Long,
    val createdAt: Long,
    val templateName: String,
    val templateArchived: Boolean,
    val exerciseCount: Int,
    val plannedSetCount: Int,
    val sessionId: Long?,
    val sessionStatus: String?
)

@Dao
interface ScheduledWorkoutDao {
    @Query(SCHEDULED_WORKOUT_SELECT + " WHERE sw.id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ScheduledWorkoutQueryRow?>

    @Query(SCHEDULED_WORKOUT_SELECT + " WHERE sw.id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduledWorkoutQueryRow?

    @Query("SELECT * FROM scheduled_workouts WHERE id = :id LIMIT 1")
    suspend fun getEntity(id: Long): ScheduledWorkoutEntity?

    @Query(
        """
        SELECT * FROM scheduled_workouts
        WHERE scheduledDate = :scheduledDate AND templateId = :templateId
        LIMIT 1
        """
    )
    suspend fun findByDateAndTemplate(scheduledDate: String, templateId: Long): ScheduledWorkoutEntity?

    @Query(
        SCHEDULED_WORKOUT_SELECT + """
        WHERE sw.scheduledDate = :date
        ORDER BY sw.scheduledDate ASC, sw.createdAt ASC, sw.id ASC
        """
    )
    fun observeOnDate(date: String): Flow<List<ScheduledWorkoutQueryRow>>

    @Query(
        SCHEDULED_WORKOUT_SELECT + """
        WHERE sw.scheduledDate BETWEEN :startInclusive AND :endInclusive
        ORDER BY sw.scheduledDate ASC, sw.createdAt ASC, sw.id ASC
        """
    )
    fun observeBetween(startInclusive: String, endInclusive: String): Flow<List<ScheduledWorkoutQueryRow>>

    @Query(
        SCHEDULED_WORKOUT_SELECT + """
        WHERE sw.scheduledDate >= :fromInclusive
        ORDER BY sw.scheduledDate ASC, sw.createdAt ASC, sw.id ASC
        """
    )
    fun observeUpcoming(fromInclusive: String): Flow<List<ScheduledWorkoutQueryRow>>

    @Query(
        SCHEDULED_WORKOUT_SELECT + """
        ORDER BY sw.scheduledDate ASC, sw.createdAt ASC, sw.id ASC
        """
    )
    fun observeAll(): Flow<List<ScheduledWorkoutQueryRow>>

    @Query("SELECT COUNT(*) FROM scheduled_workouts WHERE templateId = :templateId")
    suspend fun countByTemplate(templateId: Long): Int

    @Query("SELECT DISTINCT templateId FROM scheduled_workouts")
    fun observeTemplateIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ScheduledWorkoutEntity): Long

    @Update
    suspend fun update(entity: ScheduledWorkoutEntity)

    @Query("DELETE FROM scheduled_workouts WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}