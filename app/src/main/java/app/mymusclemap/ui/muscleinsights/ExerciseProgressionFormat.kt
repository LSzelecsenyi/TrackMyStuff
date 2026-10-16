package app.mymusclemap.ui.muscleinsights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.performance.ComparisonDimension
import app.mymusclemap.domain.performance.ComparisonLimitation
import app.mymusclemap.domain.performance.ExercisePerformanceComparison
import app.mymusclemap.domain.performance.MatchedPerformance
import app.mymusclemap.domain.performance.RecordedPerformanceOutcome
import app.mymusclemap.domain.workout.PlannedLoadKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

fun progressionDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("MMMM d", AppLocale.UI))
}

fun progressionQuantity(value: Double): String {
    val scaled = kotlin.math.round(value * 10.0) / 10.0
    return if (scaled % 1.0 == 0.0) {
        scaled.toInt().toString()
    } else {
        String.format(AppLocale.UI, "%.1f", scaled)
    }
}

@Composable
fun outcomeLabel(outcome: RecordedPerformanceOutcome): String {
    return stringResource(
        when (outcome) {
            RecordedPerformanceOutcome.IMPROVED -> R.string.exercise_progression_improved
            RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE -> R.string.exercise_progression_lower
            RecordedPerformanceOutcome.STABLE -> R.string.exercise_progression_stable
            RecordedPerformanceOutcome.MIXED -> R.string.exercise_progression_mixed
            RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE -> R.string.exercise_progression_not_comparable
            RecordedPerformanceOutcome.INSUFFICIENT_HISTORY -> R.string.exercise_progression_insufficient
            RecordedPerformanceOutcome.UNSUPPORTED -> R.string.exercise_progression_unsupported
        }
    )
}

@Composable
fun limitationText(limitation: ComparisonLimitation): String {
    return stringResource(
        when (limitation) {
            ComparisonLimitation.NONE,
            ComparisonLimitation.ONLY_ONE_OCCURRENCE -> R.string.exercise_progression_insufficient_only
            ComparisonLimitation.NO_EARLIER_OCCURRENCE_IN_WINDOW -> R.string.exercise_progression_insufficient_window
            ComparisonLimitation.NO_MATCHING_CONDITION -> R.string.exercise_progression_not_comparable_body
            ComparisonLimitation.UNSUPPORTED_TYPE -> R.string.exercise_progression_unsupported_body
            ComparisonLimitation.INVALID_OR_MISSING_LOAD -> R.string.exercise_progression_invalid_load
            ComparisonLimitation.ASSISTANCE_EXCLUDED -> R.string.exercise_progression_assistance
        }
    )
}

@Composable
fun progressionSummary(exercise: ExercisePerformanceComparison): String? {
    val comparisons = exercise.comparisons
    if (comparisons.isEmpty()) return null
    if (comparisons.size > 1) {
        return stringResource(
            if (exercise.outcome == RecordedPerformanceOutcome.MIXED) {
                R.string.exercise_progression_mixed_results
            } else {
                R.string.exercise_progression_multiple
            }
        )
    }
    return comparisonSummary(comparisons.single())
}

@Composable
fun progressionRowDescription(exercise: ExercisePerformanceComparison): String {
    val name = exercise.displayName
    val classification = outcomeLabel(exercise.outcome)
    val summary = progressionSummary(exercise)
    val opens = stringResource(R.string.exercise_progression_open_details)
    return if (summary == null) {
        stringResource(R.string.exercise_progression_row_without_summary, name, classification, opens)
    } else {
        stringResource(
            R.string.exercise_progression_row_with_summary,
            name,
            classification,
            summary,
            opens
        )
    }
}

@Composable
fun comparisonSummary(match: MatchedPerformance): String {
    return when (match.dimension) {
        ComparisonDimension.REPETITIONS -> {
            val change = signedCount(match.delta, R.plurals.exercise_progression_reps)
            val load = loadLabel(match.matchedEffectiveKilograms, match.loadKind, match.weightInterpretation)
            if (load == null) change else stringResource(R.string.exercise_progression_summary_at, change, load)
        }
        ComparisonDimension.KILOGRAMS -> {
            val change = signedLoad(match.delta, match.loadKind, match.weightInterpretation)
            val reps = countLabel(match.matchedRepetitions ?: 0, R.plurals.exercise_progression_reps)
            stringResource(R.string.exercise_progression_summary_at, change, reps)
        }
        ComparisonDimension.DURATION_SECONDS -> {
            val change = signedCount(match.delta, R.plurals.exercise_progression_seconds)
            val load = loadLabel(match.matchedEffectiveKilograms, match.loadKind, match.weightInterpretation)
            if (load == null) change else stringResource(R.string.exercise_progression_summary_at, change, load)
        }
    }
}

