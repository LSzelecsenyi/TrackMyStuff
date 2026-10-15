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

fun MuscleInsightTopic.titleRes(): Int {
    return when (this) {
        MuscleInsightTopic.LAST_STIMULUS -> R.string.muscle_insights_last_stimulus
        MuscleInsightTopic.RECENT_LOAD -> R.string.muscle_insights_recent_load
        MuscleInsightTopic.CHART -> R.string.muscle_insights_chart_title
        MuscleInsightTopic.SPACING -> R.string.muscle_insights_spacing
        MuscleInsightTopic.CONTRIBUTION -> R.string.muscle_insights_contribution_title
    }
}

fun MuscleInsightTopic.bodyRes(): Int {
    return when (this) {
        MuscleInsightTopic.LAST_STIMULUS -> R.string.muscle_insights_explain_stimulus
        MuscleInsightTopic.RECENT_LOAD -> R.string.muscle_insights_explain_load
        MuscleInsightTopic.CHART -> R.string.muscle_insights_explain_chart
        MuscleInsightTopic.SPACING -> R.string.muscle_insights_explain_spacing
        MuscleInsightTopic.CONTRIBUTION -> R.string.muscle_insights_explain_contribution
    }
}

fun formatWeeklySets(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(AppLocale.UI, "%.1f", rounded)
    }
}
