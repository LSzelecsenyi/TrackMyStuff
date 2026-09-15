package hu.laca.weighttracker.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class CalendarCell(
    val date: LocalDate,
    val inDisplayedMonth: Boolean,
    val isToday: Boolean,
    val hasMeasurement: Boolean,
    val isFuture: Boolean
)

data class MonthGrid(
    val month: YearMonth,
    val cells: List<CalendarCell>
) {
    val weeks: List<List<CalendarCell>> get() = cells.chunked(7)
}

object MonthGridCalculator {
    fun grid(
        month: YearMonth,
        today: LocalDate,
        measuredDates: Set<LocalDate>
    ): MonthGrid {
        val firstOfMonth = month.atDay(1)
        val start = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val cells = (0 until 42).map { offset ->
            val date = start.plusDays(offset.toLong())
            CalendarCell(
                date = date,
                inDisplayedMonth = YearMonth.from(date) == month,
                isToday = date == today,
                hasMeasurement = measuredDates.contains(date),
                isFuture = date.isAfter(today)
            )
        }
        return MonthGrid(month = month, cells = cells)
    }

    fun canOpenDay(date: LocalDate, today: LocalDate): Boolean {
        return !date.isAfter(today)
    }
}
