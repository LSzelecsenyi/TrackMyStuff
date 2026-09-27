package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.BodyMeasurementEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.body.BodyMeasurementType
import app.mymusclemap.domain.model.SaveOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class BodyMeasurementRepositoryTest {
    @Test
    fun sameTypeAndDateUpdatesWithoutChangingIdentity() = runTest {
        val database = database()
        try {
            val repository = BodyMeasurementRepository(
                database.bodyMeasurementDao(),
                Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
            )
            val date = LocalDate.of(2026, 9, 1)
            assertEquals(SaveOutcome.Created, repository.save(BodyMeasurementType.WAIST.code, date, 84.0))
            val created = repository.getByTypeAndDate(BodyMeasurementType.WAIST.code, date)!!
            val later = BodyMeasurementRepository(
                database.bodyMeasurementDao(),
                Clock.fixed(Instant.ofEpochMilli(2_000L), ZoneOffset.UTC)
            )
            assertEquals(SaveOutcome.Updated, later.save(BodyMeasurementType.WAIST.code, date, 83.5))
            val updated = later.getByTypeAndDate(BodyMeasurementType.WAIST.code, date)!!
            assertEquals(created.id, updated.id)
            assertEquals(created.createdAt, updated.createdAt)
            assertEquals(2_000L, updated.updatedAt)
            assertEquals(83.5, updated.value, 0.0)
            assertEquals(1, later.byType(BodyMeasurementType.WAIST.code).size)
            later.delete(updated.id)
            assertNull(later.getByTypeAndDate(BodyMeasurementType.WAIST.code, date))
        } finally {
            database.close()
        }
    }

    @Test
    fun uniqueTypeDateAndExternalIdAreEnforced() = runTest {
        val database = database()
        try {
            val dao = database.bodyMeasurementDao()
            dao.insert(row(type = "WAIST", date = "2026-09-01", externalId = null))
            val duplicateDate = runCatching {
                dao.insert(row(type = "WAIST", date = "2026-09-01", externalId = "a"))
            }
            assertTrue(duplicateDate.isFailure)
            dao.insert(row(type = "CHEST", date = "2026-09-01", externalId = "hc-1"))
            val duplicateExternal = runCatching {
                dao.insert(row(type = "HIPS", date = "2026-09-02", externalId = "hc-1"))
            }
            assertTrue(duplicateExternal.isFailure)
            dao.insert(row(type = "THIGH", date = "2026-09-03", externalId = null))
            assertEquals(3, dao.getAll().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun latestIgnoresDatesAfterToday() = runTest {
        val database = database()
        try {
            val repository = BodyMeasurementRepository(
                database.bodyMeasurementDao(),
                Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
            )
            repository.save(BodyMeasurementType.WAIST.code, LocalDate.of(2026, 8, 1), 90.0)
            repository.save(BodyMeasurementType.WAIST.code, LocalDate.of(2026, 9, 1), 88.0)
            val latest = repository.latestOnOrBefore(BodyMeasurementType.WAIST.code, LocalDate.of(2026, 8, 15))
            assertEquals(LocalDate.of(2026, 8, 1), latest!!.date)
        } finally {
            database.close()
        }
    }

    private fun database(): WeightDatabase {
        return Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WeightDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    private fun row(type: String, date: String, externalId: String?): BodyMeasurementEntity {
        return BodyMeasurementEntity(
            type = type,
            date = date,
            value = 40.0,
            source = "MANUAL",
            externalId = externalId,
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
