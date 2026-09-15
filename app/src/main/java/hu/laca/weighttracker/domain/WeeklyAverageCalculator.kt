package hu.laca.weighttracker.domain

import hu.laca.weighttracker.domain.model.WeeklyAverage
import hu.laca.weighttracker.domain.model.WeightMeasurement
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

object WeeklyAverageCalculator {
    private val iso = WeekFields.ISO

    fun calculate(
        measurements: List<WeightMeasurement>,
        today: LocalDate
    ): List<WeeklyAverage> {
        return measurements
            .filter { !it.date.isAfter(today) }
            .groupBy { isoWeekKey(it.date) }
            .map { (key, items) ->
                val weekStart = weekStart(key.year, key.week)
                val weekEnd = weekStart.plusDays(6)
                val isCurrentWeek = !today.isBefore(weekStart) && !today.isAfter(weekEnd)
                val coveredEnd = if (isCurrentWeek) minOf(today, weekEnd) else weekEnd
                WeeklyAverage(
                    weekBasedYear = key.year,
                    weekOfYear = key.week,
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    coveredEnd = coveredEnd,
                    averageKg = items.map { it.weightKg }.average(),
                    sampleCount = items.size,
                    isCurrentWeek = isCurrentWeek
                )
            }
            .sortedByDescending { it.weekStart }
    }

    fun isoWeekKey(date: LocalDate): IsoWeekKey {
        return IsoWeekKey(
            year = date.get(iso.weekBasedYear()),
            week = date.get(iso.weekOfWeekBasedYear())
        )
    }

    fun weekStart(weekBasedYear: Int, weekOfYear: Int): LocalDate {
        return LocalDate.of(weekBasedYear, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, weekOfYear.toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    data class IsoWeekKey(
        val year: Int,
        val week: Int
    )
}
