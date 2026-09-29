package app.mymusclemap.domain.health

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Health Connect v1 reads only the last 30 local calendar days, including today.
 * Older history is not requested and is not part of the 3M, 6M, 1Y, or All ranges.
 */
object HealthWindow {
    const val DAYS = 30

    fun start(today: LocalDate): LocalDate = today.minusDays(DAYS - 1L)

    fun queryRange(today: LocalDate): Pair<LocalDateTime, LocalDateTime> {
        return start(today).atStartOfDay() to today.plusDays(1).atStartOfDay()
    }

    fun contains(date: LocalDate, today: LocalDate): Boolean {
        return !date.isBefore(start(today)) && !date.isAfter(today)
    }
}
