package app.mymusclemap.domain.entitlement

import java.time.Clock

/**
 * Combines the store-neutral providers and the local Founder program into one snapshot.
 * Feature code asks this for [EffectiveEntitlement] or [FeatureAccessPolicy].
 */
class EntitlementComposer(
    private val subscriptionProvider: SubscriptionEntitlementProvider,
    private val founderLifetimeProvider: FounderLifetimeProvider,
    private val clock: Clock,
    private val founderProgram: () -> FounderProgramState = { FounderProgramState() }
) {
    fun sources(): EntitlementSources {
        return EntitlementSources.of(
            subscription = subscriptionProvider.current(),
            founderLifetime = founderLifetimeProvider.current(),
            program = founderProgram()
        )
    }

    fun resolve(): EffectiveEntitlement {
        return EntitlementResolver.resolve(sources(), clock.instant())
    }

    fun policy(): FeatureAccessPolicy = FeatureAccessPolicy(resolve())
}
