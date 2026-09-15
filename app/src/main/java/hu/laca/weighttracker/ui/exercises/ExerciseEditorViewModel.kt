package hu.laca.weighttracker.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseDraftLogic
import hu.laca.weighttracker.domain.exercise.ExerciseFieldError
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseEditorUiState(
    val draft: ExerciseDraft = ExerciseDraft(),
    val isEditing: Boolean = false,
    val loading: Boolean = false,
    val fieldErrors: List<ExerciseFieldError> = emptyList(),
    val duplicateName: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val finished: Boolean = false,
    val created: Boolean = false,
    val saved: Boolean = false
) {
    val weightInterpretationVisible: Boolean
        get() = ExerciseDraftLogic.isWeightInterpretationVisible(
            draft.measurementType,
            draft.resistanceBasis
        )
    val bodyweightHelperVisible: Boolean
        get() = ExerciseDraftLogic.bodyweightAllowsPerSetLoad(draft.resistanceBasis)
    val canSave: Boolean
        get() = ExerciseDraftLogic.validate(draft).isEmpty()
}

class ExerciseEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository
) : ViewModel() {
    private val exerciseId: Long? = savedStateHandle.get<Long>(EXERCISE_ID_KEY)
        ?.takeIf { it > 0L }

    private val original = MutableStateFlow(ExerciseDraft())
    private val _uiState = MutableStateFlow(
        ExerciseEditorUiState(
            isEditing = exerciseId != null,
            loading = exerciseId != null
        )
    )
    val uiState: StateFlow<ExerciseEditorUiState> = _uiState.asStateFlow()

    init {
        if (exerciseId != null) {
            viewModelScope.launch {
                val exercise = repository.getById(exerciseId)
                if (exercise == null) {
                    _uiState.update { it.copy(loading = false, finished = true) }
                } else {
                    val draft = ExerciseDraftLogic.fromExercise(exercise)
                    original.value = draft
                    _uiState.update {
                        it.copy(draft = draft, isEditing = true, loading = false)
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = updateDraft { it.copy(name = value) }

    fun onCategoryChange(value: ExerciseCategory) = updateDraft { draft ->
        if (draft.id == null) {
            ExerciseDraftLogic.applyCategory(draft, value)
        } else {
            draft.copy(category = value)
        }
    }

    fun onMovementChange(value: MovementPattern) = updateDraft { it.copy(movementPattern = value) }

    fun onMeasurementChange(value: MeasurementType) = updateDraft {
        ExerciseDraftLogic.applyMeasurement(it, value)
    }

    fun onResistanceChange(value: ResistanceBasis) = updateDraft {
        ExerciseDraftLogic.applyResistance(it, value)
    }

    fun onWeightInterpretationChange(value: WeightInterpretation) = updateDraft {
        it.copy(weightInterpretation = value)
    }

    fun onPrimaryMuscleChange(value: MuscleGroup) = updateDraft {
        ExerciseDraftLogic.applyPrimaryMuscle(it, value)
    }

    fun onToggleSecondary(value: MuscleGroup) = updateDraft {
        ExerciseDraftLogic.toggleSecondary(it, value)
    }

    fun onNotesChange(value: String) = updateDraft { it.copy(notes = value) }

    fun save() {
        val current = _uiState.value.draft
        val errors = ExerciseDraftLogic.validate(current)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, duplicateName = false) }
            return
        }
        viewModelScope.launch {
            when (val result = repository.save(current)) {
                is ExerciseSaveResult.Created -> {
                    _uiState.update {
                        it.copy(finished = true, created = true, saved = true, duplicateName = false)
                    }
                }
                is ExerciseSaveResult.Updated -> {
                    _uiState.update {
                        it.copy(finished = true, created = false, saved = true, duplicateName = false)
                    }
                }
                ExerciseSaveResult.DuplicateName -> {
                    _uiState.update { it.copy(duplicateName = true, fieldErrors = emptyList()) }
                }
                is ExerciseSaveResult.Invalid -> {
                    _uiState.update { it.copy(fieldErrors = result.errors, duplicateName = false) }
                }
                ExerciseSaveResult.NotFound -> {
                    _uiState.update { it.copy(finished = true) }
                }
            }
        }
    }

    fun requestLeave() {
        if (isDirty()) {
            _uiState.update { it.copy(showDiscardConfirm = true) }
        } else {
            _uiState.update { it.copy(finished = true) }
        }
    }

    fun dismissDiscard() {
        _uiState.update { it.copy(showDiscardConfirm = false) }
    }

    fun confirmDiscard() {
        _uiState.update { it.copy(showDiscardConfirm = false, finished = true) }
    }

    private fun isDirty(): Boolean {
        return _uiState.value.draft != original.value
    }

    private fun updateDraft(transform: (ExerciseDraft) -> ExerciseDraft) {
        _uiState.update { state ->
            val next = transform(state.draft)
            state.copy(
                draft = next,
                fieldErrors = emptyList(),
                duplicateName = false
            )
        }
    }

    companion object {
        const val EXERCISE_ID_KEY = "exerciseId"
    }
}
