package hu.laca.weighttracker.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseCatalogLogic
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.musclemap.TemplateMuscleMapAssembler
import hu.laca.weighttracker.domain.musclemap.TemplateMuscleMapState
import hu.laca.weighttracker.domain.workout.AddExerciseResult
import hu.laca.weighttracker.domain.workout.DistanceUnit
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedLoadLogic
import hu.laca.weighttracker.domain.workout.PlannedSetLogic
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateDraftLogic
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateFieldError
import hu.laca.weighttracker.domain.workout.TemplateNaming
import hu.laca.weighttracker.domain.workout.TemplateOrdering
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import hu.laca.weighttracker.domain.workout.TemplateValidationIssue
import hu.laca.weighttracker.domain.workout.toSetDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TemplateEditorPane {
    Form,
    Picker
}

data class TemplateEditorUiState(
    val draft: TemplateDraft = TemplateDraft(),
    val catalog: Map<Long, Exercise> = emptyMap(),
    val isEditing: Boolean = false,
    val loading: Boolean = false,
    val pane: TemplateEditorPane = TemplateEditorPane.Form,
    val pickerQuery: String = "",
    val pickerCategory: ExerciseCategory? = null,
    val pickerMuscle: MuscleGroup? = null,
    val pickerExercises: List<Exercise> = emptyList(),
    val issues: List<TemplateValidationIssue> = emptyList(),
    val duplicateName: Boolean = false,
    val pendingDuplicate: Exercise? = null,
    val showDiscardConfirm: Boolean = false,
    val finished: Boolean = false,
    val created: Boolean = false,
    val saved: Boolean = false,
    val musclePreview: TemplateMuscleMapState = TemplateMuscleMapAssembler.assemble(emptyList())
) {
    val canSave: Boolean
        get() = TemplateDraftLogic.validate(draft, catalog).isEmpty() && !duplicateName
}

class TemplateEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val templateRepository: WorkoutTemplateRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {
    private val templateId: Long? = savedStateHandle.get<Long>(TEMPLATE_ID_KEY)
        ?.takeIf { it > 0L }

    private val original = MutableStateFlow(TemplateDraft())
    private val draft = MutableStateFlow(TemplateDraft(id = templateId))
    private val pane = MutableStateFlow(TemplateEditorPane.Form)
    private val pickerQuery = MutableStateFlow("")
    private val pickerCategory = MutableStateFlow<ExerciseCategory?>(null)
    private val pickerMuscle = MutableStateFlow<MuscleGroup?>(null)
    private val issues = MutableStateFlow<List<TemplateValidationIssue>>(emptyList())
    private val duplicateName = MutableStateFlow(false)
    private val pendingDuplicate = MutableStateFlow<Exercise?>(null)
    private val showDiscardConfirm = MutableStateFlow(false)
    private val finished = MutableStateFlow(false)
    private val created = MutableStateFlow(false)
    private val saved = MutableStateFlow(false)
    private val loading = MutableStateFlow(templateId != null)
    private var nextLocalId = -1L

