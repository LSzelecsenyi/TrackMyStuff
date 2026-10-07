package app.mymusclemap

import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.FounderLifetimeEntitlement
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement

/**
 * Debug composition only. Release uses a different source file whose [adjust] is identity.
 * The adjusted sources exist for one [app.mymusclemap.domain.entitlement.EntitlementResolver]
 * call. Nothing here is written to Founder or subscription storage.
 */
enum class EntitlementOverrideMode {
    Auto,
    Founder,
    NonFounder
}

object EntitlementDebugOverride {
    fun adjust(sources: EntitlementSources, mode: EntitlementOverrideMode): EntitlementSources {
        return when (mode) {
            EntitlementOverrideMode.Auto -> sources
            EntitlementOverrideMode.Founder -> sources.copy(
                founderLifetime = FounderLifetimeEntitlement(active = true)
            )
            EntitlementOverrideMode.NonFounder -> sources.copy(
                subscription = SubscriptionEntitlement(),
                founderLifetime = FounderLifetimeEntitlement(),
                backendFounder = BackendFounderEntitlement()
            )
        }
    }
}

object EntitlementOverrideSelection {
    fun adjust(sources: EntitlementSources): EntitlementSources {
        return EntitlementDebugOverride.adjust(sources, compiledMode())
    }

    private fun compiledMode(): EntitlementOverrideMode {
        return when (val configured = BuildConfig.STRICT_ENTITLEMENT_OVERRIDE) {
            "AUTO" -> EntitlementOverrideMode.Auto
            "FOUNDER" -> EntitlementOverrideMode.Founder
            "NON_FOUNDER" -> EntitlementOverrideMode.NonFounder
            else -> error(
                "Unsupported strict.entitlementOverride \"$configured\". " +
                    "Use AUTO, FOUNDER, or NON_FOUNDER."
            )
        }
    }
}
