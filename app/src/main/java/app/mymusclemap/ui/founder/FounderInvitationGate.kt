package app.mymusclemap.ui.founder

import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.domain.entitlement.FounderProgramStatus

/**
 * Where the app goes after the first-run gate is known.
 * [FounderInvitation] is only the handoff after onboarding, and only while the
 * invitation is still pending for a tester who has not enrolled.
 */
enum class AppLaunchStage {
    Onboarding,
    FounderInvitation,
    App
}

fun shouldOfferFounderInvitation(
    status: FounderProgramStatus,
    acknowledgements: FounderMilestoneAcknowledgements
): Boolean {
    return acknowledgements.invitationPending &&
        !acknowledgements.invitationHandled &&
        status == FounderProgramStatus.NotEnrolled
}

fun appLaunchStage(
    firstRun: FirstRunDecision,
    status: FounderProgramStatus,
    acknowledgements: FounderMilestoneAcknowledgements
): AppLaunchStage {
    if (firstRun == FirstRunDecision.ShowOnboarding) {
        return AppLaunchStage.Onboarding
    }
    if (shouldOfferFounderInvitation(status, acknowledgements)) {
        return AppLaunchStage.FounderInvitation
    }
    return AppLaunchStage.App
}
