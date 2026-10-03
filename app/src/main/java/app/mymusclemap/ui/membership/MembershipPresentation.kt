package app.mymusclemap.ui.membership

import app.mymusclemap.domain.entitlement.EffectiveEntitlement
import app.mymusclemap.domain.entitlement.FounderProgramStatus

/**
 * What persistent membership mark Overview should show.
 *
 * This is presentation only. It is derived from an already-resolved [EffectiveEntitlement]
 * and Founder status for the short explanation. It does not grant or revoke access.
 * Founder program availability is not an input: closing enrollment does not hide a badge
 * the tester already earned.
 */
enum class MembershipBadge {
    None,
    Pro,
    Founder
}

/** Which short explanation a membership badge opens. */
enum class MembershipDetail {
    TemporaryFounderPro,
    PendingFounderReview,
    FoundingMember,
    Pro
}

data class MembershipPresentation(
    val badge: MembershipBadge,
    val detail: MembershipDetail?
) {
    companion object {
        val None = MembershipPresentation(MembershipBadge.None, null)
    }
}

fun membershipPresentation(
    entitlement: EffectiveEntitlement,
    founderStatus: FounderProgramStatus
): MembershipPresentation {
    if (entitlement.founderLifetime) {
        return MembershipPresentation(MembershipBadge.Founder, MembershipDetail.FoundingMember)
    }
    if (!entitlement.grantsPro) {
        return MembershipPresentation.None
    }
    val detail = when (founderStatus) {
        FounderProgramStatus.PendingApproval -> MembershipDetail.PendingFounderReview
        FounderProgramStatus.ActivePro -> MembershipDetail.TemporaryFounderPro
        else -> MembershipDetail.Pro
    }
    return MembershipPresentation(MembershipBadge.Pro, detail)
}
