package app.mymusclemap.ui.achievements

import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementAccess
import app.mymusclemap.domain.achievements.AchievementId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
        assertEquals(R.drawable.weekly_goal_8times_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_8))
        assertEquals(R.drawable.weekly_goal_12times_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_12))
        assertEquals(R.drawable.weight_goal_badge, drawable(AchievementId.TARGET_WEIGHT_REACHED))
        assertEquals(R.drawable.first_workout_badge, drawable(AchievementId.FIRST_WORKOUT))
        assertEquals(R.drawable.first_workout_plan_badge, drawable(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
        assertEquals(R.drawable.first_monthly_report_badge, drawable(AchievementId.FIRST_MONTHLY_REPORT))
        assertEquals(R.drawable.first_pr_badge, drawable(AchievementId.FIRST_PR))
        assertEquals(R.drawable.weight_pr_badge, drawable(AchievementId.WEIGHT_PR))
        assertEquals(R.drawable.highest_rep_count_badge, drawable(AchievementId.REP_RECORD))
        assertEquals(R.drawable.highest_total_volume_badge, drawable(AchievementId.VOLUME_RECORD))
    }

    @Test
    fun productionArtworkIsDistinctFromThePlaceholder() {
        assertEquals(true, BadgeArtworkResolver.isProductionArtwork(AchievementId.WORKOUTS_5.badgeKey))
        assertEquals(true, BadgeArtworkResolver.isProductionArtwork(AchievementId.WEEKLY_GOAL_STREAK_4.badgeKey))
        assertEquals(true, BadgeArtworkResolver.isProductionArtwork(AchievementId.WEEKLY_GOAL_STREAK_8.badgeKey))
        assertEquals(true, BadgeArtworkResolver.isProductionArtwork(AchievementId.WEEKLY_GOAL_STREAK_12.badgeKey))
    }

    @Test
    fun weeklyGoalStreakTiersUseTheirOwnArtwork() {
        assertEquals(R.drawable.weekly_goal_4times_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_4))
        assertEquals(R.drawable.weekly_goal_8times_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_8))
        assertEquals(R.drawable.weekly_goal_12times_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_12))
        listOf(
            AchievementId.WEEKLY_GOAL_STREAK_4,
            AchievementId.WEEKLY_GOAL_STREAK_8,
            AchievementId.WEEKLY_GOAL_STREAK_12
        ).forEach { id ->
            assertNotEquals(R.drawable.ic_badge_placeholder, drawable(id))
        }
    }

    @Test
    fun everyImplementedAchievementUsesItsOwnFinalizedArtwork() {
        assertEquals(R.drawable.volume_master_badge, drawable(AchievementId.VOLUME_MASTER))
        assertEquals(R.drawable.iron_discipline_badge, drawable(AchievementId.IRON_DISCIPLINE))
        assertEquals(R.drawable.founder_badge, drawable(AchievementId.FOUNDER))
        val awaitingArtwork = setOf(
            AchievementId.PR_HUNTER_10,
            AchievementId.PR_HUNTER_25,
            AchievementId.PR_HUNTER_50,
            AchievementId.PR_HUNTER_100,
            AchievementId.EXERCISE_MASTERY_100,
            AchievementId.EXERCISE_MASTERY_250,
            AchievementId.EXERCISE_MASTERY_500,
            AchievementId.EXERCISE_MASTERY_1000,
            AchievementId.WEEKLY_GOAL_STREAK_26,
            AchievementId.WEEKLY_GOAL_STREAK_52
        )
        val mapped = AchievementId.entries.filter { it !in awaitingArtwork }.map { drawable(it) }
        assertEquals(mapped.size, mapped.toSet().size)
        AchievementId.entries.filter { it !in awaitingArtwork }.forEach { id ->
            assertTrue(BadgeArtworkResolver.isProductionArtwork(id.badgeKey))
            assertNotEquals(R.drawable.ic_badge_placeholder, drawable(id))
        }
        awaitingArtwork.forEach { id ->
            assertEquals(R.drawable.ic_badge_placeholder, drawable(id))
        }
        assertTrue(AchievementId.entries.none { it.name == "IRON_YEAR" })
        assertEquals(AchievementAccess.PRO, AchievementId.VOLUME_MASTER.access)
        assertEquals(AchievementAccess.PRO, AchievementId.IRON_DISCIPLINE.access)
        assertEquals(100_000.0, AchievementId.VOLUME_MASTER.volumeThresholdKg)
        assertEquals(250, AchievementId.IRON_DISCIPLINE.lifetimeWorkoutTarget)
    }

    @Test
    fun unknownKeysStayOnThePlaceholder() {
        assertEquals(R.drawable.ic_badge_placeholder, BadgeArtworkResolver.drawableFor("UNKNOWN"))
    }

    private fun drawable(id: AchievementId): Int = BadgeArtworkResolver.drawableFor(id.badgeKey)
}
