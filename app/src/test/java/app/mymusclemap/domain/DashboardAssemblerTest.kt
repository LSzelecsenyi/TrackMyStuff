package app.mymusclemap.domain

import app.mymusclemap.measurement
import app.mymusclemap.domain.model.ChartRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardAssemblerTest {
    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun emptyMeasurementsProduceEmptySnapshot() {
        val snapshot = DashboardAssembler.assemble(emptyList(), today, ChartRange.Days30)
        assertTrue(snapshot.isEmpty)
        assertNull(snapshot.latest)
        assertNull(snapshot.changeFromPreviousKg)
        assertNull(snapshot.currentWeek)
        assertNull(snapshot.previousWeekChangeKg)
        assertTrue(snapshot.chartPoints.isEmpty())
        assertTrue(snapshot.measurementDates.isEmpty())
        assertNull(snapshot.chartRangeAverageKg)
    }

    @Test
    fun previousWeekChangeRequiresBothAverages() {
        val snapshot = DashboardAssembler.assemble(
            measurements = listOf(measurement("2026-03-11", 80.0)),
            today = today,
            range = ChartRange.Days30
        )
        assertNull(snapshot.previousWeekChangeKg)
        assertEquals(80.0, snapshot.currentWeek?.averageKg ?: 0.0, 0.0)
    }
}
