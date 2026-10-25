package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * Founder-grant facts the benefits screen may mention beside access.
 *
 * This is not a Play subscription and it does not grant Pro. [expiresAt] is the
 * trusted Founder Pro instant, and only while that grant is the effective
 * access. A null date means the screen must not invent one or present it as a
 * subscription renewal.
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
