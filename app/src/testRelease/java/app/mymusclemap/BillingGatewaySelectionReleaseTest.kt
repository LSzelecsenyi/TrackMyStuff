package app.mymusclemap

import org.junit.Assert.assertEquals
import org.junit.Test

class BillingGatewaySelectionReleaseTest {
    @Test
    fun releaseAlwaysUsesRealBilling() {
        assertEquals("REAL", BillingGatewaySelection.mode())
    }
}
