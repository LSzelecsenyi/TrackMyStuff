package app.mymusclemap.data.local

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

    @Query("SELECT COUNT(*) FROM workout_templates")
    abstract suspend fun countAll(): Int

    @Query("SELECT MIN(createdAt) FROM workout_templates")
    abstract suspend fun earliestCreatedAt(): Long?

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

    @Query(
        """
        SELECT DISTINCT t.id AS id, t.name AS name, t.archived AS archived
        FROM workout_templates AS t
        INNER JOIN workout_template_exercises AS e ON e.templateId = t.id
        WHERE e.exerciseId = :exerciseId
        ORDER BY t.name COLLATE NOCASE, t.id
        """
    )
    abstract suspend fun plansReferencingExercise(exerciseId: Long): List<ExercisePlanReference>

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

    @Query("UPDATE workout_template_exercises SET position = -1 - position WHERE templateId = :templateId")
    abstract suspend fun parkExercisePositions(templateId: Long)

    @Query(
        "UPDATE workout_template_sets SET position = -1 - position WHERE templateExerciseId = :templateExerciseId"
    )
    abstract suspend fun parkSetPositions(templateExerciseId: Long)

    @Query(
        """
        UPDATE workout_template_exercises
        SET exerciseId = :exerciseId, position = :position, notes = :notes, restSeconds = :restSeconds
        WHERE id = :id AND templateId = :templateId
        """
    )
    abstract suspend fun updateExerciseFields(
        id: Long,
        templateId: Long,
        exerciseId: Long,
        position: Int,
        notes: String?,
        restSeconds: Int?
    ): Int

    @Query(
        """
        UPDATE workout_template_sets
        SET position = :position,
            minReps = :minReps,
            maxReps = :maxReps,
            loadKind = :loadKind,
            weightKg = :weightKg,
            durationSeconds = :durationSeconds,
            distanceMeters = :distanceMeters,
            restSeconds = :restSeconds,
            timedWork = :timedWork
        WHERE id = :id AND templateExerciseId = :templateExerciseId
        """
    )
    abstract suspend fun updateSetFields(
        id: Long,
        templateExerciseId: Long,
        position: Int,
        minReps: Int?,
        maxReps: Int?,
        loadKind: String,
        weightKg: Double?,
        durationSeconds: Int?,
        distanceMeters: Double?,
        restSeconds: Int?,
        timedWork: Boolean
    ): Int

    @Query("DELETE FROM workout_template_exercises WHERE id = :id")
    abstract suspend fun deleteExercise(id: Long)

    @Query("DELETE FROM workout_template_sets WHERE id = :id")
    abstract suspend fun deleteSet(id: Long)

    /**
     * Inserts a new plan, or reconciles an existing plan without reassigning
     * retained exercise and set ids. Positions are parked first so the unique
     * (parent, position) indexes survive reordering inside this transaction.
     * A non-null [expectedUpdatedAt] that no longer matches the stored plan
     * aborts before any write.
     */
    @Transaction
    open suspend fun saveAggregate(
        template: WorkoutTemplateEntity,
        exercises: List<Pair<WorkoutTemplateExerciseEntity, List<WorkoutTemplateSetEntity>>>,
        expectedUpdatedAt: Long? = null
    ): TemplateAggregateWrite {
        if (template.id == 0L) {
            val id = insertTemplate(template)
            insertChildren(id, exercises)
            return TemplateAggregateWrite.Saved(id)
        }
        val current = getById(template.id) ?: return TemplateAggregateWrite.NotFound
        if (expectedUpdatedAt != null && current.updatedAt != expectedUpdatedAt) {
            return TemplateAggregateWrite.Stale
        }
        val persistedExercises = getExercises(template.id)
        val persistedById = persistedExercises.associateBy { it.id }
        val claimedExerciseIds = exercises.map { it.first.id }.filter { it > 0L }
        if (claimedExerciseIds.size != claimedExerciseIds.toSet().size ||
            claimedExerciseIds.any { it !in persistedById }
        ) {
            return TemplateAggregateWrite.Stale
        }
        val persistedSets = persistedExercises.associate { exercise ->
            exercise.id to getSets(exercise.id).associateBy { it.id }
        }
        for ((exercise, sets) in exercises) {
            val claimedSetIds = sets.map { it.id }.filter { it > 0L }
            if (claimedSetIds.size != claimedSetIds.toSet().size) {
                return TemplateAggregateWrite.Stale
            }
            if (exercise.id <= 0L) {
                if (claimedSetIds.isNotEmpty()) {
                    return TemplateAggregateWrite.Stale
                }
                continue
            }
            val owned = persistedSets.getValue(exercise.id)
            if (claimedSetIds.any { it !in owned }) {
                return TemplateAggregateWrite.Stale
            }
        }
        updateTemplate(template)
        parkExercisePositions(template.id)
        persistedExercises.forEach { parkSetPositions(it.id) }
        exercises.forEachIndexed { exercisePosition, (exercise, sets) ->
            if (exercise.id <= 0L) {
                return@forEachIndexed
            }
            val keptSetIds = sets.map { it.id }.filter { it > 0L }.toSet()
            persistedSets.getValue(exercise.id).keys
                .filter { it !in keptSetIds }
                .forEach { deleteSet(it) }
            sets.forEachIndexed { setPosition, set ->
                if (set.id > 0L) {
                    requireOne(
                        updateSetFields(
                            id = set.id,
                            templateExerciseId = exercise.id,
                            position = setPosition,
                            minReps = set.minReps,
                            maxReps = set.maxReps,
                            loadKind = set.loadKind,
                            weightKg = set.weightKg,
                            durationSeconds = set.durationSeconds,
                            distanceMeters = set.distanceMeters,
                            restSeconds = set.restSeconds,
                            timedWork = set.timedWork
                        )
                    )
                } else {
                    insertSet(
                        set.copy(
                            id = 0L,
                            templateExerciseId = exercise.id,
                            position = setPosition
                        )
                    )
                }
            }
            requireOne(
                updateExerciseFields(
                    id = exercise.id,
                    templateId = template.id,
                    exerciseId = exercise.exerciseId,
                    position = exercisePosition,
                    notes = exercise.notes,
                    restSeconds = exercise.restSeconds
                )
            )
        }
        val keptExerciseIds = claimedExerciseIds.toSet()
        persistedExercises
            .filter { it.id !in keptExerciseIds }
            .forEach { deleteExercise(it.id) }
        val fresh = exercises.mapIndexedNotNull { index, child ->
            if (child.first.id <= 0L) index to child else null
        }
        fresh.forEach { (exercisePosition, child) ->
            val exerciseId = insertExercise(
                child.first.copy(id = 0L, templateId = template.id, position = exercisePosition)
            )
            child.second.forEachIndexed { setPosition, set ->
                insertSet(set.copy(id = 0L, templateExerciseId = exerciseId, position = setPosition))
            }
        }
        return TemplateAggregateWrite.Saved(template.id)
    }

    private suspend fun insertChildren(
        templateId: Long,
        exercises: List<Pair<WorkoutTemplateExerciseEntity, List<WorkoutTemplateSetEntity>>>
    ) {
        exercises.forEachIndexed { exercisePosition, (exercise, sets) ->
            val exerciseId = insertExercise(
                exercise.copy(id = 0L, templateId = templateId, position = exercisePosition)
            )
            sets.forEachIndexed { setPosition, set ->
                insertSet(set.copy(id = 0L, templateExerciseId = exerciseId, position = setPosition))
            }
        }
    }

    private fun requireOne(updated: Int) {
        if (updated != 1) {
            error("Plan row was not updated")
        }
    }
}

data class ExercisePlanReference(
    val id: Long,
    val name: String,
    val archived: Boolean
)

sealed class TemplateAggregateWrite {
    data class Saved(val id: Long) : TemplateAggregateWrite()
    data object NotFound : TemplateAggregateWrite()
    data object Stale : TemplateAggregateWrite()
}
