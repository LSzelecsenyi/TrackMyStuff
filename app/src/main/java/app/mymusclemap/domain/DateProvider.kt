package app.mymusclemap.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

interface DateProvider {
    /** Fresh local date. Domain decisions call this at the moment of the decision. */
    fun today(): LocalDate

    /** Fresh local date-time. Domain decisions call this at the moment of the decision. */
    fun now(): LocalDateTime

    /** Calendar day for UI. Emits again when that day changes. */
    fun observeToday(): Flow<LocalDate>

    /**
     * Local time for visible relative labels, truncated to minutes by [SystemDateProvider].
     * Other providers emit [now] whenever [observeToday] emits.
     */
    fun observeNow(): Flow<LocalDateTime> = observeToday().map { now() }.distinctUntilChanged()
}

/**
 * Process-wide UI clock.
 *
 * [today] and [now] always read [clock]. The observable snapshot updates when the app
 * enters the foreground, when the device reports a time or zone change, and once a
 * minute while the app is in the foreground. The minute loop is cancelled in the
 * background. It does not write workouts, goals, or achievements.
 */
class SystemDateProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val tickDelay: suspend (Clock) -> Unit = { activeClock ->
        delay(millisUntilNextMinute(activeClock))
    }
) : DateProvider {
    private val samples = MutableStateFlow(read())
    private val foreground = java.util.concurrent.atomic.AtomicBoolean(false)
    private var boundScope: CoroutineScope? = null
    private var ticker: Job? = null

    override fun today(): LocalDate = LocalDate.now(clock)

    override fun now(): LocalDateTime = LocalDateTime.now(clock)

    override fun observeToday(): Flow<LocalDate> = samples.map { it.today }.distinctUntilChanged()

    override fun observeNow(): Flow<LocalDateTime> =
        samples.map { it.localDateTime.truncatedTo(ChronoUnit.MINUTES) }.distinctUntilChanged()

    fun observeSamples(): Flow<WallClockSample> = samples.asStateFlow()

    fun bind(scope: CoroutineScope) {
        boundScope = scope
        if (foreground.get()) {
            startTicker()
        }
    }

    fun onForeground() {
        foreground.set(true)
        publish()
        startTicker()
    }

    fun onBackground() {
        foreground.set(false)
        ticker?.cancel()
        ticker = null
    }

    /** One sample for DATE_CHANGED, TIME_SET, or TIMEZONE_CHANGED. Does not start the ticker. */
    fun onWallClockChanged() {
        publish()
    }

    fun isUiTickerRunning(): Boolean = ticker?.isActive == true

    private fun startTicker() {
        val scope = boundScope ?: return
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && foreground.get()) {
                tickDelay(clock)
                if (!isActive || !foreground.get()) return@launch
                publish()
            }
        }
    }

    private fun publish() {
        val next = read()
        if (samples.value != next) {
            samples.value = next
        }
    }

    private fun read(): WallClockSample {
        val zone = clock.zone
        val instant = clock.instant()
        val local = LocalDateTime.ofInstant(instant, zone)
        return WallClockSample(
            instant = instant,
            zone = zone,
            localDateTime = local,
            today = local.toLocalDate()
        )
    }
}

internal fun millisUntilNextMinute(clock: Clock): Long {
    val local = LocalDateTime.now(clock)
    val next = local.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
    return Duration.between(local, next).toMillis().coerceIn(1L, 60_000L)
}

class FixedDateProvider(
    private val date: LocalDate,
    private val time: LocalTime = LocalTime.NOON
) : DateProvider {
    override fun today(): LocalDate = date
    override fun now(): LocalDateTime = date.atTime(time)
    override fun observeToday(): Flow<LocalDate> = flowOf(date)
}
