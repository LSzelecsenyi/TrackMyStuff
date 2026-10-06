package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.calendar.WeekCalendar
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorkoutCountEvaluatorTest {
    @Test
    fun zeroWorkoutsUnlockNothing() {
        assertTrue(WorkoutCountEvaluator.qualified(0).isEmpty())
        assertEquals(AchievementId.WORKOUTS_5, WorkoutCountEvaluator.next(0).next)
    }

    @Test
    fun fourWorkoutsDoNotUnlockFive() {
        assertTrue(WorkoutCountEvaluator.qualified(4).isEmpty())
        assertEquals(4, WorkoutCountEvaluator.next(4).completed)
        assertEquals(AchievementId.WORKOUTS_5, WorkoutCountEvaluator.next(4).next)
    }

    @Test
    fun fiveUnlocksOnlyTheFirstThreshold() {
        assertEquals(setOf(AchievementId.WORKOUTS_5), WorkoutCountEvaluator.qualified(5))
    }

    @Test
    fun tenUnlocksFiveAndTen() {
        assertEquals(
            setOf(AchievementId.WORKOUTS_5, AchievementId.WORKOUTS_10),
            WorkoutCountEvaluator.qualified(10)
        )
    }

    @Test
    fun oneHundredThirtySevenUnlocksThroughOneHundredAndShowsTwoHundredProgress() {
        assertEquals(
            setOf(
                AchievementId.WORKOUTS_5,
                AchievementId.WORKOUTS_10,
                AchievementId.WORKOUTS_30,
                AchievementId.WORKOUTS_50,
                AchievementId.WORKOUTS_100
            ),
            WorkoutCountEvaluator.qualified(137)
        )
        val next = WorkoutCountEvaluator.next(137)
        assertEquals(AchievementId.WORKOUTS_200, next.next)
        val board = AchievementBoardAssembler.assemble(137, emptyList(), emptyList())
        val twoHundred = board.items.single { it.id == AchievementId.WORKOUTS_200 }
        assertFalse(twoHundred.unlocked)
        assertEquals(137, twoHundred.workoutProgress!!.current)
        assertEquals(200, twoHundred.workoutProgress!!.threshold)
    }

    @Test
    fun twoHundredCompletesTheCurrentMilestones() {
        val next = WorkoutCountEvaluator.next(200)
        assertNull(next.next)
        assertTrue(next.allCurrentMilestonesComplete)
    }

    @Test
    fun badgeKeyIsIndependentOfAnyDrawable() {
        AchievementId.entries.forEach { id ->
            assertEquals(id.name, id.badgeKey)
            assertFalse(id.badgeKey.all { it.isDigit() })
        }
        assertTrue(AchievementId.WORKOUTS_5.revokesWhenWorkoutCountDrops)
        assertFalse(AchievementId.TARGET_WEIGHT_REACHED.revokesWhenWorkoutCountDrops)
        assertEquals(AchievementCategory.GOALS, AchievementId.TARGET_WEIGHT_REACHED.category)
        assertNull(AchievementId.TARGET_WEIGHT_REACHED.workoutThreshold)
    }
}

class WeeklyGoalCompletionEvaluatorTest {
    private val monday = LocalDate.of(2026, 10, 5).also {
        check(it.dayOfWeek == DayOfWeek.MONDAY)
    }
    private val sunday = monday.plusDays(6)

    @Test
    fun achievedWeekUsesTheMondayKey() {
        val status = WeeklyGoalLogic.evaluate(
            history = listOf(revision(monday, 1, grace = false)),
            completedByDate = mapOf(monday to 1),
            today = sunday
        )
        val weeks = WeeklyGoalCompletionEvaluator.achievedWeeks(status)
        assertEquals(listOf(monday), weeks.map { it.weekStart })
        assertEquals("weekly-workout-achieved:2026-10-05", WeeklyGoalCompletionEvaluator.dedupeKey(monday))
    }

    @Test
    fun graceMissDoesNotCompleteAndGraceSuccessDoes() {
        val grace = revision(monday, 2, grace = true)
        val afterWeek = sunday.plusDays(1)
        val missed = WeeklyGoalLogic.evaluate(
            history = listOf(grace),
            completedByDate = mapOf(monday to 1),
            today = afterWeek
        )
        assertTrue(WeeklyGoalCompletionEvaluator.achievedWeeks(missed).isEmpty())
        val hit = WeeklyGoalLogic.evaluate(
            history = listOf(grace),
            completedByDate = mapOf(monday to 2),
            today = afterWeek
        )
        assertEquals(listOf(monday), WeeklyGoalCompletionEvaluator.achievedWeeks(hit).map { it.weekStart })
    }

