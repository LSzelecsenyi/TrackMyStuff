package hu.laca.weighttracker.domain

import hu.laca.weighttracker.domain.model.ChartPoint
import hu.laca.weighttracker.domain.model.ChartRange
import hu.laca.weighttracker.domain.model.MeasurementListItem
import hu.laca.weighttracker.domain.model.WeeklyAverage
import hu.laca.weighttracker.domain.model.WeightMeasurement
import java.time.LocalDate

object MeasurementDiffs {
    fun withChronologicalDifferences(
        measurements: List<WeightMeasurement>
    ): List<MeasurementListItem> {
        val chronological = measurements.sortedWith(compareBy({ it.date }, { it.id }))
        val items = chronological.mapIndexed { index, measurement ->
            val previous = chronological.getOrNull(index - 1)
            MeasurementListItem(
                measurement = measurement,
                differenceFromPreviousKg = previous?.let { measurement.weightKg - it.weightKg }
            )
        }
        return items.sortedWith(compareByDescending<MeasurementListItem> { it.measurement.date }
            .thenByDescending { it.measurement.id })
    }
}

data class DashboardSnapshot(
    val isEmpty: Boolean,
    val latest: WeightMeasurement?,
    val changeFromPreviousKg: Double?,
    val currentWeek: WeeklyAverage?,
    val previousWeekChangeKg: Double?,
    val recentWeeks: List<WeeklyAverage>,
    val chartPoints: List<ChartPoint>,
    val recentItems: List<MeasurementListItem>,
    val todayHasMeasurement: Boolean
)

object DashboardAssembler {
    fun assemble(
        measurements: List<WeightMeasurement>,
        today: LocalDate,
        range: ChartRange,
        recentLimit: Int = 5,
        recentWeekLimit: Int = 6
    ): DashboardSnapshot {
        val chronological = measurements.sortedWith(compareBy({ it.date }, { it.id }))
        val withDiffs = MeasurementDiffs.withChronologicalDifferences(chronological)
        val latest = chronological.lastOrNull()
        val previous = chronological.getOrNull(chronological.lastIndex - 1)
        val weeks = WeeklyAverageCalculator.calculate(chronological, today)
        val currentWeek = weeks.firstOrNull { it.isCurrentWeek }
        val previousWeek = currentWeek?.let { current ->
            weeks.firstOrNull { week ->
                week.weekStart == current.weekStart.minusWeeks(1)
            }
        }
        val previousWeekChange = if (currentWeek != null && previousWeek != null) {
            currentWeek.averageKg - previousWeek.averageKg
        } else {
            null
        }
        return DashboardSnapshot(
            isEmpty = chronological.isEmpty(),
            latest = latest,
            changeFromPreviousKg = if (latest != null && previous != null) {
                latest.weightKg - previous.weightKg
            } else {
                null
            },
            currentWeek = currentWeek,
            previousWeekChangeKg = previousWeekChange,
            recentWeeks = weeks.take(recentWeekLimit),
            chartPoints = chartPoints(chronological, today, range),
            recentItems = withDiffs.take(recentLimit),
            todayHasMeasurement = chronological.any { it.date == today }
        )
    }

    fun chartPoints(
        measurements: List<WeightMeasurement>,
        today: LocalDate,
        range: ChartRange
    ): List<ChartPoint> {
        val startDate = when (range) {
            ChartRange.Days30 -> today.minusDays(29)
            ChartRange.Days90 -> today.minusDays(89)
            ChartRange.All -> null
        }
        return measurements
            .filter { !it.date.isAfter(today) }
            .filter { startDate == null || !it.date.isBefore(startDate) }
            .sortedBy { it.date }
            .map { ChartPoint(date = it.date, weightKg = it.weightKg) }
    }
}
