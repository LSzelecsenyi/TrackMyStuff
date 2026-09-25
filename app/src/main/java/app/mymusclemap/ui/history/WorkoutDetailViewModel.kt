package app.mymusclemap.ui.history

import android.content.res.Resources
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.journal.WorkoutSetCopy
import app.mymusclemap.domain.journal.WorkoutSetDisplay
import app.mymusclemap.domain.workout.DeleteWorkoutResult
import app.mymusclemap.domain.workout.SessionProgress
import app.mymusclemap.domain.workout.SessionProgressLogic
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import app.mymusclemap.ui.components.UserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkoutDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val activeSessionId: Long? = null,
    val aggregate: WorkoutSessionAggregate? = null,
    val progress: SessionProgress = SessionProgress(0, 0, 0, 0),
    val setDisplays: Map<Long, WorkoutSetDisplay> = emptyMap(),
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val userMessage: UserMessage? = null
)

class WorkoutDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: WorkoutSessionRepository,
    private val resources: Resources
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>(SESSION_ID) ?: -1L
    private val confirmDelete = MutableStateFlow(false)
    private val deleting = MutableStateFlow(false)
    private val deleted = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<UserMessage?>(null)

    private data class DetailChrome(
        val confirmDelete: Boolean,
        val deleting: Boolean,
        val deleted: Boolean,
        val userMessage: UserMessage?
    )

    val uiState: StateFlow<WorkoutDetailUiState> = combine(
        sessionRepository.observeAggregate(sessionId),
        combine(confirmDelete, deleting, deleted, userMessage) { confirm, inFlight, gone, message ->
            DetailChrome(confirm, inFlight, gone, message)
        }
    ) { aggregate, chrome ->
        if (chrome.deleted) {
            return@combine WorkoutDetailUiState(
                loading = false,
                deleted = true,
                deleting = chrome.deleting,
                userMessage = chrome.userMessage
            )
        }
        when {
            sessionId <= 0L || (aggregate == null && !chrome.deleting) -> WorkoutDetailUiState(
                loading = false,
                missing = true,
                confirmDelete = chrome.confirmDelete,
                deleting = chrome.deleting,
                userMessage = chrome.userMessage
            )
            aggregate == null -> WorkoutDetailUiState(
                loading = false,
                deleting = true,
                userMessage = chrome.userMessage
            )
            aggregate.session.status == SessionStatus.IN_PROGRESS -> WorkoutDetailUiState(
                loading = false,
                activeSessionId = aggregate.session.id,
                confirmDelete = chrome.confirmDelete,
                deleting = chrome.deleting,
                userMessage = chrome.userMessage
            )
            else -> WorkoutDetailUiState(
                loading = false,
                aggregate = aggregate,
                progress = SessionProgressLogic.fromAggregate(aggregate),
                setDisplays = buildMap {
                    aggregate.exercises.forEach { item ->
                        item.sets.forEach { set ->
                            put(set.id, WorkoutSetCopy.display(resources, set, item.exercise))
                        }
                    }
                },
                confirmDelete = chrome.confirmDelete,
                deleting = chrome.deleting,
                userMessage = chrome.userMessage
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutDetailUiState()
    )

    fun requestDeleteWorkout() {
        if (deleting.value || deleted.value) {
            return
        }
        confirmDelete.value = true
    }

    fun dismissDeleteWorkout() {
        if (deleting.value) {
            return
        }
        confirmDelete.value = false
    }

    fun confirmDeleteWorkout() {
        if (deleting.value || deleted.value || sessionId <= 0L) {
            return
        }
        deleting.value = true
        viewModelScope.launch {
            val result = try {
                withContext(NonCancellable) {
                    sessionRepository.deleteWorkout(sessionId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DeleteWorkoutResult.Failed
            }
            when (result) {
                DeleteWorkoutResult.Deleted -> {
                    confirmDelete.value = false
                    deleted.value = true
                }
                DeleteWorkoutResult.NotFound,
                DeleteWorkoutResult.ActiveSession,
                DeleteWorkoutResult.Failed -> {
                    deleting.value = false
                    userMessage.value = UserMessage.WorkoutDeleteFailed
                }
            }
        }
    }

    fun consumeMessage() {
        userMessage.value = null
    }

    companion object {
        const val SESSION_ID = "sessionId"
    }
}
