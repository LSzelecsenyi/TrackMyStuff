package app.mymusclemap

import app.mymusclemap.domain.entitlement.WelcomeBackDebugConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class WelcomeBackDebugSelectionReleaseTest {
    @Test
    fun releaseIgnoresWelcomeBackDebugOverrides() {
        assertEquals(WelcomeBackDebugConfig(), WelcomeBackDebugSelection.current())
    }
}
