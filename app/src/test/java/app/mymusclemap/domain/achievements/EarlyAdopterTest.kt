package app.mymusclemap.domain.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlyAdopterTest {
    @Test
    fun earlyAdopterIsALockedSpecialWithoutEligibility() {
        assertEquals(AchievementAccess.SPECIAL, AchievementId.EARLY_ADOPTER.access)
        assertEquals(AchievementCategory.SPECIAL, AchievementId.EARLY_ADOPTER.category)
        assertNull(AchievementId.EARLY_ADOPTER.workoutCountTarget)
        assertNull(AchievementId.EARLY_ADOPTER.prHunterTarget)
        assertNull(AchievementId.EARLY_ADOPTER.masterySetTarget)
        assertNull(AchievementId.EARLY_ADOPTER.streakWeeks)
        assertNull(AchievementId.EARLY_ADOPTER.volumeThresholdKg)
        assertFalse(AchievementId.EARLY_ADOPTER.revokesWhenWorkoutCountDrops)
        assertTrue(AchievementId.entries.none { it.name == "FOUNDING_TESTER" })

        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 250,
            unlocks = emptyList(),
            events = emptyList(),
            currentStreak = 52,
            bestStreak = 52,
            lifetimeVolumeKg = 100_000.0,
            prEventCount = 100,
            leadingExerciseSets = 1000
        )
        val early = board.items.single { it.id == AchievementId.EARLY_ADOPTER }
        assertFalse(early.unlocked)
        assertNull(early.unlockedAt)
        assertNull(early.countProgress)
        assertNull(early.volumeProgress)
        assertFalse(early.requirementMet)
        assertEquals(BadgeVisualState.LOCKED, early.visualState())
        assertTrue(BadgeWallPresenter.almostThere(board).none { it.achievementId == AchievementId.EARLY_ADOPTER })
        assertTrue(BadgeWallPresenter.almostThere(board).none { it.achievementId == AchievementId.FOUNDER })
    }

    @Test
    fun noClientSignalCreatesAnEarlyAdopterRow() {
        val plan = AchievementReconciler.plan(
            request = ReconcileRequest(
                initialized = true,
                completedWorkoutCount = 250,
                achievedWeeks = emptyList(),
                nowMillis = 5_000L,
                triggerClientWorkoutId = "live",
                grantsPro = true,
                founderLifetime = true
            ),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertTrue(plan.insertUnlocks.none { it.achievementId.isAccountStatus })
    }

    @Test
    fun progressionFamiliesStayInThresholdOrder() {
        assertEquals(
            listOf(10, 25, 50, 100),
            AchievementCatalog.prHunter.map { it.prHunterTarget }
        )
        assertEquals(
            listOf(100, 250, 500, 1000),
            AchievementCatalog.exerciseMastery.map { it.masterySetTarget }
        )
        assertEquals(
            listOf(4, 8, 12, 26, 52),
            AchievementCatalog.weeklyStreaks.map { it.streakWeeks }
        )
    }
}
