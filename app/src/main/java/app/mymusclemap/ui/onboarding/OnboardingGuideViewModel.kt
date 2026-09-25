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
import kotlinx.coroutines.launch

class OnboardingGuideViewModel(
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {
    private val startWorkoutCoachRequested = MutableStateFlow(false)
    private val heatmapCoachRequested = MutableStateFlow(false)
    private val heatmapSpotlightReady = MutableStateFlow(false)

    val guide: StateFlow<OnboardingGuide> = combine(
        onboardingRepository.observe(),
        startWorkoutCoachRequested,
        heatmapCoachRequested,
        heatmapSpotlightReady
    ) { base, workoutRequested, heatmapRequested, heatmapReady ->
        val heatmapStep = base.resumeTarget == OnboardingResumeTarget.Heatmap
        base.copy(
            showWorkoutActionCoach = workoutRequested &&
                base.resumeTarget == OnboardingResumeTarget.StartWorkout,
            heatmapRevealRequested = heatmapRequested && !heatmapReady && heatmapStep,
            showHeatmapSpotlight = heatmapRequested && heatmapReady && heatmapStep
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
        heatmapSpotlightReady.value = false
        heatmapCoachRequested.value = true
    }

    fun markHeatmapReady() {
        if (heatmapCoachRequested.value) {
            heatmapSpotlightReady.value = true
        }
    }

    fun dismissHeatmapCoach() {
        heatmapCoachRequested.value = false
        heatmapSpotlightReady.value = false
    }

    fun confirmHeatmapCoach() {
        dismissHeatmapCoach()
        viewModelScope.launch { onboardingRepository.markHeatmapSeen() }
    }

    fun markHeatmapSeen() {
        viewModelScope.launch { onboardingRepository.markHeatmapSeen() }
    }

    fun markWeightChartSeen() {
        viewModelScope.launch { onboardingRepository.markWeightChartSeen() }
    }
}
