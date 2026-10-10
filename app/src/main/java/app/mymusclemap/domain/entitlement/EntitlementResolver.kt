package app.mymusclemap.domain.entitlement

import java.time.Instant

object EntitlementResolver {
    fun resolve(sources: EntitlementSources, now: Instant): EffectiveEntitlement {
        val subscriptionValid = sources.subscription.isValid(now)
        val trustedFounder = sources.backendFounder.trusted(now)
        val founderLifetime = sources.founderLifetime.active || trustedFounder.founderLifetime
        val founderProActive = trustedFounder.founderProActive(now) && !founderLifetime
        val founderRecognized = trustedFounder.founderRecognized || founderLifetime
        val temporaryTesterPro = trustedFounder.temporaryFounderPro && !founderLifetime && !founderProActive
        val promotionalProActive = sources.promotionalPro.isActive(now)
        val welcomeBackActive = sources.welcomeBack.isActive(now)
        val pro = subscriptionValid || founderLifetime || founderProActive ||
            temporaryTesterPro || promotionalProActive || welcomeBackActive
        return EffectiveEntitlement(
            tier = if (pro) EntitlementTier.Pro else EntitlementTier.Free,
            subscriptionValid = subscriptionValid,
            founderLifetime = founderLifetime,
            temporaryTesterPro = temporaryTesterPro,
            founderRecognized = founderRecognized,
            founderProActive = founderProActive,
            promotionalProActive = promotionalProActive,
            welcomeBackActive = welcomeBackActive
        )
    }
}
