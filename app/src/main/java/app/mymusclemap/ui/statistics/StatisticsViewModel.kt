package app.mymusclemap.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.entitlement.ProAccess
import app.mymusclemap.domain.statistics.ExerciseProgressSummary
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.statistics.TrainingStatistics
import app.mymusclemap.domain.statistics.TrainingStatisticsLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatisticsUiState(
    val loading: Boolean = true,
    val range: StatisticsRange = StatisticsRange.Days30,
    val dashboard: TrainingStatistics = TrainingStatistics(),
    val lockedFeature: AppFeature? = null
)

class StatisticsViewModel(
    sessionRepository: WorkoutSessionRepository,
    scheduledWorkoutRepository: ScheduledWorkoutRepository,
    dateProvider: DateProvider,
    private val entitlements: FeatureEntitlements = OpenFeatureEntitlements,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val lockedFeature = MutableStateFlow<AppFeature?>(null)
    private val selectedRange = savedStateHandle.getStateFlow(RANGE, StatisticsRange.Days30.name)

    val uiState: StateFlow<StatisticsUiState> = combine(
        sessionRepository.observeCompletedAggregates(),
        scheduledWorkoutRepository.observeHistorical(),
        dateProvider.observeToday(),
        selectedRange,
        lockedFeature
    ) { aggregates, scheduled, today, rangeName, locked ->
        val requested = resolvedRange(rangeName)
        StatisticsUiState(
            loading = false,
            range = requested,
            dashboard = TrainingStatisticsLogic.assemble(aggregates, scheduled, today, requested),
            lockedFeature = locked
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StatisticsUiState()
    )

    fun onRangeSelected(range: StatisticsRange) {
        val feature = range.requiredFeature
        if (feature == null) {
            savedStateHandle[RANGE] = range.name
            return
        }
        ProAccess.run(
            entitlements = entitlements,
            feature = feature,
            onLocked = { lockedFeature.value = feature },
            onAllowed = {
                lockedFeature.value = null
                savedStateHandle[RANGE] = range.name
            }
        )
    }

    fun consumeLockedFeature() {
        lockedFeature.value = null
    }

    fun exercise(exerciseId: Long): ExerciseProgressSummary? {
        return uiState.value.dashboard.exercises.firstOrNull { it.exerciseId == exerciseId }
    }

    private fun resolvedRange(rangeName: String): StatisticsRange {
        val requested = StatisticsRange.fromName(rangeName)
        val feature = requested.requiredFeature ?: return requested
        return if (entitlements.hasAccess(feature)) requested else StatisticsRange.Days30
    }

    private companion object {
        const val RANGE = "statistics_range"
    }
}