@Composable
fun comparisonCondition(match: MatchedPerformance): String? {
    return when (match.dimension) {
        ComparisonDimension.KILOGRAMS -> countLabel(
            match.matchedRepetitions ?: 0,
            R.plurals.exercise_progression_reps
        )
        ComparisonDimension.REPETITIONS,
        ComparisonDimension.DURATION_SECONDS -> loadLabel(
            match.matchedEffectiveKilograms,
            match.loadKind,
            match.weightInterpretation
        )
    }
}

@Composable
fun comparisonTransition(match: MatchedPerformance): String {
    val reference = sideValue(match, match.reference)
    val current = sideValue(match, match.current)
    return stringResource(R.string.exercise_progression_transition, reference, current)
}

@Composable
fun comparisonDelta(match: MatchedPerformance): String {
    return when (match.dimension) {
        ComparisonDimension.REPETITIONS -> signedCount(match.delta, R.plurals.exercise_progression_reps)
        ComparisonDimension.KILOGRAMS -> signedLoad(match.delta, match.loadKind, match.weightInterpretation)
        ComparisonDimension.DURATION_SECONDS -> signedCount(match.delta, R.plurals.exercise_progression_seconds)
    }
}

@Composable
private fun sideValue(match: MatchedPerformance, value: Double): String {
    return when (match.dimension) {
        ComparisonDimension.REPETITIONS -> countLabel(value.roundToInt(), R.plurals.exercise_progression_reps)
        ComparisonDimension.DURATION_SECONDS -> countLabel(value.roundToInt(), R.plurals.exercise_progression_seconds)
        ComparisonDimension.KILOGRAMS -> loadText(value, match.loadKind, match.weightInterpretation)
    }
}

@Composable
private fun loadLabel(
    effectiveKilograms: Double?,
    kind: PlannedLoadKind,
    interpretation: WeightInterpretation
): String? {
    if (effectiveKilograms == null || kind == PlannedLoadKind.BODYWEIGHT_ONLY) return null
    return loadText(effectiveKilograms, kind, interpretation)
}

@Composable
private fun loadText(
    effectiveKilograms: Double,
    kind: PlannedLoadKind,
    interpretation: WeightInterpretation
): String {
    val perSide = kind == PlannedLoadKind.EXTERNAL_WEIGHT &&
        interpretation == WeightInterpretation.PER_SIDE
    val shown = if (perSide) effectiveKilograms / 2.0 else effectiveKilograms
    val number = progressionQuantity(abs(shown))
    return when {
        kind == PlannedLoadKind.ADDED_WEIGHT -> stringResource(R.string.exercise_progression_kg_added, number)
        perSide -> stringResource(R.string.exercise_progression_kg_each_side, number)
        else -> stringResource(R.string.exercise_progression_kg, number)
    }
}

@Composable
private fun signedLoad(
    delta: Double,
    kind: PlannedLoadKind,
    interpretation: WeightInterpretation
): String {
    val perSide = kind == PlannedLoadKind.EXTERNAL_WEIGHT &&
        interpretation == WeightInterpretation.PER_SIDE
    val shown = if (perSide) delta / 2.0 else delta
    val number = progressionQuantity(abs(shown))
    val signed = signedPrefix(shown) + number
    return when {
        kind == PlannedLoadKind.ADDED_WEIGHT -> stringResource(R.string.exercise_progression_kg_added, signed)
        perSide -> stringResource(R.string.exercise_progression_kg_each_side, signed)
        else -> stringResource(R.string.exercise_progression_kg, signed)
    }
}

@Composable
private fun signedCount(delta: Double, plural: Int): String {
    val count = abs(delta).roundToInt()
    val body = pluralStringResource(plural, count, count)
    return signedPrefix(delta) + body
}

@Composable
private fun countLabel(count: Int, plural: Int): String {
    return pluralStringResource(plural, count, count)
}

private fun signedPrefix(value: Double): String {
    return when {
        value > 0.0 -> "+"
        value < 0.0 -> "\u2212"
        else -> ""
    }
}
