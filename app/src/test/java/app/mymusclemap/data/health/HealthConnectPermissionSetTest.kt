package app.mymusclemap.data.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.Period

@RunWith(RobolectricTestRunner::class)
class HealthConnectPermissionSetTest {
    @Test
    fun requestedPermissionsAreOnlyStepsAndRestingHeartRateReads() {
        val requested = HealthConnectGateway.readPermissions()
        assertEquals(
            setOf(
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(RestingHeartRateRecord::class)
            ),
            requested
        )
        val joined = requested.joinToString(" ")
        assertFalse(joined.contains("WRITE"))
        assertFalse(joined.contains("HISTORY"))
        assertFalse(joined.contains("BACKGROUND"))
        assertFalse(joined.contains("WEIGHT"))
        assertFalse(joined.contains("BODY_FAT"))
        assertTrue(joined.contains("READ_STEPS"))
        assertTrue(joined.contains("READ_RESTING_HEART_RATE"))
    }

    @Test
    fun fakeAggregateGroupByPeriodDoesNotComputeADailyTotal() = runTest {
        val fake = FakeHealthConnectClient()
        val today = LocalDate.of(2026, 9, 28)
        try {
            fake.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                    ),
                    timeRangeSlicer = Period.ofDays(1)
                )
            )
            fail("FakeHealthConnectClient must not invent an aggregated daily total")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("overrides.aggregateGroupByPeriod"))
        }
        assertEquals(3, HealthConnectClient.SDK_AVAILABLE)
        assertEquals(2, HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
        assertEquals(1, HealthConnectClient.SDK_UNAVAILABLE)
    }
}
