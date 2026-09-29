package app.mymusclemap.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthGrants
import app.mymusclemap.domain.health.HealthMetricBucket
import app.mymusclemap.domain.health.HealthSource
import java.time.LocalDateTime
import java.time.Period
import kotlin.coroutines.cancellation.CancellationException

/**
 * The only production type that talks to [HealthConnectClient].
 * Step totals come from daily [StepsRecord.COUNT_TOTAL] aggregation with no data-origin filter.
 * Resting heart rate is the daily [RestingHeartRateRecord.BPM_AVG], in beats per minute.
 */
class HealthConnectGateway(
    private val context: Context
) : HealthSource {
    override fun availability(): HealthAvailability {
        val status = try {
            HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return HealthAvailability.Unavailable
        }
        return when (status) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthAvailability.ProviderUpdateRequired
            else -> HealthAvailability.Unavailable
        }
    }

    override suspend fun grantedPermissions(): HealthGrants {
        val granted = client().permissionController.getGrantedPermissions()
        return HealthGrants(
            steps = granted.contains(HealthPermission.getReadPermission(StepsRecord::class)),
            restingHeartRate = granted.contains(
                HealthPermission.getReadPermission(RestingHeartRateRecord::class)
            )
        )
    }

    override suspend fun readSteps(
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime
    ): List<HealthMetricBucket> {
        val grouped = client().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startInclusive, endExclusive),
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        return grouped.map { row ->
            HealthMetricBucket(
                date = row.startTime.toLocalDate(),
                value = row.result[StepsRecord.COUNT_TOTAL]
            )
        }
    }

    override suspend fun readRestingHeartRate(
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime
    ): List<HealthMetricBucket> {
        val grouped = client().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(RestingHeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(startInclusive, endExclusive),
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        return grouped.map { row ->
            HealthMetricBucket(
                date = row.startTime.toLocalDate(),
                value = row.result[RestingHeartRateRecord.BPM_AVG]
            )
        }
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        const val VIEW_PERMISSION_USAGE = "android.intent.action.VIEW_PERMISSION_USAGE"

        fun readPermissions(): Set<String> = linkedSetOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        )

        fun isPermissionUsage(intent: Intent?): Boolean {
            return intent?.action == VIEW_PERMISSION_USAGE
        }

        fun providerInstallIntent(callerPackage: String): Intent {
            val uri = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
            return Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.android.vending")
                data = Uri.parse(uri)
                putExtra("overlay", true)
                putExtra("callerId", callerPackage)
            }
        }
    }
}
