package app.mymusclemap

import app.mymusclemap.domain.entitlement.ProDiscoveryDebugConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ProDiscoveryDebugSelectionTest {
    @Test
    fun debugSelectionUsesTheCompiledLocalProperties() {
        assertEquals(
            ProDiscoveryDebugConfig.parse(
                BuildConfig.STRICT_PRO_DISCOVERY,
                BuildConfig.STRICT_PRO_DISCOVERY_EXPIRES_IN_SECONDS,
                BuildConfig.STRICT_PRO_DISCOVERY_WARNING_BEFORE_SECONDS
            ),
            ProDiscoveryDebugSelection.current()
        )
    }
}
