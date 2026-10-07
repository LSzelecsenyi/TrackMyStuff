package app.mymusclemap

import app.mymusclemap.domain.entitlement.EntitlementSources

/** Release composition. Debug entitlement simulation is not on this classpath. */
object EntitlementOverrideSelection {
    fun adjust(sources: EntitlementSources): EntitlementSources = sources
}
