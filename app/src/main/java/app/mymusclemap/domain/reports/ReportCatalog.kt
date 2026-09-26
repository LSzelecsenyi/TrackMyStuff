package app.mymusclemap.domain.reports

import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.WorkoutSession
import java.time.LocalDate

/**
 * Whether My Muscle Map already had persisted report history for the whole period.
 * This is not a claim about training that was never recorded.
 */
enum class ReportHistoryCoverage {
    /** The earliest known history date is on or before the period start. */
    Complete,

    /** History begins after the period start and still falls inside the period. */
    Partial
}

data class AvailableReport(
    val period: ReportPeriod,
    val coverage: ReportHistoryCoverage
)

/**
 * Dates already stored for the records Reports will read.
 *
 * [scheduledOccurrenceDates] are current `scheduledDate` values, including cancelled
 * occurrences. `originalScheduledDate` is not a history source.
 */
data class ReportHistoryEvidence(
    val sessionWorkoutDates: List<LocalDate> = emptyList(),
    val scheduledOccurrenceDates: List<LocalDate> = emptyList(),
    val bodyWeightDates: List<LocalDate> = emptyList()
) {
    companion object {
        fun fromPersisted(
            sessions: Iterable<WorkoutSession> = emptyList(),
            scheduled: Iterable<ScheduledWorkout> = emptyList(),
            bodyWeights: Iterable<WeightMeasurement> = emptyList()
        ): ReportHistoryEvidence {
            return ReportHistoryEvidence(
                sessionWorkoutDates = sessions.map { it.workoutDate },
                scheduledOccurrenceDates = scheduled.map { it.scheduledDate },
                bodyWeightDates = bodyWeights.map { it.date }
            )
        }
    }
}

object ReportHistory {
    /**
     * Earliest date on or before [today] among completed, abandoned, and in-progress
     * sessions (including unplanned workouts), scheduled occurrences (including cancelled),
     * and body-weight measurements.
     *
     * Dates after [today] are ignored, so a future plan does not open a report and does
     * not move the history boundary. No install-date column is used.
     */
    fun earliestDate(evidence: ReportHistoryEvidence, today: LocalDate): LocalDate? {
        return sequenceOf(
            evidence.sessionWorkoutDates,
            evidence.scheduledOccurrenceDates,
            evidence.bodyWeightDates
        ).flatten()
            .filter { !it.isAfter(today) }
            .minOrNull()
    }
}

object ReportCatalog {
    /**
     * Closed periods of [kind], newest first, from the latest finished period back
     * through the earliest period that still intersects [historyStart].
     * Periods entirely before that date are omitted. No history yields an empty catalog.
     */
    fun available(
        kind: ReportKind,
        today: LocalDate,
        historyStart: LocalDate?
    ): List<AvailableReport> {
        if (historyStart == null || historyStart.isAfter(today)) {
            return emptyList()
        }
        val latest = ReportPeriod.containing(kind, today).previous()
        if (!latest.isClosed(today)) {
            return emptyList()
        }
        val periods = ArrayList<AvailableReport>()
        var cursor = latest
        while (!cursor.endInclusive.isBefore(historyStart)) {
            periods += AvailableReport(
                period = cursor,
                coverage = coverage(cursor, historyStart)
            )
            cursor = cursor.previous()
        }
        return periods
    }

    fun coverage(period: ReportPeriod, historyStart: LocalDate): ReportHistoryCoverage {
        require(!period.endInclusive.isBefore(historyStart)) {
            "Period ends before known history"
        }
        return if (!historyStart.isAfter(period.startInclusive)) {
            ReportHistoryCoverage.Complete
        } else {
            ReportHistoryCoverage.Partial
        }
    }
}
