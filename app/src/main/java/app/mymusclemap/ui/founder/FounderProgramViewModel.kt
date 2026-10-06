package app.mymusclemap.ui.founder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.auth.FounderReportSubmission
import app.mymusclemap.data.founder.FounderJoinResult
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

const val FOUNDER_FEEDBACK_MAX_LENGTH = 8000

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
    val feedbackTooLong: Boolean = false,
    val submittingReport: Boolean = false,
    val reportSubmitFailed: Boolean = false,
    val storedFeedback: String = "",
    val founderBadge: Boolean = false,
    val joining: Boolean = false,
    val joinNotice: FounderJoinNotice = FounderJoinNotice.None,
    val sessionRequired: Boolean = false,
    val journey: FounderJourney
)

enum class FounderJoinNotice {
    None,
    GoogleFailed,
    Unavailable,
    Rejected,
    NotConfigured
}

fun FounderJoinResult.toNotice(): FounderJoinNotice {
    return when (this) {
        FounderJoinResult.Enrolled,
        FounderJoinResult.Cancelled,
        FounderJoinResult.InProgress -> FounderJoinNotice.None
        FounderJoinResult.GoogleFailed -> FounderJoinNotice.GoogleFailed
        FounderJoinResult.Unavailable -> FounderJoinNotice.Unavailable
        FounderJoinResult.Rejected -> FounderJoinNotice.Rejected
        FounderJoinResult.NotConfigured -> FounderJoinNotice.NotConfigured
    }
}

