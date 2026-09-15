package hu.laca.weighttracker.domain.calendar

import hu.laca.weighttracker.measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthGridCalculatorTest {
    @Test
    fun monthStartingOnMondayHasFirstCellOnTheFirst() {
        val month = YearMonth.of(2026, 6)
        val today = LocalDate.of(2026, 6, 15)
        val grid = MonthGridCalculator.grid(month, today, emptySet())
        assertEquals(42, grid.cells.size)
        assertEquals(LocalDate.of(2026, 6, 1), grid.cells.first().date)
        assertEquals(DayOfWeek.MONDAY, grid.cells.first().date.dayOfWeek)
        assertTrue(grid.cells.first().inDisplayedMonth)
    }

    @Test
    fun monthStartingOnSundayPadsFromPreviousMonday() {
        val month = YearMonth.of(2026, 2)
        val today = LocalDate.of(2026, 2, 10)
        val grid = MonthGridCalculator.grid(month, today, emptySet())
        assertEquals(DayOfWeek.SUNDAY, month.atDay(1).dayOfWeek)
        assertEquals(LocalDate.of(2026, 1, 26), grid.cells.first().date)
        assertEquals(DayOfWeek.MONDAY, grid.cells.first().date.dayOfWeek)
        assertFalse(grid.cells.first().inDisplayedMonth)
        val firstOfMonth = grid.cells.first { it.date == LocalDate.of(2026, 2, 1) }
        assertTrue(firstOfMonth.inDisplayedMonth)
    }

    @Test
    fun februaryContainsLeapDayOnlyInLeapYears() {
        val leap = MonthGridCalculator.grid(
            YearMonth.of(2024, 2),
            LocalDate.of(2024, 2, 20),
            emptySet()
        )
        val nonLeap = MonthGridCalculator.grid(
            YearMonth.of(2025, 2),
            LocalDate.of(2025, 2, 20),
            emptySet()
        )
        assertTrue(leap.cells.any { it.date == LocalDate.of(2024, 2, 29) && it.inDisplayedMonth })
        assertFalse(nonLeap.cells.any { it.date.monthValue == 2 && it.date.dayOfMonth == 29 })
        assertEquals(LocalDate.of(2025, 3, 1), nonLeap.cells.first { it.date == LocalDate.of(2025, 3, 1) }.date)
    }

    @Test
    fun decemberToJanuaryNavigation() {
        val december = YearMonth.of(2026, 12)
        val january = december.plusMonths(1)
        assertEquals(YearMonth.of(2027, 1), january)
        val grid = MonthGridCalculator.grid(january, LocalDate.of(2027, 1, 5), emptySet())
        assertTrue(grid.cells.any { it.date == LocalDate.of(2027, 1, 1) && it.inDisplayedMonth })
        assertFalse(grid.cells.any { it.date == LocalDate.of(2026, 12, 31) && it.inDisplayedMonth })
    }

    @Test
    fun measurementMarkersMapToCorrectDays() {
        val month = YearMonth.of(2026, 3)
        val today = LocalDate.of(2026, 3, 11)
        val measured = setOf(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 3))
        val grid = MonthGridCalculator.grid(month, today, measured)
        assertTrue(grid.cells.first { it.date == LocalDate.of(2026, 3, 11) }.hasMeasurement)
        assertTrue(grid.cells.first { it.date == LocalDate.of(2026, 3, 3) }.hasMeasurement)
        assertFalse(grid.cells.first { it.date == LocalDate.of(2026, 3, 4) }.hasMeasurement)
        assertTrue(grid.cells.first { it.date == today }.isToday)
    }

    @Test
    fun futureDatesCannotBeOpened() {
        val today = LocalDate.of(2026, 3, 11)
        assertFalse(MonthGridCalculator.canOpenDay(today.plusDays(1), today))
        assertTrue(MonthGridCalculator.canOpenDay(today, today))
        assertTrue(MonthGridCalculator.canOpenDay(today.minusDays(1), today))
    }
}

class DaySheetFactoryTest {
    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun dateWithoutMeasurement() {
        val state = hu.laca.weighttracker.domain.DaySheetFactory.create(
            date = today,
            measurements = emptyList(),
            today = today
        )
        assertEquals(today, state?.date)
        assertFalse(state!!.hasMeasurement)
        assertNull(state.differenceFromPreviousKg)
    }

    @Test
    fun dateWithMeasurementIncludesPreviousDifference() {
        val measurements = listOf(
            measurement("2026-03-08", 81.0),
            measurement("2026-03-11", 81.4)
        )
        val state = hu.laca.weighttracker.domain.DaySheetFactory.create(
            date = today,
            measurements = measurements,
            today = today
        )
        assertTrue(state!!.hasMeasurement)
        assertEquals(81.4, state.measurement!!.weightKg, 0.0)
        assertEquals(0.4, state.differenceFromPreviousKg!!, 0.0001)
    }

    @Test
    fun futureDateIsRejected() {
        assertNull(
            hu.laca.weighttracker.domain.DaySheetFactory.create(
                date = today.plusDays(1),
                measurements = listOf(measurement("2026-03-11", 80.0)),
                today = today
            )
        )
    }
}
