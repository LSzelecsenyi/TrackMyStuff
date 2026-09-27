package app.mymusclemap.ui.pro

import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.AppFeature

data class ProInfoCopy(
    val titleRes: Int,
    val bodyRes: Int,
    val bodyArgRes: Int? = null,
    val highlights: List<Int> = emptyList()
)

fun AppFeature.titleRes(): Int {
    return when (this) {
        AppFeature.AdvancedStatistics -> R.string.pro_feature_advanced_statistics
        AppFeature.AdvancedReports -> R.string.pro_feature_advanced_reports
        AppFeature.AdvancedMuscleAnalytics -> R.string.pro_feature_advanced_muscle_analytics
        AppFeature.UnlimitedWorkoutPlans -> R.string.pro_feature_unlimited_workout_plans
        AppFeature.AdvancedPlanning -> R.string.pro_feature_advanced_planning
        AppFeature.AdvancedBodyMeasurements -> R.string.pro_feature_advanced_body_measurements
    }
}

fun AppFeature?.proInfoCopy(): ProInfoCopy {
    return when (this) {
        AppFeature.AdvancedStatistics -> ProInfoCopy(
            titleRes = R.string.pro_info_statistics_title,
            bodyRes = R.string.pro_info_statistics_body,
            highlights = listOf(
                R.string.pro_info_statistics_range_3m,
                R.string.pro_info_statistics_range_6m,
                R.string.pro_info_statistics_range_1y,
                R.string.pro_info_statistics_range_all
            )
        )
        AppFeature.AdvancedReports -> ProInfoCopy(
            titleRes = R.string.pro_info_reports_title,
            bodyRes = R.string.pro_info_reports_body,
            highlights = listOf(
                R.string.pro_info_reports_quarterly,
                R.string.pro_info_reports_half_year,
                R.string.pro_info_reports_yearly
            )
        )
        AppFeature.UnlimitedWorkoutPlans -> ProInfoCopy(
            titleRes = R.string.pro_info_plans_title,
            bodyRes = R.string.pro_info_plans_body,
            highlights = listOf(
                R.string.pro_info_plans_free_limit,
                R.string.pro_info_plans_existing_stay,
                R.string.pro_info_plans_unlimited
            )
        )
        AppFeature.AdvancedBodyMeasurements -> ProInfoCopy(
            titleRes = R.string.pro_info_body_measurements_title,
            bodyRes = R.string.pro_info_body_measurements_body,
            highlights = listOf(
                R.string.pro_info_body_measurements_fat,
                R.string.pro_info_body_measurements_circumferences,
                R.string.pro_info_body_measurements_existing_stay
            )
        )
        AppFeature.AdvancedPlanning -> ProInfoCopy(
            titleRes = R.string.pro_info_scheduling_title,
            bodyRes = R.string.pro_info_scheduling_body,
            highlights = listOf(
                R.string.pro_info_scheduling_schedule,
                R.string.pro_info_scheduling_reschedule,
                R.string.pro_info_scheduling_history_stays
            )
        )
        null -> ProInfoCopy(
            titleRes = R.string.pro_info_title,
            bodyRes = R.string.pro_info_body
        )
        else -> ProInfoCopy(
            titleRes = R.string.pro_info_title,
            bodyRes = R.string.pro_info_feature_body,
            bodyArgRes = titleRes()
        )
    }
}
