package app.mymusclemap.ui.achievements

import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementId
import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeArtworkResolverTest {
    @Test
    fun currentAwardsUseTheirProductionArtwork() {
        assertEquals(R.drawable.consistency_badge_bronze, drawable(AchievementId.WORKOUTS_5))
        assertEquals(R.drawable.consistency_badge_silver, drawable(AchievementId.WORKOUTS_10))
        assertEquals(R.drawable.consistency_badge_gold, drawable(AchievementId.WORKOUTS_30))
        assertEquals(R.drawable.consistency_badge_gold_1star, drawable(AchievementId.WORKOUTS_50))
        assertEquals(R.drawable.consistency_badge_gold_2star, drawable(AchievementId.WORKOUTS_100))
        assertEquals(R.drawable.consistency_badge_gold_3star, drawable(AchievementId.WORKOUTS_200))
        assertEquals(R.drawable.weekly_goal_4times_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_4))
        assertEquals(R.drawable.weekly_goal_12times_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_12))
        assertEquals(R.drawable.weight_goal_badge, drawable(AchievementId.TARGET_WEIGHT_REACHED))
        assertEquals(R.drawable.first_workout_badge, drawable(AchievementId.FIRST_WORKOUT))
        assertEquals(R.drawable.first_workout_plan_badge, drawable(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
        assertEquals(R.drawable.first_monthly_report_badge, drawable(AchievementId.FIRST_MONTHLY_REPORT))
    }

    @Test
    fun productionArtworkIsDistinctFromThePlaceholder() {
        assertEquals(true, BadgeArtworkResolver.isProductionArtwork(AchievementId.WORKOUTS_5.badgeKey))
        assertEquals(false, BadgeArtworkResolver.isProductionArtwork(AchievementId.WEEKLY_GOAL_STREAK_8.badgeKey))
    }

    @Test
    fun eightWeekStreakAndUnknownKeysStayOnThePlaceholder() {
        assertEquals(R.drawable.ic_badge_placeholder, drawable(AchievementId.WEEKLY_GOAL_STREAK_8))
        assertEquals(R.drawable.ic_badge_placeholder, BadgeArtworkResolver.drawableFor("UNKNOWN"))
    }

    private fun drawable(id: AchievementId): Int = BadgeArtworkResolver.drawableFor(id.badgeKey)
}
