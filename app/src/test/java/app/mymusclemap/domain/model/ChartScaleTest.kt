package app.mymusclemap.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartScaleTest {
    @Test
    fun nonNegativeDomainNeverProducesNegativeYAxis() {
        val bounds = ChartScale.yBounds(listOf(100.0, 400.0), ChartValueDomain.NonNegative)
        assertEquals(0.0, bounds.min, 0.0)
        assertTrue(bounds.max > 400.0)
    }

    @Test
    fun nonNegativeDomainKeepsZeroFloorForASingleSpike() {
        val bounds = ChartScale.yBounds(listOf(50.0), ChartValueDomain.NonNegative)
        assertEquals(0.0, bounds.min, 0.0)
        assertTrue(bounds.max >= 50.0)
    }

    @Test
    fun paddedDomainStillSitsAroundBodyWeightValues() {
        val bounds = ChartScale.yBounds(listOf(80.0, 82.4), ChartValueDomain.Padded)
        assertTrue(bounds.min < 80.0)
        assertTrue(bounds.max > 82.4)
    }

    @Test
    fun chartPointMapsToGenericSeriesPointWithoutRenamingBodyWeight() {
        val point = ChartPoint(LocalDate.of(2026, 9, 16), 82.4)
        assertEquals(SeriesPoint(point.date, 82.4), point.toSeriesPoint())
    }
}
