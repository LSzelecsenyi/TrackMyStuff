package app.mymusclemap.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressPhotoCompareRevealTest {
    @Test
    fun revealStartsAtHalfAndClampsToTheViewport() {
        assertEquals(0.5f, COMPARE_REVEAL_START, 0f)
        assertEquals(0f, compareRevealFraction(-0.4f), 0f)
        assertEquals(0f, compareRevealFraction(0f), 0f)
        assertEquals(0.5f, compareRevealFraction(0.5f), 0f)
        assertEquals(1f, compareRevealFraction(1f), 0f)
        assertEquals(1f, compareRevealFraction(1.8f), 0f)
        assertEquals(COMPARE_REVEAL_START, compareRevealFraction(Float.NaN), 0f)
    }
}
