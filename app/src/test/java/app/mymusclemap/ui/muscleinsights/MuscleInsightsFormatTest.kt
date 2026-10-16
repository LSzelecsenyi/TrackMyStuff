package app.mymusclemap.ui.muscleinsights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleInsightsFormatTest {
    @Test
    fun signedWholePercentUsesTheExistingComparisonInputs() {
        assertEquals(50, loadChangeWholePercent(18, 12.0))
        assertEquals(-50, loadChangeWholePercent(6, 12.0))
        assertEquals(0, loadChangeWholePercent(12, 12.0))
        assertEquals(33, loadChangeWholePercent(4, 3.0))
        assertEquals(40, loadChangeWholePercent(14, 10.0))
        assertNull(loadChangeWholePercent(6, 0.0))
        assertNull(loadChangeWholePercent(0, 0.0))
        assertNull(loadChangeWholePercent(6, null))
    }

    @Test
    fun signedChangeNumberUsesALeadingPlusOrMinus() {
        assertEquals("+50", signedChangeNumber(50))
        assertEquals("\u221250", signedChangeNumber(-50))
        assertEquals("0", signedChangeNumber(0))
    }
}
