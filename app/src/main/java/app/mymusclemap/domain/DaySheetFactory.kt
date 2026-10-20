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
        val completed = completedWorkouts(date, workouts)
        return DaySheetState(
            date = date,
            measurement = current,
            differenceFromPreviousKg = if (current != null && previous != null) {
                current.weightKg - previous.weightKg
            } else {
                null
            },
            workouts = completed,
            scheduledWorkouts = remainingScheduled(scheduledWorkouts, completed),
            canRecordWeight = !date.isAfter(today)
        )
    }

    /**
     * Completed sessions only. In-progress and abandoned sessions stay out of the
     * overview. A finished session stays completed when sets were skipped; the
     * session status is the lifecycle, not the completed-set ratio.
     */
    private fun completedWorkouts(
        date: LocalDate,
        workouts: List<WorkoutSessionSummary>
    ): List<WorkoutSessionSummary> {
        return workouts
            .filter { it.session.workoutDate == date && it.session.status == SessionStatus.COMPLETED }
            .sortedWith(
                compareByDescending<WorkoutSessionSummary> { it.session.startedAt }
                    .thenByDescending { it.session.id }
            )
    }

    /**
     * Hide a scheduled row only when the stored schedule-to-session link says that
     * occurrence is completed. That link is the session's scheduledWorkoutId,
     * which the schedule query exposes as sessionId. Name, date, or exercise
     * content never hide an unrelated row. Sessions that share a name stay separate.
     */
    private fun remainingScheduled(
        scheduledWorkouts: List<ScheduledWorkout>,
        completed: List<WorkoutSessionSummary>
    ): List<ScheduledWorkout> {
        val completedScheduleIds = completed.mapNotNull { it.session.scheduledWorkoutId }.toSet()
        return scheduledWorkouts.filter { item ->
            val linkedSessionFinished = item.id in completedScheduleIds
            val scheduleMarkedCompleted = item.sessionStatus == SessionStatus.COMPLETED
            !linkedSessionFinished && !scheduleMarkedCompleted
        }
    }
}
