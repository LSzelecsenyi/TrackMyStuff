package app.mymusclemap.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.achievements.PerformanceRecordEvaluator
import app.mymusclemap.domain.workout.ApplyPlanProgressionResult
import app.mymusclemap.domain.workout.PlanProgressionProposal
import app.mymusclemap.domain.workout.WorkoutSummary
import app.mymusclemap.domain.workout.WorkoutSummaryLogic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

sealed interface WorkoutSummaryUiState {
    data object Loading : WorkoutSummaryUiState
    data class Ready(
        val summary: WorkoutSummary,
        val progression: PlanProgressionProposal = PlanProgressionProposal.EMPTY
    ) : WorkoutSummaryUiState
    data object Unavailable : WorkoutSummaryUiState
}

sealed interface PlanProgressionNotice {
    data class Partial(val updated: Int, val skipped: Int) : PlanProgressionNotice
    data object Unavailable : PlanProgressionNotice
    data object Failed : PlanProgressionNotice
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutCompletionViewModel(
    private val sessions: WorkoutSessionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val requestedId = MutableStateFlow<String?>(null)
    private val reload = MutableStateFlow(0)
    val notice = MutableStateFlow<PlanProgressionNotice?>(null)
    val applying = MutableStateFlow(false)
    val approvalEpoch = MutableStateFlow(0)

    val state: StateFlow<WorkoutSummaryUiState> = combine(requestedId, reload) { clientWorkoutId, _ ->
        clientWorkoutId
    }
        .flatMapLatest { clientWorkoutId ->
            flow {
                when {
                    clientWorkoutId == null -> emit(WorkoutSummaryUiState.Loading)
                    clientWorkoutId.isBlank() -> emit(WorkoutSummaryUiState.Unavailable)
                    else -> {
                        emit(WorkoutSummaryUiState.Loading)
                        val loaded = withContext(ioDispatcher) { loadSummary(clientWorkoutId) }
                        emit(
                            if (loaded == null) {
                                WorkoutSummaryUiState.Unavailable
                            } else {
                                WorkoutSummaryUiState.Ready(loaded.first, loaded.second)
                            }
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState.Loading)

    fun load(clientWorkoutId: String) {
        if (requestedId.value == clientWorkoutId) return
        notice.value = null
        approvalEpoch.value = 0
        requestedId.value = clientWorkoutId
    }

    suspend fun applyApprovedPlanUpdates(): ApplyPlanProgressionResult {
        val clientWorkoutId = requestedId.value
        if (clientWorkoutId.isNullOrBlank()) {
            return ApplyPlanProgressionResult.SessionMissing
        }
        if (!applying.compareAndSet(false, true)) {
            return ApplyPlanProgressionResult.Failed
        }
        try {
            val shown = (state.value as? WorkoutSummaryUiState.Ready)
                ?.progression
                ?.updates
                ?.map { it.sourceTemplateSetId }
                ?.toSet()
                .orEmpty()
            val result = withContext(ioDispatcher) {
                sessions.applyPlanProgression(clientWorkoutId, shown)
            }
            when (result) {
                is ApplyPlanProgressionResult.Applied -> {
                    reload.value += 1
                    if (result.updated == 0 || result.skipped > 0) {
                        notice.value = if (result.updated == 0) {
                            PlanProgressionNotice.Unavailable
                        } else {
                            PlanProgressionNotice.Partial(result.updated, result.skipped)
                        }
                        approvalEpoch.value += 1
                    }
                }
                ApplyPlanProgressionResult.AlreadyApplied -> Unit
                ApplyPlanProgressionResult.PlanMissing,
                ApplyPlanProgressionResult.NothingToApply -> {
                    notice.value = PlanProgressionNotice.Unavailable
                    approvalEpoch.value += 1
                }
                ApplyPlanProgressionResult.Failed,
                ApplyPlanProgressionResult.SessionMissing -> {
                    notice.value = PlanProgressionNotice.Failed
                    approvalEpoch.value += 1
                }
            }
            return result
        } finally {
            applying.value = false
        }
    }

    private suspend fun loadSummary(clientWorkoutId: String): Pair<WorkoutSummary, PlanProgressionProposal>? {
        val aggregate = sessions.completedAggregate(clientWorkoutId) ?: return null
        val highlights = PerformanceRecordEvaluator.highlightsFor(
            sessions.completedAggregates(),
            clientWorkoutId
        )
        val summary = WorkoutSummaryLogic.from(aggregate, highlights) ?: return null
        return summary to sessions.planProgression(clientWorkoutId)
    }
}
