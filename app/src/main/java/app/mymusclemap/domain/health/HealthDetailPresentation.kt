package app.mymusclemap.domain.health

import java.time.Duration
import java.time.LocalDate

enum class HealthLineStatus {
    Off,
    Failed,
    Shown
}

data class HealthTrendSlot(
    val date: LocalDate,
    val amount: Double?
)

data class HealthLine(
    val status: HealthLineStatus,
    val headline: Double?,
    val headlineDate: LocalDate?,
    val headlineIsToday: Boolean,
    val slots: List<HealthTrendSlot>
)

data class HealthExerciseLine(
    val status: HealthLineStatus,
    val recentSessions: Int,
    val recentDuration: Duration,
    val todaySessions: Int
)

sealed interface HealthDetailState {
    data object Checking : HealthDetailState

    data class Quiet(val status: HealthQuietStatus) : HealthDetailState

    data class Connected(
        val steps: HealthLine,
        val strength: HealthExerciseLine,
        val cycling: HealthExerciseLine,
        val running: HealthExerciseLine,
        val heart: HealthLine,
        val hrv: HealthLine,
        val sleep: HealthLine
    ) : HealthDetailState
}

object HealthDetailPresentation {
    fun detail(access: HealthAccess, readings: HealthReadings, today: LocalDate): HealthDetailState {
        if (!access.checked) return HealthDetailState.Checking
        return when (access.availability) {
            HealthAvailability.Unavailable -> HealthDetailState.Quiet(HealthQuietStatus.Unavailable)
            HealthAvailability.ProviderUpdateRequired ->
                HealthDetailState.Quiet(HealthQuietStatus.UpdateRequired)
            HealthAvailability.Available -> {
                if (!access.anyGranted()) {
                    HealthDetailState.Quiet(HealthQuietStatus.NotConnected)
                } else {
                    HealthDetailState.Connected(
                        steps = todayLine(
                            granted = access.stepsGranted,
                            failed = readings.failed.contains(HealthMetric.STEPS),
                            today = today,
                            rows = readings.steps.map { it.date to it.steps.toDouble() }
                        ),
                        strength = exerciseLine(
                            granted = access.exerciseGranted,
                            failed = readings.failed.contains(HealthMetric.EXERCISE),
                            days = readings.exercise,
                            today = today
                        ) { it.strength },
                        cycling = exerciseLine(
                            granted = access.exerciseGranted,
                            failed = readings.failed.contains(HealthMetric.EXERCISE),
                            days = readings.exercise,
                            today = today
                        ) { it.cycling },
                        running = exerciseLine(
                            granted = access.exerciseGranted,
                            failed = readings.failed.contains(HealthMetric.EXERCISE),
                            days = readings.exercise,
                            today = today
                        ) { it.running },
                        heart = todayLine(
                            granted = access.restingHeartRateGranted,
                            failed = readings.failed.contains(HealthMetric.RESTING_HEART_RATE),
                            today = today,
                            rows = readings.restingHeartRate.map { it.date to it.beatsPerMinute.toDouble() }
                        ),
                        hrv = latestLine(
                            granted = access.hrvGranted,
                            failed = readings.failed.contains(HealthMetric.HRV),
                            today = today,
                            rows = readings.hrv.map { it.date to it.millis }
                        ),
                        sleep = latestLine(
                            granted = access.sleepGranted,
                            failed = readings.failed.contains(HealthMetric.SLEEP),
                            today = today,
                            rows = readings.sleep.map { it.date to it.duration.toMinutes().toDouble() }
                        )
                    )
                }
            }
        }
    }

    private fun todayLine(
        granted: Boolean,
        failed: Boolean,
        today: LocalDate,
        rows: List<Pair<LocalDate, Double>>
    ): HealthLine {
        if (!granted) return off()
        if (failed) return failed()
        val todayValue = rows.firstOrNull { it.first == today }?.second
        return HealthLine(
            status = HealthLineStatus.Shown,
            headline = todayValue,
            headlineDate = if (todayValue == null) null else today,
            headlineIsToday = todayValue != null,
            slots = slots(today, rows)
        )
    }

    /**
     * Headline is today's value when one exists. Otherwise it is the latest value in the
     * 30-day window, with [HealthLine.headlineIsToday] false so the UI can show the date.
     */
    private fun latestLine(
        granted: Boolean,
        failed: Boolean,
        today: LocalDate,
        rows: List<Pair<LocalDate, Double>>
    ): HealthLine {
        if (!granted) return off()
        if (failed) return failed()
        val todayValue = rows.firstOrNull { it.first == today }?.second
        val latest = rows.filter { !it.first.isAfter(today) }.maxByOrNull { it.first }
        val headline = todayValue ?: latest?.second
        val headlineDate = if (todayValue != null) today else latest?.first
        return HealthLine(
            status = HealthLineStatus.Shown,
            headline = headline,
            headlineDate = headlineDate,
            headlineIsToday = todayValue != null,
            slots = slots(today, rows)
        )
    }

    private fun exerciseLine(
        granted: Boolean,
        failed: Boolean,
        days: List<DailyExercise>,
        today: LocalDate,
        pick: (DailyExercise) -> ExerciseTotal
    ): HealthExerciseLine {
        if (!granted) {
            return HealthExerciseLine(HealthLineStatus.Off, 0, Duration.ZERO, 0)
        }
        if (failed) {
            return HealthExerciseLine(HealthLineStatus.Failed, 0, Duration.ZERO, 0)
        }
        var sessions = 0
        var duration = Duration.ZERO
        var todaySessions = 0
        days.forEach { day ->
            val age = java.time.temporal.ChronoUnit.DAYS.between(day.date, today)
            if (age < 0 || age > HealthPresentation.RECENT_STEP_DAYS - 1L) return@forEach
            val total = pick(day)
            sessions += total.sessions
            duration = duration.plus(total.duration)
            if (day.date == today) todaySessions = total.sessions
        }
        return HealthExerciseLine(HealthLineStatus.Shown, sessions, duration, todaySessions)
    }

    private fun slots(today: LocalDate, rows: List<Pair<LocalDate, Double>>): List<HealthTrendSlot> {
        return (HealthPresentation.RECENT_STEP_DAYS - 1 downTo 0).map { ago ->
            val date = today.minusDays(ago.toLong())
            HealthTrendSlot(date, rows.firstOrNull { it.first == date }?.second)
        }
    }

    private fun off() = HealthLine(HealthLineStatus.Off, null, null, false, emptyList())

    private fun failed() = HealthLine(HealthLineStatus.Failed, null, null, false, emptyList())
}
