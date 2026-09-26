package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import java.time.LocalDate

/**
 * Derived Advanced Statistics. Every field is computed from completed workout sessions
 * already stored by the app; nothing is pre-aggregated in Room.
 */
data class TrainingStatistics(
    val completedWorkoutCount: Int = 0,
    val workoutsLast7Days: Int = 0,
    val trainingDaysLast7Days: Int = 0,
    val workoutsLast30Days: Int = 0,
    val trainingDaysLast30Days: Int = 0,
    val recentWeeklyFrequency: Double? = null,
    val weightedLoad: WeightedLoadVolume? = null,
    val weeklyVolume: List<WeeklyVolumePoint> = emptyList(),
    val exerciseProgress: List<ExerciseProgressSummary> = emptyList(),
    val recentlyTrainedMuscles: List<MuscleTrainingCount> = emptyList(),
    val mostTrainedMuscles: List<MuscleTrainingCount> = emptyList()
) {
    val hasCompletedWorkouts: Boolean get() = completedWorkoutCount > 0
    val hasVolumeTrend: Boolean get() = weeklyVolume.any { it.volumeKg > 0.0 }
}

/**
 * Kilogram volume from completed [MeasurementType.REPETITIONS_AND_WEIGHT] sets whose
 * actual load is [app.mymusclemap.domain.workout.PlannedLoadKind.EXTERNAL_WEIGHT] or
 * [app.mymusclemap.domain.workout.PlannedLoadKind.ADDED_WEIGHT]. Bodyweight-only and
 * assistance sets are excluded because stored body mass is not applied as load.
 */
data class WeightedLoadVolume(
    val last7DaysKg: Double,
    val last30DaysKg: Double,
    val completedSetCountLast7Days: Int,
    val completedSetCountLast30Days: Int
)

data class WeeklyVolumePoint(
    val weekStart: LocalDate,
    val volumeKg: Double
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

data class MuscleTrainingCount(
    val muscle: MuscleGroup,
    val completedSetCount: Int,
    val workoutCount: Int,
    val lastTrained: LocalDate
)

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
