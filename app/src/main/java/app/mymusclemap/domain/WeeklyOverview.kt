package app.mymusclemap.domain

import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

data class WeeklyOverview(
    val workoutCount: Int = 0,
    val completedSetCount: Int = 0,
    val weightChangeKg: Double? = null
)

object WeeklyOverviewLogic {
    const val WINDOW_DAYS = 7L
    const val INSUFFICIENT_WEIGHT = "Nincs elég testsúlyadat"

    fun windowStart(today: LocalDate): LocalDate = today.minusDays(WINDOW_DAYS - 1)

    fun inWindow(date: LocalDate, today: LocalDate): Boolean {
        return !date.isBefore(windowStart(today)) && !date.isAfter(today)
    }

    fun assemble(
        today: LocalDate,
        sessions: List<WorkoutSessionSummary>,
        measurements: List<WeightMeasurement>
    ): WeeklyOverview {
        val completed = sessions.filter { summary ->
            summary.session.status == SessionStatus.COMPLETED &&
                inWindow(summary.session.workoutDate, today)
        }
        val windowMeasurements = measurements
            .filter { inWindow(it.date, today) }
            .sortedWith(compareBy({ it.date }, { it.id }))
        val weightChange = if (windowMeasurements.size >= 2) {
            windowMeasurements.last().weightKg - windowMeasurements.first().weightKg
        } else {
            null
        }
        return WeeklyOverview(
            workoutCount = completed.size,
            completedSetCount = completed.sumOf { it.progress.completed },
            weightChangeKg = weightChange
        )
    }

    fun workoutLabel(count: Int): String = "$count edzés"

    fun setLabel(count: Int): String = "$count sorozat"

    fun activityLine(overview: WeeklyOverview): String {
        return "${workoutLabel(overview.workoutCount)} · ${setLabel(overview.completedSetCount)}"
    }

    fun weightChangeLabel(changeKg: Double?): String {
        if (changeKg == null) {
            return INSUFFICIENT_WEIGHT
        }
        val formatted = String.format(Locale.US, "%.1f", abs(changeKg)).replace('.', ',') + " kg"
        return when {
            changeKg > 0 -> "+$formatted"
            changeKg < 0 -> "−$formatted"
            else -> formatted
        }
    }
}
