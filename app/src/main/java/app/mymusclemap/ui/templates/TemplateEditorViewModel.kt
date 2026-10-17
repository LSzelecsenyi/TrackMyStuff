package app.mymusclemap.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.entitlement.WorkoutPlanAccess
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCatalogLogic
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.TemplateMuscleMapAssembler
import app.mymusclemap.domain.musclemap.TemplateMuscleMapState
import app.mymusclemap.domain.workout.AddExerciseResult
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedLoadLogic
import app.mymusclemap.domain.workout.PlannedSetLogic
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.domain.workout.RestDuration
import app.mymusclemap.domain.workout.RestProgressionMode
import app.mymusclemap.domain.workout.WorkDuration
import app.mymusclemap.domain.workout.WorkoutTimerAccess
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateDraftLogic
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateFieldError
import app.mymusclemap.domain.workout.TemplateNaming
import app.mymusclemap.domain.workout.TemplateOrdering
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.domain.workout.TemplateValidationIssue
import app.mymusclemap.domain.workout.toSetDraft
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

data class TemplateScrollEvent(
    val generation: Long,
    val exerciseLocalId: Long
)

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
    val stalePlan: Boolean = false,
    val pendingDuplicate: Exercise? = null,
    val showDiscardConfirm: Boolean = false,
    val finished: Boolean = false,
    val created: Boolean = false,
    val saved: Boolean = false,
    val musclePreview: TemplateMuscleMapState = TemplateMuscleMapAssembler.assemble(emptyList()),
    val scrollEvent: TemplateScrollEvent? = null,
    val lockedFeature: AppFeature? = null
) {
    val canSave: Boolean
        get() = TemplateDraftLogic.validate(draft, catalog).isEmpty() && !duplicateName
}

class TemplateEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val templateRepository: WorkoutTemplateRepository,
    private val exerciseRepository: ExerciseRepository,
    private val entitlements: FeatureEntitlements = OpenFeatureEntitlements
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
    private val scrollEvent = MutableStateFlow<TemplateScrollEvent?>(null)
    private val chrome = MutableStateFlow(TemplateEditorChrome())
    private var nextLocalId = -1L
    private var scrollGeneration = 0L

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
        combine(finished, created, saved, loading, scrollEvent) { done, wasCreated, wasSaved, isLoading, scroll ->
            EditorFinish(done, wasCreated, wasSaved, isLoading, scroll)
        },
        chrome
    ) { core, flags, finish, currentChrome ->
        val catalog = core.exercises.associateBy { it.id }
        val pickerVisible = ExerciseCatalogLogic.filter(
            exercises = core.exercises,
            query = core.query,
            category = core.category,
            muscle = flags.muscle,
            archiveFilter = app.mymusclemap.domain.exercise.ArchiveFilter.ACTIVE
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
            stalePlan = currentChrome.stalePlan,
            pendingDuplicate = flags.pendingDuplicate,
            showDiscardConfirm = flags.showDiscard,
            finished = finish.finished,
            created = finish.created,
            saved = finish.saved,
            musclePreview = TemplateMuscleMapAssembler.assemble(
                core.draft.exercises.mapNotNull { catalog[it.exerciseId] }
            ),
            scrollEvent = finish.scroll,
            lockedFeature = currentChrome.lockedFeature
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
        val loading: Boolean,
        val scroll: TemplateScrollEvent?
    )

    init {
        if (templateId != null) {
            viewModelScope.launch {
                val aggregate = templateRepository.getAggregate(templateId)
                if (aggregate == null) {
                    loading.value = false
                    finished.value = true
                } else {
                    applyLoaded(aggregate)
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
                val addedId = result.draft.exercises.last().localId
                scrollGeneration += 1L
                scrollEvent.value = TemplateScrollEvent(scrollGeneration, addedId)
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

    fun onRestMode(enabled: Boolean) {
        if (!requireTimers()) return
        updateDraft { current ->
            current.copy(
                restProgressionMode = if (enabled) {
                    RestProgressionMode.STRICT
                } else {
                    RestProgressionMode.FLEXIBLE
                }
            )
        }
    }

    fun onExerciseRest(exerciseLocalId: Long, seconds: Int?) {
        if (!WorkoutTimerAccess.allowsRestChange(seconds, WorkoutTimerAccess.allowed(entitlements))) {
            requireTimers()
            return
        }
        updateDraft { current ->
            current.copy(
                exercises = current.exercises.map { item ->
                    if (item.localId == exerciseLocalId) {
                        item.copy(restSeconds = seconds?.takeIf { it > 0 }?.coerceAtMost(RestDuration.MAX_SECONDS))
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun onSetRest(exerciseLocalId: Long, setLocalId: Long, seconds: Int?) {
        if (!WorkoutTimerAccess.allowsRestChange(seconds, WorkoutTimerAccess.allowed(entitlements))) {
            requireTimers()
            return
        }
        updateSet(exerciseLocalId, setLocalId) { set ->
            set.copy(
                restSeconds = seconds?.let { value ->
                    if (value <= 0) 0 else value.coerceAtMost(RestDuration.MAX_SECONDS)
                }
            )
        }
    }

    fun onTimedMode(exerciseLocalId: Long, timed: Boolean) {
        val exercise = draft.value.exercises.firstOrNull { it.localId == exerciseLocalId } ?: return
        val already = exercise.sets.isNotEmpty() && exercise.sets.all { it.timedWork }
        if (timed == already) return
        if (timed && !requireTimers()) return
        updateDraft { current ->
            current.copy(
                exercises = current.exercises.map { item ->
                    if (item.localId != exerciseLocalId) {
                        item
                    } else {
                        item.copy(
                            sets = item.sets.map { set ->
                                if (!timed) {
                                    set.copy(timedWork = false)
                                } else {
                                    val existing = QuantityParser.toSeconds(
                                        set.minutesText.toIntOrNull() ?: 0,
                                        set.secondsText.toIntOrNull() ?: 0
                                    )
                                    val (minutes, seconds) = QuantityParser.fromSeconds(
                                        if (existing > 0) existing else 30
                                    )
                                    set.copy(
                                        timedWork = true,
                                        minutesText = minutes.toString(),
                                        secondsText = seconds.toString()
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }
    }

    fun onWorkDuration(exerciseLocalId: Long, setLocalId: Long, seconds: Int) {
        if (!requireTimers()) return
        val bounded = seconds.coerceIn(1, WorkDuration.MAX_SECONDS)
        val (minutes, remainder) = QuantityParser.fromSeconds(bounded)
        updateSet(exerciseLocalId, setLocalId) { set ->
            set.copy(
                timedWork = true,
                minutesText = minutes.toString(),
                secondsText = remainder.toString()
            )
        }
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
        if (timedSet(exerciseLocalId, setLocalId) && !requireTimers()) return
        updateSet(exerciseLocalId, setLocalId) { it.copy(minutesText = value) }
    }

    fun onSeconds(exerciseLocalId: Long, setLocalId: Long, value: String) {
        if (timedSet(exerciseLocalId, setLocalId) && !requireTimers()) return
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
            if (current.id == null &&
                !WorkoutPlanAccess.canCreateAnother(templateRepository.planCount(), entitlements)
            ) {
                chrome.update { it.copy(lockedFeature = AppFeature.UnlimitedWorkoutPlans) }
                return@launch
            }
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
                TemplateSaveResult.Stale -> chrome.update { it.copy(stalePlan = true) }
            }
        }
    }

    fun reload() {
        val id = templateId ?: return
        viewModelScope.launch {
            loading.value = true
            chrome.update { it.copy(stalePlan = false) }
            val aggregate = templateRepository.getAggregate(id)
            if (aggregate == null) {
                loading.value = false
                finished.value = true
            } else {
                nextLocalId = -1L
                issues.value = emptyList()
                duplicateName.value = false
                applyLoaded(aggregate)
                loading.value = false
            }
        }
    }

    fun consumeScrollEvent() {
        scrollEvent.value = null
    }

    fun consumeLockedFeature() {
        chrome.update { it.copy(lockedFeature = null) }
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

    private fun applyLoaded(aggregate: app.mymusclemap.domain.workout.WorkoutTemplateAggregate) {
        val loaded = TemplateDraft(
            id = aggregate.template.id,
            name = aggregate.template.name,
            notes = aggregate.template.notes.orEmpty(),
            createdAt = aggregate.template.createdAt,
            baselineUpdatedAt = aggregate.template.updatedAt,
            restProgressionMode = aggregate.template.restProgressionMode,
            exercises = aggregate.exercises.map { item ->
                TemplateExerciseDraft(
                    localId = item.relation.id,
                    exerciseId = item.exercise.id,
                    notes = item.relation.notes.orEmpty(),
                    restSeconds = item.relation.restSeconds,
                    sets = item.sets.map { set -> set.toSetDraft(set.id) }
                )
            }
        )
        original.value = loaded
        draft.value = loaded
    }

    private fun isDirty(): Boolean {
        return TemplateNaming.displayName(draft.value.name) !=
            TemplateNaming.displayName(original.value.name) ||
            draft.value.notes.trim() != original.value.notes.trim() ||
            draft.value.restProgressionMode != original.value.restProgressionMode ||
            draft.value.exercises != original.value.exercises
    }

    private fun requireTimers(): Boolean {
        if (WorkoutTimerAccess.allowed(entitlements)) return true
        chrome.update { current ->
            if (current.lockedFeature != null) current else current.copy(lockedFeature = AppFeature.WorkoutTimers)
        }
        return false
    }

    private fun timedSet(exerciseLocalId: Long, setLocalId: Long): Boolean {
        return draft.value.exercises.firstOrNull { it.localId == exerciseLocalId }
            ?.sets
            ?.firstOrNull { it.localId == setLocalId }
            ?.timedWork == true
    }

    private fun catalogExercise(exerciseLocalId: Long): Exercise? {
        val exerciseId = draft.value.exercises.firstOrNull { it.localId == exerciseLocalId }?.exerciseId
            ?: return null
        return uiState.value.catalog[exerciseId]
    }

    private fun updateSet(
        exerciseLocalId: Long,
        setLocalId: Long,
        transform: (app.mymusclemap.domain.workout.PlannedSetDraft) -> app.mymusclemap.domain.workout.PlannedSetDraft
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

    private data class TemplateEditorChrome(
        val lockedFeature: AppFeature? = null,
        val stalePlan: Boolean = false
    )

    companion object {
        const val TEMPLATE_ID_KEY = "templateId"
    }
}
