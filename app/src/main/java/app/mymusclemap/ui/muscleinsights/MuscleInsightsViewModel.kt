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
import app.mymusclemap.domain.performance.ExercisePerformanceComparisonLogic
import app.mymusclemap.domain.performance.ExerciseProgressionDemo
import app.mymusclemap.domain.performance.MuscleExercisePerformance
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class MuscleInsightsUiState(
    val loading: Boolean = true,
    val demo: Boolean = false,
    val muscle: MuscleGroup = MuscleGroup.CHEST,
    val period: MuscleInsightsPeriod = MuscleInsightsPeriod.Weeks4,
    val insight: MuscleInsight = MuscleInsight.empty(MuscleGroup.CHEST),
    val progression: MuscleExercisePerformance = MuscleExercisePerformance(
        muscle = MuscleGroup.CHEST,
        period = MuscleInsightsPeriod.Weeks4,
        exercises = emptyList(),
        improved = 0,
        lowerRecordedPerformance = 0,
        stable = 0,
        mixed = 0,
        insufficientOrNotComparable = 0
    ),
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
        val progression = resolveExerciseProgression(
            demo = demo,
            aggregates = aggregates,
            muscle = safeMuscle,
            period = period,
            today = today
        )
        MuscleInsightsUiState(
            loading = false,
            demo = demo,
            muscle = safeMuscle,
            period = period,
            insight = insight,
            progression = progression,
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

/**
 * Free preview uses the fixed demo history. Pro uses the observed completed
 * workouts, including references older than the selected period.
 */
internal fun resolveExerciseProgression(
    demo: Boolean,
    aggregates: List<WorkoutSessionAggregate>,
    muscle: MuscleGroup,
    period: MuscleInsightsPeriod,
    today: LocalDate
): MuscleExercisePerformance {
    return if (demo) {
        ExerciseProgressionDemo.summarize(muscle, period, today)
    } else {
        ExercisePerformanceComparisonLogic.summarizeMuscle(aggregates, muscle, period, today)
    }
}
