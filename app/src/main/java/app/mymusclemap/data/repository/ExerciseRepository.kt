package app.mymusclemap.data.repository

import android.database.sqlite.SQLiteConstraintException
import app.mymusclemap.data.local.ExerciseDao
import app.mymusclemap.data.local.ExerciseEntity
import app.mymusclemap.data.local.ExerciseMuscleEntity
import app.mymusclemap.data.local.WorkoutSessionDao
import app.mymusclemap.data.local.WorkoutTemplateDao
import app.mymusclemap.data.local.toModel
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseDeleteResult
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseDraftLogic
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MuscleRole
import app.mymusclemap.domain.exercise.StarterCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Clock

class ExerciseRepository(
    private val dao: ExerciseDao,
    private val clock: Clock,
    private val templateDao: WorkoutTemplateDao? = null,
    private val sessionDao: WorkoutSessionDao? = null,
    private val allowCustomCreate: suspend (activeCustomCount: Int) -> Boolean = { true },
    private val mayUpdate: suspend (Long) -> Boolean = { true },
    private val onCustomCreated: suspend (Long) -> Boolean = { true }
) {
    fun observeAll(): Flow<List<Exercise>> {
        return combine(dao.observeAll(), dao.observeMuscles()) { exercises, muscles ->
            val grouped = muscles.groupBy { it.exerciseId }
            exercises.map { entity -> entity.toModel(grouped[entity.id].orEmpty()) }
        }
    }

    suspend fun all(): List<Exercise> {
        val grouped = dao.getAllMuscles().groupBy { it.exerciseId }
        return dao.getAll().map { entity -> entity.toModel(grouped[entity.id].orEmpty()) }
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

    suspend fun activeCustomCount(): Int = dao.countActiveCustom()

    suspend fun save(
        draft: ExerciseDraft,
        asStarter: Boolean = false,
        catalogKey: String? = null
    ): ExerciseSaveResult {
        val officialKey = catalogKey?.takeIf { StarterCatalog.isOfficialKey(it) }
        if (catalogKey != null && officialKey == null) {
            return ExerciseSaveResult.SystemProtected
        }
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
        val now = clock.millis()
        val notes = prepared.notes.trim().ifBlank { null }
        val secondary = prepared.secondaryMuscles
        val weight = prepared.weightInterpretation
        val existing = prepared.id?.let { dao.getById(it) }
        if (prepared.id != null && existing == null) {
            return ExerciseSaveResult.NotFound
        }
        if (existing != null && existing.isVerifiedSystemRow()) {
            return ExerciseSaveResult.SystemProtected
        }
        if (officialKey != null) {
            if (existing != null) {
                return ExerciseSaveResult.SystemProtected
            }
            val already = dao.findByCatalogKey(officialKey)
            if (already != null) {
                return ExerciseSaveResult.Created(already.id)
            }
        } else if (dao.findIdByCustomNormalizedName(normalized, exceptId) != null) {
            return ExerciseSaveResult.DuplicateName
        }
        if (existing != null && existing.custom && !mayUpdate(existing.id)) {
            return ExerciseSaveResult.Locked
        }
        val creatingCustom = existing == null && officialKey == null && !asStarter
        if (creatingCustom && !allowCustomCreate(dao.countActiveCustom())) {
            return ExerciseSaveResult.CreationLimited
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
            updatedAt = now,
            custom = when {
                officialKey != null -> false
                existing != null -> existing.custom
                else -> !asStarter
            },
            catalogKey = officialKey,
            customNormalizedName = if (officialKey == null) normalized else null
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
                if (creatingCustom && !onCustomCreated(id)) {
                    dao.deleteById(id)
                    return ExerciseSaveResult.CreationLimited
                }
                ExerciseSaveResult.Created(id)
            } else {
                ExerciseSaveResult.Updated(id)
            }
        } catch (error: Exception) {
            if (isUniqueConstraint(error)) {
                if (officialKey != null) {
                    val winner = dao.findByCatalogKey(officialKey)
                    if (winner != null) {
                        return ExerciseSaveResult.Created(winner.id)
                    }
                }
                ExerciseSaveResult.DuplicateName
            } else {
                throw error
            }
        }
    }

    private fun ExerciseEntity.isVerifiedSystemRow(): Boolean {
        return catalogKey != null && StarterCatalog.isOfficialKey(catalogKey)
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
        if (existing.isVerifiedSystemRow()) return false
        if (existing.custom && !mayUpdate(id)) return false
        dao.update(existing.copy(archived = true, updatedAt = clock.millis()))
        return true
    }

    suspend fun restore(id: Long): Boolean {
        val existing = dao.getById(id) ?: return false
        if (existing.isVerifiedSystemRow()) return false
        if (existing.custom && !mayUpdate(id)) return false
        dao.update(existing.copy(archived = false, updatedAt = clock.millis()))
        return true
    }

    suspend fun canDeletePermanently(exerciseId: Long): Boolean {
        val existing = dao.getById(exerciseId) ?: return false
        if (existing.isVerifiedSystemRow()) {
            return false
        }
        return !hasWorkoutReferences(exerciseId)
    }

    suspend fun deletePermanently(id: Long): ExerciseDeleteResult {
        val existing = dao.getById(id) ?: return ExerciseDeleteResult.NotFound
        if (existing.isVerifiedSystemRow()) {
            return ExerciseDeleteResult.SystemProtected
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
