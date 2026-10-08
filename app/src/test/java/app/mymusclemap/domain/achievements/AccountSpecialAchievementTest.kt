package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.SpecialAchievementGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AccountSpecialAchievementTest {
    private val grantedAt = Instant.parse("2026-01-15T00:00:00Z")
    private val grantedMillis = grantedAt.toEpochMilli()

    @Test
    fun founderEarlyAdopterAndDeveloperFollowTrustedAccountAuthority() {
        val legacy = board(
            unlocks = listOf(AchievementId.FOUNDER, AchievementId.EARLY_ADOPTER, AchievementId.DEVELOPER),
            authority = AccountAchievementAuthority()
        )
        assertFalse(item(legacy, AchievementId.FOUNDER).unlocked)
        assertFalse(item(legacy, AchievementId.EARLY_ADOPTER).unlocked)
        assertFalse(item(legacy, AchievementId.DEVELOPER).unlocked)
        assertEquals(BadgeVisualState.LOCKED, item(legacy, AchievementId.EARLY_ADOPTER).visualState())
        assertTrue(BadgeWallPresenter.present(legacy).catalog.none { it.id == AchievementId.DEVELOPER })

        val trusted = board(
            unlocks = emptyList(),
            authority = AccountAchievementAuthority(
                founderRecognized = true,
                founderGrantedAtMillis = grantedMillis,
                earlyAdopterGrantedAtMillis = grantedMillis,
                developerGrantedAtMillis = grantedMillis
            )
        )
        assertEquals(grantedMillis, item(trusted, AchievementId.FOUNDER).unlockedAt)
        assertEquals(grantedMillis, item(trusted, AchievementId.EARLY_ADOPTER).unlockedAt)
        assertEquals(grantedMillis, item(trusted, AchievementId.DEVELOPER).unlockedAt)
        assertEquals(BadgeVisualState.EARNED, item(trusted, AchievementId.FOUNDER).visualState())
        assertEquals(BadgeVisualState.EARNED, item(trusted, AchievementId.EARLY_ADOPTER).visualState())
        assertEquals(BadgeVisualState.EARNED, item(trusted, AchievementId.DEVELOPER).visualState())
        assertTrue(trusted.pending.isEmpty())
        val special = BadgeWallPresenter.present(trusted, AchievementAccess.SPECIAL)
        assertEquals(
            listOf(AchievementId.FOUNDER, AchievementId.EARLY_ADOPTER, AchievementId.DEVELOPER),
            special.sections.flatMap { it.items }.map { it.id }
        )
        assertTrue(special.almostThere.none { it.achievementId.isAccountStatus })
        assertTrue(special.catalog.none { it.visualState() == BadgeVisualState.REQUIREMENT_MET_PRO_LOCKED })
    }

    @Test
    fun switchingAccountDropsThePreviousSpecialBadges() {
        val accountA = AccountAchievementAuthority(
            founderRecognized = true,
            founderGrantedAtMillis = grantedMillis,
            earlyAdopterGrantedAtMillis = grantedMillis,
            developerGrantedAtMillis = grantedMillis
        )
        val first = BadgeWallPresenter.present(board(emptyList(), accountA))
        assertTrue(first.item(AchievementId.FOUNDER)!!.unlocked)
        assertTrue(first.catalog.any { it.id == AchievementId.DEVELOPER })

        val second = BadgeWallPresenter.present(board(emptyList(), AccountAchievementAuthority()))
        assertFalse(second.item(AchievementId.FOUNDER)!!.unlocked)
        assertFalse(second.item(AchievementId.EARLY_ADOPTER)!!.unlocked)
        assertTrue(second.catalog.none { it.id == AchievementId.DEVELOPER })
    }

    @Test
    fun specialGrantsDoNotGrantProExceptFounderLifetime() {
        val now = Instant.parse("2026-10-07T00:00:00Z")
        val specialsOnly = EntitlementResolver.resolve(
            EntitlementSources.of(
                backendFounder = BackendFounderEntitlement(
                    validUntil = now.plusSeconds(60),
                    specialGrants = listOf(
                        SpecialAchievementGrant("EARLY_ADOPTER", grantedAt),
                        SpecialAchievementGrant("DEVELOPER", grantedAt)
                    )
                )
            ),
            now
        )
        assertFalse(specialsOnly.grantsPro)
        assertFalse(specialsOnly.founderLifetime)
        val founder = EntitlementResolver.resolve(
            EntitlementSources.of(
                backendFounder = BackendFounderEntitlement(
                    founderLifetime = true,
                    founderGrantedAt = grantedAt,
                    validUntil = now.plusSeconds(60)
                )
            ),
            now
        )
        assertTrue(founder.grantsPro)
        assertTrue(founder.founderLifetime)
    }

    @Test
    fun proOwnershipFollowsTheCurrentEntitlementAndKeepsTheHistoricalInstant() {
        val historical = 2_000L
        val unlocks = listOf(
            UnlockSnapshot(AchievementId.VOLUME_MASTER, historical, historical, null),
            UnlockSnapshot(AchievementId.PR_HUNTER_50, historical, historical, null),
            UnlockSnapshot(AchievementId.EXERCISE_MASTERY_250, historical, historical, null),
            UnlockSnapshot(AchievementId.WEEKLY_GOAL_STREAK_26, historical, historical, null),
            UnlockSnapshot(AchievementId.IRON_DISCIPLINE, historical, historical, null),
            UnlockSnapshot(AchievementId.WORKOUTS_10, historical, historical, null)
        )
        val free = board(
            unlocks = emptyList(),
            authority = AccountAchievementAuthority(),
            grantsPro = false,
            workouts = 250,
            volume = 100_000.0,
            records = 50,
            mastery = 250,
            streak = 26,
            stored = unlocks
        )
        listOf(
            AchievementId.VOLUME_MASTER,
            AchievementId.PR_HUNTER_50,
            AchievementId.EXERCISE_MASTERY_250,
            AchievementId.WEEKLY_GOAL_STREAK_26,
            AchievementId.IRON_DISCIPLINE
        ).forEach { id ->
            val badge = item(free, id)
            assertTrue(id.name, badge.requirementMet)
            assertFalse(id.name, badge.unlocked)
            assertEquals(id.name, historical, badge.unlockedAt)
            assertEquals(BadgeVisualState.LOCKED, badge.visualState())
        }
        assertTrue(item(free, AchievementId.WORKOUTS_10).unlocked)
        assertTrue(free.pending.none { it is PendingCelebration.ProUnlocked })

        val pro = board(
            unlocks = emptyList(),
            authority = AccountAchievementAuthority(),
            grantsPro = true,
            workouts = 250,
            volume = 100_000.0,
            records = 50,
            mastery = 250,
            streak = 26,
            stored = unlocks
        )
        listOf(
            AchievementId.VOLUME_MASTER,
            AchievementId.PR_HUNTER_50,
            AchievementId.EXERCISE_MASTERY_250,
            AchievementId.WEEKLY_GOAL_STREAK_26,
            AchievementId.IRON_DISCIPLINE
        ).forEach { id ->
            val badge = item(pro, id)
            assertTrue(id.name, badge.unlocked)
            assertEquals(historical, badge.unlockedAt)
        }
        assertTrue(item(pro, AchievementId.WORKOUTS_10).unlocked)
        assertTrue(pro.pending.none { it is PendingCelebration.ProUnlocked })

        val founderStillEarned = board(
            unlocks = emptyList(),
            authority = AccountAchievementAuthority(
                founderRecognized = true,
                founderGrantedAtMillis = grantedMillis
            ),
            grantsPro = false,
            workouts = 250,
            volume = 100_000.0,
            stored = unlocks
        )
        assertTrue(item(founderStillEarned, AchievementId.FOUNDER).unlocked)
        assertEquals(grantedMillis, item(founderStillEarned, AchievementId.FOUNDER).unlockedAt)
        assertEquals(BadgeVisualState.EARNED, item(founderStillEarned, AchievementId.FOUNDER).visualState())
        assertFalse(item(founderStillEarned, AchievementId.VOLUME_MASTER).unlocked)
        assertEquals(historical, item(founderStillEarned, AchievementId.VOLUME_MASTER).unlockedAt)
        assertEquals(
            BadgeVisualState.LOCKED,
            item(founderStillEarned, AchievementId.VOLUME_MASTER).visualState()
        )
        assertEquals(listOf(10, 25, 50, 100), AchievementCatalog.prHunter.map { it.prHunterTarget })
        assertEquals(listOf(100, 250, 500, 1000), AchievementCatalog.exerciseMastery.map { it.masterySetTarget })
        assertEquals(listOf(4, 8, 12, 26, 52), AchievementCatalog.weeklyStreaks.map { it.streakWeeks })
    }

    private fun item(board: AchievementBoard, id: AchievementId): BadgeWallItem {
        return board.items.single { it.id == id }
    }

    private fun board(
        unlocks: List<AchievementId>,
        authority: AccountAchievementAuthority,
        grantsPro: Boolean = false,
        workouts: Int = 0,
        volume: Double = 0.0,
        records: Int = 0,
        mastery: Int = 0,
        streak: Int = 0,
        stored: List<UnlockSnapshot> = unlocks.map {
            UnlockSnapshot(it, unlockedAt = 1L, celebratedAt = 1L, triggerClientWorkoutId = null)
        }
    ): AchievementBoard {
        return AchievementBoardAssembler.assemble(
            completedWorkoutCount = workouts,
            unlocks = stored,
            events = emptyList(),
            currentStreak = streak,
            bestStreak = streak,
            lifetimeVolumeKg = volume,
            prEventCount = records,
            leadingExerciseSets = mastery,
            grantsPro = grantsPro,
            accountAuthority = authority
        )
    }
}
