package app.mymusclemap

import app.mymusclemap.domain.entitlement.WelcomeBackDebugConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class WelcomeBackDebugSelectionTest {
    @Test
    fun debugSelectionUsesTheCompiledLocalProperties() {
        assertEquals(
            WelcomeBackDebugConfig.parse(
                BuildConfig.STRICT_WELCOME_BACK,
                BuildConfig.STRICT_WELCOME_BACK_GAP_DAYS,
                BuildConfig.STRICT_WELCOME_BACK_EXPIRES_IN_SECONDS,
                BuildConfig.STRICT_WELCOME_BACK_WARNING_BEFORE_SECONDS,
                BuildConfig.STRICT_WELCOME_BACK_COOLDOWN_SECONDS
            ),
            WelcomeBackDebugSelection.current()
        )
    }
}
