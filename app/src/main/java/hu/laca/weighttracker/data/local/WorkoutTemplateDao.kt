package hu.laca.weighttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutTemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY name COLLATE NOCASE ASC, id ASC")
    abstract fun observeAll(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC, id ASC")
    abstract fun observeActive(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE archived = 1 ORDER BY name COLLATE NOCASE ASC, id ASC")
    abstract fun observeArchived(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT COUNT(*) FROM workout_templates WHERE archived = 0")
    abstract fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM workout_templates WHERE archived = 1")
    abstract fun observeArchivedCount(): Flow<Int>

    @Query("SELECT * FROM workout_template_exercises ORDER BY templateId ASC, position ASC, id ASC")
    abstract fun observeExercises(): Flow<List<WorkoutTemplateExerciseEntity>>

    @Query("SELECT * FROM workout_template_sets ORDER BY templateExerciseId ASC, position ASC, id ASC")
    abstract fun observeSets(): Flow<List<WorkoutTemplateSetEntity>>

    @Query("SELECT * FROM workout_templates WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_template_exercises WHERE templateId = :templateId ORDER BY position ASC, id ASC")
    abstract suspend fun getExercises(templateId: Long): List<WorkoutTemplateExerciseEntity>

    @Query("SELECT * FROM workout_template_sets WHERE templateExerciseId = :templateExerciseId ORDER BY position ASC, id ASC")
    abstract suspend fun getSets(templateExerciseId: Long): List<WorkoutTemplateSetEntity>

    @Query("SELECT id FROM workout_templates WHERE normalizedName = :normalizedName AND id != :exceptId LIMIT 1")
    abstract suspend fun findIdByNormalizedName(normalizedName: String, exceptId: Long): Long?

    @Query("SELECT COUNT(*) FROM workout_template_exercises WHERE exerciseId = :exerciseId")
    abstract suspend fun countReferences(exerciseId: Long): Int

    @Query("SELECT DISTINCT exerciseId FROM workout_template_exercises")
    abstract fun observeReferencedExerciseIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTemplate(entity: WorkoutTemplateEntity): Long

    @Update
    abstract suspend fun updateTemplate(entity: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    abstract suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM workout_template_exercises WHERE templateId = :templateId")
    abstract suspend fun deleteExercises(templateId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertExercise(entity: WorkoutTemplateExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSet(entity: WorkoutTemplateSetEntity): Long

    /**
     * Replaces child exercise/set rows for a template in one transaction.
     * Child IDs are reassigned; the template id and createdAt stay stable.
     */
    @Transaction
    open suspend fun saveAggregate(
        template: WorkoutTemplateEntity,
        exercises: List<Pair<WorkoutTemplateExerciseEntity, List<WorkoutTemplateSetEntity>>>
    ): Long {
        val id = if (template.id == 0L) {
            insertTemplate(template)
        } else {
            updateTemplate(template)
            template.id
        }
        deleteExercises(id)
        exercises.forEachIndexed { exercisePosition, (exercise, sets) ->
            val exerciseId = insertExercise(
                exercise.copy(id = 0L, templateId = id, position = exercisePosition)
            )
            sets.forEachIndexed { setPosition, set ->
                insertSet(
                    set.copy(id = 0L, templateExerciseId = exerciseId, position = setPosition)
                )
            }
        }
        return id
    }
}
