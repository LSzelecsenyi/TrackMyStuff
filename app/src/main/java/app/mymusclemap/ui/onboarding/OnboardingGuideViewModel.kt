package app.mymusclemap.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.OnboardingRepository
import app.mymusclemap.domain.onboarding.OnboardingGuide
import app.mymusclemap.domain.onboarding.OnboardingResumeTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class RevealCoach(
    val requested: Boolean = false,
    val ready: Boolean = false
) {
    val reveal: Boolean get() = requested && !ready
    val show: Boolean get() = requested && ready
}

class OnboardingGuideViewModel(
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {
    private val startWorkoutCoachRequested = MutableStateFlow(false)
    private val heatmapCoach = MutableStateFlow(RevealCoach())
    private val calendarWeightCoach = MutableStateFlow(RevealCoach())
    private val chartCoach = MutableStateFlow(RevealCoach())
    private val calendarHistoryCoach = MutableStateFlow(RevealCoach())

    val guide: StateFlow<OnboardingGuide> = combine(
        onboardingRepository.observe(),
        startWorkoutCoachRequested,
        combine(heatmapCoach, calendarWeightCoach, chartCoach, calendarHistoryCoach) {
                heatmap, calendarWeight, chart, calendarHistory ->
            CoachSignals(heatmap, calendarWeight, chart, calendarHistory)
        }
    ) { base, workoutRequested, coaches ->
        val heatmapStep = base.resumeTarget == OnboardingResumeTarget.Heatmap
        val calendarWeightStep = base.resumeTarget == OnboardingResumeTarget.WeightPrompt
        val chartStep = base.resumeTarget == OnboardingResumeTarget.WeightChart
        val calendarHistoryStep = base.resumeTarget == OnboardingResumeTarget.Calendar
        base.copy(
            showWorkoutActionCoach = workoutRequested &&
                base.resumeTarget == OnboardingResumeTarget.StartWorkout,
            heatmapRevealRequested = coaches.heatmap.reveal && heatmapStep,
            showHeatmapSpotlight = coaches.heatmap.show && heatmapStep,
            calendarRevealRequested =
                (coaches.calendarWeight.reveal && calendarWeightStep) ||
                    (coaches.calendarHistory.reveal && calendarHistoryStep),
            showCalendarWeightSpotlight = coaches.calendarWeight.show && calendarWeightStep,
            chartRevealRequested = coaches.chart.reveal && chartStep,
            showChartSpotlight = coaches.chart.show && chartStep,
            showCalendarHistorySpotlight = coaches.calendarHistory.show && calendarHistoryStep
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OnboardingGuide.Inactive
    )

    fun requestStartWorkoutCoach() {
        startWorkoutCoachRequested.value = true
    }

    fun dismissStartWorkoutCoach() {
        startWorkoutCoachRequested.value = false
    }

    fun requestHeatmapCoach() {
        dismissDashboardCoaches()
        heatmapCoach.value = RevealCoach(requested = true)
    }

    fun requestCalendarWeightCoach() {
        dismissDashboardCoaches()
        calendarWeightCoach.value = RevealCoach(requested = true)
    }

    fun requestChartCoach() {
        dismissDashboardCoaches()
        chartCoach.value = RevealCoach(requested = true)
    }

    fun requestCalendarHistoryCoach() {
        dismissDashboardCoaches()
        calendarHistoryCoach.value = RevealCoach(requested = true)
    }

    fun markDashboardTargetReady() {
        heatmapCoach.readyIfRequested()
        calendarWeightCoach.readyIfRequested()
        chartCoach.readyIfRequested()
        calendarHistoryCoach.readyIfRequested()
    }

    fun markHeatmapReady() {
        markDashboardTargetReady()
    }

    fun dismissHeatmapCoach() {
        heatmapCoach.value = RevealCoach()
    }

    fun dismissCalendarWeightCoach() {
        calendarWeightCoach.value = RevealCoach()
    }

    fun dismissChartCoach() {
        chartCoach.value = RevealCoach()
    }

    fun dismissCalendarHistoryCoach() {
        calendarHistoryCoach.value = RevealCoach()
    }

    fun confirmHeatmapCoach() {
        dismissHeatmapCoach()
        viewModelScope.launch {
            val alreadyHasWeight = guide.value.facts.hasWeight
            onboardingRepository.markHeatmapSeen()
            if (alreadyHasWeight) {
                requestChartCoach()
            } else {
                requestCalendarWeightCoach()
            }
        }
    }

    fun confirmCalendarWeightCoach() {
        dismissCalendarWeightCoach()
        viewModelScope.launch { onboardingRepository.markWeightIntroduced() }
    }

    fun confirmChartCoach() {
        dismissChartCoach()
        viewModelScope.launch {
            onboardingRepository.markWeightChartSeen()
            requestCalendarHistoryCoach()
        }
    }

    fun confirmCalendarHistoryCoach() {
        dismissCalendarHistoryCoach()
        viewModelScope.launch { onboardingRepository.markCalendarSeen() }
    }

    fun markHeatmapSeen() {
        viewModelScope.launch { onboardingRepository.markHeatmapSeen() }
    }

    fun markWeightChartSeen() {
        viewModelScope.launch { onboardingRepository.markWeightChartSeen() }
    }

    private fun dismissDashboardCoaches() {
        heatmapCoach.value = RevealCoach()
        calendarWeightCoach.value = RevealCoach()
        chartCoach.value = RevealCoach()
        calendarHistoryCoach.value = RevealCoach()
    }

    private fun MutableStateFlow<RevealCoach>.readyIfRequested() {
        update { current ->
            if (current.requested) current.copy(ready = true) else current
        }
    }

    private data class CoachSignals(
        val heatmap: RevealCoach,
        val calendarWeight: RevealCoach,
        val chart: RevealCoach,
        val calendarHistory: RevealCoach
    )
}
