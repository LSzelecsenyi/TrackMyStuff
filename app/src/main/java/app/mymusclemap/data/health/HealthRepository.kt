package app.mymusclemap.data.health

import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.health.ExerciseHistory
import app.mymusclemap.domain.health.ExerciseHistoryBoundary
import app.mymusclemap.domain.health.ExerciseHistoryEntry
import app.mymusclemap.domain.health.ExerciseHistoryQuery
import app.mymusclemap.domain.health.ExerciseHistorySnapshot
import app.mymusclemap.domain.health.HealthAccess
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthExerciseAggregator
import app.mymusclemap.domain.health.HealthExerciseSession
import app.mymusclemap.domain.health.HealthHistoryAccess
import app.mymusclemap.domain.health.HealthHrvAggregator
import app.mymusclemap.domain.health.HealthMetric
import app.mymusclemap.domain.health.HealthReadingMapper
import app.mymusclemap.domain.health.HealthReadings
import app.mymusclemap.domain.health.HealthSleepAggregator
import app.mymusclemap.domain.health.HealthSource
import app.mymusclemap.domain.health.HealthWindow
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * In-memory Health Connect session. Readings are never written to Room, backup, or workout history.
 * Blocking external-workout import skips a new exercise read and keeps readings already loaded.
 * It does not delete workouts. Steps and resting heart rate stay readable.
 * Exercise history is a separate paged read and is not loaded by [refresh].
 */
