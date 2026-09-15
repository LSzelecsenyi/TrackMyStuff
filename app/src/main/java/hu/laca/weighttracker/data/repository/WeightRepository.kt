package hu.laca.weighttracker.data.repository

import hu.laca.weighttracker.data.local.WeightMeasurementDao
import hu.laca.weighttracker.data.local.WeightMeasurementEntity
import hu.laca.weighttracker.data.local.toModel
import hu.laca.weighttracker.domain.csv.WeightCsv
import hu.laca.weighttracker.domain.model.SaveOutcome
import hu.laca.weighttracker.domain.model.UpsertSummary
import hu.laca.weighttracker.domain.model.WeightMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

class WeightRepository(
    private val dao: WeightMeasurementDao,
    private val clock: Clock
) {
    fun observeAll(): Flow<List<WeightMeasurement>> {
        return dao.observeAllAscending().map { entities ->
            entities.map { it.toModel() }
        }
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
        }
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun importRows(rows: List<WeightCsv.ParsedRow>): UpsertSummary {
        var created = 0
        var updated = 0
        rows.forEach { row ->
            when (save(row.date, row.weightKg)) {
                SaveOutcome.Created -> created++
                SaveOutcome.Updated -> updated++
            }
        }
        return UpsertSummary(createdCount = created, updatedCount = updated)
    }
}