    @Test
    fun goalOffProducesNoCompletion() {
        val status = WeeklyGoalLogic.evaluate(
            history = listOf(revision(monday, null, grace = false)),
            completedByDate = mapOf(monday to 4),
            today = sunday
        )
        assertTrue(WeeklyGoalCompletionEvaluator.achievedWeeks(status).isEmpty())
    }

    @Test
    fun fixedDateProviderWeeksStayMondayAlignedAcrossSundayAndTheNextMonday() {
        val provider = FixedDateProvider(sunday)
        val nextProvider: DateProvider = FixedDateProvider(sunday.plusDays(1))
        val history = listOf(revision(monday, 1, grace = false))
        val counts = mapOf(monday to 1)
        val onSunday = WeeklyGoalCompletionEvaluator.achievedWeeks(
            WeeklyGoalLogic.evaluate(history, counts, provider.today())
        )
        val onNextMonday = WeeklyGoalCompletionEvaluator.achievedWeeks(
            WeeklyGoalLogic.evaluate(history, counts, nextProvider.today())
        )
        assertEquals(onSunday.map { it.weekStart }, onNextMonday.map { it.weekStart })
        assertEquals(WeekCalendar.start(sunday), onSunday.single().weekStart)
        assertEquals(DayOfWeek.MONDAY, nextProvider.today().dayOfWeek)
    }

    @Test
    fun workoutDateSelectsTheWeekEvenWhenTheFinishInstantIsTheNextMonday() {
        val sunday = monday.plusDays(6)
        val nextMonday = sunday.plusDays(1)
        val status = WeeklyGoalLogic.evaluate(
            history = listOf(revision(monday, 1, grace = false)),
            completedByDate = mapOf(sunday to 1),
            today = nextMonday
        )
        assertEquals(listOf(monday), WeeklyGoalCompletionEvaluator.achievedWeeks(status).map { it.weekStart })
        assertFalse(
            WeeklyGoalCompletionEvaluator.achievedWeeks(status).any { it.weekStart == nextMonday }
        )
    }

    private fun revision(weekStart: LocalDate, workouts: Int?, grace: Boolean): WeeklyGoalRevision {
        return WeeklyGoalRevision(
            effectiveWeekStart = weekStart,
            workoutsPerWeek = workouts,
            graceWeek = grace
        )
    }
}

class AchievementReconcilerTest {
    private val now = 1_000L

