package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * Paid access cached from a store provider. Cancellation is [renewalCancelled];
 * Pro continues until [paidUntilInclusive]. Null means there is no paid period.
 * This type has no store, product id, or billing-client fields.
 */
data class SubscriptionEntitlement(
    val paidUntilInclusive: Instant? = null,
    val renewalCancelled: Boolean = false
) {
    fun isValid(now: Instant): Boolean {
        val until = paidUntilInclusive ?: return false
        return !now.isAfter(until)
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

data class EntitlementSources(
    val subscription: SubscriptionEntitlement = SubscriptionEntitlement(),
    val founderLifetime: FounderLifetimeEntitlement = FounderLifetimeEntitlement(),
    val founderProgram: FounderProgramSnapshot = FounderProgramSnapshot(),
    val backendFounder: BackendFounderEntitlement = BackendFounderEntitlement()
) {
    companion object {
        fun of(
            subscription: SubscriptionEntitlement = SubscriptionEntitlement(),
            founderLifetime: FounderLifetimeEntitlement = FounderLifetimeEntitlement(),
            program: FounderProgramState = FounderProgramState(),
            backendFounder: BackendFounderEntitlement = BackendFounderEntitlement()
        ): EntitlementSources {
            return EntitlementSources(
                subscription = subscription,
                founderLifetime = founderLifetime,
                founderProgram = FounderProgramSnapshot(program.status),
                backendFounder = backendFounder
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
