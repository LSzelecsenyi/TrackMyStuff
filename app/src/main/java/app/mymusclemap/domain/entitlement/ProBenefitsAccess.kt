package app.mymusclemap.domain.entitlement

enum class ProAccessLevel {
    Free,
    Pro
}

/**
 * A trustworthy reason for the current effective level.
 * [DebugOverride] is selected only when the caller is allowed to reveal it.
 */
enum class ProAccessSource {
    None,
    GooglePlaySubscription,
    Trial,
    Founder,
    Promotional,
    DebugOverride
}

data class ProBenefitsAccess(
    val level: ProAccessLevel,
    val source: ProAccessSource = ProAccessSource.None
)

/**
 * Maps effective access separately from the underlying grant.
 * [actual] is the resolver result before a debug overlay. Release builds pass
 * [revealDebugOverride] false, so a debug label cannot appear.
 */
fun proBenefitsAccess(
    effective: EffectiveEntitlement,
    actual: EffectiveEntitlement = effective,
    revealDebugOverride: Boolean = false
): ProBenefitsAccess {
    val overridden = revealDebugOverride && effective != actual
    val level = if (effective.grantsPro) ProAccessLevel.Pro else ProAccessLevel.Free
    val source = when {
        overridden -> ProAccessSource.DebugOverride
        !effective.grantsPro -> ProAccessSource.None
        effective.subscriptionValid -> ProAccessSource.GooglePlaySubscription
        effective.founderLifetime || effective.founderProActive || effective.temporaryTesterPro ->
            ProAccessSource.Founder
        effective.promotionalProActive -> ProAccessSource.Trial
        effective.welcomeBackActive -> ProAccessSource.Promotional
        else -> ProAccessSource.None
    }
    return ProBenefitsAccess(level = level, source = source)
}