    @Test
    fun historicalBackfillStoresAwardsSilentlyAndQueuesOneSummary() {
        val plan = AchievementReconciler.plan(
            request = request(initialized = false, count = 137),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertEquals(
            listOf(
                AchievementId.WORKOUTS_5,
                AchievementId.WORKOUTS_10,
                AchievementId.WORKOUTS_30,
                AchievementId.WORKOUTS_50,
                AchievementId.WORKOUTS_100
            ),
            plan.insertUnlocks.map { it.achievementId }
        )
        assertTrue(plan.insertUnlocks.all { it.celebratedAt == now })
        val summary = plan.insertEvents.single { it.kind == ProgressEventKind.HISTORY_RECOGNIZED }
        assertNull(summary.celebratedAt)
        assertEquals("5", summary.payload)
        assertEquals(WeeklyGoalCompletionEvaluator.HISTORY_RECOGNIZED_KEY, summary.dedupeKey)
        assertTrue(plan.markInitialized)
    }

    @Test
    fun repeatedPlanWithTheSameHistoryChangesNothing() {
        val first = AchievementReconciler.plan(
            request = request(initialized = false, count = 10),
            unlocks = emptyList(),
            events = emptyList()
        )
        val stored = first.insertUnlocks.map { StoredUnlock(it.achievementId, it.celebratedAt) }
        val events = first.insertEvents.map {
            StoredProgressEvent(it.dedupeKey, it.kind, it.celebratedAt)
        }
        val second = AchievementReconciler.plan(
            request = request(initialized = true, count = 10),
            unlocks = stored,
            events = events
        )
        assertTrue(second.changesNothing)
    }

    @Test
    fun crossingFiftyAfterInitializationQueuesOnePendingAward() {
        val stored = WorkoutCountEvaluator.qualified(49).map { StoredUnlock(it, celebratedAt = 1L) }
        val plan = AchievementReconciler.plan(
            request = request(initialized = true, count = 50, trigger = "workout-50"),
            unlocks = stored,
            events = emptyList()
        )
        val inserted = plan.insertUnlocks.single()
        assertEquals(AchievementId.WORKOUTS_50, inserted.achievementId)
        assertNull(inserted.celebratedAt)
        assertEquals("workout-50", inserted.triggerClientWorkoutId)
    }

    @Test
    fun droppingBelowAThresholdRevokesOnlyThatCountAward() {
        val stored = WorkoutCountEvaluator.qualified(100).map { StoredUnlock(it, celebratedAt = 1L) }
        val plan = AchievementReconciler.plan(
            request = request(initialized = true, count = 99),
            unlocks = stored,
            events = emptyList()
        )
        assertEquals(setOf(AchievementId.WORKOUTS_100), plan.revoke)
        assertTrue(plan.insertUnlocks.isEmpty())
    }

    @Test
    fun reachingTheThresholdAgainCanUnlockOnceMore() {
        val stored = WorkoutCountEvaluator.qualified(99).map { StoredUnlock(it, celebratedAt = 1L) }
        val plan = AchievementReconciler.plan(
            request = request(initialized = true, count = 100, trigger = "again"),
            unlocks = stored,
            events = emptyList()
        )
        assertEquals(AchievementId.WORKOUTS_100, plan.insertUnlocks.single().achievementId)
        assertNull(plan.insertUnlocks.single().celebratedAt)
    }

    @Test
    fun oneNewWeekAfterInitializationIsPendingAndASecondPassIsNot() {
        val week = AchievedWeek(LocalDate.of(2026, 10, 5), completed = 4, goal = 4)
        val first = AchievementReconciler.plan(
            request = request(initialized = true, count = 4, weeks = listOf(week), trigger = "live"),
            unlocks = emptyList(),
            events = emptyList()
        )
        val event = first.insertEvents.single()
        assertEquals("weekly-workout-achieved:2026-10-05", event.dedupeKey)
        assertNull(event.celebratedAt)
        assertEquals("live", event.triggerClientWorkoutId)
        val second = AchievementReconciler.plan(
            request = request(initialized = true, count = 5, weeks = listOf(week)),
            unlocks = emptyList(),
            events = listOf(StoredProgressEvent(event.dedupeKey, event.kind, event.celebratedAt))
        )
        assertTrue(second.insertEvents.isEmpty())
    }

    @Test
    fun aNewMondayCanCreateANewEvent() {
        val first = AchievedWeek(LocalDate.of(2026, 10, 5), 1, 1)
        val second = AchievedWeek(LocalDate.of(2026, 10, 12), 1, 1)
        val plan = AchievementReconciler.plan(
            request = request(initialized = true, count = 2, weeks = listOf(second), trigger = "next"),
            unlocks = emptyList(),
            events = listOf(
                StoredProgressEvent(
                    WeeklyGoalCompletionEvaluator.dedupeKey(first.weekStart),
                    ProgressEventKind.WEEKLY_GOAL_COMPLETED,
                    celebratedAt = 1L
                )
            )
        )
        assertEquals(
            WeeklyGoalCompletionEvaluator.dedupeKey(second.weekStart),
            plan.insertEvents.single().dedupeKey
        )
    }

    @Test
    fun severalHistoricalWeeksDiscoveredTogetherAreAcknowledgedWithoutIndividualCelebrations() {
        val weeks = listOf(
            AchievedWeek(LocalDate.of(2026, 9, 7), 2, 2),
            AchievedWeek(LocalDate.of(2026, 9, 14), 2, 2),
            AchievedWeek(LocalDate.of(2026, 9, 21), 2, 2)
        )
        val plan = AchievementReconciler.plan(
            request = request(initialized = true, count = 6, weeks = weeks),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertEquals(3, plan.insertEvents.size)
        assertTrue(plan.insertEvents.all { it.celebratedAt == now })
    }

    @Test
    fun uninitializedHistoricalWeeksAreAlsoSilent() {
        val week = AchievedWeek(LocalDate.of(2026, 10, 5), 4, 4)
        val plan = AchievementReconciler.plan(
            request = request(initialized = false, count = 4, weeks = listOf(week)),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertTrue(plan.insertEvents.filter { it.kind == ProgressEventKind.WEEKLY_GOAL_COMPLETED }
            .all { it.celebratedAt == now })
    }

    private fun request(
        initialized: Boolean,
        count: Int,
        weeks: List<AchievedWeek> = emptyList(),
        trigger: String? = null
    ): ReconcileRequest {
        return ReconcileRequest(
            initialized = initialized,
            completedWorkoutCount = count,
            achievedWeeks = weeks,
            nowMillis = now,
            triggerClientWorkoutId = trigger
        )
    }
}
