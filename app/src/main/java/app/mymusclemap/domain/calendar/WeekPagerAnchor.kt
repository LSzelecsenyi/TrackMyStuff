package app.mymusclemap.domain.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The overview week pager is centered on the current week.
 * When [today] moves, a page the user has swiped away from keeps the same Monday.
 * The centered page keeps meaning "this week".
 */
object WeekPagerAnchor {
    fun pageAfterTodayChange(
        currentPage: Int,
        centerPage: Int,
        previousToday: LocalDate,
        today: LocalDate
    ): Int {
        val weekDelta = ChronoUnit.WEEKS.between(
            WeekCalendar.start(previousToday),
            WeekCalendar.start(today)
        ).toInt()
        if (weekDelta == 0) return currentPage
        val offset = currentPage - centerPage
        if (offset == 0) return currentPage
        return centerPage + offset - weekDelta
    }
}
