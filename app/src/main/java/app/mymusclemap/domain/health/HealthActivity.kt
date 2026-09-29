package app.mymusclemap.domain.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect exercise types from connect-client 1.1.0.
 * Strength is only the three types that are resistance or bodyweight training.
 * Boot camp, HIIT, boxing, yoga, pilates, stretching, and wheelchair are not strength.
 * Cycling is biking and stationary biking, not wheelchair.
 * Running is outdoor and treadmill running, not walking.
 */
object HealthExerciseClassifier {
    const val STRENGTH_TRAINING = 70
    const val WEIGHTLIFTING = 81
    const val CALISTHENICS = 13
    const val BIKING = 8
    const val BIKING_STATIONARY = 9
    const val RUNNING = 56
    const val RUNNING_TREADMILL = 57

    fun classify(exerciseType: Int): HealthExerciseKind {
        return when (exerciseType) {
            STRENGTH_TRAINING, WEIGHTLIFTING, CALISTHENICS -> HealthExerciseKind.STRENGTH
            BIKING, BIKING_STATIONARY -> HealthExerciseKind.CYCLING
            RUNNING, RUNNING_TREADMILL -> HealthExerciseKind.RUNNING
            else -> HealthExerciseKind.OTHER
        }
    }
}

enum class HealthExerciseKind {
    STRENGTH,
    CYCLING,
    RUNNING,
    OTHER
}

enum class HealthMetric {
    STEPS,
    EXERCISE,
    RESTING_HEART_RATE,
    HRV,
    SLEEP
}

data class HealthExerciseSession(
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    val kind: HealthExerciseKind
)

data class ExerciseTotal(
    val sessions: Int = 0,
    val duration: Duration = Duration.ZERO
) {
    operator fun plus(other: ExerciseTotal): ExerciseTotal {
        return ExerciseTotal(sessions + other.sessions, duration.plus(other.duration))
    }
}

data class DailyExercise(
    val date: LocalDate,
    val strength: ExerciseTotal = ExerciseTotal(),
    val cycling: ExerciseTotal = ExerciseTotal(),
    val running: ExerciseTotal = ExerciseTotal()
)

/** One RMSSD sample. [millis] is the official record value, with no quality label. */
data class HealthHrvSample(
    val time: Instant,
    val zone: ZoneId,
    val millis: Double
)

data class DailyHrv(
    val date: LocalDate,
    val millis: Double
)

/**
 * One asleep interval. Awake, awake-in-bed, and out-of-bed time is omitted before this type
 * is built. An empty stage list becomes one span covering the whole session.
 */
data class HealthSleepSpan(
    val start: Instant,
    val end: Instant,
    val zone: ZoneId
)

data class DailySleep(
    val date: LocalDate,
    val duration: Duration
)

/**
 * Maps external Health Connect activity into daily totals.
 * A session counts on the local date of its start, including duration that crosses midnight,
 * so one session is never split across two days. Distance is not included.
 */
object HealthExerciseAggregator {
    fun daily(sessions: List<HealthExerciseSession>, today: LocalDate): List<DailyExercise> {
        val totals = linkedMapOf<LocalDate, DailyExercise>()
        sessions.forEach { session ->
            if (session.kind == HealthExerciseKind.OTHER) return@forEach
            if (!session.end.isAfter(session.start)) return@forEach
            val date = session.start.atZone(session.zone).toLocalDate()
            if (!HealthWindow.contains(date, today)) return@forEach
            val addition = ExerciseTotal(1, Duration.between(session.start, session.end))
            val current = totals[date] ?: DailyExercise(date)
            totals[date] = when (session.kind) {
                HealthExerciseKind.STRENGTH -> current.copy(strength = current.strength + addition)
                HealthExerciseKind.CYCLING -> current.copy(cycling = current.cycling + addition)
                HealthExerciseKind.RUNNING -> current.copy(running = current.running + addition)
                HealthExerciseKind.OTHER -> current
            }
        }
        return totals.values.sortedBy { it.date }
    }
}

/**
 * Daily HRV is the arithmetic mean of RMSSD samples whose local date is that day.
 * Health Connect 1.1.0 exposes no daily aggregate metric for heart-rate-variability RMSSD.
 * Samples outside 1–200 ms are ignored because that is the record's valid range.
 * A day with no samples is omitted, not stored as zero.
 */
object HealthHrvAggregator {
    const val MIN_MILLIS = 1.0
    const val MAX_MILLIS = 200.0

    fun daily(samples: List<HealthHrvSample>, today: LocalDate): List<DailyHrv> {
        val grouped = linkedMapOf<LocalDate, MutableList<Double>>()
        samples.forEach { sample ->
            if (!sample.millis.isFinite()) return@forEach
            if (sample.millis < MIN_MILLIS || sample.millis > MAX_MILLIS) return@forEach
            val date = sample.time.atZone(sample.zone).toLocalDate()
            if (!HealthWindow.contains(date, today)) return@forEach
            grouped.getOrPut(date) { mutableListOf() }.add(sample.millis)
        }
        return grouped.map { (date, values) ->
            DailyHrv(date, values.average())
        }.sortedBy { it.date }
    }
}

/**
 * Sleep duration is clipped onto each local calendar date, matching
 * aggregateGroupByPeriod day buckets. A session from 22:00 to 06:00 contributes
 * two hours to the start date and six hours to the next date. An end instant that
 * falls exactly on local midnight does not add time to that next date.
 * Days with no overlap are omitted.
 */
object HealthSleepAggregator {
    fun daily(spans: List<HealthSleepSpan>, today: LocalDate): List<DailySleep> {
        val totals = linkedMapOf<LocalDate, Duration>()
        spans.forEach { span ->
            if (!span.end.isAfter(span.start)) return@forEach
            var day = span.start.atZone(span.zone).toLocalDate()
            val lastInstant = span.end.atZone(span.zone)
            val lastDay = if (lastInstant.toLocalTime() == java.time.LocalTime.MIDNIGHT) {
                lastInstant.toLocalDate().minusDays(1)
            } else {
                lastInstant.toLocalDate()
            }
            while (!day.isAfter(lastDay)) {
                val windowStart = day.atStartOfDay(span.zone).toInstant()
                val windowEnd = day.plusDays(1).atStartOfDay(span.zone).toInstant()
                val from = if (span.start.isAfter(windowStart)) span.start else windowStart
                val to = if (span.end.isBefore(windowEnd)) span.end else windowEnd
                if (to.isAfter(from) && HealthWindow.contains(day, today)) {
                    val overlap = Duration.between(from, to)
                    totals[day] = (totals[day] ?: Duration.ZERO).plus(overlap)
                }
                day = day.plusDays(1)
            }
        }
        return totals
            .filter { it.value > Duration.ZERO }
            .map { DailySleep(it.key, it.value) }
            .sortedBy { it.date }
    }
}
