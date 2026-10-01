package app.mymusclemap.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

object WeekCalendar {
    fun start(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun end(weekStart: LocalDate): LocalDate = weekStart.plusDays(6)

    fun dates(weekStart: LocalDate): List<LocalDate> {
        return List(7) { offset -> weekStart.plusDays(offset.toLong()) }
    }

    /**
     * Month whose six-week grid contains every day of this Monday-start week,
     * including weeks that cross a month or year boundary.
     */
    fun primaryMonth(weekStart: LocalDate): YearMonth {
        return YearMonth.from(weekStart.plusDays(3))
    }

    fun fitsInMonth(month: YearMonth, weekStart: LocalDate): Boolean {
        val gridStart = MonthGridCalculator.gridStart(month)
        val gridEnd = MonthGridCalculator.gridEnd(month)
        return !weekStart.isBefore(gridStart) && !end(weekStart).isAfter(gridEnd)
    }
}
