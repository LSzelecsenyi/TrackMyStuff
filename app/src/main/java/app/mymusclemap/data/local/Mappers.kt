package app.mymusclemap.data.local

import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.progress.ProgressPhoto
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

fun BodyMeasurementEntity.toModel(): app.mymusclemap.domain.body.BodyMeasurement {
    return app.mymusclemap.domain.body.BodyMeasurement(
        id = id,
        typeCode = type,
        date = LocalDate.parse(date),
        value = value,
        source = source,
        externalId = externalId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ProgressPhotoEntity.toModel(): ProgressPhoto {
    return ProgressPhoto(
        id = id,
        date = LocalDate.parse(date),
        fileName = fileName,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
