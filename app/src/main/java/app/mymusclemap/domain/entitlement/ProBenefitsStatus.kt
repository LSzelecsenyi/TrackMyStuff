package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * What the Pro Benefits screen may say about current access.
 *
 * This is not a Founder record and it does not grant Pro. [expiresAt] is an
 * authoritative instant already stored for the active grant. A null date means
 * the screen must not invent one.
 */
data class ProBenefitsStatus(
    val proActive: Boolean = false,
    val expiresAt: Instant? = null
)

fun proBenefitsStatus(
    grantsPro: Boolean,
    founderProActive: Boolean,
    founderLifetime: Boolean,
    founderProExpiresAt: Instant?
): ProBenefitsStatus {
    val expiresAt = if (grantsPro && founderProActive && !founderLifetime) {
        founderProExpiresAt
    } else {
        null
    }
    return ProBenefitsStatus(proActive = grantsPro, expiresAt = expiresAt)
}
