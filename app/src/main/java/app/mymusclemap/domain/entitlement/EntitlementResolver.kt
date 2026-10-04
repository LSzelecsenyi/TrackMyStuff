package app.mymusclemap.domain.entitlement

import java.time.Instant

object EntitlementResolver {
    fun resolve(sources: EntitlementSources, now: Instant): EffectiveEntitlement {
        val subscriptionValid = sources.subscription.isValid(now)
        val trustedFounder = sources.backendFounder.trusted(now)
        val founderLifetime = sources.founderLifetime.active || trustedFounder.founderLifetime
        val temporaryTesterPro = trustedFounder.temporaryFounderPro && !founderLifetime
        val pro = subscriptionValid || founderLifetime || temporaryTesterPro
        return EffectiveEntitlement(
            tier = if (pro) EntitlementTier.Pro else EntitlementTier.Free,
            subscriptionValid = subscriptionValid,
            founderLifetime = founderLifetime,
            temporaryTesterPro = temporaryTesterPro
        )
    }
}
