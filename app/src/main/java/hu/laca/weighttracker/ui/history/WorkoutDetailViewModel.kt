package hu.laca.weighttracker.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.domain.journal.WorkoutSetCopy
import hu.laca.weighttracker.domain.journal.WorkoutSetDisplay
import hu.laca.weighttracker.domain.workout.SessionProgress
import hu.laca.weighttracker.domain.workout.SessionProgressLogic
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.WorkoutSessionAggregate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class WorkoutDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val activeSessionId: Long? = null,
    val aggregate: WorkoutSessionAggregate? = null,
    val progress: SessionProgress = SessionProgress(0, 0, 0, 0),
    val setDisplays: Map<Long, WorkoutSetDisplay> = emptyMap()
)

class WorkoutDetailViewModel(
    savedStateHandle: SavedStateHandle,
    sessionRepository: WorkoutSessionRepository
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>(SESSION_ID) ?: -1L

    val uiState: StateFlow<WorkoutDetailUiState> = sessionRepository.observeAggregate(sessionId)
        .map { aggregate ->
            when {
                sessionId <= 0L || aggregate == null -> WorkoutDetailUiState(
                    loading = false,
                    missing = true
                )
                aggregate.session.status == SessionStatus.IN_PROGRESS -> WorkoutDetailUiState(
                    loading = false,
                    activeSessionId = aggregate.session.id
                )
                else -> WorkoutDetailUiState(
                    loading = false,
                    aggregate = aggregate,
                    progress = SessionProgressLogic.fromAggregate(aggregate),
                    setDisplays = buildMap {
                        aggregate.exercises.forEach { item ->
                            item.sets.forEach { set ->
                                put(set.id, WorkoutSetCopy.display(set, item.exercise))
                            }
                        }
                    }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WorkoutDetailUiState()
        )

    companion object {
        const val SESSION_ID = "sessionId"
    }
}
