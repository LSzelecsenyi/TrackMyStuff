package app.mymusclemap.ui.muscleinsights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.muscleinsights.MuscleInsight
import app.mymusclemap.domain.muscleinsights.MuscleInsightTopic
import app.mymusclemap.domain.muscleinsights.MuscleInsightsDemo
import app.mymusclemap.domain.muscleinsights.MuscleInsightsLogic
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

data class MuscleInsightsUiState(
    val loading: Boolean = true,
    val demo: Boolean = false,
    val muscle: MuscleGroup = MuscleGroup.CHEST,
    val period: MuscleInsightsPeriod = MuscleInsightsPeriod.Weeks4,
    val insight: MuscleInsight = MuscleInsight.empty(MuscleGroup.CHEST),
    val explanation: MuscleInsightTopic? = null
)

class MuscleInsightsViewModel(
    sessionRepository: WorkoutSessionRepository,
    private val dateProvider: DateProvider,
    private val entitlements: FeatureEntitlements = OpenFeatureEntitlements,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {
    private val explanation = MutableStateFlow<MuscleInsightTopic?>(null)
    private val selectedMuscle = savedStateHandle.getStateFlow(MUSCLE, initialMuscle())
    private val selectedPeriod = savedStateHandle.getStateFlow(PERIOD, initialPeriod())

    val uiState: StateFlow<MuscleInsightsUiState> = combine(
        sessionRepository.observeCompletedAggregates(),
        dateProvider.observeToday(),
        selectedMuscle,
        selectedPeriod,
        combine(explanation, entitlements.changes()) { topic, _ -> topic }
    ) { aggregates, today, muscleName, periodName, topic ->
        val muscle = MuscleGroup.entries.firstOrNull { it.name == muscleName } ?: MuscleGroup.CHEST
        val safeMuscle = if (muscle == MuscleGroup.FULL_BODY || muscle == MuscleGroup.CARDIOVASCULAR) {
            MuscleGroup.CHEST
        } else {
            muscle
        }
        val period = MuscleInsightsPeriod.fromName(periodName)
        val now = dateProvider.now()
        val demo = !entitlements.hasAccess(AppFeature.AdvancedMuscleAnalytics)
        val insight = if (demo) {
            MuscleInsightsDemo.insight(safeMuscle, today, now, zone, period)
        } else {
            MuscleInsightsLogic.index(aggregates, zone).insight(safeMuscle, today, now, period)
        }
        MuscleInsightsUiState(
            loading = false,
            demo = demo,
            muscle = safeMuscle,
            period = period,
            insight = insight,
            explanation = topic
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MuscleInsightsUiState()
    )

    fun onMuscleSelected(muscle: MuscleGroup) {
        if (muscle == MuscleGroup.FULL_BODY || muscle == MuscleGroup.CARDIOVASCULAR) return
        savedStateHandle[MUSCLE] = muscle.name
    }

    fun onPeriodSelected(period: MuscleInsightsPeriod) {
        savedStateHandle[PERIOD] = period.name
    }

    fun onExplain(topic: MuscleInsightTopic) {
        explanation.value = topic
    }

    fun dismissExplanation() {
        explanation.value = null
    }

    private fun initialMuscle(): String {
        return savedStateHandle.get<String>(MUSCLE) ?: MuscleGroup.CHEST.name
    }

    private fun initialPeriod(): String {
        return savedStateHandle.get<String>(PERIOD) ?: MuscleInsightsPeriod.Weeks4.name
    }

    private companion object {
        const val MUSCLE = "muscle"
        const val PERIOD = "period"
    }
}
