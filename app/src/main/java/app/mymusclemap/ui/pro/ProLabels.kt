package app.mymusclemap.ui.pro

import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.AppFeature

fun AppFeature.titleRes(): Int {
    return when (this) {
        AppFeature.AdvancedStatistics -> R.string.pro_feature_advanced_statistics
        AppFeature.AdvancedMuscleAnalytics -> R.string.pro_feature_advanced_muscle_analytics
        AppFeature.UnlimitedWorkoutPlans -> R.string.pro_feature_unlimited_workout_plans
        AppFeature.AdvancedPlanning -> R.string.pro_feature_advanced_planning
    }
}
