package app.mymusclemap.ui.muscleinsights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.mymusclemap.R
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.muscleinsights.LoadComparison
import app.mymusclemap.domain.muscleinsights.MuscleInsightTopic
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import app.mymusclemap.domain.muscleinsights.StimulusAge
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.round

@Composable
fun stimulusText(age: StimulusAge): String {
    return when (age) {
        StimulusAge.None -> stringResource(R.string.heatmap_band_never)
        is StimulusAge.Minutes -> if (age.minutes <= 0L) {
            stringResource(R.string.muscle_insights_less_than_minute)
        } else {
            pluralStringResource(
                R.plurals.muscle_insights_minutes_ago,
                age.minutes.toInt(),
                age.minutes.toInt()
            )
        }
        is StimulusAge.Hours -> if (age.hours <= 0L) {
            stringResource(R.string.muscle_insights_less_than_hour)
        } else {
            pluralStringResource(R.plurals.muscle_insights_hours_ago, age.hours.toInt(), age.hours.toInt())
        }
        is StimulusAge.Days -> if (age.days <= 0) {
            stringResource(R.string.heatmap_band_today)
        } else {
            pluralStringResource(R.plurals.heatmap_days_ago, age.days, age.days)
        }
    }
}

@Composable
fun comparisonText(comparison: LoadComparison): String {
    return stringResource(
        when (comparison) {
            LoadComparison.ABOVE -> R.string.muscle_insights_above
            LoadComparison.NEAR -> R.string.muscle_insights_near
            LoadComparison.BELOW -> R.string.muscle_insights_below
            LoadComparison.INSUFFICIENT -> R.string.muscle_insights_insufficient
        }
    )
}

fun MuscleInsightsPeriod.labelRes(): Int {
    return when (this) {
        MuscleInsightsPeriod.Weeks4 -> R.string.muscle_insights_period_4w
        MuscleInsightsPeriod.Months3 -> R.string.muscle_insights_period_3m
        MuscleInsightsPeriod.Months6 -> R.string.muscle_insights_period_6m
        MuscleInsightsPeriod.Year1 -> R.string.muscle_insights_period_1y
        MuscleInsightsPeriod.All -> R.string.muscle_insights_period_all
    }
}

fun MuscleInsightsPeriod.rangeNameRes(): Int {
    return when (this) {
        MuscleInsightsPeriod.Weeks4 -> R.string.muscle_insights_period_name_4w
        MuscleInsightsPeriod.Months3 -> R.string.muscle_insights_period_name_3m
        MuscleInsightsPeriod.Months6 -> R.string.muscle_insights_period_name_6m
        MuscleInsightsPeriod.Year1 -> R.string.muscle_insights_period_name_1y
        MuscleInsightsPeriod.All -> R.string.muscle_insights_period_name_all
    }
}

fun MuscleInsightTopic.titleRes(): Int {
    return when (this) {
        MuscleInsightTopic.LAST_STIMULUS -> R.string.muscle_insights_last_stimulus
        MuscleInsightTopic.RECENT_LOAD -> R.string.muscle_insights_recent_load
        MuscleInsightTopic.CHART -> R.string.muscle_insights_chart_title
        MuscleInsightTopic.SPACING -> R.string.muscle_insights_spacing
        MuscleInsightTopic.CONTRIBUTION -> R.string.muscle_insights_contribution_title
        MuscleInsightTopic.EXERCISE_PROGRESSION -> R.string.exercise_progression_title
    }
}

fun MuscleInsightTopic.bodyRes(): Int {
    return when (this) {
        MuscleInsightTopic.LAST_STIMULUS -> R.string.muscle_insights_explain_stimulus
        MuscleInsightTopic.RECENT_LOAD -> R.string.muscle_insights_explain_load
        MuscleInsightTopic.CHART -> R.string.muscle_insights_explain_chart
        MuscleInsightTopic.SPACING -> R.string.muscle_insights_explain_spacing
        MuscleInsightTopic.CONTRIBUTION -> R.string.muscle_insights_explain_contribution
        MuscleInsightTopic.EXERCISE_PROGRESSION -> R.string.exercise_progression_explain
    }
}

fun formatWeeklySets(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(AppLocale.UI, "%.1f", rounded)
    }
}

/** Whole-number display of the existing comparison inputs. Null when the ratio is undefined. */
fun loadChangeWholePercent(recentSets: Int, baselineWeeklySets: Double?): Int? {
    if (baselineWeeklySets == null || baselineWeeklySets == 0.0) return null
    return round(((recentSets - baselineWeeklySets) / baselineWeeklySets) * 100.0).toInt()
}

fun signedChangeNumber(percent: Int): String {
    val digits = NumberFormat.getIntegerInstance(AppLocale.UI).format(abs(percent))
    return when {
        percent > 0 -> "+$digits"
        percent < 0 -> "\u2212$digits"
        else -> digits
    }
}

@Composable
fun completedSetsLabel(count: Int): String {
    return pluralStringResource(R.plurals.muscle_insights_set_count, count, count)
}

@Composable
fun weeklySetsLabel(weekly: Double): String {
    val rounded = round(weekly * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        completedSetsLabel(rounded.toInt())
    } else {
        stringResource(R.string.muscle_insights_sets_value, formatWeeklySets(weekly))
    }
}

@Composable
fun loadChangeLabel(recentSets: Int, baselineWeeklySets: Double?, comparison: LoadComparison): String? {
    if (comparison == LoadComparison.INSUFFICIENT) return null
    val percent = loadChangeWholePercent(recentSets, baselineWeeklySets) ?: return null
    return stringResource(R.string.muscle_insights_change_percent, signedChangeNumber(percent))
}
