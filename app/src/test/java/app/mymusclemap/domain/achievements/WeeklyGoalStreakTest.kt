package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.calendar.WeekCalendar
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import app.mymusclemap.domain.workout.WeeklyGoalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WeeklyGoalStreakTest {
    private val start = LocalDate.of(2026, 10, 5).also {
        check(it == WeekCalendar.start(it))
    }

    @Test
    fun streakLengthMatchesConsecutiveAchievedWeeks() {
        listOf(0, 1, 3, 4, 7, 8, 11, 12).forEach { weeks ->
            val status = achievedWeeks(weeks)
            assertEquals(weeks, WeeklyGoalStreakEvaluator.currentStreak(status))
            assertEquals(weeks, WeeklyGoalStreakEvaluator.bestStreak(status))
        }
    }

    @Test
    fun aMissResetsTheRunInsteadOfCountingEverySuccess() {
        val counts = mapOf(
            start to 1,
            start.plusWeeks(1) to 1,
            start.plusWeeks(3) to 1,
            start.plusWeeks(4) to 1
        )
        val status = evaluate(listOf(goal(start, 1)), counts, sundayOf(4))
        assertEquals(2, WeeklyGoalStreakEvaluator.currentStreak(status))
        assertEquals(2, WeeklyGoalStreakEvaluator.bestStreak(status))
        assertTrue(WeeklyGoalStreakEvaluator.qualifications(status).isEmpty())
    }

    @Test
    fun thresholdsUnlockOnlyTheTiersTheBestRunHasReached() {
        assertEquals(emptySet<AchievementId>(), qualified(3))
        assertEquals(setOf(AchievementId.WEEKLY_GOAL_STREAK_4), qualified(4))
        assertEquals(
            setOf(AchievementId.WEEKLY_GOAL_STREAK_4, AchievementId.WEEKLY_GOAL_STREAK_8),
            qualified(9)
        )
        assertEquals(
            setOf(
                AchievementId.WEEKLY_GOAL_STREAK_4,
                AchievementId.WEEKLY_GOAL_STREAK_8,
                AchievementId.WEEKLY_GOAL_STREAK_12
            ),
            qualified(12)
        )
    }

    @Test
    fun eachHistoricalWeekUsesTheRevisionInForceThatWeek() {
        val week3 = start.plusWeeks(2)
        val history = listOf(goal(start, 3), goal(week3, 4))
        val counts = mapOf(
            start to 3,
            start.plusWeeks(1) to 3,
            week3 to 4,
            start.plusWeeks(3) to 4
        )
        val status = evaluate(history, counts, sundayOf(3))
        assertEquals(4, WeeklyGoalStreakEvaluator.bestStreak(status))
        assertTrue(status.weeks.getValue(start).achieved)
        assertEquals(3, status.weeks.getValue(start).goal)
        assertEquals(4, status.weeks.getValue(week3).goal)
    }

    @Test
    fun aWeekWithNoGoalBreaksTheStreak() {
        val history = listOf(goal(start, 1), goal(start.plusWeeks(2), null))
        val counts = mapOf(start to 1, start.plusWeeks(1) to 1, start.plusWeeks(2) to 3)
        val status = evaluate(history, counts, sundayOf(2))
        assertEquals(0, WeeklyGoalStreakEvaluator.currentStreak(status))
        assertEquals(2, WeeklyGoalStreakEvaluator.bestStreak(status))
        assertEquals(app.mymusclemap.domain.workout.WeekVerdict.NoGoal, status.current.verdict)
    }

    @Test
    fun anUnfinishedCurrentWeekKeepsTheStreakAlreadyEarned() {
        val status = evaluate(
            listOf(goal(start, 1)),
            (0 until 4).associate { start.plusWeeks(it.toLong()) to 1 },
            start.plusWeeks(4)
        )
        assertEquals(4, WeeklyGoalStreakEvaluator.bestStreak(status))
        assertEquals(4, WeeklyGoalStreakEvaluator.currentStreak(status))
        assertFalse(status.current.achieved)
        assertTrue(AchievementId.WEEKLY_GOAL_STREAK_4 in qualifiedIds(status))
        assertFalse(AchievementId.WEEKLY_GOAL_STREAK_8 in qualifiedIds(status))
    }

    @Test
    fun aGraceMissDoesNotWipeTheStreakTheDomainAlreadyKept() {
        val graceWeek = start.plusWeeks(2)
        val history = listOf(goal(start, 1), WeeklyGoalRevision(graceWeek, 1, graceWeek = true))
        val counts = mapOf(
            start to 1,
            start.plusWeeks(1) to 1,
            start.plusWeeks(3) to 1
        )
        val status = evaluate(history, counts, sundayOf(3))
        assertEquals(3, WeeklyGoalStreakEvaluator.currentStreak(status))
        assertEquals(app.mymusclemap.domain.workout.WeekVerdict.GraceMiss, status.weeks.getValue(graceWeek).verdict)
    }

    @Test
    fun unlockedAtIsTheDayTheQualifyingWeekCrossedItsGoal() {
        val wednesday = start.plusDays(2)
        val status = evaluate(
            listOf(goal(start, 2)),
            mapOf(
                start to 2,
                start.plusWeeks(1) to 2,
                start.plusWeeks(2) to 2,
                start.plusWeeks(3) to 1,
                wednesday.plusWeeks(3) to 1
            ),
            sundayOf(3)
        )
        val bronze = WeeklyGoalStreakEvaluator.qualifications(status).single()
        assertEquals(AchievementId.WEEKLY_GOAL_STREAK_4, bronze.achievementId)
        val expected = wednesday.plusWeeks(3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, bronze.unlockedAt)
    }

    @Test
    fun progressUsesTheCurrentStreakAndEarnedTiersStayEarned() {
        val two = board(currentStreak = 2, earned = emptyList())
        assertEquals(2, progress(two, AchievementId.WEEKLY_GOAL_STREAK_4).current)
        assertEquals(4, progress(two, AchievementId.WEEKLY_GOAL_STREAK_4).threshold)
        assertEquals(2, progress(two, AchievementId.WEEKLY_GOAL_STREAK_8).current)
        assertEquals(2, progress(two, AchievementId.WEEKLY_GOAL_STREAK_12).current)

        val six = board(currentStreak = 6, earned = listOf(AchievementId.WEEKLY_GOAL_STREAK_4))
        assertTrue(six.items.single { it.id == AchievementId.WEEKLY_GOAL_STREAK_4 }.unlocked)
        assertEquals(6, progress(six, AchievementId.WEEKLY_GOAL_STREAK_8).current)
        assertEquals(6, progress(six, AchievementId.WEEKLY_GOAL_STREAK_12).current)

        val ten = board(
            currentStreak = 10,
            earned = listOf(AchievementId.WEEKLY_GOAL_STREAK_4, AchievementId.WEEKLY_GOAL_STREAK_8)
        )
        assertTrue(ten.items.single { it.id == AchievementId.WEEKLY_GOAL_STREAK_8 }.unlocked)
        assertEquals(10, progress(ten, AchievementId.WEEKLY_GOAL_STREAK_12).current)
    }

    @Test
    fun twelveHistoricalWeeksLeaveOnlyGoldPending() {
        val qualifications = WeeklyGoalStreakEvaluator.qualifications(achievedWeeks(12))
        val plan = AchievementReconciler.plan(
            request = request(qualifications),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertEquals(
            setOf(
                AchievementId.WEEKLY_GOAL_STREAK_4,
                AchievementId.WEEKLY_GOAL_STREAK_8,
                AchievementId.WEEKLY_GOAL_STREAK_12
            ),
            plan.insertUnlocks.map { it.achievementId }.toSet()
        )
        val pending = plan.insertUnlocks.single { it.celebratedAt == null }
        assertEquals(AchievementId.WEEKLY_GOAL_STREAK_12, pending.achievementId)
        assertTrue(plan.insertUnlocks.filter { it.achievementId != AchievementId.WEEKLY_GOAL_STREAK_12 }
            .all { it.celebratedAt == 1_000L })
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 12,
            unlocks = plan.insertUnlocks.map {
                UnlockSnapshot(it.achievementId, it.unlockedAt, it.celebratedAt, it.triggerClientWorkoutId)
            },
            events = emptyList(),
            currentStreak = 12
        )
        val shown = board.pending.filterIsInstance<PendingCelebration.WeeklyStreakUnlocked>()
        assertEquals(listOf(AchievementId.WEEKLY_GOAL_STREAK_12), shown.map { it.achievementId })
    }

    @Test
    fun reachingFourSilencesTheWeeklyCompletionCelebration() {
        val week = AchievedWeek(start, completed = 1, goal = 1)
        val qualifications = WeeklyGoalStreakEvaluator.qualifications(achievedWeeks(4))
        val plan = AchievementReconciler.plan(
            request = request(qualifications, weeks = listOf(week), trigger = "week-4"),
            unlocks = emptyList(),
            events = emptyList()
        )
        val weekly = plan.insertEvents.single { it.kind == ProgressEventKind.WEEKLY_GOAL_COMPLETED }
        assertEquals(1_000L, weekly.celebratedAt)
        val streak = plan.insertUnlocks.single { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_4 }
        assertNull(streak.celebratedAt)
        assertEquals("week-4", streak.triggerClientWorkoutId)
    }

    @Test
    fun aLaterMissDoesNotRevokeOrMoveTheOriginalUnlock() {
        val stored = listOf(
            StoredUnlock(AchievementId.WEEKLY_GOAL_STREAK_4, celebratedAt = 60L),
            StoredUnlock(AchievementId.WEEKLY_GOAL_STREAK_8, celebratedAt = 60L),
            StoredUnlock(AchievementId.WEEKLY_GOAL_STREAK_12, celebratedAt = 60L),
            StoredUnlock(AchievementId.WORKOUTS_5, celebratedAt = 60L)
        )
        val plan = AchievementReconciler.plan(
            request = request(emptyList(), count = 4),
            unlocks = stored,
            events = emptyList()
        )
        assertTrue(plan.revoke.none { it.streakWeeks != null })
        assertEquals(setOf(AchievementId.WORKOUTS_5), plan.revoke)
        assertTrue(plan.insertUnlocks.none { it.achievementId.streakWeeks != null })
    }

    @Test
    fun twentyFiveDoesNotReachTwentySixAndFiftyOneDoesNotReachFiftyTwo() {
        assertFalse(qualified(25).contains(AchievementId.WEEKLY_GOAL_STREAK_26))
        assertTrue(qualified(26).contains(AchievementId.WEEKLY_GOAL_STREAK_26))
        assertFalse(qualified(26).contains(AchievementId.WEEKLY_GOAL_STREAK_52))
        assertFalse(qualified(51).contains(AchievementId.WEEKLY_GOAL_STREAK_52))
        assertTrue(qualified(52).contains(AchievementId.WEEKLY_GOAL_STREAK_52))
    }

    @Test
    fun proStreakTiersStayProAndTheFamilyOrderIsUnchanged() {
        assertEquals(AchievementAccess.FREE, AchievementId.WEEKLY_GOAL_STREAK_4.access)
        assertEquals(AchievementAccess.FREE, AchievementId.WEEKLY_GOAL_STREAK_8.access)
        assertEquals(AchievementAccess.FREE, AchievementId.WEEKLY_GOAL_STREAK_12.access)
        assertEquals(AchievementAccess.PRO, AchievementId.WEEKLY_GOAL_STREAK_26.access)
        assertEquals(AchievementAccess.PRO, AchievementId.WEEKLY_GOAL_STREAK_52.access)
        assertEquals(
            listOf(4, 8, 12, 26, 52),
            AchievementCatalog.weeklyStreaks.map { it.streakWeeks }
        )
    }

    @Test
    fun aLaterMissKeepsTheHistoricalTwentySixDiscoverable() {
        val counts = (0 until 26).associate { start.plusWeeks(it.toLong()) to 1 }
        val afterMiss = evaluate(listOf(goal(start, 1)), counts, start.plusWeeks(28))
        assertEquals(0, WeeklyGoalStreakEvaluator.currentStreak(afterMiss))
        assertEquals(26, WeeklyGoalStreakEvaluator.bestStreak(afterMiss))
        assertTrue(qualifiedIds(afterMiss).contains(AchievementId.WEEKLY_GOAL_STREAK_26))
    }

    @Test
    fun aFreeUserCompletesTwentySixWithoutAnEarnedRowUntilUpgrade() {
        val qualifications = WeeklyGoalStreakEvaluator.qualifications(achievedWeeks(26))
        val locked = AchievementReconciler.plan(
            request = request(qualifications),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertTrue(locked.insertUnlocks.none { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_26 })
        val crossing = qualifications.single { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_26 }
        val upgraded = AchievementReconciler.plan(
            request = request(qualifications).copy(grantsPro = true),
            unlocks = emptyList(),
            events = emptyList()
        )
        val row = upgraded.insertUnlocks.single { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_26 }
        assertEquals(crossing.unlockedAt, row.unlockedAt)
        assertEquals(1_000L, row.celebratedAt)
        val kept = AchievementReconciler.plan(
            request = request(emptyList()).copy(grantsPro = false),
            unlocks = listOf(StoredUnlock(AchievementId.WEEKLY_GOAL_STREAK_26, celebratedAt = 1_000L)),
            events = emptyList()
        )
        assertTrue(kept.revoke.none { it == AchievementId.WEEKLY_GOAL_STREAK_26 })
    }

    @Test
    fun almostThereShowsOnlyTheNextStreakTierAcrossFreeAndPro() {
        val atTen = BadgeWallPresenter.present(
            board(10, listOf(AchievementId.WEEKLY_GOAL_STREAK_4, AchievementId.WEEKLY_GOAL_STREAK_8))
        )
        assertEquals(
            listOf(AchievementId.WEEKLY_GOAL_STREAK_12),
            atTen.almostThere.filter { it.achievementId.streakWeeks != null }.map { it.achievementId }
        )
        val earned = listOf(
            AchievementId.WEEKLY_GOAL_STREAK_4,
            AchievementId.WEEKLY_GOAL_STREAK_8,
            AchievementId.WEEKLY_GOAL_STREAK_12
        )
        val atEighteen = BadgeWallPresenter.present(board(18, earned))
        assertEquals(
            listOf(AchievementId.WEEKLY_GOAL_STREAK_26),
            atEighteen.almostThere.filter { it.achievementId.streakWeeks != null }.map { it.achievementId }
        )
        val atThirtyFour = BadgeWallPresenter.present(
            board(
                34,
                earned + AchievementId.WEEKLY_GOAL_STREAK_26
            )
        )
        assertEquals(
            listOf(AchievementId.WEEKLY_GOAL_STREAK_52),
            atThirtyFour.almostThere.filter { it.achievementId.streakWeeks != null }.map { it.achievementId }
        )
    }

    private fun qualified(weeks: Int): Set<AchievementId> = qualifiedIds(achievedWeeks(weeks))

    private fun qualifiedIds(status: WeeklyGoalStatus): Set<AchievementId> {
        return WeeklyGoalStreakEvaluator.qualifications(status).map { it.achievementId }.toSet()
    }

    private fun achievedWeeks(count: Int): WeeklyGoalStatus {
        if (count == 0) {
            return WeeklyGoalLogic.evaluate(listOf(goal(start, 1)), emptyMap(), start)
        }
        val counts = (0 until count).associate { start.plusWeeks(it.toLong()) to 1 }
        return evaluate(listOf(goal(start, 1)), counts, sundayOf(count - 1))
    }

    private fun evaluate(
        history: List<WeeklyGoalRevision>,
        counts: Map<LocalDate, Int>,
        today: LocalDate
    ): WeeklyGoalStatus = WeeklyGoalLogic.evaluate(history, counts, today)

    private fun goal(week: LocalDate, workouts: Int?): WeeklyGoalRevision {
        return WeeklyGoalRevision(week, workouts, graceWeek = false)
    }

    private fun sundayOf(weekIndex: Int): LocalDate = start.plusWeeks(weekIndex.toLong()).plusDays(6)

    private fun progress(board: AchievementBoard, id: AchievementId): CountProgress {
        return board.items.single { it.id == id }.countProgress!!
    }

    private fun board(currentStreak: Int, earned: List<AchievementId>): AchievementBoard {
        return AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = earned.map { UnlockSnapshot(it, unlockedAt = 10L, celebratedAt = 10L, triggerClientWorkoutId = null) },
            events = emptyList(),
            currentStreak = currentStreak
        )
    }

    private fun request(
        qualifications: List<StreakQualification>,
        weeks: List<AchievedWeek> = emptyList(),
        count: Int = 0,
        trigger: String? = null
    ): ReconcileRequest {
        return ReconcileRequest(
            initialized = true,
            completedWorkoutCount = count,
            achievedWeeks = weeks,
            nowMillis = 1_000L,
            triggerClientWorkoutId = trigger,
            streakQualifications = qualifications
        )
    }
}
