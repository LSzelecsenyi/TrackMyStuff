package hu.laca.weighttracker.domain

import hu.laca.weighttracker.domain.model.WeightMeasurement
import java.time.LocalDate

data class DaySheetState(
    val date: LocalDate,
    val measurement: WeightMeasurement?,
    val differenceFromPreviousKg: Double?
) {
    val hasMeasurement: Boolean get() = measurement != null
}

object DaySheetFactory {
    fun create(
        date: LocalDate,
        measurements: List<WeightMeasurement>,
        today: LocalDate
    ): DaySheetState? {
        if (date.isAfter(today)) return null
        val chronological = measurements.sortedWith(compareBy({ it.date }, { it.id }))
        val current = chronological.lastOrNull { it.date == date }
        val previous = chronological.lastOrNull { it.date.isBefore(date) }
        return DaySheetState(
            date = date,
            measurement = current,
            differenceFromPreviousKg = if (current != null && previous != null) {
                current.weightKg - previous.weightKg
            } else {
                null
            }
        )
    }
}
