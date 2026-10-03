package app.mymusclemap.ui.founder

import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.FounderQualification

/**
 * Display model for the Founder screen.
 *
 * Completion still comes from [FounderQualification]. The order of [nextAction] is only
 * which incomplete requirement to mention first. This type does not change program status
 * and is not an entitlement.
 */
enum class FounderJourneyPhase {
    Welcome,
    ActiveFree,
    ActivePro,
    PendingReview,
    FoundingMember,
    Expired,
    Rejected
}

enum class FounderMilestone {
    TemporaryProUnlocked,
    QualificationComplete,
    FounderApproved
}

enum class FounderNextAction {
    UnlockTemporaryPro,
    QualifyingWorkout,
    TrainingDay,
    Feedback,
    TesterReport
}

enum class FounderChecklistKind {
    TemporaryPro,
    QualifyingWorkouts,
    TrainingDays,
    Feedback,
    TesterReport
}

data class FounderChecklistRow(
    val kind: FounderChecklistKind,
    val done: Boolean,
    val current: Int,
    val required: Int
)

data class FounderJourney(
    val phase: FounderJourneyPhase,
    val milestone: FounderMilestone?,
    val nextAction: FounderNextAction?,
    val nextRemaining: Int,
    val checklist: List<FounderChecklistRow>,
    val showDeadline: Boolean,
    val temporaryProActive: Boolean,
    val feedbackSaved: Boolean,
    val reportShared: Boolean,
    val acceptsContribution: Boolean,
    val trainingComplete: Boolean,
    val showFeedback: Boolean,
    val showReport: Boolean,
    val rules: FounderProgramRules
)

fun founderJourney(
    status: FounderProgramStatus,
    qualification: FounderQualification,
    acknowledgements: FounderMilestoneAcknowledgements
): FounderJourney {
    val next = nextAction(status, qualification)
    val acceptsContribution = status == FounderProgramStatus.ActiveFree || status == FounderProgramStatus.ActivePro
    val trainingComplete = qualification.trainingRequirementsComplete
    val showFeedback = acceptsContribution &&
        trainingComplete &&
        (qualification.rules.feedbackRequired || qualification.feedbackRecorded)
    val showReport = acceptsContribution &&
        trainingComplete &&
        qualification.feedbackMet &&
        (qualification.rules.testerAnalyticsReportRequired || qualification.testerAnalyticsReportSubmitted)
    return FounderJourney(
        phase = phase(status),
        milestone = milestone(status, acknowledgements),
        nextAction = next?.first,
        nextRemaining = next?.second ?: 0,
        checklist = checklist(qualification),
        showDeadline = status == FounderProgramStatus.ActiveFree || status == FounderProgramStatus.ActivePro,
        temporaryProActive = status.grantsTemporaryPro(),
        feedbackSaved = qualification.feedbackRecorded,
        reportShared = qualification.testerAnalyticsReportSubmitted,
        acceptsContribution = acceptsContribution,
        trainingComplete = trainingComplete,
        showFeedback = showFeedback,
        showReport = showReport,
        rules = qualification.rules
    )
}

/** Training rows stay visible. Feedback and the report appear only at their stages. */
fun FounderJourney.presentedChecklist(): List<FounderChecklistRow> {
    return checklist.filter { row ->
        when (row.kind) {
            FounderChecklistKind.Feedback -> showFeedback
            FounderChecklistKind.TesterReport -> showReport
            else -> true
        }
    }
}

private fun phase(status: FounderProgramStatus): FounderJourneyPhase {
    return when (status) {
        FounderProgramStatus.NotEnrolled -> FounderJourneyPhase.Welcome
        FounderProgramStatus.ActiveFree -> FounderJourneyPhase.ActiveFree
        FounderProgramStatus.ActivePro -> FounderJourneyPhase.ActivePro
        FounderProgramStatus.PendingApproval -> FounderJourneyPhase.PendingReview
        FounderProgramStatus.Approved -> FounderJourneyPhase.FoundingMember
        FounderProgramStatus.Expired -> FounderJourneyPhase.Expired
        FounderProgramStatus.Rejected -> FounderJourneyPhase.Rejected
    }
}

