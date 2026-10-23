package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * Introductory copy for the FREE selection screen.
 * [ProEnded] is used only when this required period follows PRO access.
 * A first FREE period with no record of a prior grant uses [WithinFreeLimits].
 */
enum class FreeAccessIntro {
    ProEnded,
    WithinFreeLimits
}

fun freeAccessIntro(
    phase: FreeAccessPhase,
    periodId: Long,
    priorProGrantEnded: Boolean
): FreeAccessIntro {
    val openedByDowngrade = phase == FreeAccessPhase.Required &&
        (periodId > 1L || priorProGrantEnded)
    return if (openedByDowngrade) FreeAccessIntro.ProEnded else FreeAccessIntro.WithinFreeLimits
}

/**
 * Reads grants that already exist and are no longer active.
 * Nothing here is stored, and access rules are unchanged.
 */
fun priorProGrantEnded(
    sources: EntitlementSources,
    now: Instant,
    actualGrantsPro: Boolean,
    effectiveGrantsPro: Boolean
): Boolean {
    if (actualGrantsPro && !effectiveGrantsPro) return true
    if (ended(sources.subscription.paidUntilInclusive, now)) return true
    if (ended(sources.promotionalPro.expiresAt, now)) return true
    if (ended(sources.welcomeBack.expiresAt, now)) return true
    if (ended(sources.backendFounder.founderProExpiresAt, now)) return true
    return false
}

private fun ended(until: Instant?, now: Instant): Boolean {
    return until != null && !now.isBefore(until)
}
