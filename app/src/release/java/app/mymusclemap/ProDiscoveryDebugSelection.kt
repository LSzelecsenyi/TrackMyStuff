package app.mymusclemap

import app.mymusclemap.domain.entitlement.ProDiscoveryDebugConfig

/** Release builds ignore strict.debug.proDiscovery* and always use the real trial. */
object ProDiscoveryDebugSelection {
    fun current(): ProDiscoveryDebugConfig = ProDiscoveryDebugConfig()
}
