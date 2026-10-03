package app.mymusclemap.ui.founder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FounderProgramUiState(
    val loading: Boolean = true,
    val status: FounderProgramStatus = FounderProgramStatus.NotEnrolled,
    val rules: FounderProgramRules,
    val nativeWorkouts: Int = 0,
    val distinctDays: Int = 0,
    val temporaryProActive: Boolean = false,
    val feedbackRecorded: Boolean = false,
    val reportSubmitted: Boolean = false,
    val requirementsComplete: Boolean = false,
    val enrolledOn: LocalDate? = null,
    val deadline: LocalDate? = null,
    val rejectionReason: String? = null,
    val feedbackDraft: String = "",
    val feedbackBlank: Boolean = false,
    val storedFeedback: String = "",
    val founderBadge: Boolean = false,
    val reportShareFailed: Boolean = false,
    val journey: FounderJourney
)

class FounderProgramViewModel(
    private val coordinator: FounderProgramCoordinator,
    private val rules: FounderProgramRules,
    private val versionName: String,
    private val milestoneAcknowledgements: FounderMilestoneAcknowledgementStore
) : ViewModel() {
    private val feedbackDraft = MutableStateFlow("")
    private val feedbackBlank = MutableStateFlow(false)
    private val reportShareFailed = MutableStateFlow(false)
    private val acknowledgements = MutableStateFlow<FounderMilestoneAcknowledgements?>(null)

    val uiState: StateFlow<FounderProgramUiState> = combine(
        coordinator.view,
        feedbackDraft,
        feedbackBlank,
        reportShareFailed,
        acknowledgements
    ) { program, draft, blank, shareFailed, acks ->
        val state = program.state
        val qualification = program.qualification
        val journey = founderJourney(
            status = state.status,
            qualification = qualification,
            acknowledgements = acks ?: FounderMilestoneAcknowledgements()
        ).let { presented ->
            if (acks == null) presented.copy(milestone = null) else presented
        }
        FounderProgramUiState(
            loading = !program.ready || acks == null,
            status = state.status,
            rules = qualification.rules,
            nativeWorkouts = qualification.nativeCompletedWorkouts,
            distinctDays = qualification.distinctNativeWorkoutDays,
            temporaryProActive = state.status.grantsTemporaryPro(),
            feedbackRecorded = state.feedbackRecorded,
            reportSubmitted = state.testerAnalyticsReportSubmitted,
            requirementsComplete = qualification.testerRequirementsComplete ||
                state.status == FounderProgramStatus.PendingApproval ||
                state.status == FounderProgramStatus.Approved,
            enrolledOn = state.enrolledOn,
            deadline = state.deadline,
            rejectionReason = state.rejectionReason,
            feedbackDraft = draft,
            feedbackBlank = blank,
            storedFeedback = program.feedbackText,
            founderBadge = state.status == FounderProgramStatus.Approved,
            reportShareFailed = shareFailed,
            journey = journey
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FounderProgramUiState(
            rules = rules,
            journey = founderJourney(
                status = FounderProgramStatus.NotEnrolled,
                qualification = FounderProgramCoordinator.emptyView(rules).qualification,
                acknowledgements = FounderMilestoneAcknowledgements()
            ).copy(milestone = null)
        )
    )

    init {
        viewModelScope.launch {
            acknowledgements.value = milestoneAcknowledgements.load()
        }
        viewModelScope.launch {
            coordinator.refresh()
        }
    }

    fun onFeedbackChange(value: String) {
        feedbackDraft.value = value
        feedbackBlank.value = false
    }

    fun enroll() {
        viewModelScope.launch {
            coordinator.enroll()
        }
    }

    fun saveFeedback() {
        viewModelScope.launch {
            val saved = coordinator.recordFeedback(feedbackDraft.value)
            feedbackBlank.value = !saved && feedbackDraft.value.isBlank()
            if (saved) {
                feedbackDraft.value = ""
            }
        }
    }

    fun reportText(): String = coordinator.reportText(versionName)

    suspend fun onReportShareResult(shared: Boolean) {
        if (!shared) {
            reportShareFailed.value = true
            return
        }
        reportShareFailed.value = false
        coordinator.submitTesterAnalyticsReport()
    }

    fun acknowledgeMilestone() {
        val milestone = uiState.value.journey.milestone ?: return
        val current = acknowledgements.value ?: return
        viewModelScope.launch {
            val updated = when (milestone) {
                FounderMilestone.TemporaryProUnlocked -> current.copy(temporaryProUnlocked = true)
                FounderMilestone.QualificationComplete -> current.copy(qualificationComplete = true)
                FounderMilestone.FounderApproved -> current.copy(founderApproved = true)
            }
            milestoneAcknowledgements.save(updated)
            acknowledgements.value = updated
        }
    }

    fun approve() {
        viewModelScope.launch {
            coordinator.approve()
        }
    }

    fun reject(reason: String) {
        viewModelScope.launch {
            coordinator.reject(reason)
        }
    }
}