class HealthRepository(
    private val source: HealthSource,
    private val dateProvider: DateProvider,
    private val canImportExternalWorkouts: () -> Boolean = { true }
) {
    private val mutex = Mutex()
    private val historyMutex = Mutex()
    private val accessState = MutableStateFlow(HealthAccess())
    private val readingsState = MutableStateFlow(HealthReadings())
    private val historyState = MutableStateFlow(ExerciseHistorySnapshot())
    private var activeHistoryQuery: ExerciseHistoryQuery? = null
    private var nextHistoryToken: String? = null

    val access: StateFlow<HealthAccess> = accessState.asStateFlow()
    val readings: StateFlow<HealthReadings> = readingsState.asStateFlow()
    val exerciseHistory: StateFlow<ExerciseHistorySnapshot> = historyState.asStateFlow()

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
                syncHistoryAccess()
                return
            }
            if (availability != HealthAvailability.Available) {
                accessState.value = HealthAccess(availability = availability, checked = true)
                readingsState.value = HealthReadings()
                syncHistoryAccess()
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
                syncHistoryAccess()
                return
            }
            accessState.value = HealthAccess(
                availability = HealthAvailability.Available,
                stepsGranted = grants.steps,
                restingHeartRateGranted = grants.restingHeartRate,
                checked = true,
                exerciseGranted = grants.exercise,
                hrvGranted = grants.hrv,
                sleepGranted = grants.sleep,
                history = grants.history
            )
            if (!grants.any()) {
                readingsState.value = HealthReadings()
                syncHistoryAccess()
                return
            }
            val today = dateProvider.today()
            val (start, endExclusive) = HealthWindow.queryRange(today)
            val (exerciseStart, exerciseEnd) = HealthWindow.recentExerciseRange(today)
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
            val exercise = if (canImportExternalWorkouts()) {
                readMetric(grants.exercise, HealthMetric.EXERCISE, failed) {
                    HealthExerciseAggregator.daily(
                        source.readExerciseSessions(exerciseStart, exerciseEnd),
                        today
                    )
                }
            } else {
                readingsState.value.exercise
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
            syncHistoryAccess()
        }
    }

    suspend fun reloadExerciseHistory() {
        historyMutex.withLock {
            if (!canReadExercise()) {
                clearHistory()
                return
            }
            val query = openHistoryQuery()
            historyState.value = historyState.value.copy(
                visible = true,
                access = accessState.value.history,
                loading = true,
                failed = false,
                entries = emptyList(),
                canLoadMore = false,
                limitedToRecentWindow = false
            )
            val loaded = readHistory(query, pageToken = null, allowFallback = true)
            if (loaded.failed) {
                activeHistoryQuery = null
                nextHistoryToken = null
                historyState.value = historyState.value.copy(
                    loading = false,
                    failed = true,
                    entries = emptyList(),
                    canLoadMore = false
                )
                return
            }
            activeHistoryQuery = loaded.query
            historyState.value = snapshot(loaded, replace = emptyList())
        }
    }

    suspend fun loadMoreExerciseHistory() {
        historyMutex.withLock {
            val current = historyState.value
            val token = nextHistoryToken ?: return
            val query = activeHistoryQuery ?: return
            if (!canReadExercise() || current.loading) return
            historyState.value = current.copy(loading = true, failed = false)
            val loaded = readHistory(query, pageToken = token, allowFallback = false)
            if (loaded.failed) {
                historyState.value = historyState.value.copy(loading = false, failed = true)
                return
            }
            activeHistoryQuery = loaded.query
            historyState.value = snapshot(loaded, replace = current.entries)
        }
    }

    private suspend fun syncHistoryAccess() {
        historyMutex.withLock {
            if (!canReadExercise()) {
                clearHistory()
            } else {
                historyState.value = historyState.value.copy(
                    visible = true,
                    access = accessState.value.history
                )
            }
        }
    }

    private fun canReadExercise(): Boolean {
        if (!canImportExternalWorkouts()) return false
        val access = accessState.value
        return access.checked &&
            access.availability == HealthAvailability.Available &&
            access.exerciseGranted
    }

    private fun clearHistory() {
        activeHistoryQuery = null
        nextHistoryToken = null
        historyState.value = ExerciseHistorySnapshot()
    }

    private fun openHistoryQuery(): ExerciseHistoryQuery {
        val zone = ZoneId.systemDefault()
        val end = dateProvider.today().plusDays(1).atStartOfDay(zone).toInstant()
        return ExerciseHistoryQuery(
            fullHistory = accessState.value.history == HealthHistoryAccess.Granted,
            start = null,
            end = end
        )
    }

    private fun safeHistoryQuery(end: Instant): ExerciseHistoryQuery {
        val zone = ZoneId.systemDefault()
        val now = dateProvider.now().atZone(zone).toInstant()
        return ExerciseHistoryQuery(
            fullHistory = false,
            start = ExerciseHistoryBoundary.safeRecentStart(now),
            end = end
        )
    }

    private data class LoadedHistory(
        val sessions: List<HealthExerciseSession>,
        val nextPageToken: String?,
        val query: ExerciseHistoryQuery,
        val failed: Boolean,
        val limitedToRecentWindow: Boolean
    )

    private suspend fun readHistory(
        query: ExerciseHistoryQuery,
        pageToken: String?,
        allowFallback: Boolean
    ): LoadedHistory {
        try {
            val page = source.readExerciseHistoryPage(query, pageToken, ExerciseHistory.PAGE_SIZE)
            return LoadedHistory(
                sessions = page.sessions,
                nextPageToken = page.nextPageToken?.takeIf { it.isNotBlank() && it != pageToken },
                query = query,
                failed = false,
                limitedToRecentWindow = false
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (!allowFallback || query.fullHistory || pageToken != null || query.start != null) {
                return LoadedHistory(emptyList(), null, query, failed = true, limitedToRecentWindow = false)
            }
            val fallback = safeHistoryQuery(query.end)
            val start = fallback.start
            if (start == null || !start.isBefore(fallback.end)) {
                return LoadedHistory(emptyList(), null, query, failed = true, limitedToRecentWindow = false)
            }
            return try {
                val page = source.readExerciseHistoryPage(fallback, null, ExerciseHistory.PAGE_SIZE)
                LoadedHistory(
                    sessions = page.sessions,
                    nextPageToken = page.nextPageToken?.takeIf { it.isNotBlank() },
                    query = fallback,
                    failed = false,
                    limitedToRecentWindow = true
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                LoadedHistory(emptyList(), null, query, failed = true, limitedToRecentWindow = false)
            }
        }
    }

    private fun snapshot(loaded: LoadedHistory, replace: List<ExerciseHistoryEntry>): ExerciseHistorySnapshot {
        val merged = ExerciseHistory.merge(replace, ExerciseHistory.entries(loaded.sessions))
        nextHistoryToken = loaded.nextPageToken
        return ExerciseHistorySnapshot(
            visible = true,
            access = accessState.value.history,
            entries = merged,
            canLoadMore = loaded.nextPageToken != null,
            loading = false,
            failed = false,
            limitedToRecentWindow = loaded.limitedToRecentWindow || historyState.value.limitedToRecentWindow
        )
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
