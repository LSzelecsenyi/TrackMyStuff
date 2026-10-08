package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * One-time presentation of a backend-confirmed Founder approval whose Founder Pro
 * grant is still active. This is not recognition, and it does not grant Pro.
 * [proExpiresAt] is the backend instant; Android does not calculate a new date.
 */
data class FounderApprovalCelebration(
    val proExpiresAt: Instant
)

/**
 * Pending only for the signed-in account after a backend-owned APPROVED snapshot
 * with a still-active Founder Pro expiry. A debug entitlement override, temporary Pro,
 * an unapproved application, a legacy lifetime grant, and an already acknowledged
 * account do not qualify.
 */
fun pendingFounderApprovalCelebration(
    userId: String?,
    backendOwned: Boolean,
    status: FounderProgramStatus,
    founderLifetime: Boolean,
    founderProExpiresAt: Instant?,
    now: Instant,
    acknowledged: Boolean
): FounderApprovalCelebration? {
    if (userId.isNullOrBlank() || !backendOwned || acknowledged || founderLifetime) {
        return null
    }
    if (status != FounderProgramStatus.Approved) {
        return null
    }
    val expiresAt = founderProExpiresAt ?: return null
    if (!now.isBefore(expiresAt)) {
        return null
    }
    return FounderApprovalCelebration(proExpiresAt = expiresAt)
}
