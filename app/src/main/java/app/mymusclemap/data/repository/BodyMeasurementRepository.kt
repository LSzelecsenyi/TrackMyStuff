package app.mymusclemap.data.repository

import app.mymusclemap.data.local.BodyMeasurementDao
import app.mymusclemap.data.local.BodyMeasurementEntity
import app.mymusclemap.data.local.toModel
import app.mymusclemap.domain.body.BODY_MEASUREMENT_SOURCE_MANUAL
import app.mymusclemap.domain.body.BodyMeasurement
import app.mymusclemap.domain.model.SaveOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

class BodyMeasurementRepository(
    private val dao: BodyMeasurementDao,
    private val clock: Clock
) {
    fun observeAll(): Flow<List<BodyMeasurement>> {
        return dao.observeAll().map { rows -> rows.map { it.toModel() } }
    }

    fun observeByType(typeCode: String): Flow<List<BodyMeasurement>> {
        return dao.observeByType(typeCode).map { rows -> rows.map { it.toModel() } }
    }

    suspend fun all(): List<BodyMeasurement> = dao.getAll().map { it.toModel() }

    suspend fun byType(typeCode: String): List<BodyMeasurement> {
        return dao.getByType(typeCode).map { it.toModel() }
    }

    suspend fun getByTypeAndDate(typeCode: String, date: LocalDate): BodyMeasurement? {
        return dao.getByTypeAndDate(typeCode, date.toString())?.toModel()
    }

    suspend fun latestOnOrBefore(typeCode: String, today: LocalDate): BodyMeasurement? {
        return dao.latestOnOrBefore(typeCode, today.toString())?.toModel()
    }

    suspend fun save(
        typeCode: String,
        date: LocalDate,
        value: Double,
        source: String = BODY_MEASUREMENT_SOURCE_MANUAL,
        externalId: String? = null
    ): SaveOutcome {
        val now = clock.millis()
        val existing = dao.getByTypeAndDate(typeCode, date.toString())
        return if (existing == null) {
            dao.insert(
                BodyMeasurementEntity(
                    type = typeCode,
                    date = date.toString(),
                    value = value,
                    source = source,
                    externalId = externalId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            SaveOutcome.Created
        } else {
            dao.update(
                existing.copy(
                    value = value,
                    updatedAt = now
                )
            )
            SaveOutcome.Updated
        }
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
