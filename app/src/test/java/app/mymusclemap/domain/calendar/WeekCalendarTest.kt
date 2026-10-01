package app.mymusclemap.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class WeekCalendarTest {
    @Test
    fun currentWeekStartsOnMonday() {
        val wednesday = LocalDate.of(2026, 3, 11)
        val start = WeekCalendar.start(wednesday)
        assertEquals(LocalDate.of(2026, 3, 9), start)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 3, 15), WeekCalendar.end(start))
        assertEquals(DayOfWeek.SUNDAY, WeekCalendar.end(start).dayOfWeek)
        assertEquals(7, WeekCalendar.dates(start).size)
        assertEquals(start, WeekCalendar.dates(start).first())
        assertEquals(WeekCalendar.end(start), WeekCalendar.dates(start).last())
    }

    @Test
    fun weekCrossingMonthUsesThursdayMonthAndStillFitsThatGrid() {
        val thursday = LocalDate.of(2026, 10, 1)
        val start = WeekCalendar.start(thursday)
        assertEquals(LocalDate.of(2026, 9, 28), start)
        assertEquals(LocalDate.of(2026, 10, 4), WeekCalendar.end(start))
        assertEquals(YearMonth.of(2026, 10), WeekCalendar.primaryMonth(start))
        assertTrue(WeekCalendar.fitsInMonth(YearMonth.of(2026, 10), start))
        assertTrue(WeekCalendar.fitsInMonth(YearMonth.of(2026, 9), start))
    }

    @Test
    fun weekCrossingYearStaysInsideDecemberGrid() {
        val start = WeekCalendar.start(LocalDate.of(2026, 12, 31))
        assertEquals(LocalDate.of(2026, 12, 28), start)
        assertEquals(LocalDate.of(2027, 1, 3), WeekCalendar.end(start))
        assertEquals(YearMonth.of(2026, 12), WeekCalendar.primaryMonth(start))
        assertTrue(WeekCalendar.fitsInMonth(YearMonth.of(2026, 12), start))
        assertFalse(WeekCalendar.fitsInMonth(YearMonth.of(2026, 11), start))
    }

    @Test
    fun completedWorkoutTakesPrecedenceOverPlanned() {
        val both = cell(completed = 1, planned = 2)
        val planned = cell(completed = 0, planned = 1)
        val clear = cell(completed = 0, planned = 0)
        assertEquals(CalendarWorkoutMark.Completed, both.workoutMark())
        assertEquals(CalendarWorkoutMark.Planned, planned.workoutMark())
        assertEquals(CalendarWorkoutMark.None, clear.workoutMark())
    }

    private fun cell(completed: Int, planned: Int): CalendarCell {
        return CalendarCell(
            date = LocalDate.of(2026, 3, 11),
            inDisplayedMonth = true,
            isToday = true,
            hasMeasurement = false,
            completedWorkoutCount = completed,
            plannedWorkoutCount = planned,
            isFuture = false
        )
    }
}
