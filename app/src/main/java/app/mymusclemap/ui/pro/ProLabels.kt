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
        AppFeature.AdvancedMuscleAnalytics -> R.string.pro_feature_advanced_muscle_analytics
        AppFeature.UnlimitedWorkoutPlans -> R.string.pro_feature_unlimited_workout_plans
        AppFeature.AdvancedPlanning -> R.string.pro_feature_advanced_planning
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
