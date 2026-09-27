package app.mymusclemap.domain.body

import java.time.LocalDate

const val BODY_MEASUREMENT_SOURCE_MANUAL = "MANUAL"

data class BodyMeasurement(
    val id: Long,
    val typeCode: String,
    val date: LocalDate,
    val value: Double,
    val source: String,
    val externalId: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    val knownType: BodyMeasurementType? get() = BodyMeasurementType.known(typeCode)
}
