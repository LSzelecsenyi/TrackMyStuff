package app.mymusclemap.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.statistics.TrainingStatistics
import app.mymusclemap.domain.statistics.TrainingStatisticsLogic
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatisticsUiState(
    val loading: Boolean = true,
    val dashboard: TrainingStatistics = TrainingStatistics()
)

class StatisticsViewModel(
    sessionRepository: WorkoutSessionRepository,
    dateProvider: DateProvider
) : ViewModel() {
    val uiState: StateFlow<StatisticsUiState> = combine(
        sessionRepository.observeCompletedAggregates(),
        dateProvider.observeToday()
    ) { aggregates, today ->
        StatisticsUiState(
            loading = false,
            dashboard = TrainingStatisticsLogic.assemble(aggregates, today)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StatisticsUiState()
    )
}
