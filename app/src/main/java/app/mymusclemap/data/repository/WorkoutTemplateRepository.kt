package app.mymusclemap.data.repository

import android.database.sqlite.SQLiteConstraintException
import app.mymusclemap.data.local.ExerciseDao
import app.mymusclemap.data.local.ScheduledWorkoutDao
import app.mymusclemap.data.local.WorkoutSessionDao
import app.mymusclemap.data.local.WorkoutTemplateDao
import app.mymusclemap.data.local.WorkoutTemplateEntity
import app.mymusclemap.data.local.WorkoutTemplateExerciseEntity
import app.mymusclemap.data.local.WorkoutTemplateSetEntity
import app.mymusclemap.data.local.toModel
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.workout.PlannedSetLogic
import app.mymusclemap.domain.workout.TemplateDeleteResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateDraftLogic
import app.mymusclemap.domain.workout.TemplateListItem
import app.mymusclemap.domain.workout.TemplateNaming
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.domain.workout.WorkoutTemplateAggregate
import app.mymusclemap.domain.workout.WorkoutTemplateExerciseItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock

class WorkoutTemplateRepository(
    private val templateDao: WorkoutTemplateDao,
    private val exerciseDao: ExerciseDao,
    private val clock: Clock,
    private val sessionDao: WorkoutSessionDao? = null,
    private val scheduledWorkoutDao: ScheduledWorkoutDao? = null
) {
    fun observeActiveCount(): Flow<Int> = templateDao.observeActiveCount()

    fun observeArchivedCount(): Flow<Int> = templateDao.observeArchivedCount()

    fun observeReferencedExerciseIds(): Flow<Set<Long>> {
        return templateDao.observeReferencedExerciseIds().map { it.toSet() }
    }

    fun observeActive(): Flow<List<TemplateListItem>> {
        return observeItems(templateDao.observeActive())
    }

    fun observeArchived(): Flow<List<TemplateListItem>> {
        return observeItems(templateDao.observeArchived())
    }

    fun observeAll(): Flow<List<TemplateListItem>> {
        return observeItems(templateDao.observeAll())
    }

    private fun observeItems(
        templates: Flow<List<WorkoutTemplateEntity>>
    ): Flow<List<TemplateListItem>> {
        return combine(
            templates,
            templateDao.observeExercises(),
            templateDao.observeSets(),
            exerciseDao.observeAll(),
            exerciseDao.observeMuscles()
        ) { currentTemplates, relations, sets, exercises, muscles ->
            val catalog = exercises.associate { entity ->
                entity.id to entity.toModel(muscles.filter { it.exerciseId == entity.id })
            }
            val setsByExercise = sets.groupBy { it.templateExerciseId }
            val relationsByTemplate = relations.groupBy { it.templateId }
            currentTemplates.map { template ->
                val items = relationsByTemplate[template.id]
                    .orEmpty()
                    .sortedBy { it.position }
                    .mapNotNull { relation ->
                        val exercise = catalog[relation.exerciseId] ?: return@mapNotNull null
                        WorkoutTemplateExerciseItem(
                            relation = relation.toModel(),
                            exercise = exercise,
                            sets = setsByExercise[relation.id].orEmpty().map { it.toModel() }
                        )
                    }
                TemplateListItem(
                    template = template.toModel(),
                    exerciseCount = items.size,
                    setCount = items.sumOf { it.sets.size },
                    primaryMuscles = items.map { it.exercise.primaryMuscle }.distinct(),
                    muscleSummary = TemplateDraftLogic.muscleSummary(items),
                    exerciseNames = items.map { it.exercise.name }
                )
            }
        }
    }

    suspend fun getAggregate(id: Long): WorkoutTemplateAggregate? {
        val template = templateDao.getById(id) ?: return null
        val relations = templateDao.getExercises(id)
        val items = relations.mapNotNull { relation ->
            val entity = exerciseDao.getById(relation.exerciseId) ?: return@mapNotNull null
            val exercise = entity.toModel(exerciseDao.getMuscles(relation.exerciseId))
            val sets = templateDao.getSets(relation.id).map { it.toModel() }
            WorkoutTemplateExerciseItem(relation.toModel(), exercise, sets)
        }
        return WorkoutTemplateAggregate(template.toModel(), items)
    }

    suspend fun save(draft: TemplateDraft): TemplateSaveResult {
        val catalog = catalogMap(draft.exercises.map { it.exerciseId }.distinct())
        val issues = TemplateDraftLogic.validate(draft, catalog)
        if (issues.isNotEmpty()) {
            return TemplateSaveResult.Invalid(issues)
        }
        val name = TemplateNaming.displayName(draft.name)
        val normalized = TemplateNaming.normalize(name)
        val exceptId = draft.id ?: 0L
        if (templateDao.findIdByNormalizedName(normalized, exceptId) != null) {
            return TemplateSaveResult.DuplicateName
        }
        val now = clock.millis()
        val existing = draft.id?.let { templateDao.getById(it) }
        if (draft.id != null && existing == null) {
            return TemplateSaveResult.NotFound
        }
        val template = WorkoutTemplateEntity(
            id = existing?.id ?: 0L,
            name = name,
            normalizedName = normalized,
            notes = draft.notes.trim().ifBlank { null },
            archived = existing?.archived ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        val children = draft.exercises.map { item ->
            val exercise = catalog.getValue(item.exerciseId)
            val relation = WorkoutTemplateExerciseEntity(
                id = 0L,
                templateId = template.id,
                exerciseId = item.exerciseId,
                position = 0,
                notes = item.notes.trim().ifBlank { null },
                createdAt = now
            )
            val sets = item.sets.map { setDraft ->
                val values = PlannedSetLogic.parseValues(setDraft, exercise).first!!
                WorkoutTemplateSetEntity(
                    id = 0L,
                    templateExerciseId = 0L,
                    position = 0,
                    minReps = values.minReps,
                    maxReps = values.maxReps,
                    loadKind = values.loadKind.name,
                    weightKg = values.weightKg,
                    durationSeconds = values.durationSeconds,
                    distanceMeters = values.distanceMeters
                )
            }
            relation to sets
        }
        return try {
            val id = templateDao.saveAggregate(template, children)
            if (existing == null) {
                TemplateSaveResult.Created(id)
            } else {
                TemplateSaveResult.Updated(id)
            }
        } catch (error: Exception) {
            if (isUniqueConstraint(error)) {
                TemplateSaveResult.DuplicateName
            } else {
                throw error
            }
        }
    }

    suspend fun archive(id: Long): Boolean {
        val existing = templateDao.getById(id) ?: return false
        templateDao.updateTemplate(existing.copy(archived = true, updatedAt = clock.millis()))
        return true
    }

    suspend fun restore(id: Long): Boolean {
        val existing = templateDao.getById(id) ?: return false
        templateDao.updateTemplate(existing.copy(archived = false, updatedAt = clock.millis()))
        return true
    }

    suspend fun canDeletePermanently(id: Long): Boolean {
        if (templateDao.getById(id) == null) {
            return false
        }
        return !hasPerformedSessionReferences(id)
    }

    suspend fun deletePermanently(id: Long): TemplateDeleteResult {
        if (templateDao.getById(id) == null) {
            return TemplateDeleteResult.NotFound
        }
        if (!canDeletePermanently(id)) {
            return TemplateDeleteResult.BlockedByReferences
        }
        templateDao.deleteTemplate(id)
        return TemplateDeleteResult.Deleted
    }

    suspend fun hasTemplateReferences(exerciseId: Long): Boolean {
        return templateDao.countReferences(exerciseId) > 0
    }

    fun observeReferencedTemplateIds(): Flow<Set<Long>> {
        val sessionIds = sessionDao?.observeReferencedTemplateIds() ?: MutableStateFlow(emptyList())
        val scheduledIds = scheduledWorkoutDao?.observeTemplateIds() ?: MutableStateFlow(emptyList())
        return combine(sessionIds, scheduledIds) { sessions, scheduled ->
            (sessions + scheduled).toSet()
        }
    }

    private suspend fun hasPerformedSessionReferences(templateId: Long): Boolean {
        val sessionRefs = sessionDao?.countTemplateReferences(templateId) ?: 0
        val scheduledRefs = scheduledWorkoutDao?.countByTemplate(templateId) ?: 0
        return sessionRefs > 0 || scheduledRefs > 0
    }

    private suspend fun catalogMap(ids: List<Long>): Map<Long, Exercise> {
        return ids.mapNotNull { id ->
            val entity = exerciseDao.getById(id) ?: return@mapNotNull null
            id to entity.toModel(exerciseDao.getMuscles(id))
        }.toMap()
    }

    private fun isUniqueConstraint(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SQLiteConstraintException) {
                return true
            }
            if (current.message.orEmpty().contains("UNIQUE", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
