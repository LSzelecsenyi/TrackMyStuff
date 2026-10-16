package app.mymusclemap.domain.performance

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import app.mymusclemap.domain.workout.PlannedLoadKind
import java.time.LocalDate

/**
 * Recorded-performance outcome for one exercise.
 *
 * [IMPROVED], [LOWER_RECORDED_PERFORMANCE], [STABLE], and [MIXED] describe the
 * selected pair of occurrences only. They are not a strength, hypertrophy, or
 * recovery score. The other outcomes mean no direct comparison was made.
 */
enum class RecordedPerformanceOutcome {
    IMPROVED,
    LOWER_RECORDED_PERFORMANCE,
    STABLE,
    MIXED,
    NOT_DIRECTLY_COMPARABLE,
    INSUFFICIENT_HISTORY,
    UNSUPPORTED
}

/** Why a direct comparison was not produced. [NONE] when a pair was compared. */
enum class ComparisonLimitation {
    NONE,
    ONLY_ONE_OCCURRENCE,
    NO_EARLIER_OCCURRENCE_IN_WINDOW,
    NO_MATCHING_CONDITION,
    UNSUPPORTED_TYPE,
    INVALID_OR_MISSING_LOAD,
    ASSISTANCE_EXCLUDED
}

enum class ComparisonDimension {
    REPETITIONS,
    KILOGRAMS,
    DURATION_SECONDS
}

/**
 * One matched condition between the latest occurrence and the selected reference.
 *
 * Kilogram values are effective load from [app.mymusclemap.domain.statistics.WorkoutSetVolume].
 * Per-side external weight is already doubled. Added weight is the external load only.
 */
data class MatchedPerformance(
    val dimension: ComparisonDimension,
    val delta: Double,
    val current: Double,
    val reference: Double,
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val loadKind: PlannedLoadKind,
    val weightInterpretation: WeightInterpretation,
    /** Shared effective kilograms for a repetition or duration comparison. */
    val matchedEffectiveKilograms: Double?,
    /** Shared repetition count for a kilogram comparison. */
    val matchedRepetitions: Int?
) {
    val positive: Boolean get() = delta > 0.0
    val negative: Boolean get() = delta < 0.0
    val zero: Boolean get() = delta == 0.0
}

/**
 * Comparison of one catalog exercise against the nearest comparable earlier occurrence.
 * [displayName] is the snapshot name on the latest occurrence, not the live catalog name.
 */
data class ExercisePerformanceComparison(
    val exerciseId: Long,
    val displayName: String,
    val currentSessionId: Long,
    val currentDate: LocalDate,
    val currentCompletedAt: Long,
    val referenceSessionId: Long?,
    val referenceDate: LocalDate?,
    val referenceCompletedAt: Long?,
    val outcome: RecordedPerformanceOutcome,
    val limitation: ComparisonLimitation,
    val comparisons: List<MatchedPerformance>,
    val includesZeroDelta: Boolean,
    val matchedConditionCount: Int
)

/**
 * Exercise comparisons for one snapshotted primary muscle.
 * Counts are exercise counts. There is no combined strength percentage.
 */
data class MuscleExercisePerformance(
    val muscle: MuscleGroup,
    val period: MuscleInsightsPeriod,
    val exercises: List<ExercisePerformanceComparison>,
    val improved: Int,
    val lowerRecordedPerformance: Int,
    val stable: Int,
    val mixed: Int,
    val insufficientOrNotComparable: Int
)
