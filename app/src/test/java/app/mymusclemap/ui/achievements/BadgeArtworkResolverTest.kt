package app.mymusclemap.ui.achievements

import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementAccess
import app.mymusclemap.domain.achievements.AchievementCatalog
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
        assertEquals(R.drawable.rep_record_badge, drawable(AchievementId.REP_RECORD))
        assertEquals(R.drawable.volume_record_badge, drawable(AchievementId.VOLUME_RECORD))
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
        assertEquals(R.drawable.pr_hunter_10_badge, drawable(AchievementId.PR_HUNTER_10))
        assertEquals(R.drawable.pr_hunter_25_badge, drawable(AchievementId.PR_HUNTER_25))
        assertEquals(R.drawable.pr_hunter_50_badge, drawable(AchievementId.PR_HUNTER_50))
        assertEquals(R.drawable.pr_hunter_100_badge, drawable(AchievementId.PR_HUNTER_100))
        assertEquals(R.drawable.exercise_mastery_100_badge, drawable(AchievementId.EXERCISE_MASTERY_100))
        assertEquals(R.drawable.exercise_mastery_250_badge, drawable(AchievementId.EXERCISE_MASTERY_250))
        assertEquals(R.drawable.exercise_mastery_500_badge, drawable(AchievementId.EXERCISE_MASTERY_500))
        assertEquals(R.drawable.exercise_mastery_1000_badge, drawable(AchievementId.EXERCISE_MASTERY_1000))
        assertEquals(R.drawable.weekly_goal_streak_26_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_26))
        assertEquals(R.drawable.weekly_goal_streak_52_badge, drawable(AchievementId.WEEKLY_GOAL_STREAK_52))
        assertEquals(R.drawable.early_adopter_badge, drawable(AchievementId.EARLY_ADOPTER))
        assertEquals(R.drawable.developer_badge, drawable(AchievementId.DEVELOPER))
        val published = AchievementId.entries.filter { !it.secret }
        val mapped = published.map { drawable(it) }
        assertEquals(mapped.size, mapped.toSet().size)
        published.forEach { id ->
            assertTrue(BadgeArtworkResolver.isProductionArtwork(id.badgeKey))
            assertNotEquals(R.drawable.ic_badge_placeholder, drawable(id))
        }
        val locked = AchievementCatalog.secrets.map {
            BadgeArtworkResolver.drawableForWall(it, unlocked = false)
        }
        assertEquals(listOf(R.drawable.hidden_gem_placeholder), locked.toSet().toList())
        assertTrue(
            AchievementId.entries.none { drawable(it) == R.drawable.hidden_gem_placeholder }
        )
        assertEquals(
            mapOf(
                AchievementId.SILENT_NIGHT to R.drawable.silent_night_heavy_weights_badge,
                AchievementId.TRICK_OR_LIFT to R.drawable.trick_or_lift_badge,
                AchievementId.NEW_YEAR_SAME_ME to R.drawable.new_year_same_me_badge,
                AchievementId.LEAP_DAY_LIFTER to R.drawable.leap_day_lifter_badge,
                AchievementId.FRIDAY_THE_STRONGTEENTH to R.drawable.friday_the_strongteenth_badge,
                AchievementId.ONE_MORE_THING to R.drawable.one_more_thing_badge,
                AchievementId.TRIPLE_CROWN to R.drawable.triple_crown_badge
            ),
            AchievementCatalog.secrets.associateWith { drawable(it) }
        )
        AchievementCatalog.secrets.forEach { id ->
            assertTrue(BadgeArtworkResolver.isProductionArtwork(id.badgeKey))
            assertEquals(drawable(id), BadgeArtworkResolver.drawableForWall(id, unlocked = true))
            assertEquals(
                R.drawable.hidden_gem_placeholder,
                BadgeArtworkResolver.drawableForWall(id, unlocked = false)
            )
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
