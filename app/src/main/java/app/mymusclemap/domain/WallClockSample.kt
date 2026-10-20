package app.mymusclemap.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class WallClockSample(
    val instant: Instant,
    val zone: ZoneId,
    val localDateTime: LocalDateTime,
    val today: LocalDate
)

/**
 * Decides when a wall-clock sample should refresh time-sensitive business rules.
 * The first sample only arms the next boundary. Later samples report a calendar-day
 * change once, and each promotion boundary instant at most once.
 */
class WallClockMonitor(
    private val nextBoundary: (Instant) -> Instant?
) {
    private var seenDay: LocalDate? = null
    private var armedBoundary: Instant? = null
    private val firedBoundaries = HashSet<Instant>()

    data class Effect(
        val dayChanged: Boolean = false,
        val boundaryReached: Boolean = false
    )

    fun onSample(instant: Instant, today: LocalDate): Effect {
        val previousDay = seenDay
        seenDay = today
        if (previousDay == null) {
            armedBoundary = nextBoundary(instant)
            return Effect()
        }
        val dayChanged = previousDay != today
        val due = armedBoundary
        val boundaryReached = due != null && !instant.isBefore(due) && firedBoundaries.add(due)
        armedBoundary = nextBoundary(instant)
        return Effect(dayChanged = dayChanged, boundaryReached = boundaryReached)
    }
}
