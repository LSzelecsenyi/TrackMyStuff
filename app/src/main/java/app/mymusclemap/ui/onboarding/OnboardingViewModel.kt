package app.mymusclemap.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.founder.FounderJoinResult
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.repository.WeeklyGoalRepository
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.ui.founder.FounderJoinNotice
import app.mymusclemap.ui.founder.shouldOfferFounderOnboardingInvitation
import app.mymusclemap.ui.founder.toNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep {
    Welcome,
    FounderInvitation,
    WeeklyGoal,
    CreatePlan
}

enum class OnboardingExit {
    OpenTemplateEditor,
    Dismiss
}

class OnboardingViewModel(
    private val firstRunCoordinator: FirstRunCoordinator,
    private val weeklyGoalRepository: WeeklyGoalRepository? = null,
    private val dateProvider: DateProvider? = null,
    private val founderInvitations: FounderMilestoneAcknowledgementStore? = null,
    private val founderProgram: FounderProgramCoordinator? = null,
    private val founderAvailability: FounderProgramAvailability = FounderProgramAvailability.Open,
    private val founderJoin: (suspend () -> FounderJoinResult)? = null
) : ViewModel() {
    private var founderDeclined = false
    private val stepState = MutableStateFlow(OnboardingStep.Welcome)
    private val exitState = MutableStateFlow<OnboardingExit?>(null)
    private val joiningState = MutableStateFlow(false)
    private val joinNoticeState = MutableStateFlow(FounderJoinNotice.None)

    val step: StateFlow<OnboardingStep> = stepState
    val exit: StateFlow<OnboardingExit?> = exitState
    val founderJoining: StateFlow<Boolean> = joiningState
    val founderJoinNotice: StateFlow<FounderJoinNotice> = joinNoticeState

    fun onContinue() {
        val invitations = founderInvitations
        val program = founderProgram
        if (
            invitations == null ||
            program == null ||
            founderAvailability != FounderProgramAvailability.Open
        ) {
            stepState.value = OnboardingStep.WeeklyGoal
            return
        }
        viewModelScope.launch {
            program.refresh()
            val offer = shouldOfferFounderOnboardingInvitation(
                availability = founderAvailability,
                status = program.currentState().status,
                acknowledgements = invitations.load()
            )
            stepState.value = if (offer) {
                OnboardingStep.FounderInvitation
            } else {
                OnboardingStep.WeeklyGoal
            }
        }
    }

    fun onJoinFounder() {
        if (founderDeclined || joiningState.value || stepState.value != OnboardingStep.FounderInvitation) {
            return
        }
        val join = founderJoin ?: return
        joiningState.value = true
        joinNoticeState.value = FounderJoinNotice.None
        viewModelScope.launch {
            val result = join()
            if (result == FounderJoinResult.InProgress) {
                return@launch
            }
            joiningState.value = false
            if (result == FounderJoinResult.Enrolled) {
                founderInvitations?.markInvitationHandled()
                stepState.value = OnboardingStep.WeeklyGoal
                return@launch
            }
            joinNoticeState.value = result.toNotice()
        }
    }

    fun onDeclineFounder() {
        if (founderDeclined || joiningState.value || stepState.value != OnboardingStep.FounderInvitation) return
        founderDeclined = true
        viewModelScope.launch {
            founderInvitations?.markInvitationHandled()
            stepState.value = OnboardingStep.WeeklyGoal
        }
    }

    fun onWeeklyGoalSkipped() {
        stepState.value = OnboardingStep.CreatePlan
    }

    fun onWeeklyGoalSet(workoutsPerWeek: Int) {
        viewModelScope.launch {
            val today = dateProvider?.today()
            if (today != null) {
                weeklyGoalRepository?.setGoal(today, workoutsPerWeek)
            }
            stepState.value = OnboardingStep.CreatePlan
        }
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
