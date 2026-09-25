package app.mymusclemap.data.local

import app.mymusclemap.domain.model.WeightMeasurement
import java.time.LocalDate

fun WeightMeasurementEntity.toModel(): WeightMeasurement {
    return WeightMeasurement(
        id = id,
        date = LocalDate.parse(date),
        weightKg = weightKg,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
