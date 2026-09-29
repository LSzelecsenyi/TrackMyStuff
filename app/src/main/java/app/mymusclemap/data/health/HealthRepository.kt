package app.mymusclemap.data.health

import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.health.HealthAccess
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthExerciseAggregator
import app.mymusclemap.domain.health.HealthHrvAggregator
import app.mymusclemap.domain.health.HealthMetric
import app.mymusclemap.domain.health.HealthReadingMapper
import app.mymusclemap.domain.health.HealthReadings
import app.mymusclemap.domain.health.HealthSleepAggregator
import app.mymusclemap.domain.health.HealthSource
import app.mymusclemap.domain.health.HealthWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * In-memory Health Connect session. Readings are never written to Room, backup, or weight storage.
 */
class HealthRepository(
    private val source: HealthSource,
    private val dateProvider: DateProvider
) {
    private val mutex = Mutex()
    private val accessState = MutableStateFlow(HealthAccess())
    private val readingsState = MutableStateFlow(HealthReadings())

    val access: StateFlow<HealthAccess> = accessState.asStateFlow()
    val readings: StateFlow<HealthReadings> = readingsState.asStateFlow()

    suspend fun refresh() {
        mutex.withLock {
            val availability = try {
                source.availability()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                accessState.value = HealthAccess(
                    availability = HealthAvailability.Unavailable,
                    checked = true
                )
                readingsState.value = HealthReadings()
                return
            }
            if (availability != HealthAvailability.Available) {
                accessState.value = HealthAccess(availability = availability, checked = true)
                readingsState.value = HealthReadings()
                return
            }
            val grants = try {
                source.grantedPermissions()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                accessState.value = HealthAccess(
                    availability = HealthAvailability.Available,
                    checked = true
                )
                readingsState.value = HealthReadings(readFailed = true)
                return
            }
            accessState.value = HealthAccess(
                availability = HealthAvailability.Available,
                stepsGranted = grants.steps,
                restingHeartRateGranted = grants.restingHeartRate,
                checked = true,
                exerciseGranted = grants.exercise,
                hrvGranted = grants.hrv,
                sleepGranted = grants.sleep
            )
            if (!grants.any()) {
                readingsState.value = HealthReadings()
                return
            }
            val today = dateProvider.today()
            val (start, endExclusive) = HealthWindow.queryRange(today)
            val failed = mutableSetOf<HealthMetric>()
            val steps = readMetric(grants.steps, HealthMetric.STEPS, failed) {
                HealthReadingMapper.steps(source.readSteps(start, endExclusive), today)
            }
            val heart = readMetric(grants.restingHeartRate, HealthMetric.RESTING_HEART_RATE, failed) {
                HealthReadingMapper.restingHeartRate(
                    source.readRestingHeartRate(start, endExclusive),
                    today
                )
            }
            val exercise = readMetric(grants.exercise, HealthMetric.EXERCISE, failed) {
                HealthExerciseAggregator.daily(source.readExerciseSessions(start, endExclusive), today)
            }
            val hrv = readMetric(grants.hrv, HealthMetric.HRV, failed) {
                HealthHrvAggregator.daily(source.readHrvSamples(start, endExclusive), today)
            }
            val sleep = readMetric(grants.sleep, HealthMetric.SLEEP, failed) {
                HealthSleepAggregator.daily(source.readSleepSpans(start, endExclusive), today)
            }
            val overviewFailed = failed.contains(HealthMetric.STEPS) ||
                failed.contains(HealthMetric.RESTING_HEART_RATE)
            readingsState.value = HealthReadings(
                steps = steps,
                restingHeartRate = heart,
                readFailed = overviewFailed,
                exercise = exercise,
                hrv = hrv,
                sleep = sleep,
                failed = failed
            )
        }
    }

    private suspend fun <T> readMetric(
        granted: Boolean,
        metric: HealthMetric,
        failed: MutableSet<HealthMetric>,
        read: suspend () -> List<T>
    ): List<T> {
        if (!granted) return emptyList()
        return try {
            read()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed += metric
            emptyList()
        }
    }
}
