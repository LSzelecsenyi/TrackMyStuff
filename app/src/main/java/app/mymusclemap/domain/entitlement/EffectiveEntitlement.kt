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
    val temporaryTesterPro: Boolean
) {
    val grantsPro: Boolean
        get() = tier == EntitlementTier.Pro
}
