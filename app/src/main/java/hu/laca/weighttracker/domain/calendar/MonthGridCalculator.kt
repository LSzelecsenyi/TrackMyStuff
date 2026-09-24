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
    val completedWorkoutCount: Int,
    val plannedWorkoutCount: Int = 0,
    val isFuture: Boolean
) {
    val hasCompletedWorkout: Boolean get() = completedWorkoutCount > 0
    val hasPlannedWorkout: Boolean get() = plannedWorkoutCount > 0
}

data class MonthGrid(
    val month: YearMonth,
    val cells: List<CalendarCell>
) {
    val weeks: List<List<CalendarCell>> get() = cells.chunked(7)
}

object MonthGridCalculator {
    fun gridStart(month: YearMonth): LocalDate {
        return month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun gridEnd(month: YearMonth): LocalDate {
        return gridStart(month).plusDays(41)
    }

    fun grid(
        month: YearMonth,
        today: LocalDate,
        measuredDates: Set<LocalDate>,
        completedWorkoutCounts: Map<LocalDate, Int> = emptyMap(),
        plannedWorkoutCounts: Map<LocalDate, Int> = emptyMap()
    ): MonthGrid {
        val start = gridStart(month)
        val cells = (0 until 42).map { offset ->
            val date = start.plusDays(offset.toLong())
            CalendarCell(
                date = date,
                inDisplayedMonth = YearMonth.from(date) == month,
                isToday = date == today,
                hasMeasurement = measuredDates.contains(date),
                completedWorkoutCount = completedWorkoutCounts[date] ?: 0,
                plannedWorkoutCount = plannedWorkoutCounts[date] ?: 0,
                isFuture = date.isAfter(today)
            )
        }
        return MonthGrid(month = month, cells = cells)
    }

    fun canOpenDay(@Suppress("UNUSED_PARAMETER") date: LocalDate, @Suppress("UNUSED_PARAMETER") today: LocalDate): Boolean {
        return true
    }
}