private fun milestone(
    status: FounderProgramStatus,
    acknowledgements: FounderMilestoneAcknowledgements
): FounderMilestone? {
    return when (status) {
        FounderProgramStatus.Approved ->
            if (acknowledgements.founderApproved) null else FounderMilestone.FounderApproved
        FounderProgramStatus.PendingApproval ->
            if (acknowledgements.qualificationComplete) null else FounderMilestone.QualificationComplete
        FounderProgramStatus.ActivePro ->
            if (acknowledgements.temporaryProUnlocked) null else FounderMilestone.TemporaryProUnlocked
        else -> null
    }
}

private fun nextAction(
    status: FounderProgramStatus,
    qualification: FounderQualification
): Pair<FounderNextAction, Int>? {
    val rules = qualification.rules
    return when (status) {
        FounderProgramStatus.ActiveFree -> {
            val remaining = (rules.temporaryProWorkoutCount - qualification.nativeCompletedWorkouts)
                .coerceAtLeast(0)
            FounderNextAction.UnlockTemporaryPro to remaining
        }
        FounderProgramStatus.ActivePro -> activeProNext(qualification)
        else -> null
    }
}

private fun activeProNext(qualification: FounderQualification): Pair<FounderNextAction, Int>? {
    val rules = qualification.rules
    return when {
        !qualification.founderWorkoutsMet -> {
            val remaining = (rules.founderWorkoutCount - qualification.nativeCompletedWorkouts)
                .coerceAtLeast(0)
            FounderNextAction.QualifyingWorkout to remaining
        }
        !qualification.distinctDaysMet -> {
            val remaining = (rules.requiredDistinctWorkoutDays - qualification.distinctNativeWorkoutDays)
                .coerceAtLeast(0)
            FounderNextAction.TrainingDay to remaining
        }
        !qualification.feedbackMet -> FounderNextAction.Feedback to 1
        !qualification.reportMet -> FounderNextAction.TesterReport to 1
        else -> null
    }
}

private fun checklist(qualification: FounderQualification): List<FounderChecklistRow> {
    val rules = qualification.rules
    val rows = mutableListOf(
        FounderChecklistRow(
            kind = FounderChecklistKind.TemporaryPro,
            done = qualification.temporaryProUnlocked,
            current = qualification.nativeCompletedWorkouts.coerceAtMost(rules.temporaryProWorkoutCount),
            required = rules.temporaryProWorkoutCount
        ),
        FounderChecklistRow(
            kind = FounderChecklistKind.QualifyingWorkouts,
            done = qualification.founderWorkoutsMet,
            current = qualification.nativeCompletedWorkouts.coerceAtMost(rules.founderWorkoutCount),
            required = rules.founderWorkoutCount
        ),
        FounderChecklistRow(
            kind = FounderChecklistKind.TrainingDays,
            done = qualification.distinctDaysMet,
            current = qualification.distinctNativeWorkoutDays.coerceAtMost(rules.requiredDistinctWorkoutDays),
            required = rules.requiredDistinctWorkoutDays
        )
    )
    if (rules.feedbackRequired) {
        rows += FounderChecklistRow(
            kind = FounderChecklistKind.Feedback,
            done = qualification.feedbackRecorded,
            current = if (qualification.feedbackRecorded) 1 else 0,
            required = 1
        )
    }
    if (rules.testerAnalyticsReportRequired) {
        rows += FounderChecklistRow(
            kind = FounderChecklistKind.TesterReport,
            done = qualification.testerAnalyticsReportSubmitted,
            current = if (qualification.testerAnalyticsReportSubmitted) 1 else 0,
            required = 1
        )
    }
    return rows
}
