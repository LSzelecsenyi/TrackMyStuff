package app.mymusclemap.domain.health

import java.time.LocalDate
import java.time.LocalDateTime

enum class HealthAvailability {
    Available,
    ProviderUpdateRequired,
    Unavailable
}

data class HealthGrants(
    val steps: Boolean,
    val restingHeartRate: Boolean
)

data class HealthAccess(
    val availability: HealthAvailability = HealthAvailability.Unavailable,
    val stepsGranted: Boolean = false,
    val restingHeartRateGranted: Boolean = false,
    val checked: Boolean = false
)

data class DailyStepTotal(
    val date: LocalDate,
    val steps: Long
)

data class DailyRestingHeartRate(
    val date: LocalDate,
    val beatsPerMinute: Long
)

data class HealthReadings(
    val steps: List<DailyStepTotal> = emptyList(),
    val restingHeartRate: List<DailyRestingHeartRate> = emptyList(),
    val readFailed: Boolean = false
)

/** One already-aggregated daily bucket. A null [value] is missing data, not zero. */
data class HealthMetricBucket(
    val date: LocalDate,
    val value: Long?
)

/**
 * Read port for Health Connect. Implementations may use the platform client.
 * Callers outside the health integration only see [HealthAccess] and [HealthReadings].
 */
interface HealthSource {
    fun availability(): HealthAvailability

    suspend fun grantedPermissions(): HealthGrants

    suspend fun readSteps(
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime
    ): List<HealthMetricBucket>

    suspend fun readRestingHeartRate(
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime
    ): List<HealthMetricBucket>

    companion object {
        val Unavailable: HealthSource = object : HealthSource {
            override fun availability(): HealthAvailability = HealthAvailability.Unavailable

            override suspend fun grantedPermissions(): HealthGrants = HealthGrants(
                steps = false,
                restingHeartRate = false
            )

            override suspend fun readSteps(
                startInclusive: LocalDateTime,
                endExclusive: LocalDateTime
            ): List<HealthMetricBucket> = emptyList()

            override suspend fun readRestingHeartRate(
                startInclusive: LocalDateTime,
                endExclusive: LocalDateTime
            ): List<HealthMetricBucket> = emptyList()
        }
    }
}
