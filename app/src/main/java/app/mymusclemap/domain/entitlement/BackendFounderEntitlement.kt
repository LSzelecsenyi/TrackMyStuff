package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * Last Founder entitlement read from the backend.
 * A missing or elapsed [validUntil] is not a grant. Local Founder status is not this value.
 */
data class BackendFounderEntitlement(
    val temporaryFounderPro: Boolean = false,
    val founderLifetime: Boolean = false,
    val validUntil: Instant? = null
) {
    fun trusted(now: Instant): BackendFounderEntitlement {
        val until = validUntil ?: return BackendFounderEntitlement()
        if (now.isAfter(until)) {
            return BackendFounderEntitlement()
        }
        return this
    }
}
