package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.FounderLifetimeEntitlement
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FounderAchievementTest {
    private val now = Instant.parse("2026-10-07T08:00:00Z")

    @Test
    fun founderIsTheOnlyReleasedSpecialAchievement() {
        assertEquals(listOf(AchievementId.FOUNDER), AchievementCatalog.special)
        assertEquals(AchievementAccess.SPECIAL, AchievementId.FOUNDER.access)
        assertEquals(AchievementCategory.SPECIAL, AchievementId.FOUNDER.category)
        assertNull(AchievementId.FOUNDER.workoutCountTarget)
        assertNull(AchievementId.FOUNDER.volumeThresholdKg)
        assertNull(AchievementId.FOUNDER.streakWeeks)
        assertFalse(AchievementId.FOUNDER.revokesWhenWorkoutCountDrops)
        assertTrue(AchievementCatalog.pro.none { it == AchievementId.FOUNDER })
        assertTrue(AchievementCatalog.free.none { it.access == AchievementAccess.SPECIAL })
    }

    @Test
    fun onlyFounderLifetimeQualifies() {
        val paid = EntitlementResolver.resolve(
            EntitlementSources.of(
                subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(86_400))
            ),
            now
        )
        val tester = EntitlementResolver.resolve(
            EntitlementSources.of(
                backendFounder = BackendFounderEntitlement(
                    temporaryFounderPro = true,
                    validUntil = now.plusSeconds(86_400)
                )
            ),
            now
        )
        val lifetime = EntitlementResolver.resolve(
            EntitlementSources.of(founderLifetime = FounderLifetimeEntitlement(active = true)),
            now
        )
        val backendLifetime = EntitlementResolver.resolve(
            EntitlementSources.of(
                backendFounder = BackendFounderEntitlement(
                    founderLifetime = true,
                    temporaryFounderPro = true,
                    validUntil = now.plusSeconds(86_400)
                )
            ),
            now
        )
        assertTrue(paid.grantsPro)
        assertFalse(paid.founderLifetime)
        assertTrue(tester.grantsPro)
        assertTrue(tester.temporaryTesterPro)
        assertFalse(tester.founderLifetime)
        assertTrue(lifetime.founderLifetime)
        assertTrue(lifetime.grantsPro)
        assertFalse(lifetime.temporaryTesterPro)
        assertTrue(backendLifetime.founderLifetime)
        assertFalse(backendLifetime.temporaryTesterPro)

        assertTrue(plan(grantsPro = true, founderLifetime = false).insertUnlocks.none { it.achievementId == AchievementId.FOUNDER })
        assertTrue(plan(grantsPro = false, founderLifetime = false).insertUnlocks.none { it.achievementId == AchievementId.FOUNDER })
        val awarded = plan(grantsPro = false, founderLifetime = true).insertUnlocks.single { it.achievementId == AchievementId.FOUNDER }
        assertEquals(5_000L, awarded.unlockedAt)
        assertEquals(5_000L, awarded.celebratedAt)
    }

    @Test
    fun founderUnlockIsIdempotentAndSurvivesLosingTheGrant() {
        val first = plan(grantsPro = true, founderLifetime = true)
        val stored = listOf(StoredUnlock(AchievementId.FOUNDER, celebratedAt = 5_000L))
        val again = AchievementReconciler.plan(
            request(grantsPro = true, founderLifetime = true, nowMillis = 9_000L),
            stored,
            emptyList()
        )
        assertTrue(again.insertUnlocks.none { it.achievementId == AchievementId.FOUNDER })
        val lost = AchievementReconciler.plan(
            request(grantsPro = false, founderLifetime = false, nowMillis = 11_000L),
            stored,
            emptyList()
        )
        assertTrue(lost.revoke.isEmpty())
        assertTrue(lost.insertUnlocks.none { it.achievementId == AchievementId.FOUNDER })
        assertEquals(5_000L, first.insertUnlocks.single { it.achievementId == AchievementId.FOUNDER }.unlockedAt)
    }

    @Test
    fun lockedFounderHasNoProgressAndIsNotAlmostThere() {
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 249,
            unlocks = emptyList(),
            events = emptyList(),
            lifetimeVolumeKg = 99_000.0
        )
        val founder = board.items.single { it.id == AchievementId.FOUNDER }
        assertFalse(founder.unlocked)
        assertEquals(BadgeVisualState.LOCKED, founder.visualState())
        assertNull(founder.countProgress)
        assertNull(founder.volumeProgress)
        assertTrue(BadgeWallPresenter.almostThere(board).none { it.achievementId == AchievementId.FOUNDER })
        val earned = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = listOf(UnlockSnapshot(AchievementId.FOUNDER, 5_000L, 5_000L, null)),
            events = emptyList()
        )
        assertEquals(BadgeVisualState.EARNED, earned.items.single { it.id == AchievementId.FOUNDER }.visualState())
        assertTrue(earned.pending.none { celebration ->
            when (celebration) {
                is PendingCelebration.ProUnlocked -> celebration.achievementId == AchievementId.FOUNDER
                is PendingCelebration.JourneyUnlocked -> celebration.achievementId == AchievementId.FOUNDER
                is PendingCelebration.PerformanceUnlocked -> celebration.achievementId == AchievementId.FOUNDER
                is PendingCelebration.WorkoutCountUnlocked -> celebration.achievementId == AchievementId.FOUNDER
                is PendingCelebration.WeeklyStreakUnlocked -> celebration.achievementId == AchievementId.FOUNDER
                else -> false
            }
        })
    }

    private fun plan(grantsPro: Boolean, founderLifetime: Boolean): ReconcilePlan {
        return AchievementReconciler.plan(request(grantsPro, founderLifetime, 5_000L), emptyList(), emptyList())
    }

    private fun request(grantsPro: Boolean, founderLifetime: Boolean, nowMillis: Long): ReconcileRequest {
        return ReconcileRequest(
            initialized = true,
            completedWorkoutCount = 0,
            achievedWeeks = emptyList(),
            nowMillis = nowMillis,
            triggerClientWorkoutId = null,
            grantsPro = grantsPro,
            founderLifetime = founderLifetime
        )
    }
}
