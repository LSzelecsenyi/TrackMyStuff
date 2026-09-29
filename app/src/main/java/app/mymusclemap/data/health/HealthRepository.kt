package app.mymusclemap.data.health

import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.health.HealthAccess
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthReadingMapper
import app.mymusclemap.domain.health.HealthReadings
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
                checked = true
            )
            if (!grants.steps && !grants.restingHeartRate) {
                readingsState.value = HealthReadings()
                return
            }
            val today = dateProvider.today()
            val (start, endExclusive) = HealthWindow.queryRange(today)
            try {
                val steps = if (grants.steps) {
                    HealthReadingMapper.steps(source.readSteps(start, endExclusive), today)
                } else {
                    emptyList()
                }
                val heart = if (grants.restingHeartRate) {
                    HealthReadingMapper.restingHeartRate(
                        source.readRestingHeartRate(start, endExclusive),
                        today
                    )
                } else {
                    emptyList()
                }
                readingsState.value = HealthReadings(steps = steps, restingHeartRate = heart)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                readingsState.value = HealthReadings(readFailed = true)
            }
        }
    }
}
