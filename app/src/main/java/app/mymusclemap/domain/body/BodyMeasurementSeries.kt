package app.mymusclemap.domain.body

import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.model.ChartRange
import java.time.LocalDate

data class BodyMeasurementWindow(
    val latest: BodyMeasurement?,
    val points: List<BodyMeasurement>,
    val change: Double?
)

object BodyMeasurementSeries {
    fun chronological(measurements: List<BodyMeasurement>): List<BodyMeasurement> {
        return measurements.sortedWith(compareBy({ it.date }, { it.id }))
    }

    fun latestOnOrBefore(measurements: List<BodyMeasurement>, today: LocalDate): BodyMeasurement? {
        return chronological(measurements).lastOrNull { !it.date.isAfter(today) }
    }

    fun window(
        measurements: List<BodyMeasurement>,
        today: LocalDate,
        range: ChartRange
    ): BodyMeasurementWindow {
        val start = when (range) {
            ChartRange.Days30 -> today.minusDays(29)
            ChartRange.Days90 -> today.minusDays(89)
            ChartRange.All -> null
        }
        val points = chronological(measurements).filter { measurement ->
            !measurement.date.isAfter(today) && (start == null || !measurement.date.isBefore(start))
        }
        val change = if (points.size >= 2) {
            points.last().value - points.first().value
        } else {
            null
        }
        return BodyMeasurementWindow(
            latest = latestOnOrBefore(measurements, today),
            points = points,
            change = change
        )
    }
}

object BodyMeasurementAccess {
    fun canCreateOnDate(
        type: BodyMeasurementType,
        dateAlreadyStored: Boolean,
        hasAccess: (AppFeature) -> Boolean
    ): Boolean {
        if (dateAlreadyStored) return true
        val feature = type.requiredFeature ?: return true
        return hasAccess(feature)
    }
}
