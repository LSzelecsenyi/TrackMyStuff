package app.mymusclemap.domain.workout

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyGoalLogicTest {
    @Test
    fun noGoalHasEverBeenConfigured() {
        val today = LocalDate.of(2026, 3, 11)
        val status = WeeklyGoalLogic.evaluate(emptyList(), emptyMap(), today)
        assertNull(status.current.goal)
        assertEquals(0, status.current.streak)
        assertEquals(WeekVerdict.NoGoal, status.current.verdict)
        assertNull(status.pending)
        assertNull(WeeklyGoalLogic.propose(emptyList(), today, null))
    }

    @Test
    fun firstGoalOnMondayIsAFullWeek() {
        val monday = LocalDate.of(2026, 3, 9)
        val proposal = WeeklyGoalLogic.propose(emptyList(), monday, 4)
        assertEquals(monday, proposal!!.effectiveWeekStart)
        assertEquals(4, proposal.workoutsPerWeek)
        assertFalse(proposal.graceWeek)
    }

    @Test
    fun firstGoalMidWeekIsAGraceWeek() {
        val thursday = LocalDate.of(2026, 3, 12)
        val proposal = WeeklyGoalLogic.propose(emptyList(), thursday, 4)
        assertEquals(LocalDate.of(2026, 3, 9), proposal!!.effectiveWeekStart)
        assertTrue(proposal.graceWeek)
    }

    @Test
    fun graceWeekSuccessStartsTheStreakImmediately() {
        val monday = LocalDate.of(2026, 3, 9)
        val thursday = LocalDate.of(2026, 3, 12)
        val history = listOf(WeeklyGoalLogic.propose(emptyList(), thursday, 4)!!)
        val counts = mapOf(
            LocalDate.of(2026, 3, 10) to 1,
            LocalDate.of(2026, 3, 11) to 1,
            LocalDate.of(2026, 3, 12) to 2
        )
        val during = WeeklyGoalLogic.evaluate(history, counts, thursday)
        assertEquals(4, during.current.completed)
        assertTrue(during.current.achieved)
        assertEquals(1, during.current.streak)
        assertEquals(WeekVerdict.Achieved, during.current.verdict)

        val after = WeeklyGoalLogic.evaluate(history, counts, monday.plusWeeks(1))
        assertEquals(1, after.weeks.getValue(monday).streak)
        assertEquals(WeekVerdict.Achieved, after.weeks.getValue(monday).verdict)
    }

    @Test
    fun graceWeekFailureDoesNotCreateAFailedWeek() {
        val monday = LocalDate.of(2026, 3, 9)
        val history = listOf(
            WeeklyGoalRevision(monday, 4, graceWeek = true)
        )
        val counts = mapOf(LocalDate.of(2026, 3, 10) to 2)
        val sunday = LocalDate.of(2026, 3, 15)
        val during = WeeklyGoalLogic.evaluate(history, counts, sunday)
        assertEquals(WeekVerdict.InProgress, during.current.verdict)
        assertEquals(0, during.current.streak)

        val nextMonday = LocalDate.of(2026, 3, 16)
        val afterMiss = WeeklyGoalLogic.evaluate(history, counts, nextMonday)
        assertEquals(WeekVerdict.GraceMiss, afterMiss.weeks.getValue(monday).verdict)
        assertEquals(0, afterMiss.weeks.getValue(monday).streak)
        assertEquals(4, afterMiss.current.goal)
        assertEquals(WeekVerdict.InProgress, afterMiss.current.verdict)

        val hitNext = counts + (LocalDate.of(2026, 3, 17) to 4)
        val afterHit = WeeklyGoalLogic.evaluate(history, hitNext, LocalDate.of(2026, 3, 18))
        assertEquals(1, afterHit.current.streak)
        assertEquals(WeekVerdict.Achieved, afterHit.current.verdict)
    }

    @Test
    fun graceMissDoesNotBreakAnExistingStreak() {
        val week1 = LocalDate.of(2026, 3, 2)
        val week2 = LocalDate.of(2026, 3, 9)
        val history = listOf(
            WeeklyGoalRevision(week1, 2, graceWeek = false),
            WeeklyGoalRevision(week2, 2, graceWeek = true)
        )
        val counts = mapOf(
            LocalDate.of(2026, 3, 2) to 1,
            LocalDate.of(2026, 3, 3) to 1
        )
        val after = WeeklyGoalLogic.evaluate(history, counts, LocalDate.of(2026, 3, 16))
        assertEquals(WeekVerdict.Achieved, after.weeks.getValue(week1).verdict)
        assertEquals(1, after.weeks.getValue(week1).streak)
        assertEquals(WeekVerdict.GraceMiss, after.weeks.getValue(week2).verdict)
        assertEquals(1, after.weeks.getValue(week2).streak)
    }

    @Test
    fun exactGoalAndExtraWorkoutsBothSucceed() {
        val monday = LocalDate.of(2026, 3, 9)
        val history = listOf(WeeklyGoalRevision(monday, 4, graceWeek = false))
        val exact = WeeklyGoalLogic.evaluate(history, mapOf(monday to 4), LocalDate.of(2026, 3, 11))
        assertTrue(exact.current.achieved)
        assertEquals(1, exact.current.streak)
        val extra = WeeklyGoalLogic.evaluate(history, mapOf(monday to 5), LocalDate.of(2026, 3, 11))
        assertTrue(extra.current.achieved)
        assertEquals(1, extra.current.streak)
    }

    @Test
    fun multipleCompletedWorkoutsOnOneDayEachCount() {
        val monday = LocalDate.of(2026, 3, 9)
        val history = listOf(WeeklyGoalRevision(monday, 3, graceWeek = false))
        val status = WeeklyGoalLogic.evaluate(
            history,
            mapOf(LocalDate.of(2026, 3, 11) to 3),
            LocalDate.of(2026, 3, 11)
        )
        assertEquals(3, status.current.completed)
        assertTrue(status.current.achieved)
    }

    @Test
    fun fullWeekMissBreaksTheStreakAndALaterHitStartsAgain() {
        val week1 = LocalDate.of(2026, 3, 2)
        val week2 = LocalDate.of(2026, 3, 9)
        val week3 = LocalDate.of(2026, 3, 16)
        val history = listOf(WeeklyGoalRevision(week1, 2, graceWeek = false))
        val counts = mapOf(
            week1 to 2,
            week2 to 1,
            week3 to 2
        )
        val status = WeeklyGoalLogic.evaluate(history, counts, LocalDate.of(2026, 3, 20))
        assertEquals(1, status.weeks.getValue(week1).streak)
        assertEquals(WeekVerdict.Missed, status.weeks.getValue(week2).verdict)
        assertEquals(0, status.weeks.getValue(week2).streak)
        assertEquals(1, status.weeks.getValue(week3).streak)
        assertEquals(WeekVerdict.Achieved, status.weeks.getValue(week3).verdict)
    }

    @Test
    fun currentIncompleteWeekKeepsTheStreakUntilItEnds() {
        val week1 = LocalDate.of(2026, 3, 2)
        val week2 = LocalDate.of(2026, 3, 9)
        val history = listOf(WeeklyGoalRevision(week1, 4, graceWeek = false))
        val counts = mapOf(week1 to 4, LocalDate.of(2026, 3, 11) to 1)
        val wednesday = WeeklyGoalLogic.evaluate(history, counts, LocalDate.of(2026, 3, 11))
        assertEquals(1, wednesday.weeks.getValue(week1).streak)
        assertEquals(1, wednesday.current.streak)
        assertEquals(WeekVerdict.InProgress, wednesday.current.verdict)
        assertFalse(wednesday.current.achieved)

        val reached = WeeklyGoalLogic.evaluate(
            history,
            counts + (LocalDate.of(2026, 3, 12) to 3),
            LocalDate.of(2026, 3, 12)
        )
        assertEquals(2, reached.current.streak)
        assertEquals(WeekVerdict.Achieved, reached.current.verdict)

        val afterSundayMiss = WeeklyGoalLogic.evaluate(history, counts, week2.plusWeeks(1))
        assertEquals(WeekVerdict.Missed, afterSundayMiss.weeks.getValue(week2).verdict)
        assertEquals(0, afterSundayMiss.current.streak)
    }

    @Test
    fun streakOfSeveralWeeksCreditsTheCurrentWeekAsSoonAsTheGoalIsMet() {
        val start = LocalDate.of(2026, 1, 5)
        val history = listOf(WeeklyGoalRevision(start, 1, graceWeek = false))
        val counts = (0L until 6L).associate { offset ->
            start.plusWeeks(offset) to 1
        }
        val before = WeeklyGoalLogic.evaluate(history, counts, LocalDate.of(2026, 2, 16))
        assertEquals(6, before.current.streak)
        val withCurrent = counts + (LocalDate.of(2026, 2, 18) to 1)
        val during = WeeklyGoalLogic.evaluate(history, withCurrent, LocalDate.of(2026, 2, 18))
        assertEquals(7, during.current.streak)
    }

    @Test
    fun midWeekChangeKeepsTheCurrentGoalAndCanBeReplacedBeforeMonday() {
        val thisWeek = LocalDate.of(2026, 3, 9)
        val nextWeek = LocalDate.of(2026, 3, 16)
        val thursday = LocalDate.of(2026, 3, 12)
        val original = listOf(WeeklyGoalRevision(thisWeek, 3, graceWeek = false))
        val toFive = WeeklyGoalLogic.propose(original, thursday, 5)!!
        assertEquals(nextWeek, toFive.effectiveWeekStart)
        assertEquals(5, toFive.workoutsPerWeek)
        assertFalse(toFive.graceWeek)
        val withPending = WeeklyGoalLogic.apply(original, toFive)
        assertEquals(3, WeeklyGoalLogic.applicableGoal(withPending, thisWeek))
        assertEquals(5, WeeklyGoalLogic.applicableGoal(withPending, nextWeek))

        val toSix = WeeklyGoalLogic.propose(withPending, thursday, 6)!!
        val replaced = WeeklyGoalLogic.apply(withPending, toSix)
        assertEquals(1, replaced.count { it.effectiveWeekStart == nextWeek })
        assertEquals(6, WeeklyGoalLogic.applicableGoal(replaced, nextWeek))
        assertEquals(3, WeeklyGoalLogic.applicableGoal(replaced, thisWeek))

        val backToThree = WeeklyGoalLogic.apply(replaced, WeeklyGoalLogic.propose(replaced, thursday, 3)!!)
        assertEquals(original, backToThree)
        assertEquals(3, WeeklyGoalLogic.applicableGoal(backToThree, nextWeek))
    }

    @Test
    fun disablingTakesEffectNextMondayAndReEnablingDoesNotBridgeTheGap() {
        val week1 = LocalDate.of(2026, 3, 2)
        val week2 = LocalDate.of(2026, 3, 9)
        val week3 = LocalDate.of(2026, 3, 16)
        val week4 = LocalDate.of(2026, 3, 23)
        var history = listOf(WeeklyGoalRevision(week1, 2, graceWeek = false))
        val disable = WeeklyGoalLogic.propose(history, LocalDate.of(2026, 3, 4), null)!!
        assertEquals(week2, disable.effectiveWeekStart)
        assertNull(disable.workoutsPerWeek)
        history = WeeklyGoalLogic.apply(history, disable)
        val counts = mapOf(week1 to 2, LocalDate.of(2026, 3, 3) to 1)
        val stillActive = WeeklyGoalLogic.evaluate(history, counts, LocalDate.of(2026, 3, 4))
        assertEquals(2, stillActive.current.goal)
        assertTrue(stillActive.pending is PendingWeeklyGoal.Disable)

        val duringGap = WeeklyGoalLogic.evaluate(history, counts, LocalDate.of(2026, 3, 18))
        assertNull(duringGap.current.goal)
        assertEquals(0, duringGap.current.streak)
        assertEquals(1, duringGap.weeks.getValue(week1).streak)

        val reenable = WeeklyGoalLogic.propose(history, LocalDate.of(2026, 3, 25), 4)!!
        assertEquals(week4, reenable.effectiveWeekStart)
        assertTrue(reenable.graceWeek)
        history = WeeklyGoalLogic.apply(history, reenable)
        val restarted = WeeklyGoalLogic.evaluate(
            history,
            counts + (LocalDate.of(2026, 3, 25) to 4),
            LocalDate.of(2026, 3, 25)
        )
        assertEquals(WeekVerdict.NoGoal, restarted.weeks.getValue(week2).verdict)
        assertEquals(WeekVerdict.NoGoal, restarted.weeks.getValue(week3).verdict)
        assertEquals(1, restarted.current.streak)
        assertEquals(4, restarted.current.goal)
    }

    @Test
    fun historicalWeeksKeepTheGoalThatAppliedThen() {
        val march = LocalDate.of(2026, 3, 2)
        val april = LocalDate.of(2026, 4, 6)
        val history = listOf(
            WeeklyGoalRevision(march, 3, graceWeek = false),
            WeeklyGoalRevision(april, 5, graceWeek = false)
        )
        assertEquals(3, WeeklyGoalLogic.applicableGoal(history, LocalDate.of(2026, 3, 30)))
        assertEquals(5, WeeklyGoalLogic.applicableGoal(history, april))
        val counts = mapOf(
            LocalDate.of(2026, 3, 30) to 3,
            april to 4
        )
        val status = WeeklyGoalLogic.evaluate(history, counts, LocalDate.of(2026, 4, 8))
        assertTrue(status.weeks.getValue(LocalDate.of(2026, 3, 30)).achieved)
        assertFalse(status.current.achieved)
        assertEquals(5, status.current.goal)
        assertEquals(4, status.current.completed)
    }

    @Test
    fun weeksThatCrossMonthAndYearBoundariesSumCompletedWorkouts() {
        val week = LocalDate.of(2026, 12, 28)
        val history = listOf(WeeklyGoalRevision(week, 2, graceWeek = false))
        val counts = mapOf(
            LocalDate.of(2026, 12, 31) to 1,
            LocalDate.of(2027, 1, 1) to 1
        )
        val status = WeeklyGoalLogic.evaluate(history, counts, LocalDate.of(2026, 12, 31))
        assertEquals(week, status.current.weekStart)
        assertEquals(2, status.current.completed)
        assertTrue(status.current.achieved)
        assertEquals(1, status.current.streak)
    }

    @Test
    fun goalOutsideTheSupportedRangeIsRejected() {
        assertNull(WeeklyGoalLogic.propose(emptyList(), LocalDate.of(2026, 3, 9), 0))
        assertNull(WeeklyGoalLogic.propose(emptyList(), LocalDate.of(2026, 3, 9), 8))
    }
}
