package hu.laca.weighttracker.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarDayCopyTest {
    private val date = LocalDate.parse("2026-09-15")

    @Test
    fun weightOnlyDescription() {
        assertEquals("testsúlymérés", CalendarDayCopy.entryState(true, 0))
        val text = CalendarDayCopy.description(date, hasMeasurement = true, completedWorkoutCount = 0)
        assertTrue(text.contains("testsúlymérés"))
        assertFalse(text.contains("edzés"))
    }

    @Test
    fun workoutOnlyDescription() {
        assertEquals("1 befejezett edzés", CalendarDayCopy.entryState(false, 1))
    }

    @Test
    fun bothMarkersDescription() {
        assertEquals(
            "testsúlymérés és 1 befejezett edzés",
            CalendarDayCopy.entryState(true, 1)
        )
        val text = CalendarDayCopy.description(date, true, 1)
        assertTrue(text.contains("testsúlymérés és 1 befejezett edzés"))
    }

    @Test
    fun multipleWorkoutsAggregate() {
        assertEquals("2 befejezett edzés", CalendarDayCopy.entryState(false, 2))
        assertEquals("testsúlymérés és 2 befejezett edzés", CalendarDayCopy.entryState(true, 2))
    }

    @Test
    fun emptyDay() {
        assertEquals("nincs bejegyzés", CalendarDayCopy.entryState(false, 0))
    }

    @Test
    fun plannedWorkoutDescription() {
        assertEquals("1 tervezett edzés", CalendarDayCopy.entryState(false, 0, 1))
        assertEquals(
            "testsúlymérés, 1 befejezett edzés és 1 tervezett edzés",
            CalendarDayCopy.entryState(true, 1, 1)
        )
    }

    @Test
    fun futureDayStaysSelectableInCopy() {
        val text = CalendarDayCopy.description(
            date,
            hasMeasurement = false,
            completedWorkoutCount = 0,
            isFuture = true
        )
        assertTrue(text.contains("jövőbeli nap"))
        assertFalse(text.contains("nem rögzíthető"))
    }

    @Test
    fun abandonedDoesNotCreateCompletedMarker() {
        val grid = MonthGridCalculator.grid(
            month = YearMonth.of(2026, 9),
            today = date,
            measuredDates = emptySet(),
            completedWorkoutCounts = emptyMap()
        )
        val cell = grid.cells.first { it.date == date }
        assertFalse(cell.hasCompletedWorkout)
        assertEquals(0, cell.completedWorkoutCount)
    }
}
