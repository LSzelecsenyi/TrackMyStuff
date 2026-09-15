package hu.laca.weighttracker.domain

import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import java.time.LocalDate

data class DaySheetState(
    val date: LocalDate,
    val measurement: WeightMeasurement?,
    val differenceFromPreviousKg: Double?,
    val workouts: List<WorkoutSessionSummary> = emptyList()
) {
    val hasMeasurement: Boolean get() = measurement != null
    val hasWorkouts: Boolean get() = workouts.isNotEmpty()
}

object DaySheetFactory {
    fun create(
        date: LocalDate,
        measurements: List<WeightMeasurement>,
        today: LocalDate,
        workouts: List<WorkoutSessionSummary> = emptyList()
    ): DaySheetState? {
        if (date.isAfter(today)) return null
        val chronological = measurements.sortedWith(compareBy({ it.date }, { it.id }))
        val current = chronological.lastOrNull { it.date == date }
        val previous = chronological.lastOrNull { it.date.isBefore(date) }
        return DaySheetState(
            date = date,
            measurement = current,
            differenceFromPreviousKg = if (current != null && previous != null) {
                current.weightKg - previous.weightKg
            } else {
                null
            },
            workouts = workouts
                .filter { it.session.workoutDate == date && it.session.status == SessionStatus.COMPLETED }
                .sortedWith(
                    compareByDescending<WorkoutSessionSummary> { it.session.startedAt }
                        .thenByDescending { it.session.id }
                )
        )
    }
}
