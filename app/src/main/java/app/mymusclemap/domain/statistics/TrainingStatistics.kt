package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.model.SeriesPoint
import java.time.LocalDate

/**
 * Statistics for one [StatisticsRange], derived from completed sessions and
 * scheduled workouts already stored by the app.
 */
data class TrainingStatistics(
    val range: StatisticsRange = StatisticsRange.Days30,
    val activity: TrainingActivity = TrainingActivity(),
    val adherence: PlanAdherence = PlanAdherence(),
    val volume: TrainingVolume = TrainingVolume(),
    val muscleDistribution: List<MuscleTrainingCount> = emptyList(),
    val restBetweenSessions: List<MuscleRestSummary> = emptyList(),
    val exercises: List<ExerciseProgressSummary> = emptyList()
) {
    val hasCompletedWorkouts: Boolean get() = activity.workoutCount > 0
}

data class TrainingActivity(
    val workoutCount: Int = 0,
    val completedSetCount: Int = 0,
    val trainingDayCount: Int = 0,
    /** Sum of completed session elapsed times of at least one second; otherwise null. */
    val durationMillis: Long? = null
)

/**
 * Plan adherence uses the explicit `scheduledWorkoutId` link.
 *
 * A due plan is a [app.mymusclemap.domain.workout.ScheduledWorkout] whose
 * `scheduledDate` falls in the selected range (and not after today).
 * It is completed only when that row is linked to a COMPLETED session.
 * Unlinked completed workouts, including same-name/same-day sessions started
 * from the hub, are not inferred as completing a plan.
 * Deleted or unscheduled plans disappear from history and cannot be counted.
 */
data class PlanAdherence(
    val plannedCount: Int = 0,
    val completedCount: Int = 0,
    val missedCount: Int = 0,
    val inProgressCount: Int = 0
) {
    val percent: Int?
        get() = if (plannedCount == 0) {
            null
        } else {
            kotlin.math.round(100.0 * completedCount / plannedCount).toInt()
        }
}

data class TrainingVolume(
    val totalKg: Double? = null,
    val completedSetCount: Int = 0,
    val trend: List<SeriesPoint> = emptyList()
) {
    val hasTotal: Boolean get() = totalKg != null
    val hasTrend: Boolean get() = trend.size >= 2
}

data class MuscleTrainingCount(
    val muscle: MuscleGroup,
    val completedSetCount: Int,
    val workoutCount: Int,
    val lastTrained: LocalDate
)

/**
 * Observed spacing between distinct dates on which a primary muscle was trained.
 * This is not physiological recovery time.
 */
data class MuscleRestSummary(
    val muscle: MuscleGroup,
    val sessionDates: Int,
    val averageDays: Double,
    val shortestDays: Int,
    val longestDays: Int,
    val lastTrained: LocalDate
)

data class ExerciseProgressSummary(
    val exerciseId: Long,
    val name: String,
    val measurementType: MeasurementType,
    val lastTrained: LocalDate,
    val best: ExerciseBest?,
    val recent: ExerciseBest?,
    val history: List<ExerciseHistoryPoint>,
    val historyKind: ExerciseHistoryKind
) {
    val hasProgression: Boolean get() = history.size >= 2
    val showsBestSeparately: Boolean get() = history.size >= 2 && best != recent
}

data class ExerciseHistoryPoint(
    val date: LocalDate,
    val value: Double
)

enum class ExerciseHistoryKind {
    REPS,
    EFFECTIVE_KG,
    DURATION_SECONDS,
    DISTANCE_METERS,
    COMPLETIONS
}

sealed class ExerciseBest {
    data class Reps(val reps: Int) : ExerciseBest()
    data class WeightedSet(
        val effectiveKg: Double,
        val recordedKg: Double,
        val reps: Int,
        val perSide: Boolean
    ) : ExerciseBest()
    data class Duration(val seconds: Int) : ExerciseBest()
    data class Distance(
        val meters: Double,
        val durationSeconds: Int?
    ) : ExerciseBest()
    data class Completions(val count: Int) : ExerciseBest()
}

internal data class VolumeEligibleSet(
    val date: LocalDate,
    val volumeKg: Double
)

internal data class WeightedCandidate(
    val date: LocalDate,
    val effectiveKg: Double,
    val recordedKg: Double,
    val reps: Int,
    val perSide: Boolean
)
