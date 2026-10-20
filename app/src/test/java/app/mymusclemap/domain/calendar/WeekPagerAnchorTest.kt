package app.mymusclemap.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekPagerAnchorTest {
    private val center = 500

    @Test
    fun centeredPageKeepsMeaningThisWeekWhenTheWeekRolls() {
        val sunday = LocalDate.of(2027, 1, 3)
        val monday = LocalDate.of(2027, 1, 4)
        assertEquals(
            center,
            WeekPagerAnchor.pageAfterTodayChange(center, center, sunday, monday)
        )
    }

    @Test
    fun swipedWeekStaysOnTheSameMondayAcrossAWeekRollover() {
        val sunday = LocalDate.of(2027, 1, 3)
        val monday = LocalDate.of(2027, 1, 4)
        assertEquals(
            center - 3,
            WeekPagerAnchor.pageAfterTodayChange(center - 2, center, sunday, monday)
        )
    }

    @Test
    fun sameWeekAndYearBoundaryInsideTheWeekDoNotMoveThePage() {
        val newYearsEve = LocalDate.of(2026, 12, 31)
        val newYear = LocalDate.of(2027, 1, 1)
        assertEquals(
            center - 1,
            WeekPagerAnchor.pageAfterTodayChange(center - 1, center, newYearsEve, newYear)
        )
        assertEquals(
            center,
            WeekPagerAnchor.pageAfterTodayChange(center, center, newYearsEve, newYearsEve)
        )
    }
}
