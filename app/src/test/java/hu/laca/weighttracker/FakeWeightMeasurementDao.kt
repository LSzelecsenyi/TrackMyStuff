package hu.laca.weighttracker

import hu.laca.weighttracker.data.local.WeightMeasurementDao
import hu.laca.weighttracker.data.local.WeightMeasurementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWeightMeasurementDao : WeightMeasurementDao {
    private val items = linkedMapOf<Long, WeightMeasurementEntity>()
    private val flow = MutableStateFlow<List<WeightMeasurementEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAllAscending(): Flow<List<WeightMeasurementEntity>> = flow

    override suspend fun getAllAscending(): List<WeightMeasurementEntity> {
        return items.values.sortedWith(compareBy({ it.date }, { it.id }))
    }

    override suspend fun getByDate(date: String): WeightMeasurementEntity? {
        return items.values.firstOrNull { it.date == date }
    }

    override suspend fun getLatestBefore(date: String): WeightMeasurementEntity? {
        return items.values.filter { it.date < date }.maxByOrNull { it.date }
    }

    override suspend fun insert(entity: WeightMeasurementEntity): Long {
        if (items.values.any { it.date == entity.date }) {
            throw IllegalStateException("UNIQUE constraint failed: date")
        }
        val id = if (entity.id == 0L) nextId++ else entity.id
        items[id] = entity.copy(id = id)
        publish()
        return id
    }

    override suspend fun update(entity: WeightMeasurementEntity) {
        val current = items[entity.id] ?: error("missing id ${entity.id}")
        if (items.values.any { it.date == entity.date && it.id != entity.id }) {
            throw IllegalStateException("UNIQUE constraint failed: date")
        }
        items[entity.id] = entity.copy(createdAt = current.createdAt)
        publish()
    }

    override suspend fun deleteById(id: Long) {
        items.remove(id)
        publish()
    }

    private fun publish() {
        flow.value = items.values.sortedWith(compareBy({ it.date }, { it.id }))
    }
}
