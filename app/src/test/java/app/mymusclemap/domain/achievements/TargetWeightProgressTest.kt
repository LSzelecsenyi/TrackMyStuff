package app.mymusclemap.domain.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetWeightProgressTest {
    @Test
    fun directionIsFixedFromTheBaselineAndTreatsAnEqualStartAsAlreadyThere() {
        assertEquals(TargetWeightDirection.LOSS, TargetWeightProgressEvaluator.direction(87.0, 78.0))
        assertEquals(TargetWeightDirection.GAIN, TargetWeightProgressEvaluator.direction(70.0, 78.0))
        assertEquals(TargetWeightDirection.ALREADY_THERE, TargetWeightProgressEvaluator.direction(78.0, 78.04))
    }

    @Test
    fun lossProgressUsesTheOriginalDistanceAndClampsTheBar() {
        val goal = goal(id = 1, baseline = 87.0, target = 78.0)
        val midway = TargetWeightProgressEvaluator.progress(goal, 82.5)!!
        assertEquals(9.0, midway.totalDistanceKg, 0.0)
        assertEquals(4.5, midway.progressedKg, 0.0)
        assertEquals(4.5, midway.remainingKg, 0.0)
        assertEquals(0.5, midway.progressFraction, 0.0001)
        assertFalse(midway.reached)

        val away = TargetWeightProgressEvaluator.progress(goal, 90.0)!!
        assertEquals(TargetWeightDirection.LOSS, away.direction)
        assertTrue(away.progressedKg < 0.0)
        assertEquals(0.0, away.progressFraction, 0.0)
        assertEquals(12.0, away.remainingTowardTargetKg, 0.0)
        assertFalse(away.reached)

        val overshot = TargetWeightProgressEvaluator.progress(goal, 76.0)!!
        assertTrue(overshot.reached)
        assertEquals(1.0, overshot.progressFraction, 0.0)
        assertEquals(76.0, overshot.currentKg, 0.0)
        assertTrue(overshot.remainingKg < 0.0)
        assertEquals(0.0, overshot.remainingTowardTargetKg, 0.0)
    }

    @Test
    fun gainProgressRunsTowardAHigherTarget() {
        val goal = goal(id = 2, baseline = 70.0, target = 78.0)
        assertEquals(TargetWeightDirection.GAIN, goal.direction)
        val current = TargetWeightProgressEvaluator.progress(goal, 74.0)!!
        assertEquals(8.0, current.totalDistanceKg, 0.0)
        assertEquals(4.0, current.progressedKg, 0.0)
        assertEquals(4.0, current.remainingKg, 0.0)
        assertEquals(0.5, current.progressFraction, 0.0001)
        val reached = TargetWeightProgressEvaluator.progress(goal, 79.0)!!
        assertTrue(reached.reached)
        assertEquals(1.0, reached.progressFraction, 0.0)
    }

    @Test
    fun equalBaselineIsReachedOnlyWhileTheCurrentWeightStaysThere() {
        val goal = TargetWeightGoalFacts(
            id = 3,
            targetKg = 78.0,
            baselineKg = 78.0,
            direction = TargetWeightDirection.ALREADY_THERE,
            active = true
        )
        val there = TargetWeightProgressEvaluator.progress(goal, 78.0)!!
        assertTrue(there.reached)
        assertEquals(0.0, there.totalDistanceKg, 0.0)
        assertEquals(listOf(WeightMilestone.REACHED), TargetWeightProgressEvaluator.crossed(there))
        val moved = TargetWeightProgressEvaluator.progress(goal, 79.0)!!
        assertFalse(moved.reached)
        assertTrue(TargetWeightProgressEvaluator.crossed(moved).isEmpty())
    }

    @Test
    fun thresholdsThatTheJourneyAlreadyStartedInsideAreNotAwarded() {
        val short = TargetWeightProgressEvaluator.progress(goal(4, baseline = 80.0, target = 78.0), 80.0)!!
        assertTrue(TargetWeightProgressEvaluator.crossed(short).isEmpty())
        val oneLeft = TargetWeightProgressEvaluator.progress(goal(4, baseline = 80.0, target = 78.0), 79.0)!!
        assertEquals(
            listOf(WeightMilestone.REMAINING_1, WeightMilestone.HALFWAY),
            TargetWeightProgressEvaluator.crossed(oneLeft)
        )
    }

    @Test
    fun oneMeasurementCanCrossEveryThreshold() {
        val jumped = TargetWeightProgressEvaluator.progress(goal(5, baseline = 87.0, target = 78.0), 78.0)!!
        assertEquals(
            listOf(
                WeightMilestone.REACHED,
                WeightMilestone.REMAINING_1,
                WeightMilestone.REMAINING_2,
                WeightMilestone.REMAINING_5,
                WeightMilestone.HALFWAY
            ),
            TargetWeightProgressEvaluator.crossed(jumped)
        )
    }

    @Test
    fun aJumpToTheTargetLeavesOnlyReachedPendingAndRecordsTheRest() {
        val goal = goal(7, baseline = 87.0, target = 78.0)
        val plan = TargetWeightMilestonePlanner.plan(
            goal = goal,
            currentKg = 78.0,
            historicalKg = listOf(78.0),
            events = emptyList(),
            targetWeightUnlocked = false,
            nowMillis = 500L
        )
        val pending = plan.insertEvents.single { it.celebratedAt == null }
        assertEquals("weight-goal:7:reached", pending.dedupeKey)
        assertEquals(
            setOf(
                "weight-goal:7:halfway",
                "weight-goal:7:remaining-5",
                "weight-goal:7:remaining-2",
                "weight-goal:7:remaining-1"
            ),
            plan.insertEvents.filter { it.celebratedAt == 500L }.map { it.dedupeKey }.toSet()
        )
        assertEquals(AchievementId.TARGET_WEIGHT_REACHED, plan.insertUnlock?.achievementId)
        assertNull(plan.insertUnlock?.celebratedAt)
    }

    @Test
    fun fluctuationDoesNotCreateASecondTwoKilogramEvent() {
        val goal = goal(8, baseline = 87.0, target = 78.0)
        val first = TargetWeightMilestonePlanner.plan(
            goal = goal,
            currentKg = 79.9,
            historicalKg = listOf(80.1, 79.9),
            events = emptyList(),
            targetWeightUnlocked = false,
            nowMillis = 10L
        )
        val stored = first.insertEvents.map {
            StoredProgressEvent(it.dedupeKey, it.kind, it.celebratedAt)
        }
        val bounced = TargetWeightMilestonePlanner.plan(
            goal = goal,
            currentKg = 80.2,
            historicalKg = listOf(80.1, 79.9, 80.2),
            events = stored,
            targetWeightUnlocked = false,
            nowMillis = 20L
        )
        assertTrue(bounced.insertEvents.none { it.dedupeKey.endsWith("remaining-2") })
        val again = TargetWeightMilestonePlanner.plan(
            goal = goal,
            currentKg = 79.8,
            historicalKg = listOf(80.1, 79.9, 80.2, 79.8),
            events = stored + bounced.insertEvents.map {
                StoredProgressEvent(it.dedupeKey, it.kind, it.celebratedAt)
            },
            targetWeightUnlocked = false,
            nowMillis = 30L
        )
        assertTrue(again.insertEvents.none { it.dedupeKey.endsWith("remaining-2") })
        val keys = stored.map { it.dedupeKey } +
            bounced.insertEvents.map { it.dedupeKey } +
            again.insertEvents.map { it.dedupeKey }
        assertEquals(1, keys.count { it.endsWith("remaining-2") })
    }

    @Test
    fun anUnseenMilestoneDisappearsWhenNoStoredWeightStillCrossesIt() {
        val goal = goal(9, baseline = 87.0, target = 78.0)
        val pending = StoredProgressEvent(
            dedupeKey = "weight-goal:9:remaining-2",
            kind = ProgressEventKind.WEIGHT_GOAL_MILESTONE,
            celebratedAt = null
        )
        val plan = TargetWeightMilestonePlanner.plan(
            goal = goal,
            currentKg = 82.0,
            historicalKg = listOf(82.0),
            events = listOf(pending),
            targetWeightUnlocked = false,
            nowMillis = 40L
        )
        assertTrue("weight-goal:9:remaining-2" in plan.deleteEventKeys)
    }

    @Test
    fun aCelebratedMilestoneStaysAfterTheWeightMovesAway() {
        val goal = goal(10, baseline = 87.0, target = 78.0)
        val celebrated = StoredProgressEvent(
            dedupeKey = "weight-goal:10:remaining-2",
            kind = ProgressEventKind.WEIGHT_GOAL_MILESTONE,
            celebratedAt = 5L
        )
        val plan = TargetWeightMilestonePlanner.plan(
            goal = goal,
            currentKg = 84.0,
            historicalKg = listOf(84.0),
            events = listOf(celebrated),
            targetWeightUnlocked = false,
            nowMillis = 50L
        )
        assertFalse("weight-goal:10:remaining-2" in plan.deleteEventKeys)
        assertTrue(plan.insertEvents.isEmpty())
    }

    @Test
    fun aSecondGoalCanCelebrateReachedWithoutInsertingAnotherLifetimeUnlock() {
        val goal = goal(11, baseline = 80.0, target = 78.0)
        val plan = TargetWeightMilestonePlanner.plan(
            goal = goal,
            currentKg = 78.0,
            historicalKg = listOf(78.0),
            events = emptyList(),
            targetWeightUnlocked = true,
            nowMillis = 60L
        )
        assertNull(plan.insertUnlock)
        assertEquals("weight-goal:11:reached", plan.insertEvents.single { it.celebratedAt == null }.dedupeKey)
    }

    @Test
    fun removingTheGoalDropsUnseenEventsAndLeavesCelebratedOnes() {
        val plan = TargetWeightMilestonePlanner.plan(
            goal = null,
            currentKg = null,
            events = listOf(
                StoredProgressEvent("weight-goal:1:halfway", ProgressEventKind.WEIGHT_GOAL_MILESTONE, null),
                StoredProgressEvent("weight-goal:1:reached", ProgressEventKind.WEIGHT_GOAL_MILESTONE, 9L)
            ),
            targetWeightUnlocked = true,
            nowMillis = 70L
        )
        assertEquals(setOf("weight-goal:1:halfway"), plan.deleteEventKeys)
        assertNull(plan.insertUnlock)
    }

    private fun goal(id: Long, baseline: Double, target: Double): TargetWeightGoalFacts {
        return TargetWeightGoalFacts(
            id = id,
            targetKg = target,
            baselineKg = baseline,
            direction = TargetWeightProgressEvaluator.direction(baseline, target),
            active = true
        )
    }
}
