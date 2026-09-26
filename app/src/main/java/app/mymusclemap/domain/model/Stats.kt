package app.mymusclemap.domain.model

import java.time.LocalDate

data class MeasurementListItem(
    val measurement: WeightMeasurement,
    val differenceFromPreviousKg: Double?
)

data class ChartPoint(
    val date: LocalDate,
    val weightKg: Double
) {
    fun toSeriesPoint(): SeriesPoint = SeriesPoint(date = date, value = weightKg)
}

enum class ChartRange {
    Days30,
    Days90,
    All
}

data class WeeklyAverage(
    val weekBasedYear: Int,
    val weekOfYear: Int,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val coveredEnd: LocalDate,
    val averageKg: Double,
    val sampleCount: Int,
    val isCurrentWeek: Boolean
)

enum class SaveOutcome {
    Created,
    Updated
}

data class UpsertSummary(
    val createdCount: Int,
    val updatedCount: Int
)
