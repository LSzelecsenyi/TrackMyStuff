package app.mymusclemap.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.MeasurementValidationResult
import app.mymusclemap.domain.MeasurementValidator
import app.mymusclemap.domain.journal.JournalAssembler
import app.mymusclemap.domain.journal.JournalEmptyKind
import app.mymusclemap.domain.journal.JournalFilter
import app.mymusclemap.domain.journal.JournalTimeline
import app.mymusclemap.domain.journal.WorkoutJournalEntry
import app.mymusclemap.domain.model.SaveOutcome
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.DeleteWorkoutResult
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import app.mymusclemap.ui.components.EditorUiState
import app.mymusclemap.ui.components.UserMessage
import app.mymusclemap.ui.components.formatWeightInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class HistoryUiState(
    val timeline: JournalTimeline = JournalTimeline(
        groups = emptyList(),
        filter = JournalFilter.WORKOUT,
        includeAbandoned = false,
        emptyKind = JournalEmptyKind.NoEntries
    ),
    val loading: Boolean = true,
    val editor: EditorUiState? = null,
    val userMessage: UserMessage? = null,
    val pendingWorkoutDelete: WorkoutSessionSummary? = null,
    val deletingWorkout: Boolean = false
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
    private val filter = savedStateHandle.getStateFlow(FILTER, JournalFilter.WORKOUT.name)
    private val includeAbandoned = savedStateHandle.getStateFlow(INCLUDE_ABANDONED, false)
    private val editor = MutableStateFlow<EditorUiState?>(null)
    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val measurements = MutableStateFlow<List<WeightMeasurement>>(emptyList())
    private val pendingWorkoutDelete = MutableStateFlow<WorkoutSessionSummary?>(null)
    private val deletingWorkout = MutableStateFlow(false)

    private data class HistoryChrome(
        val editor: EditorUiState?,
        val userMessage: UserMessage?,
        val pendingWorkoutDelete: WorkoutSessionSummary?,
        val deletingWorkout: Boolean
    )

    val uiState: StateFlow<HistoryUiState> = combine(
        measurements,
        sessionRepository.observeSummaries(),
        filter,
        includeAbandoned,
        combine(editor, userMessage, pendingWorkoutDelete, deletingWorkout) { editorState, message, pending, deleting ->
            HistoryChrome(editorState, message, pending, deleting)
        }
    ) { items, summaries, currentFilter, abandoned, chrome ->
        val timeline = JournalAssembler.assemble(
            measurements = items,
            summaries = summaries,
            filter = runCatching { JournalFilter.valueOf(currentFilter) }.getOrDefault(JournalFilter.WORKOUT),
            includeAbandoned = abandoned
        )
        HistoryUiState(
            timeline = timeline,
            loading = false,
            editor = chrome.editor,
            userMessage = chrome.userMessage,
            pendingWorkoutDelete = chrome.pendingWorkoutDelete,
            deletingWorkout = chrome.deletingWorkout
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

    fun requestDeleteWorkout(sessionId: Long) {
        if (deletingWorkout.value) {
            return
        }
        val summary = uiState.value.timeline.entries
            .filterIsInstance<WorkoutJournalEntry>()
            .firstOrNull { it.summary.session.id == sessionId }
            ?.summary
            ?: return
        pendingWorkoutDelete.value = summary
    }

    fun dismissDeleteWorkout() {
        if (deletingWorkout.value) {
            return
        }
        pendingWorkoutDelete.value = null
    }

    fun confirmDeleteWorkout() {
        if (deletingWorkout.value) {
            return
        }
        val target = pendingWorkoutDelete.value ?: return
        deletingWorkout.value = true
        viewModelScope.launch {
            val result = try {
                withContext(NonCancellable) {
                    sessionRepository.deleteWorkout(target.session.id)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DeleteWorkoutResult.Failed
            }
            deletingWorkout.value = false
            when (result) {
                DeleteWorkoutResult.Deleted -> {
                    pendingWorkoutDelete.value = null
                    userMessage.value = UserMessage.WorkoutDeleted
                }
                DeleteWorkoutResult.NotFound,
                DeleteWorkoutResult.ActiveSession,
                DeleteWorkoutResult.Failed -> {
                    userMessage.value = UserMessage.WorkoutDeleteFailed
                }
            }
        }
    }

    fun showWorkoutDeleted() {
        userMessage.value = UserMessage.WorkoutDeleted
    }

    fun consumeMessage() {
        userMessage.value = null
    }

    companion object {
        const val FILTER = "journalFilter"
        const val INCLUDE_ABANDONED = "journalIncludeAbandoned"
    }
}
