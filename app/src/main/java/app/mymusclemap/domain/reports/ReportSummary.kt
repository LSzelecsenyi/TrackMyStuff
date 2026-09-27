package app.mymusclemap.domain.reports

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.statistics.VolumeTrendResolution
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.time.LocalDate
import kotlin.math.round

data class ReportInputs(
    val sessions: List<WorkoutSessionAggregate> = emptyList(),
    val scheduled: List<ScheduledWorkout> = emptyList(),
    val bodyWeights: List<WeightMeasurement> = emptyList()
)

data class ReportSummary(
    val period: ReportPeriod,
    val coverage: ReportHistoryCoverage?,
    /** Earliest known report-history date used to classify [coverage]. Not an install date. */
    val historyStart: LocalDate?,
    val activity: ReportActivity,
    val adherence: ReportAdherence,
    val volume: ReportVolume,
    val muscles: List<ReportMuscleCount>,
    val exerciseHighlights: List<ReportExerciseHighlight>,
    val bodyWeight: ReportBodyWeight?,
    val previous: ReportPeriodComparison?
) {
    fun topMuscles(): List<ReportMuscleCount> = muscles.take(TOP_MUSCLES)

    companion object {
        const val TOP_MUSCLES = 3
        const val HIGHLIGHT_LIMIT = 3
    }
}

data class ReportActivity(
    val workoutCount: Int = 0,
    val trainingDayCount: Int = 0,
    val completedSetCount: Int = 0,
    /** Sum of completed session elapsed times of at least one second; otherwise null. */
    val durationMillis: Long? = null
)

data class ReportAdherence(
    val plannedCount: Int = 0,
    val completedCount: Int = 0
) {
    val percent: Int?
        get() = if (plannedCount == 0) {
            null
        } else {
            round(100.0 * completedCount / plannedCount).toInt()
        }
}

data class ReportVolume(
    val totalKg: Double? = null,
    val completedSetCount: Int = 0,
    val resolution: VolumeTrendResolution,
    val trend: List<SeriesPoint> = emptyList()
) {
    val hasTotal: Boolean get() = totalKg != null
}

data class ReportMuscleCount(
    val muscle: MuscleGroup,
    val completedSetCount: Int,
    val workoutCount: Int,
    val lastTrained: LocalDate
)

enum class ReportPerformanceKind {
    Reps,
    EffectiveKg,
    DurationSeconds,
    DistanceMeters
}

data class ReportExerciseHighlight(
    val exerciseId: Long,
    val name: String,
    val measurementType: MeasurementType,
    val kind: ReportPerformanceKind,
    val baselineDate: LocalDate,
    val latestDate: LocalDate,
    val baseline: Double,
    val latest: Double
) {
    val delta: Double get() = latest - baseline
}

/**
 * In-period weigh-ins only. [lastKg] and [changeKg] are present when there are
 * at least two measurements. Missing boundary days are not filled in.
 */
data class ReportBodyWeight(
    val firstDate: LocalDate,
    val firstKg: Double,
    val lastDate: LocalDate? = null,
    val lastKg: Double? = null,
    val changeKg: Double? = null,
    val averageKg: Double? = null
)

data class ReportPeriodComparison(
    val period: ReportPeriod,
    val workoutCountDelta: Int,
    val workoutCountPercent: Double?,
    val volumeDeltaKg: Double,
    val volumePercent: Double?,
    /** Current adherence percent minus the previous percent. Null unless both periods had due workouts. */
    val adherencePointDelta: Int?
)
