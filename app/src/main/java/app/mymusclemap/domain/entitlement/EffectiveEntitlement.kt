package app.mymusclemap.domain.entitlement

enum class EntitlementTier {
    Free,
    Pro
}

/**
 * Resolved access. [tier] is derived by [EntitlementResolver] and is not stored.
 */
data class EffectiveEntitlement(
    val tier: EntitlementTier,
    val subscriptionValid: Boolean,
    val founderLifetime: Boolean,
    val temporaryTesterPro: Boolean,
    /** Permanent Founding Member recognition. This is not Pro access. */
    val founderRecognized: Boolean = false,
    /** True only while a Founder Pro grant is still before its expiration. */
    val founderProActive: Boolean = false,
    /** True only while a promotional Pro trial is still before its expiration. */
    val promotionalProActive: Boolean = false
) {
    val grantsPro: Boolean
        get() = tier == EntitlementTier.Pro
}
