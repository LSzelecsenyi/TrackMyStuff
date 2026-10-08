package app.mymusclemap.domain.entitlement

import java.time.Instant

data class SpecialAchievementGrant(
    val key: String,
    val grantedAt: Instant
)

/**
 * Last account entitlement read from the backend.
 * A missing or elapsed [validUntil] is not a grant. Local Founder status is not this value.
 * [userId] binds the cache to one signed-in account. Special grants never grant Pro.
 */
data class BackendFounderEntitlement(
    val temporaryFounderPro: Boolean = false,
    val founderLifetime: Boolean = false,
    val validUntil: Instant? = null,
    val userId: String? = null,
    val founderGrantedAt: Instant? = null,
    val specialGrants: List<SpecialAchievementGrant> = emptyList(),
    /** Approved Founding Member. Stays true after Founder Pro expires. */
    val founderRecognized: Boolean = false,
    /** End of the 12-month Founder Pro grant. Null when that grant is absent. */
    val founderProExpiresAt: Instant? = null
) {
    fun trusted(now: Instant): BackendFounderEntitlement {
        val until = validUntil ?: return BackendFounderEntitlement()
        if (now.isAfter(until)) {
            return BackendFounderEntitlement()
        }
        return this
    }

    /** Founder Pro ends at [founderProExpiresAt] even when the cache itself is still trusted. */
    fun founderProActive(now: Instant): Boolean {
        val expiresAt = founderProExpiresAt ?: return false
        return now.isBefore(expiresAt)
    }

    fun specialGrantedAtMillis(key: String): Long? {
        return specialGrants.firstOrNull { it.key == key }?.grantedAt?.toEpochMilli()
    }
}
