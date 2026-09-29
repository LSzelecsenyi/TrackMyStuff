package app.mymusclemap.data.health

import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthGrants
import app.mymusclemap.domain.health.HealthMetricBucket
import app.mymusclemap.domain.health.HealthSource
import app.mymusclemap.domain.health.HealthWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

class HealthRepositoryTest {
    private val today = LocalDate.of(2024, 6, 15)
    private val source = FakeHealthSource()
    private val repository = HealthRepository(source, FixedDateProvider(today))

    @Test
    fun unavailableDoesNotReadAndDoesNotInventReadings() = runTest {
        source.availability = HealthAvailability.Unavailable
        repository.refresh()
        assertEquals(HealthAvailability.Unavailable, repository.access.value.availability)
        assertFalse(repository.access.value.stepsGranted)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertEquals(0, source.stepReads)
        assertEquals(0, source.heartReads)
    }

    @Test
    fun providerUpdateRequiredDoesNotRead() = runTest {
        source.availability = HealthAvailability.ProviderUpdateRequired
        repository.refresh()
        assertEquals(HealthAvailability.ProviderUpdateRequired, repository.access.value.availability)
        assertEquals(0, source.permissionReads)
        assertEquals(0, source.stepReads)
    }

    @Test
    fun availableWithoutPermissionsShowsNothingAsReadings() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = false, restingHeartRate = false)
        source.steps = listOf(HealthMetricBucket(today, 8_000))
        repository.refresh()
        assertTrue(repository.access.value.checked)
        assertFalse(repository.access.value.stepsGranted)
        assertFalse(repository.access.value.restingHeartRateGranted)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertFalse(repository.readings.value.readFailed)
        assertEquals(0, source.stepReads)
        assertEquals(0, source.heartReads)
    }

    @Test
    fun stepsOnlyStillReadsStepsWhenHeartRateIsDenied() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = false)
        source.steps = listOf(
            HealthMetricBucket(today.minusDays(1), null),
            HealthMetricBucket(today, 7_300)
        )
        repository.refresh()
        assertTrue(repository.access.value.stepsGranted)
        assertFalse(repository.access.value.restingHeartRateGranted)
        assertEquals(1, repository.readings.value.steps.size)
        assertEquals(7_300L, repository.readings.value.steps.single().steps)
        assertTrue(repository.readings.value.restingHeartRate.isEmpty())
        assertEquals(0, source.heartReads)
        assertNull(repository.readings.value.steps.firstOrNull { it.date == today.minusDays(1) })
    }

    @Test
    fun heartRateOnlyStillReadsHeartRateWhenStepsAreDenied() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = false, restingHeartRate = true)
        source.heart = listOf(HealthMetricBucket(today, 59))
        repository.refresh()
        assertFalse(repository.access.value.stepsGranted)
        assertEquals(59L, repository.readings.value.restingHeartRate.single().beatsPerMinute)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertEquals(0, source.stepReads)
    }

    @Test
    fun bothGrantedMapDailyValuesInsideTheThirtyDayQuery() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(
            HealthMetricBucket(today.minusDays(2), 6_500),
            HealthMetricBucket(today, 7_300),
            HealthMetricBucket(today.plusDays(1), 99_999),
            HealthMetricBucket(HealthWindow.start(today).minusDays(1), 111)
        )
        source.heart = listOf(HealthMetricBucket(today, 59), HealthMetricBucket(today.minusDays(4), 62))
        repository.refresh()
        val (start, end) = HealthWindow.queryRange(today)
        assertEquals(start, source.stepStart)
        assertEquals(end, source.stepEnd)
        assertEquals(start, source.heartStart)
        assertEquals(end, source.heartEnd)
        assertEquals(
            listOf(today.minusDays(2), today),
            repository.readings.value.steps.map { it.date }
        )
        assertEquals(listOf(62L, 59L), repository.readings.value.restingHeartRate.map { it.beatsPerMinute })
    }

    @Test
    fun noDataStaysEmptyWithoutFailure() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        repository.refresh()
        assertTrue(repository.readings.value.steps.isEmpty())
        assertTrue(repository.readings.value.restingHeartRate.isEmpty())
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun revokedStepsClearStepsAndKeepHeartRate() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(HealthMetricBucket(today, 5_100))
        source.heart = listOf(HealthMetricBucket(today, 61))
        repository.refresh()
        assertEquals(5_100L, repository.readings.value.steps.single().steps)

        source.grants = HealthGrants(steps = false, restingHeartRate = true)
        repository.refresh()
        assertFalse(repository.access.value.stepsGranted)
        assertTrue(repository.access.value.restingHeartRateGranted)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertEquals(61L, repository.readings.value.restingHeartRate.single().beatsPerMinute)
    }

    @Test
    fun revokedHeartRateClearsHeartRateAndKeepsSteps() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(HealthMetricBucket(today, 7_300))
        source.heart = listOf(HealthMetricBucket(today, 59))
        repository.refresh()

        source.grants = HealthGrants(steps = true, restingHeartRate = false)
        repository.refresh()
        assertTrue(repository.access.value.stepsGranted)
        assertFalse(repository.access.value.restingHeartRateGranted)
        assertEquals(7_300L, repository.readings.value.steps.single().steps)
        assertTrue(repository.readings.value.restingHeartRate.isEmpty())
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun revokedBothClearsReadingsWithoutFailure() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(HealthMetricBucket(today, 1))
        repository.refresh()
        source.grants = HealthGrants(steps = false, restingHeartRate = false)
        repository.refresh()
        assertFalse(repository.access.value.stepsGranted)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun dayAfterTheLastReadingKeepsYesterdayAndDoesNotInventToday() = runTest {
        val today = LocalDate.of(2026, 9, 29)
        val source = FakeHealthSource()
        val repository = HealthRepository(source, FixedDateProvider(today))
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(
            HealthMetricBucket(LocalDate.of(2026, 9, 28), 7_300),
            HealthMetricBucket(today, null)
        )
        source.heart = listOf(HealthMetricBucket(LocalDate.of(2026, 9, 28), 59))
        repository.refresh()
        val (start, end) = HealthWindow.queryRange(today)
        assertEquals(start, source.stepStart)
        assertEquals(end, source.stepEnd)
        assertEquals(LocalDate.of(2026, 9, 28), repository.readings.value.steps.single().date)
        assertEquals(7_300L, repository.readings.value.steps.single().steps)
        assertEquals(59L, repository.readings.value.restingHeartRate.single().beatsPerMinute)
        assertTrue(repository.readings.value.steps.none { it.date == today })
        assertTrue(repository.readings.value.restingHeartRate.none { it.date == today })
    }

    @Test
    fun readFailureDoesNotKeepPartialNumbers() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.failReads = true
        repository.refresh()
        assertTrue(repository.access.value.stepsGranted)
        assertTrue(repository.readings.value.readFailed)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertTrue(repository.readings.value.restingHeartRate.isEmpty())
    }

    private class FakeHealthSource : HealthSource {
        var availability: HealthAvailability = HealthAvailability.Unavailable
        var grants = HealthGrants(steps = false, restingHeartRate = false)
        var steps: List<HealthMetricBucket> = emptyList()
        var heart: List<HealthMetricBucket> = emptyList()
        var failReads = false
        var permissionReads = 0
        var stepReads = 0
        var heartReads = 0
        var stepStart: LocalDateTime? = null
        var stepEnd: LocalDateTime? = null
        var heartStart: LocalDateTime? = null
        var heartEnd: LocalDateTime? = null

        override fun availability(): HealthAvailability = availability

        override suspend fun grantedPermissions(): HealthGrants {
            permissionReads += 1
            return grants
        }

        override suspend fun readSteps(
            startInclusive: LocalDateTime,
            endExclusive: LocalDateTime
        ): List<HealthMetricBucket> {
            stepReads += 1
            stepStart = startInclusive
            stepEnd = endExclusive
            if (failReads) throw IOException("health read failed")
            return steps
        }

        override suspend fun readRestingHeartRate(
            startInclusive: LocalDateTime,
            endExclusive: LocalDateTime
        ): List<HealthMetricBucket> {
            heartReads += 1
            heartStart = startInclusive
            heartEnd = endExclusive
            if (failReads) throw IOException("health read failed")
            return heart
        }
    }
}
