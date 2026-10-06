package app.mymusclemap.data.repository

import app.mymusclemap.data.local.WeightMeasurementDao
import app.mymusclemap.data.local.WeightMeasurementEntity
import app.mymusclemap.data.local.toModel
import app.mymusclemap.domain.model.SaveOutcome
import app.mymusclemap.domain.model.WeightMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

class WeightRepository(
    private val dao: WeightMeasurementDao,
    private val clock: Clock,
    private val onWeightChanged: suspend () -> Unit = {}
) {
    fun observeAll(): Flow<List<WeightMeasurement>> {
        return dao.observeAllAscending().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun all(): List<WeightMeasurement> {
        return dao.getAllAscending().map { it.toModel() }
    }

    suspend fun getByDate(date: LocalDate): WeightMeasurement? {
        return dao.getByDate(date.toString())?.toModel()
    }

    suspend fun getLatestBefore(date: LocalDate): WeightMeasurement? {
        return dao.getLatestBefore(date.toString())?.toModel()
    }

    suspend fun save(date: LocalDate, weightKg: Double): SaveOutcome {
        val now = clock.millis()
        val existing = dao.getByDate(date.toString())
        return if (existing == null) {
            dao.insert(
                WeightMeasurementEntity(
                    date = date.toString(),
                    weightKg = weightKg,
                    createdAt = now,
                    updatedAt = now
                )
            )
            SaveOutcome.Created
        } else {
            dao.update(
                existing.copy(
                    weightKg = weightKg,
                    updatedAt = now
                )
            )
            SaveOutcome.Updated
        }.also {
            notifyChanged()
        }
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
        notifyChanged()
    }

    private suspend fun notifyChanged() {
        try {
            onWeightChanged()
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
}
