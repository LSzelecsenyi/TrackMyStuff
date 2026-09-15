package hu.laca.weighttracker

import hu.laca.weighttracker.domain.model.WeightMeasurement
import java.time.LocalDate

fun measurement(
    date: String,
    weightKg: Double,
    id: Long = date.replace("-", "").toLong()
): WeightMeasurement {
    return WeightMeasurement(
        id = id,
        date = LocalDate.parse(date),
        weightKg = weightKg,
        createdAt = id,
        updatedAt = id
    )
}
