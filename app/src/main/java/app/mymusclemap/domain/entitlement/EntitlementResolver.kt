package app.mymusclemap.domain.entitlement

import java.time.Instant

object EntitlementResolver {
    fun resolve(sources: EntitlementSources, now: Instant): EffectiveEntitlement {
        val subscriptionValid = sources.subscription.isValid(now)
        val founderLifetime = sources.founderLifetime.active ||
            sources.founderProgram.status == FounderProgramStatus.Approved
        val temporaryTesterPro = sources.founderProgram.status.grantsTemporaryPro()
        val pro = subscriptionValid || founderLifetime || temporaryTesterPro
        return EffectiveEntitlement(
            tier = if (pro) EntitlementTier.Pro else EntitlementTier.Free,
            subscriptionValid = subscriptionValid,
            founderLifetime = founderLifetime,
            temporaryTesterPro = temporaryTesterPro
        )
    }
}
