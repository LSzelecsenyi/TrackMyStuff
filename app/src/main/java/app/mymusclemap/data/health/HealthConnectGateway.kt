package app.mymusclemap.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthExerciseClassifier
import app.mymusclemap.domain.health.HealthExerciseKind
import app.mymusclemap.domain.health.HealthExerciseSession
import app.mymusclemap.domain.health.HealthGrants
import app.mymusclemap.domain.health.HealthHrvSample
import app.mymusclemap.domain.health.HealthMetricBucket
import app.mymusclemap.domain.health.HealthSleepSpan
import app.mymusclemap.domain.health.HealthSource
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * The only production type that talks to [HealthConnectClient].
 * Step totals come from daily [StepsRecord.COUNT_TOTAL] aggregation with no data-origin filter.
 * Resting heart rate is the daily [RestingHeartRateRecord.BPM_AVG], in beats per minute.
 * Exercise, HRV, and sleep are read as records and mapped to domain types here.
 * Distance records are not read. Session distance cannot be attributed without a route.
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
            ),
            exercise = granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class)),
            hrv = granted.contains(
                HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
            ),
            sleep = granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))
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

    override suspend fun readExerciseSessions(
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime
    ): List<HealthExerciseSession> {
        return readAll(ExerciseSessionRecord::class, startInclusive, endExclusive).mapNotNull { record ->
            val kind = HealthExerciseClassifier.classify(record.exerciseType)
            if (kind == HealthExerciseKind.OTHER) return@mapNotNull null
            HealthExerciseSession(
                start = record.startTime,
                end = record.endTime,
                zone = zoneOf(record.startZoneOffset),
                kind = kind
            )
        }
    }

    override suspend fun readHrvSamples(
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime
    ): List<HealthHrvSample> {
        return readAll(HeartRateVariabilityRmssdRecord::class, startInclusive, endExclusive).map { record ->
            HealthHrvSample(
                time = record.time,
                zone = zoneOf(record.zoneOffset),
                millis = record.heartRateVariabilityMillis
            )
        }
    }

    override suspend fun readSleepSpans(
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime
    ): List<HealthSleepSpan> {
        return readAll(SleepSessionRecord::class, startInclusive, endExclusive).flatMap { record ->
            val zone = zoneOf(record.startZoneOffset)
            if (record.stages.isEmpty()) {
                listOf(HealthSleepSpan(record.startTime, record.endTime, zone))
            } else {
                record.stages.mapNotNull { stage ->
                    if (isAwake(stage.stage)) {
                        null
                    } else {
                        HealthSleepSpan(stage.startTime, stage.endTime, zone)
                    }
                }
            }
        }
    }

    private suspend fun <T : Record> readAll(
        type: KClass<T>,
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime
    ): List<T> {
        val records = ArrayList<T>()
        var token: String? = null
        var pages = 0
        do {
            val response = client().readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(startInclusive, endExclusive),
                    pageToken = token
                )
            )
            records += response.records
            token = response.pageToken
            pages += 1
        } while (!token.isNullOrEmpty() && pages < MAX_RECORD_PAGES)
        return records
    }

    private fun zoneOf(offset: java.time.ZoneOffset?): ZoneId {
        return offset ?: ZoneId.systemDefault()
    }

    private fun isAwake(stage: Int): Boolean {
        return stage == SleepSessionRecord.STAGE_TYPE_AWAKE ||
            stage == SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED ||
            stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        const val VIEW_PERMISSION_USAGE = "android.intent.action.VIEW_PERMISSION_USAGE"
        private const val MAX_RECORD_PAGES = 20

        fun readPermissions(): Set<String> = linkedSetOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
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
