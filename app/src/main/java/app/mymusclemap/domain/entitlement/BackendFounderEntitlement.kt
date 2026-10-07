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
    val specialGrants: List<SpecialAchievementGrant> = emptyList()
) {
    fun trusted(now: Instant): BackendFounderEntitlement {
        val until = validUntil ?: return BackendFounderEntitlement()
        if (now.isAfter(until)) {
            return BackendFounderEntitlement()
        }
        return this
    }

    fun specialGrantedAtMillis(key: String): Long? {
        return specialGrants.firstOrNull { it.key == key }?.grantedAt?.toEpochMilli()
    }
}
