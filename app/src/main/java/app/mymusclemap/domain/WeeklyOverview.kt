package app.mymusclemap.domain

import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import app.mymusclemap.domain.units.MeasurementText
import app.mymusclemap.domain.units.UnitSystem
import java.time.LocalDate

data class WeeklyOverview(
    val workoutCount: Int = 0,
    val completedSetCount: Int = 0,
    val weightChangeKg: Double? = null
)

object WeeklyOverviewLogic {
    const val WINDOW_DAYS = 7L

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

    fun weightChangeLabel(changeKg: Double, system: UnitSystem = UnitSystem.METRIC): String {
        return MeasurementText.signedWeightLabel(changeKg, system)
    }
}