    val uiState: StateFlow<TemplateEditorUiState> = combine(
        combine(draft, exerciseRepository.observeAll(), pane, pickerQuery, pickerCategory) {
                current, exercises, currentPane, query, category ->
            EditorCore(current, exercises, currentPane, query, category)
        },
        combine(
            pickerMuscle,
            issues,
            duplicateName,
            pendingDuplicate,
            showDiscardConfirm
        ) { muscle, currentIssues, duplicate, pending, discard ->
            EditorFlags(muscle, currentIssues, duplicate, pending, discard)
        },
        combine(finished, created, saved, loading) { done, wasCreated, wasSaved, isLoading ->
            EditorFinish(done, wasCreated, wasSaved, isLoading)
        }
    ) { core, flags, finish ->
        val catalog = core.exercises.associateBy { it.id }
        val pickerVisible = ExerciseCatalogLogic.filter(
            exercises = core.exercises,
            query = core.query,
            category = core.category,
            muscle = flags.muscle,
            archiveFilter = hu.laca.weighttracker.domain.exercise.ArchiveFilter.ACTIVE
        )
        TemplateEditorUiState(
            draft = core.draft,
            catalog = catalog,
            isEditing = templateId != null,
            loading = finish.loading,
            pane = core.pane,
            pickerQuery = core.query,
            pickerCategory = core.category,
            pickerMuscle = flags.muscle,
            pickerExercises = pickerVisible,
            issues = flags.issues,
            duplicateName = flags.duplicateName,
            pendingDuplicate = flags.pendingDuplicate,
            showDiscardConfirm = flags.showDiscard,
            finished = finish.finished,
            created = finish.created,
            saved = finish.saved,
            musclePreview = TemplateMuscleMapAssembler.assemble(
                core.draft.exercises.mapNotNull { catalog[it.exerciseId] }
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TemplateEditorUiState(isEditing = templateId != null, loading = templateId != null)
    )

    private data class EditorCore(
        val draft: TemplateDraft,
        val exercises: List<Exercise>,
        val pane: TemplateEditorPane,
        val query: String,
        val category: ExerciseCategory?
    )

    private data class EditorFlags(
        val muscle: MuscleGroup?,
        val issues: List<TemplateValidationIssue>,
        val duplicateName: Boolean,
        val pendingDuplicate: Exercise?,
        val showDiscard: Boolean
    )

    private data class EditorFinish(
        val finished: Boolean,
        val created: Boolean,
        val saved: Boolean,
        val loading: Boolean
    )

    init {
        if (templateId != null) {
            viewModelScope.launch {
                val aggregate = templateRepository.getAggregate(templateId)
                if (aggregate == null) {
                    loading.value = false
                    finished.value = true
                } else {
                    val loaded = TemplateDraft(
                        id = aggregate.template.id,
                        name = aggregate.template.name,
                        notes = aggregate.template.notes.orEmpty(),
                        createdAt = aggregate.template.createdAt,
                        exercises = aggregate.exercises.map { item ->
                            TemplateExerciseDraft(
                                localId = item.relation.id,
                                exerciseId = item.exercise.id,
                                notes = item.relation.notes.orEmpty(),
                                sets = item.sets.map { set -> set.toSetDraft(set.id) }
                            )
                        }
                    )
                    original.value = loaded
                    draft.value = loaded
                    loading.value = false
                }
            }
        }
    }

    fun onNameChange(value: String) = updateDraft { it.copy(name = value) }

    fun onNotesChange(value: String) = updateDraft { it.copy(notes = value) }

    fun openPicker() {
        pane.value = TemplateEditorPane.Picker
    }

    fun closePicker() {
        pane.value = TemplateEditorPane.Form
        pickerQuery.value = ""
        pickerCategory.value = null
        pickerMuscle.value = null
    }

    fun onPickerQuery(value: String) {
        pickerQuery.value = value
    }

    fun onPickerCategory(value: ExerciseCategory?) {
        pickerCategory.value = value
    }

    fun onPickerMuscle(value: MuscleGroup?) {
        pickerMuscle.value = value
    }

    fun selectExercise(exercise: Exercise) {
        applyAdd(exercise, allowDuplicate = false)
    }

    fun confirmDuplicate() {
        val exercise = pendingDuplicate.value ?: return
        pendingDuplicate.value = null
        applyAdd(exercise, allowDuplicate = true)
    }

    fun dismissDuplicate() {
        pendingDuplicate.value = null
    }

    private fun applyAdd(exercise: Exercise, allowDuplicate: Boolean) {
        when (
            val result = TemplateDraftLogic.addExercise(
                draft = draft.value,
                exercise = exercise,
                nextLocalId = nextLocalId,
                allowDuplicate = allowDuplicate
            )
        ) {
            is AddExerciseResult.NeedsConfirmation -> pendingDuplicate.value = result.exercise
            AddExerciseResult.ArchivedRejected -> Unit
            is AddExerciseResult.Added -> {
                nextLocalId = result.nextLocalId
                draft.value = result.draft
                pane.value = TemplateEditorPane.Form
                issues.value = emptyList()
                duplicateName.value = false
            }
        }
    }

    fun removeExercise(localId: Long) = updateDraft { current ->
        current.copy(exercises = current.exercises.filterNot { it.localId == localId })
    }

    fun moveExercise(localId: Long, up: Boolean) = updateDraft { current ->
        val index = current.exercises.indexOfFirst { it.localId == localId }
        val moved = if (up) {
            TemplateOrdering.moveUp(current.exercises, index)
        } else {
            TemplateOrdering.moveDown(current.exercises, index)
        }
        current.copy(exercises = moved)
    }

    fun toggleExpanded(localId: Long) = updateDraft { current ->
        current.copy(
            exercises = current.exercises.map { item ->
                if (item.localId == localId) item.copy(expanded = !item.expanded) else item
            }
        )
    }

    fun setSetCount(exerciseLocalId: Long, count: Int) {
        val exercise = catalogExercise(exerciseLocalId) ?: return
        updateDraft { current ->
            current.copy(
                exercises = current.exercises.map { item ->
                    if (item.localId != exerciseLocalId) {
                        item
                    } else {
                        val (sets, next) = PlannedSetLogic.resizeSets(
                            item.sets,
                            count,
                            exercise,
                            nextLocalId
                        )
                        nextLocalId = next
                        item.copy(sets = sets)
                    }
                }
            )
        }
    }

    fun addSet(exerciseLocalId: Long) {
        val exercise = catalogExercise(exerciseLocalId) ?: return
        if (exercise.measurementType == MeasurementType.COMPLETION_ONLY) {
            return
        }
        updateDraft { current ->
            current.copy(
                exercises = current.exercises.map { item ->
                    if (item.localId != exerciseLocalId) {
                        item
                    } else {
                        val grown = PlannedSetLogic.copyPrevious(item.sets, nextLocalId)
                        nextLocalId -= 1
                        item.copy(sets = grown)
                    }
                }
            )
        }
    }

    fun removeSet(exerciseLocalId: Long, setLocalId: Long) = updateDraft { current ->
        current.copy(
            exercises = current.exercises.map { item ->
                if (item.localId != exerciseLocalId) {
                    item
                } else if (item.sets.size <= 1) {
                    item
                } else {
                    item.copy(sets = item.sets.filterNot { it.localId == setLocalId })
                }
            }
        )
    }

    fun moveSet(exerciseLocalId: Long, setLocalId: Long, up: Boolean) = updateDraft { current ->
        current.copy(
            exercises = current.exercises.map { item ->
                if (item.localId != exerciseLocalId) {
                    item
                } else {
                    val index = item.sets.indexOfFirst { it.localId == setLocalId }
                    val moved = if (up) {
                        TemplateOrdering.moveUp(item.sets, index)
                    } else {
                        TemplateOrdering.moveDown(item.sets, index)
                    }
                    item.copy(sets = moved)
                }
            }
        )
    }

    fun applyToRemaining(exerciseLocalId: Long, setLocalId: Long) = updateDraft { current ->
        current.copy(
            exercises = current.exercises.map { item ->
                if (item.localId != exerciseLocalId) {
                    item
                } else {
                    val index = item.sets.indexOfFirst { it.localId == setLocalId }
                    item.copy(sets = PlannedSetLogic.applyToRemaining(item.sets, index))
                }
            }
        )
    }

    fun applyToAll(exerciseLocalId: Long, setLocalId: Long) = updateDraft { current ->
        current.copy(
            exercises = current.exercises.map { item ->
                if (item.localId != exerciseLocalId) {
                    item
                } else {
                    val source = item.sets.firstOrNull { it.localId == setLocalId } ?: return@map item
                    item.copy(sets = PlannedSetLogic.applyToAll(item.sets, source))
                }
            }
        )
    }

    fun onMinReps(exerciseLocalId: Long, setLocalId: Long, value: String) {
        updateSet(exerciseLocalId, setLocalId) { it.copy(minRepsText = value) }
    }

    fun onMaxReps(exerciseLocalId: Long, setLocalId: Long, value: String) {
        updateSet(exerciseLocalId, setLocalId) { it.copy(maxRepsText = value) }
    }

    fun onLoadKind(exerciseLocalId: Long, setLocalId: Long, kind: PlannedLoadKind) {
        updateSet(exerciseLocalId, setLocalId) { set ->
            set.copy(
                loadKind = kind,
                weightText = if (PlannedLoadLogic.requiresPositiveWeight(kind)) set.weightText else ""
            )
        }
    }

    fun onWeight(exerciseLocalId: Long, setLocalId: Long, value: String) {
        updateSet(exerciseLocalId, setLocalId) { it.copy(weightText = value) }
    }

    fun onMinutes(exerciseLocalId: Long, setLocalId: Long, value: String) {
        updateSet(exerciseLocalId, setLocalId) { it.copy(minutesText = value) }
    }

    fun onSeconds(exerciseLocalId: Long, setLocalId: Long, value: String) {
        updateSet(exerciseLocalId, setLocalId) { it.copy(secondsText = value) }
    }

    fun onDistance(exerciseLocalId: Long, setLocalId: Long, value: String) {
        updateSet(exerciseLocalId, setLocalId) { it.copy(distanceText = value) }
    }

    fun onDistanceUnit(exerciseLocalId: Long, setLocalId: Long, unit: DistanceUnit) {
        updateSet(exerciseLocalId, setLocalId) { it.copy(distanceUnit = unit) }
    }

    fun save() {
        val current = draft.value
        val catalog = uiState.value.catalog
        val currentIssues = TemplateDraftLogic.validate(current, catalog)
        if (currentIssues.isNotEmpty()) {
            issues.value = currentIssues
            duplicateName.value = false
            return
        }
        viewModelScope.launch {
            when (val result = templateRepository.save(current)) {
                is TemplateSaveResult.Created -> {
                    created.value = true
                    saved.value = true
                    finished.value = true
                }
                is TemplateSaveResult.Updated -> {
                    created.value = false
                    saved.value = true
                    finished.value = true
                }
                is TemplateSaveResult.Invalid -> issues.value = result.issues
                TemplateSaveResult.DuplicateName -> duplicateName.value = true
                TemplateSaveResult.NotFound -> finished.value = true
            }
        }
    }

    fun requestLeave() {
        if (isDirty()) {
            showDiscardConfirm.value = true
        } else {
            finished.value = true
        }
    }

    fun dismissDiscard() {
        showDiscardConfirm.value = false
    }

    fun confirmDiscard() {
        showDiscardConfirm.value = false
        finished.value = true
    }

    private fun isDirty(): Boolean {
        return TemplateNaming.displayName(draft.value.name) !=
            TemplateNaming.displayName(original.value.name) ||
            draft.value.notes.trim() != original.value.notes.trim() ||
            draft.value.exercises != original.value.exercises
    }

    private fun catalogExercise(exerciseLocalId: Long): Exercise? {
        val exerciseId = draft.value.exercises.firstOrNull { it.localId == exerciseLocalId }?.exerciseId
            ?: return null
        return uiState.value.catalog[exerciseId]
    }

    private fun updateSet(
        exerciseLocalId: Long,
        setLocalId: Long,
        transform: (hu.laca.weighttracker.domain.workout.PlannedSetDraft) -> hu.laca.weighttracker.domain.workout.PlannedSetDraft
    ) {
        updateDraft { current ->
            current.copy(
                exercises = current.exercises.map { item ->
                    if (item.localId != exerciseLocalId) {
                        item
                    } else {
                        item.copy(
                            sets = item.sets.map { set ->
                                if (set.localId == setLocalId) transform(set) else set
                            }
                        )
                    }
                }
            )
        }
    }

    private fun updateDraft(transform: (TemplateDraft) -> TemplateDraft) {
        draft.value = transform(draft.value)
        issues.value = emptyList()
        duplicateName.value = false
    }

    companion object {
        const val TEMPLATE_ID_KEY = "templateId"
    }
}
