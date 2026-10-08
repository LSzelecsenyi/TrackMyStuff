package app.mymusclemap

import app.mymusclemap.domain.entitlement.ProDiscoveryDebugConfig

/** Debug builds read the values compiled from local.properties. */
object ProDiscoveryDebugSelection {
    fun current(): ProDiscoveryDebugConfig {
        return ProDiscoveryDebugConfig.parse(
            BuildConfig.STRICT_PRO_DISCOVERY,
            BuildConfig.STRICT_PRO_DISCOVERY_EXPIRES_IN_SECONDS,
            BuildConfig.STRICT_PRO_DISCOVERY_WARNING_BEFORE_SECONDS
        )
    }
}
