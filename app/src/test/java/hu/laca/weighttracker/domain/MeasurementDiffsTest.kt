package hu.laca.weighttracker.domain

import hu.laca.weighttracker.measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementDiffsTest {
    @Test
    fun differenceUsesChronologicalPreviousEvenWhenDisplayedNewestFirst() {
        val items = MeasurementDiffs.withChronologicalDifferences(
            listOf(
                measurement("2026-03-11", 84.0, id = 3),
                measurement("2026-03-09", 80.0, id = 1),
                measurement("2026-03-10", 81.5, id = 2)
            )
        )
        assertEquals("2026-03-11", items[0].measurement.date.toString())
        assertEquals(2.5, items[0].differenceFromPreviousKg!!, 0.0001)
        assertEquals("2026-03-10", items[1].measurement.date.toString())
        assertEquals(1.5, items[1].differenceFromPreviousKg!!, 0.0001)
        assertEquals("2026-03-09", items[2].measurement.date.toString())
        assertNull(items[2].differenceFromPreviousKg)
    }
}
