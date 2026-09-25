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

    val guide: StateFlow<OnboardingGuide> = combine(
        onboardingRepository.observe(),
        startWorkoutCoachRequested
    ) { base, requested ->
        base.copy(
            showWorkoutActionCoach = requested &&
                base.resumeTarget == OnboardingResumeTarget.StartWorkout
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

    fun markHeatmapSeen() {
        viewModelScope.launch { onboardingRepository.markHeatmapSeen() }
    }

    fun markWeightChartSeen() {
        viewModelScope.launch { onboardingRepository.markWeightChartSeen() }
    }
}
