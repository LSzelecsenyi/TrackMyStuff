package app.mymusclemap

import app.mymusclemap.domain.entitlement.ProDiscoveryDebugConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ProDiscoveryDebugSelectionReleaseTest {
    @Test
    fun releaseIgnoresPromotionalDebugOverrides() {
        assertEquals(ProDiscoveryDebugConfig(), ProDiscoveryDebugSelection.current())
    }
}
