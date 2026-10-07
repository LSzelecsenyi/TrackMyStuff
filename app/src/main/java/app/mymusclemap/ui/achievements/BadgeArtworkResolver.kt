package app.mymusclemap.ui.achievements

import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementId

/**
 * Maps a stable [AchievementId.badgeKey] to production artwork.
 * The key is the identity. The drawable can change without changing stored unlocks.
 * Unknown keys, and awards that do not have artwork yet, keep the placeholder.
 */
object BadgeArtworkResolver {
    fun drawableFor(badgeKey: String): Int {
        return when (badgeKey) {
            AchievementId.WORKOUTS_5.name -> R.drawable.consistency_badge_bronze
            AchievementId.WORKOUTS_10.name -> R.drawable.consistency_badge_silver
            AchievementId.WORKOUTS_30.name -> R.drawable.consistency_badge_gold
            AchievementId.WORKOUTS_50.name -> R.drawable.consistency_badge_gold_1star
            AchievementId.WORKOUTS_100.name -> R.drawable.consistency_badge_gold_2star
            AchievementId.WORKOUTS_200.name -> R.drawable.consistency_badge_gold_3star
            AchievementId.WEEKLY_GOAL_STREAK_4.name -> R.drawable.weekly_goal_4times_badge
            AchievementId.WEEKLY_GOAL_STREAK_8.name -> R.drawable.weekly_goal_8times_badge
            AchievementId.WEEKLY_GOAL_STREAK_12.name -> R.drawable.weekly_goal_12times_badge
            AchievementId.WEEKLY_GOAL_STREAK_26.name -> R.drawable.weekly_goal_streak_26_badge
            AchievementId.WEEKLY_GOAL_STREAK_52.name -> R.drawable.weekly_goal_streak_52_badge
            AchievementId.TARGET_WEIGHT_REACHED.name -> R.drawable.weight_goal_badge
            AchievementId.FIRST_WORKOUT.name -> R.drawable.first_workout_badge
            AchievementId.FIRST_CUSTOM_WORKOUT_PLAN.name -> R.drawable.first_workout_plan_badge
            AchievementId.FIRST_MONTHLY_REPORT.name -> R.drawable.first_monthly_report_badge
            AchievementId.FIRST_PR.name -> R.drawable.first_pr_badge
            AchievementId.WEIGHT_PR.name -> R.drawable.weight_pr_badge
            AchievementId.REP_RECORD.name -> R.drawable.rep_record_badge
            AchievementId.VOLUME_RECORD.name -> R.drawable.volume_record_badge
            AchievementId.VOLUME_MASTER.name -> R.drawable.volume_master_badge
            AchievementId.IRON_DISCIPLINE.name -> R.drawable.iron_discipline_badge
            AchievementId.PR_HUNTER_10.name -> R.drawable.pr_hunter_10_badge
            AchievementId.PR_HUNTER_25.name -> R.drawable.pr_hunter_25_badge
            AchievementId.PR_HUNTER_50.name -> R.drawable.pr_hunter_50_badge
            AchievementId.PR_HUNTER_100.name -> R.drawable.pr_hunter_100_badge
            AchievementId.EXERCISE_MASTERY_100.name -> R.drawable.exercise_mastery_100_badge
            AchievementId.EXERCISE_MASTERY_250.name -> R.drawable.exercise_mastery_250_badge
            AchievementId.EXERCISE_MASTERY_500.name -> R.drawable.exercise_mastery_500_badge
            AchievementId.EXERCISE_MASTERY_1000.name -> R.drawable.exercise_mastery_1000_badge
            AchievementId.FOUNDER.name -> R.drawable.founder_badge
            AchievementId.EARLY_ADOPTER.name -> R.drawable.early_adopter_badge
            AchievementId.DEVELOPER.name -> R.drawable.developer_badge
            else -> R.drawable.ic_badge_placeholder
        }
    }

    fun isProductionArtwork(badgeKey: String): Boolean {
        return drawableFor(badgeKey) != R.drawable.ic_badge_placeholder
    }
}
