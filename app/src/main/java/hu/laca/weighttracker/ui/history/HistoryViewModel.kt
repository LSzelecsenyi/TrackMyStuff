package hu.laca.weighttracker.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.MeasurementValidationResult
import hu.laca.weighttracker.domain.MeasurementValidator
import hu.laca.weighttracker.domain.journal.JournalAssembler
import hu.laca.weighttracker.domain.journal.JournalEmptyKind
import hu.laca.weighttracker.domain.journal.JournalFilter
import hu.laca.weighttracker.domain.journal.JournalTimeline
import hu.laca.weighttracker.domain.model.SaveOutcome
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.ui.components.EditorUiState
import hu.laca.weighttracker.ui.components.UserMessage
import hu.laca.weighttracker.ui.components.formatWeightInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HistoryUiState(
    val timeline: JournalTimeline = JournalTimeline(
        groups = emptyList(),
        filter = JournalFilter.ALL,
        includeAbandoned = false,
        emptyKind = JournalEmptyKind.NoEntries
    ),
    val loading: Boolean = true,
    val editor: EditorUiState? = null,
    val userMessage: UserMessage? = null
) {
    val filter: JournalFilter get() = timeline.filter
    val includeAbandoned: Boolean get() = timeline.includeAbandoned
    val isEmpty: Boolean get() = !loading && timeline.emptyKind != null
    val emptyKind: JournalEmptyKind? get() = if (loading) null else timeline.emptyKind
}

class HistoryViewModel(
    private val repository: WeightRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val dateProvider: DateProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val filter = savedStateHandle.getStateFlow(FILTER, JournalFilter.ALL.name)
    private val includeAbandoned = savedStateHandle.getStateFlow(INCLUDE_ABANDONED, false)
    private val editor = MutableStateFlow<EditorUiState?>(null)
    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val measurements = MutableStateFlow<List<WeightMeasurement>>(emptyList())

    val uiState: StateFlow<HistoryUiState> = combine(
        measurements,
        sessionRepository.observeSummaries(),
        filter,
        includeAbandoned,
        combine(editor, userMessage) { editorState, message -> editorState to message }
    ) { items, summaries, currentFilter, abandoned, extras ->
        val timeline = JournalAssembler.assemble(
            measurements = items,
            summaries = summaries,
            filter = runCatching { JournalFilter.valueOf(currentFilter) }.getOrDefault(JournalFilter.ALL),
            includeAbandoned = abandoned
        )
        HistoryUiState(
            timeline = timeline,
            loading = false,
            editor = extras.first,
            userMessage = extras.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HistoryUiState()
    )

    init {
        viewModelScope.launch {
            repository.observeAll().collect { measurements.value = it }
        }
    }

    fun onFilterSelected(value: JournalFilter) {
        savedStateHandle[FILTER] = value.name
    }

    fun onIncludeAbandoned(value: Boolean) {
        savedStateHandle[INCLUDE_ABANDONED] = value
    }

    fun openDelete(date: LocalDate) {
        openEditor(date)
        editor.value = editor.value?.copy(showDeleteConfirm = true)
    }

    fun openEditor(date: LocalDate) {
        val existing = measurements.value.find { it.date == date }
        editor.value = EditorUiState(
            date = date,
            weightInput = existing?.let { formatWeightInput(it.weightKg) }.orEmpty(),
            existing = existing,
            weightError = null,
            dateError = null
        )
    }

    fun openNew() {
        openEditor(dateProvider.today())
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun onEditorDateChange(date: LocalDate) {
        val current = editor.value ?: return
        val existing = measurements.value.find { it.date == date }
        editor.value = current.copy(
            date = date,
            existing = existing,
            weightInput = existing?.let { formatWeightInput(it.weightKg) } ?: current.weightInput,
            dateError = null,
            showDeleteConfirm = false
        )
    }

    fun onEditorWeightChange(value: String) {
        val current = editor.value ?: return
        editor.value = current.copy(weightInput = value, weightError = null)
    }

    fun saveEditor() {
        val current = editor.value ?: return
        when (val result = MeasurementValidator.validate(current.date, current.weightInput, dateProvider.today())) {
            is MeasurementValidationResult.Invalid -> {
                editor.value = current.copy(
                    weightError = result.weightError,
                    dateError = result.dateError
                )
            }
            is MeasurementValidationResult.Valid -> {
                viewModelScope.launch {
                    val outcome = repository.save(result.date, result.weightKg)
                    editor.value = null
                    userMessage.value = if (outcome == SaveOutcome.Updated) {
                        UserMessage.Updated
                    } else {
                        UserMessage.Created
                    }
                }
            }
        }
    }

    fun requestDelete() {
        val current = editor.value ?: return
        if (current.existing != null) {
            editor.value = current.copy(showDeleteConfirm = true)
        }
    }

    fun dismissDelete() {
        editor.value = editor.value?.copy(showDeleteConfirm = false)
    }

    fun confirmDelete() {
        val existing = editor.value?.existing ?: return
        viewModelScope.launch {
            repository.delete(existing.id)
            editor.value = null
            userMessage.value = UserMessage.Deleted
        }
    }

    fun consumeMessage() {
        userMessage.value = null
    }

    companion object {
        const val FILTER = "journalFilter"
        const val INCLUDE_ABANDONED = "journalIncludeAbandoned"
    }
}
