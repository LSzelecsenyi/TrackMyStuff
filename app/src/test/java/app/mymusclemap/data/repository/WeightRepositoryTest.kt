package app.mymusclemap.data.repository

import app.mymusclemap.FakeWeightMeasurementDao
import app.mymusclemap.domain.model.SaveOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WeightRepositoryTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-03-11T10:15:30Z"), ZoneOffset.UTC)

    @Test
    fun savingTheSameDateUpdatesInsteadOfInsertingADuplicate() = runTest {
        val dao = FakeWeightMeasurementDao()
        val repository = WeightRepository(dao, clock)
        val date = LocalDate.of(2026, 3, 11)

        val first = repository.save(date, 80.0)
        val original = repository.observeAll().first().single()
        val second = repository.save(date, 81.5)
        val updated = repository.observeAll().first().single()

        assertEquals(SaveOutcome.Created, first)
        assertEquals(SaveOutcome.Updated, second)
        assertEquals(1, repository.observeAll().first().size)
        assertEquals(original.id, updated.id)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(81.5, updated.weightKg, 0.0)
        assertEquals(clock.millis(), updated.updatedAt)
    }
}
