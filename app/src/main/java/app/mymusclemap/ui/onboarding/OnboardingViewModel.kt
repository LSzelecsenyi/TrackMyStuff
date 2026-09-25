package app.mymusclemap.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.FirstRunCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep {
    Welcome,
    CreatePlan
}

enum class OnboardingExit {
    OpenTemplateEditor,
    Dismiss
}

class OnboardingViewModel(
    private val firstRunCoordinator: FirstRunCoordinator
) : ViewModel() {
    private val stepState = MutableStateFlow(OnboardingStep.Welcome)
    private val exitState = MutableStateFlow<OnboardingExit?>(null)

    val step: StateFlow<OnboardingStep> = stepState
    val exit: StateFlow<OnboardingExit?> = exitState

    fun onContinue() {
        stepState.value = OnboardingStep.CreatePlan
    }

    fun onCreatePlan() {
        finish(OnboardingExit.OpenTemplateEditor)
    }

    fun onSkip() {
        finish(OnboardingExit.Dismiss)
    }

    private fun finish(exit: OnboardingExit) {
        viewModelScope.launch {
            firstRunCoordinator.markOnboardingStarted()
            exitState.value = exit
        }
    }
}
