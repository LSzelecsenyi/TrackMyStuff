package app.mymusclemap.domain

import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import java.time.LocalDate

data class DaySheetState(
    val date: LocalDate,
    val measurement: WeightMeasurement?,
    val differenceFromPreviousKg: Double?,
    val workouts: List<WorkoutSessionSummary> = emptyList(),
    val scheduledWorkouts: List<ScheduledWorkout> = emptyList(),
    val canRecordWeight: Boolean = true
) {
    val hasMeasurement: Boolean get() = measurement != null
    val hasWorkouts: Boolean get() = workouts.isNotEmpty()
    val hasScheduledWorkouts: Boolean get() = scheduledWorkouts.isNotEmpty()
}

object DaySheetFactory {
    fun create(
        date: LocalDate,
        measurements: List<WeightMeasurement>,
        today: LocalDate,
        workouts: List<WorkoutSessionSummary> = emptyList(),
        scheduledWorkouts: List<ScheduledWorkout> = emptyList()
    ): DaySheetState {
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
                ),
            scheduledWorkouts = scheduledWorkouts,
            canRecordWeight = !date.isAfter(today)
        )
    }
}
