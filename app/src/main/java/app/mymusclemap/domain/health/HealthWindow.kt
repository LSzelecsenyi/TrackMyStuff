package app.mymusclemap.domain.health

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Steps, resting heart rate, HRV, and sleep use the last 30 local calendar days, including today.
 * The exercise summary uses [recentExerciseRange]. Older exercise sessions are a separate read.
 */
object HealthWindow {
    const val DAYS = 30
    const val RECENT_EXERCISE_DAYS = 7

    fun start(today: LocalDate): LocalDate = today.minusDays(DAYS - 1L)

    fun queryRange(today: LocalDate): Pair<LocalDateTime, LocalDateTime> {
        return start(today).atStartOfDay() to today.plusDays(1).atStartOfDay()
    }

    fun recentExerciseRange(today: LocalDate): Pair<LocalDateTime, LocalDateTime> {
        val start = today.minusDays(RECENT_EXERCISE_DAYS - 1L)
        return start.atStartOfDay() to today.plusDays(1).atStartOfDay()
    }

    fun contains(date: LocalDate, today: LocalDate): Boolean {
        return !date.isBefore(start(today)) && !date.isAfter(today)
    }
}
