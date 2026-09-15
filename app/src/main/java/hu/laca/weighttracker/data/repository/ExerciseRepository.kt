package hu.laca.weighttracker.data.repository

import android.database.sqlite.SQLiteConstraintException
import hu.laca.weighttracker.data.local.ExerciseDao
import hu.laca.weighttracker.data.local.ExerciseEntity
import hu.laca.weighttracker.data.local.ExerciseMuscleEntity
import hu.laca.weighttracker.data.local.WorkoutSessionDao
import hu.laca.weighttracker.data.local.WorkoutTemplateDao
import hu.laca.weighttracker.data.local.toModel
import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseDeleteResult
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseDraftLogic
import hu.laca.weighttracker.domain.exercise.ExerciseNaming
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MuscleRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Clock

class ExerciseRepository(
    private val dao: ExerciseDao,
    private val clock: Clock,
    private val templateDao: WorkoutTemplateDao? = null,
    private val sessionDao: WorkoutSessionDao? = null
) {
    fun observeAll(): Flow<List<Exercise>> {
        return combine(dao.observeAll(), dao.observeMuscles()) { exercises, muscles ->
            val grouped = muscles.groupBy { it.exerciseId }
            exercises.map { entity -> entity.toModel(grouped[entity.id].orEmpty()) }
        }
    }

    fun observeActive(): Flow<List<Exercise>> {
        return combine(dao.observeActive(), dao.observeMuscles()) { exercises, muscles ->
            val grouped = muscles.groupBy { it.exerciseId }
            exercises.map { entity -> entity.toModel(grouped[entity.id].orEmpty()) }
        }
    }

    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    fun observeArchivedCount(): Flow<Int> = dao.observeArchivedCount()

    suspend fun getById(id: Long): Exercise? {
        val entity = dao.getById(id) ?: return null
        return entity.toModel(dao.getMuscles(id))
    }

    suspend fun save(draft: ExerciseDraft): ExerciseSaveResult {
        val prepared = draft.copy(
            secondaryMuscles = ExerciseDraftLogic.normalizeSecondary(
                draft.primaryMuscle,
                draft.secondaryMuscles
            ),
            weightInterpretation = ExerciseDraftLogic.resolvedWeightInterpretation(
                draft.measurementType,
                draft.resistanceBasis,
                draft.weightInterpretation
            )
        )
        val errors = ExerciseDraftLogic.validate(prepared)
        if (errors.isNotEmpty()) {
            return ExerciseSaveResult.Invalid(errors)
        }
        val name = ExerciseNaming.displayName(prepared.name)
        val normalized = ExerciseNaming.normalize(name)
        val exceptId = prepared.id ?: 0L
        if (dao.findIdByNormalizedName(normalized, exceptId) != null) {
            return ExerciseSaveResult.DuplicateName
        }
        val now = clock.millis()
        val notes = prepared.notes.trim().ifBlank { null }
        val secondary = prepared.secondaryMuscles
        val weight = prepared.weightInterpretation
        val existing = prepared.id?.let { dao.getById(it) }
        if (prepared.id != null && existing == null) {
            return ExerciseSaveResult.NotFound
        }
        val entity = ExerciseEntity(
            id = existing?.id ?: 0L,
            name = name,
            normalizedName = normalized,
            category = prepared.category.name,
            movementPattern = prepared.movementPattern.name,
            measurementType = prepared.measurementType.name,
            resistanceBasis = prepared.resistanceBasis.name,
            weightInterpretation = weight.name,
            notes = notes,
            archived = existing?.archived ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        val muscles = buildList {
            add(
                ExerciseMuscleEntity(
                    exerciseId = entity.id,
                    muscleGroup = prepared.primaryMuscle.name,
                    role = MuscleRole.PRIMARY.name
                )
            )
            secondary.forEach { group ->
                add(
                    ExerciseMuscleEntity(
                        exerciseId = entity.id,
                        muscleGroup = group.name,
                        role = MuscleRole.SECONDARY.name
                    )
                )
            }
        }
        return try {
            val id = dao.saveWithMuscles(entity, muscles)
            if (existing == null) {
                ExerciseSaveResult.Created(id)
            } else {
                ExerciseSaveResult.Updated(id)
            }
        } catch (error: Exception) {
            if (isUniqueConstraint(error)) {
                ExerciseSaveResult.DuplicateName
            } else {
                throw error
            }
        }
    }

    private fun isUniqueConstraint(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SQLiteConstraintException) {
                return true
            }
            val message = current.message.orEmpty()
            if (message.contains("UNIQUE", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    suspend fun archive(id: Long): Boolean {
        val existing = dao.getById(id) ?: return false
        dao.update(existing.copy(archived = true, updatedAt = clock.millis()))
        return true
    }

    suspend fun restore(id: Long): Boolean {
        val existing = dao.getById(id) ?: return false
        dao.update(existing.copy(archived = false, updatedAt = clock.millis()))
        return true
    }

    suspend fun canDeletePermanently(exerciseId: Long): Boolean {
        if (dao.getById(exerciseId) == null) {
            return false
        }
        return !hasWorkoutReferences(exerciseId)
    }

    suspend fun deletePermanently(id: Long): ExerciseDeleteResult {
        if (dao.getById(id) == null) {
            return ExerciseDeleteResult.NotFound
        }
        if (!canDeletePermanently(id)) {
            return ExerciseDeleteResult.BlockedByReferences
        }
        dao.deleteById(id)
        return ExerciseDeleteResult.Deleted
    }

    fun observeReferencedExerciseIds(): Flow<Set<Long>> {
        val templates = templateDao?.observeReferencedExerciseIds() ?: MutableStateFlow(emptyList())
        val sessions = sessionDao?.observeReferencedExerciseIds() ?: MutableStateFlow(emptyList())
        return combine(templates, sessions) { templateIds, sessionIds ->
            (templateIds + sessionIds).toSet()
        }
    }

    private suspend fun hasWorkoutReferences(exerciseId: Long): Boolean {
        val templateRefs = templateDao?.countReferences(exerciseId) ?: 0
        val sessionRefs = sessionDao?.countExerciseReferences(exerciseId) ?: 0
        return templateRefs + sessionRefs > 0
    }
}
