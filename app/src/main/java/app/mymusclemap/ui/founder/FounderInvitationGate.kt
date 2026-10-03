package app.mymusclemap.ui.founder

import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.domain.entitlement.FounderProgramStatus

/**
 * The activity shows onboarding or the app. The Founder invitation is a step inside onboarding,
 * and only while new enrollment is open.
 */
enum class AppLaunchStage {
    Onboarding,
    App
}

fun appLaunchStage(firstRun: FirstRunDecision): AppLaunchStage {
    return if (firstRun == FirstRunDecision.ShowOnboarding) {
        AppLaunchStage.Onboarding
    } else {
        AppLaunchStage.App
    }
}

fun shouldOfferFounderOnboardingInvitation(
    availability: FounderProgramAvailability,
    status: FounderProgramStatus,
    acknowledgements: FounderMilestoneAcknowledgements
): Boolean {
    return availability == FounderProgramAvailability.Open &&
        status == FounderProgramStatus.NotEnrolled &&
        !acknowledgements.invitationHandled
}

/** New enrollment only. An existing participant is unchanged by [FounderProgramAvailability.Closed]. */
fun founderEnrollmentAllowed(
    availability: FounderProgramAvailability,
    status: FounderProgramStatus
): Boolean {
    return availability == FounderProgramAvailability.Open &&
        status == FounderProgramStatus.NotEnrolled
}

fun founderSettingsEntryVisible(
    availability: FounderProgramAvailability,
    status: FounderProgramStatus,
    programReady: Boolean
): Boolean {
    if (availability == FounderProgramAvailability.Open) {
        return true
    }
    if (!programReady) {
        return false
    }
    return status != FounderProgramStatus.NotEnrolled
}