class FounderProgramViewModel(
    private val coordinator: FounderProgramCoordinator,
    private val rules: FounderProgramRules,
    private val versionName: String,
    private val milestoneAcknowledgements: FounderMilestoneAcknowledgementStore,
    private val availability: FounderProgramAvailability = FounderProgramAvailability.Open,
    private val joinFounder: suspend () -> FounderJoinResult = { FounderJoinResult.Rejected },
    private val sessionRevision: Flow<Int> = flowOf(0),
    private val sessionPresent: () -> Boolean = { true },
    private val submitReport: suspend (submissionId: String, feedback: String, appVersion: String) -> FounderReportSubmission =
        { _, _, _ -> FounderReportSubmission.Unavailable },
    private val refreshAuthority: suspend () -> Unit = {}
) : ViewModel() {
    private val feedbackDraft = MutableStateFlow("")
    private val feedbackBlank = MutableStateFlow(false)
    private val feedbackTooLong = MutableStateFlow(false)
    private val submittingReport = MutableStateFlow(false)
    private val reportSubmitFailed = MutableStateFlow(false)
    private val submissionInFlight = AtomicBoolean(false)
    private val authorityRefreshInFlight = AtomicBoolean(false)
    private val acknowledgements = MutableStateFlow<FounderMilestoneAcknowledgements?>(null)
    private val joining = MutableStateFlow(false)
    private val joinNotice = MutableStateFlow(FounderJoinNotice.None)

    private val programUi: StateFlow<FounderProgramUiState> = combine(
        coordinator.view,
        feedbackDraft,
        feedbackBlank,
        combine(feedbackTooLong, submittingReport, reportSubmitFailed) { tooLong, submitting, failed ->
            Triple(tooLong, submitting, failed)
        },
        acknowledgements
    ) { program, draft, blank, submit, acks ->
        val (tooLong, submitting, failed) = submit
        val state = program.state
        val qualification = program.qualification
        val journey = founderJourney(
            status = state.status,
            qualification = qualification,
            acknowledgements = acks ?: FounderMilestoneAcknowledgements(),
            authoritative = state.backendOwned,
            trainingCompleteOverride = if (state.backendOwned) {
                state.serverTrainingRequirementsComplete
            } else {
                null
            }
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
            feedbackTooLong = tooLong,
            submittingReport = submitting,
            reportSubmitFailed = failed,
            storedFeedback = program.feedbackText,
            founderBadge = state.status == FounderProgramStatus.Approved,
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

    val uiState: StateFlow<FounderProgramUiState> = combine(
        programUi,
        joining,
        joinNotice,
        sessionRevision
    ) { program, active, notice, _ ->
        program.copy(
            joining = active,
            joinNotice = notice,
            sessionRequired = program.status != FounderProgramStatus.NotEnrolled &&
                coordinator.currentState().backendOwned &&
                !sessionPresent()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = programUi.value
    )

    init {
        viewModelScope.launch {
            acknowledgements.value = milestoneAcknowledgements.load()
        }
        viewModelScope.launch {
            coordinator.refresh()
            if (feedbackDraft.value.isEmpty()) {
                feedbackDraft.value = coordinator.loadFeedbackDraft()
            }
        }
    }

    fun onFeedbackChange(value: String) {
        feedbackDraft.value = value
        feedbackBlank.value = false
        feedbackTooLong.value = false
        reportSubmitFailed.value = false
        viewModelScope.launch {
            coordinator.saveFeedbackDraft(value)
        }
    }

    fun enroll() {
        if (joining.value) {
            return
        }
        if (!founderEnrollmentAllowed(availability, coordinator.currentState().status)) {
            return
        }
        joining.value = true
        joinNotice.value = FounderJoinNotice.None
        viewModelScope.launch {
            val result = joinFounder()
            if (result == FounderJoinResult.InProgress) {
                return@launch
            }
            joining.value = false
            joinNotice.value = result.toNotice()
        }
    }

    /**
     * Explicit sign-in for an enrolled Founder whose bearer is gone.
     * Completing a workout does not call this.
     */
    fun resumeSession() {
        if (joining.value) {
            return
        }
        if (!coordinator.currentState().backendOwned || sessionPresent()) {
            return
        }
        joining.value = true
        joinNotice.value = FounderJoinNotice.None
        viewModelScope.launch {
            val result = joinFounder()
            if (result == FounderJoinResult.InProgress) {
                return@launch
            }
            joining.value = false
            joinNotice.value = result.toNotice()
        }
    }

    fun submitTesterReport() {
        val trimmed = feedbackDraft.value.trim()
        if (trimmed.isEmpty()) {
            feedbackBlank.value = true
            feedbackTooLong.value = false
            return
        }
        if (trimmed.length > FOUNDER_FEEDBACK_MAX_LENGTH) {
            feedbackBlank.value = false
            feedbackTooLong.value = true
            return
        }
        if (!submissionInFlight.compareAndSet(false, true)) {
            return
        }
        submittingReport.value = true
        reportSubmitFailed.value = false
        feedbackBlank.value = false
        feedbackTooLong.value = false
        viewModelScope.launch {
            try {
                coordinator.saveFeedbackDraft(trimmed)
                val submissionId = coordinator.loadOrCreateSubmissionId()
                when (submitReport(submissionId, trimmed, versionName)) {
                    is FounderReportSubmission.Accepted -> {
                        coordinator.clearFeedbackDraft()
                        feedbackDraft.value = ""
                        reportSubmitFailed.value = false
                    }
                    FounderReportSubmission.Unauthenticated,
                    FounderReportSubmission.NoSession,
                    FounderReportSubmission.Rejected,
                    FounderReportSubmission.Unavailable -> reportSubmitFailed.value = true
                }
            } finally {
                submittingReport.value = false
                submissionInFlight.set(false)
            }
        }
    }

    /**
     * Reloads the backend Founder snapshot and entitlement.
     * One call runs at a time. A failure leaves the last stored state in place.
     */
    fun refreshFromBackend() {
        if (!authorityRefreshInFlight.compareAndSet(false, true)) {
            return
        }
        viewModelScope.launch {
            try {
                refreshAuthority()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            } finally {
                authorityRefreshInFlight.set(false)
            }
        }
    }

    fun acknowledgeMilestone() {
        val milestone = uiState.value.journey.milestone ?: return
        val current = acknowledgements.value ?: return
        viewModelScope.launch {
            val updated = when (milestone) {
                FounderMilestone.TemporaryProUnlocked -> current.copy(temporaryProUnlocked = true)
                FounderMilestone.TrainingComplete -> current.copy(trainingComplete = true)
                FounderMilestone.QualificationComplete -> current.copy(qualificationComplete = true)
                FounderMilestone.FounderApproved -> current.copy(founderApproved = true)
            }
            milestoneAcknowledgements.save(updated)
            acknowledgements.value = updated
        }
    }
}
