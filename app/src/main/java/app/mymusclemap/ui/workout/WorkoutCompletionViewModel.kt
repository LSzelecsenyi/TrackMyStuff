package app.mymusclemap.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.achievements.PerformanceRecordEvaluator
import app.mymusclemap.domain.workout.WorkoutSummary
import app.mymusclemap.domain.workout.WorkoutSummaryLogic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

sealed interface WorkoutSummaryUiState {
    data object Loading : WorkoutSummaryUiState
    data class Ready(val summary: WorkoutSummary) : WorkoutSummaryUiState
    data object Unavailable : WorkoutSummaryUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutCompletionViewModel(
    private val sessions: WorkoutSessionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val requestedId = MutableStateFlow<String?>(null)

    val state: StateFlow<WorkoutSummaryUiState> = requestedId
        .flatMapLatest { clientWorkoutId ->
            flow {
                when {
                    clientWorkoutId == null -> emit(WorkoutSummaryUiState.Loading)
                    clientWorkoutId.isBlank() -> emit(WorkoutSummaryUiState.Unavailable)
                    else -> {
                        emit(WorkoutSummaryUiState.Loading)
                        val summary = withContext(ioDispatcher) { loadSummary(clientWorkoutId) }
                        emit(
                            if (summary == null) {
                                WorkoutSummaryUiState.Unavailable
                            } else {
                                WorkoutSummaryUiState.Ready(summary)
                            }
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState.Loading)

    fun load(clientWorkoutId: String) {
        if (requestedId.value == clientWorkoutId) return
        requestedId.value = clientWorkoutId
    }

    private suspend fun loadSummary(clientWorkoutId: String): WorkoutSummary? {
        val aggregate = sessions.completedAggregate(clientWorkoutId) ?: return null
        val highlights = PerformanceRecordEvaluator.highlightsFor(
            sessions.completedAggregates(),
            clientWorkoutId
        )
        return WorkoutSummaryLogic.from(aggregate, highlights)
    }
}
