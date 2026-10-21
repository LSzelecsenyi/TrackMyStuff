package app.mymusclemap.domain.achievements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewAchievementTest {
    @Test
    fun latestUnlockUsesTheNewestStoredTimestamp() {
        val overview = BadgeWallPresenter.overview(
            board(
                unlocks = listOf(
                    unlock(AchievementId.FIRST_WORKOUT, 100L),
                    unlock(AchievementId.FIRST_PR, 250L),
                    unlock(AchievementId.WORKOUTS_5, 180L)
                ),
                completed = 5
            )
        )
        assertEquals(AchievementId.FIRST_PR, overview.latest?.id)
        assertEquals(250L, overview.latest?.unlockedAtEpochMillis)
    }

    @Test
    fun equalTimestampsUseTheLaterCatalogEntry() {
        val overview = BadgeWallPresenter.overview(
            board(
                unlocks = listOf(
                    unlock(AchievementId.FIRST_WORKOUT, 100L),
                    unlock(AchievementId.SILENT_NIGHT, 100L)
                )
            )
        )
        assertEquals(AchievementId.SILENT_NIGHT, overview.latest?.id)
        val again = BadgeWallPresenter.overview(
            board(
                unlocks = listOf(
                    unlock(AchievementId.SILENT_NIGHT, 100L),
                    unlock(AchievementId.FIRST_WORKOUT, 100L)
                )
            )
        )
        assertEquals(overview.latest, again.latest)
    }

    @Test
    fun anEarlierCatalogEntryStillWinsWhenItsTimestampIsNewer() {
        val overview = BadgeWallPresenter.overview(
            board(
                unlocks = listOf(
                    unlock(AchievementId.FIRST_WORKOUT, 400L),
                    unlock(AchievementId.SILENT_NIGHT, 100L)
                )
            )
        )
        assertEquals(AchievementId.FIRST_WORKOUT, overview.latest?.id)
    }

    @Test
    fun anEarnedHiddenGemCanBeTheLatestUnlock() {
        val overview = BadgeWallPresenter.overview(
            board(
                unlocks = listOf(
                    unlock(AchievementId.FIRST_PR, 20L),
                    unlock(AchievementId.SILENT_NIGHT, 90L)
                )
            )
        )
        assertEquals(AchievementId.SILENT_NIGHT, overview.latest?.id)
        assertFalse(overview.upcoming?.id?.secret == true)
    }

    @Test
    fun lockedHiddenGemsAreNeverRecommended() {
        val actionAwards = listOf(
            AchievementId.TARGET_WEIGHT_REACHED,
            AchievementId.FIRST_WORKOUT,
            AchievementId.FIRST_CUSTOM_WORKOUT_PLAN,
            AchievementId.FIRST_MONTHLY_REPORT,
            AchievementId.FIRST_PR,
            AchievementId.WEIGHT_PR,
            AchievementId.REP_RECORD,
            AchievementId.VOLUME_RECORD
        )
        val overview = BadgeWallPresenter.overview(
            board(unlocks = actionAwards.map { unlock(it, 10L) })
        )
        assertNull(overview.upcoming)
        assertTrue(overview.latest != null)
        assertTrue(AchievementCatalog.secrets.none { it == overview.upcoming?.id })
        assertTrue(AchievementCatalog.special.none { it == overview.upcoming?.id })
    }

    @Test
    fun earnedAndIneligibleAwardsAreNotTheNextMilestone() {
        val overview = BadgeWallPresenter.overview(
            board(
                completed = 8,
                prEventCount = 9,
                unlocks = listOf(unlock(AchievementId.FIRST_PR, 30L), unlock(AchievementId.WORKOUTS_5, 10L))
            )
        )
        assertEquals(AchievementId.WORKOUTS_10, overview.upcoming?.id)
        val progress = overview.upcoming?.progress
        assertTrue(progress is OverviewProgress.Count)
        progress as OverviewProgress.Count
        assertEquals(8, progress.current)
        assertEquals(10, progress.threshold)
        assertFalse(overview.upcoming?.id == AchievementId.FIRST_PR)
        assertTrue(AchievementCatalog.pro.none { it == overview.upcoming?.id })
        assertTrue(AchievementCatalog.special.none { it == overview.upcoming?.id })
    }

    @Test
    fun aProAwardIsRecommendedOnlyWhileItCanBeEarned() {
        val locked = BadgeWallPresenter.overview(
            board(completed = 2, prEventCount = 9, grantsPro = false)
        )
        assertEquals(AchievementId.WORKOUTS_5, locked.upcoming?.id)

        val entitled = BadgeWallPresenter.overview(
            board(prEventCount = 9, grantsPro = true)
        )
        assertEquals(AchievementId.PR_HUNTER_10, entitled.upcoming?.id)
        val progress = entitled.upcoming?.progress
        assertTrue(progress is OverviewProgress.Count)
        progress as OverviewProgress.Count
        assertEquals(9, progress.current)
        assertEquals(10, progress.threshold)
    }

    @Test
    fun measurableProgressOutranksAnActionAndKeepsTheNextTier() {
        val workoutsAhead = BadgeWallPresenter.overview(board(completed = 8, streak = 3))
        assertEquals(AchievementId.WORKOUTS_10, workoutsAhead.upcoming?.id)

        val streakAhead = BadgeWallPresenter.overview(board(completed = 2, streak = 3))
        assertEquals(AchievementId.WEEKLY_GOAL_STREAK_4, streakAhead.upcoming?.id)
        assertTrue(streakAhead.upcoming?.progress is OverviewProgress.Count)
    }

    @Test
    fun volumeUsesItsOwnProgressInsteadOfAPercentage() {
        val overview = BadgeWallPresenter.overview(
            board(lifetimeVolumeKg = 25_000.0, grantsPro = true)
        )
        val progress = overview.upcoming?.progress
        assertEquals(AchievementId.VOLUME_MASTER, overview.upcoming?.id)
        assertTrue(progress is OverviewProgress.Volume)
        progress as OverviewProgress.Volume
        assertEquals(25_000.0, progress.currentKg, 0.0)
        assertEquals(100_000.0, progress.thresholdKg, 0.0)
    }

    @Test
    fun noMeasurableProgressUsesTheFirstEligibleAction() {
        val overview = BadgeWallPresenter.overview(board())
        assertNull(overview.latest)
        assertEquals(AchievementId.TARGET_WEIGHT_REACHED, overview.upcoming?.id)
        assertEquals(OverviewProgress.Requirement, overview.upcoming?.progress)

        val afterTarget = BadgeWallPresenter.overview(
            board(unlocks = listOf(unlock(AchievementId.TARGET_WEIGHT_REACHED, 5L)))
        )
        assertEquals(AchievementId.FIRST_WORKOUT, afterTarget.upcoming?.id)
        assertEquals(OverviewProgress.Requirement, afterTarget.upcoming?.progress)
    }

    @Test
    fun aNewAwardUpdatesTheOverviewFromTheSameBoardFlow() = runTest {
        val boards = MutableStateFlow(board())
        val seen = mutableListOf<OverviewAchievements>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            boards.map { current -> current.let(BadgeWallPresenter::overview) }.collect { seen.add(it) }
        }
        assertNull(seen.last().latest)
        boards.value = board(unlocks = listOf(unlock(AchievementId.REP_RECORD, 70L)))
        assertEquals(AchievementId.REP_RECORD, seen.last()?.latest?.id)
        assertFalse(seen.last()?.upcoming?.id == AchievementId.REP_RECORD)
        job.cancel()
    }

    private fun board(
        completed: Int = 0,
        streak: Int = 0,
        unlocks: List<UnlockSnapshot> = emptyList(),
        grantsPro: Boolean = false,
        prEventCount: Int = 0,
        lifetimeVolumeKg: Double = 0.0
    ): AchievementBoard {
        return AchievementBoardAssembler.assemble(
            completedWorkoutCount = completed,
            unlocks = unlocks,
            events = emptyList(),
            currentStreak = streak,
            bestStreak = streak,
            lifetimeVolumeKg = lifetimeVolumeKg,
            prEventCount = prEventCount,
            grantsPro = grantsPro
        )
    }

    private fun unlock(id: AchievementId, at: Long): UnlockSnapshot {
        return UnlockSnapshot(
            achievementId = id,
            unlockedAt = at,
            celebratedAt = at,
            triggerClientWorkoutId = null
        )
    }
}
