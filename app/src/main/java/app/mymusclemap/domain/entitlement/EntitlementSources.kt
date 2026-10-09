package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * Paid access cached from a store provider. Cancellation is [renewalCancelled].
 * [paidUntilInclusive] is the Play expiry instant. Access is exclusive: Pro is
 * off at that instant, not after it. Null means there is no paid period.
 */
data class SubscriptionEntitlement(
    val paidUntilInclusive: Instant? = null,
    val renewalCancelled: Boolean = false
) {
    fun isValid(now: Instant): Boolean {
        val until = paidUntilInclusive ?: return false
        return now.isBefore(until)
    }
}

/** Permanent Founder grant. Inactive until a provider, or an approved program, says otherwise. */
data class FounderLifetimeEntitlement(
    val active: Boolean = false
)

/**
 * Independent entitlement sources. More than one may be active.
 * Temporary Tester Pro is the Founder program status, not a second stored flag.
 */
data class FounderProgramSnapshot(
    val status: FounderProgramStatus = FounderProgramStatus.NotEnrolled
)

/**
 * Promotional Pro from the 60-day discovery trial.
 * Active only while [now] is strictly before [expiresAt]. There is no renewal.
 */
data class PromotionalProEntitlement(
    val expiresAt: Instant? = null
) {
    fun isActive(now: Instant): Boolean {
        val until = expiresAt ?: return false
        return now.isBefore(until)
    }
}

data class EntitlementSources(
    val subscription: SubscriptionEntitlement = SubscriptionEntitlement(),
    val founderLifetime: FounderLifetimeEntitlement = FounderLifetimeEntitlement(),
    val founderProgram: FounderProgramSnapshot = FounderProgramSnapshot(),
    val backendFounder: BackendFounderEntitlement = BackendFounderEntitlement(),
    val promotionalPro: PromotionalProEntitlement = PromotionalProEntitlement()
) {
    companion object {
        fun of(
            subscription: SubscriptionEntitlement = SubscriptionEntitlement(),
            founderLifetime: FounderLifetimeEntitlement = FounderLifetimeEntitlement(),
            program: FounderProgramState = FounderProgramState(),
            backendFounder: BackendFounderEntitlement = BackendFounderEntitlement(),
            promotionalPro: PromotionalProEntitlement = PromotionalProEntitlement()
        ): EntitlementSources {
            return EntitlementSources(
                subscription = subscription,
                founderLifetime = founderLifetime,
                founderProgram = FounderProgramSnapshot(program.status),
                backendFounder = backendFounder,
                promotionalPro = promotionalPro
            )
        }
    }
}

/** Store boundary. Implementations must not expose billing types to feature code. */
interface SubscriptionEntitlementProvider {
    fun current(): SubscriptionEntitlement
}

/** Store boundary for a verified Founder Lifetime grant. */
interface FounderLifetimeProvider {
    fun current(): FounderLifetimeEntitlement
}

/** No store purchase has been observed. Used until Billing or StoreKit exists. */
object InactiveSubscriptionProvider : SubscriptionEntitlementProvider {
    override fun current(): SubscriptionEntitlement = SubscriptionEntitlement()
}

/** No store-backed Founder Lifetime grant. Founder Lifetime comes from a backend entitlement cache. */
object InactiveFounderLifetimeProvider : FounderLifetimeProvider {
    override fun current(): FounderLifetimeEntitlement = FounderLifetimeEntitlement()
}
