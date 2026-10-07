package app.mymusclemap.domain.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyAchievementTest {
    private val now = 1_000L

    @Test
    fun noSourcesLeaveJourneyLocked() {
        assertTrue(
            JourneyEvaluator.qualifications(
                earliestNativeCompletedAt = null,
                earliestCustomPlanAt = null,
                monthlyReportGeneratedAt = null
            ).isEmpty()
        )
        val plan = AchievementReconciler.plan(request(), emptyList(), emptyList())
        assertTrue(plan.insertUnlocks.none { it.achievementId.journeyMilestone != null })
    }

    @Test
    fun severalHistoricalMilestonesPersistAndOnlyTheLatestStaysPending() {
        val qualifications = JourneyEvaluator.qualifications(
            earliestNativeCompletedAt = 100L,
            earliestCustomPlanAt = 500L,
            monthlyReportGeneratedAt = null
        )
        val plan = AchievementReconciler.plan(
            request(journey = qualifications),
            emptyList(),
            emptyList()
        )
        assertEquals(
            setOf(AchievementId.FIRST_WORKOUT, AchievementId.FIRST_CUSTOM_WORKOUT_PLAN),
            plan.insertUnlocks.map { it.achievementId }.toSet()
        )
        val planner = plan.insertUnlocks.single { it.achievementId == AchievementId.FIRST_CUSTOM_WORKOUT_PLAN }
        val first = plan.insertUnlocks.single { it.achievementId == AchievementId.FIRST_WORKOUT }
        assertEquals(500L, planner.unlockedAt)
        assertNull(planner.celebratedAt)
        assertEquals(100L, first.unlockedAt)
        assertEquals(now, first.celebratedAt)
        assertTrue(plan.revoke.isEmpty())
    }

    @Test
    fun equalTimestampsPreferTheLaterProductMilestone() {
        val plan = AchievementReconciler.plan(
            request(
                journey = JourneyEvaluator.qualifications(
                    earliestNativeCompletedAt = 40L,
                    earliestCustomPlanAt = 40L,
                    monthlyReportGeneratedAt = 40L
                ),
                recordMonthly = true
            ),
            emptyList(),
            emptyList()
        )
        val pending = plan.insertUnlocks.single { it.celebratedAt == null }
        assertEquals(AchievementId.FIRST_MONTHLY_REPORT, pending.achievementId)
        assertEquals(40L, pending.unlockedAt)
    }

    @Test
    fun storedJourneyAwardsAreNotRevokedWhenTheSourceDisappears() {
        val stored = listOf(
            StoredUnlock(AchievementId.FIRST_WORKOUT, celebratedAt = 20L),
            StoredUnlock(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN, celebratedAt = null)
        )
        val plan = AchievementReconciler.plan(request(), stored, emptyList())
        assertTrue(plan.insertUnlocks.isEmpty())
        assertTrue(plan.revoke.isEmpty())
    }

    @Test
    fun repeatingTheSameQualificationsInsertsNothing() {
        val qualifications = JourneyEvaluator.qualifications(80L, null, null)
        val first = AchievementReconciler.plan(request(journey = qualifications), emptyList(), emptyList())
        val stored = first.insertUnlocks.map { StoredUnlock(it.achievementId, it.celebratedAt) }
        val second = AchievementReconciler.plan(
            request(initialized = true, journey = qualifications),
            stored,
            emptyList()
        )
        assertTrue(second.insertUnlocks.isEmpty())
        assertEquals(80L, first.insertUnlocks.single().unlockedAt)
    }

    @Test
    fun firstStepUsesTheWorkoutTriggerOnlyWhenItIsTheCelebration() {
        val plan = AchievementReconciler.plan(
            request(
                trigger = "session-1",
                journey = JourneyEvaluator.qualifications(80L, null, null)
            ),
            emptyList(),
            emptyList()
        )
        val unlock = plan.insertUnlocks.single()
        assertEquals("session-1", unlock.triggerClientWorkoutId)
        assertNull(unlock.celebratedAt)
    }

    @Test
    fun monthlyReportRequiresAGeneratedMonthWithWorkouts() {
        assertFalse(JourneyEvaluator.monthlyReportQualifies(isMonthly = true, completedWorkoutsInPeriod = 0))
        assertFalse(JourneyEvaluator.monthlyReportQualifies(isMonthly = false, completedWorkoutsInPeriod = 3))
        assertTrue(JourneyEvaluator.monthlyReportQualifies(isMonthly = true, completedWorkoutsInPeriod = 1))
    }

    @Test
    fun monthlyMarkerIsWrittenOnceAndDoesNotCelebrateItself() {
        val qualifications = JourneyEvaluator.qualifications(null, null, now)
        val first = AchievementReconciler.plan(
            request(journey = qualifications, recordMonthly = true),
            emptyList(),
            emptyList()
        )
        val marker = first.insertEvents.single { it.kind == ProgressEventKind.JOURNEY_MARKER }
        assertEquals(JourneyEvaluator.MONTHLY_REPORT_KEY, marker.dedupeKey)
        assertEquals(now, marker.celebratedAt)
        assertEquals(now, marker.occurredAt)
        val again = AchievementReconciler.plan(
            request(initialized = true, journey = qualifications, recordMonthly = true),
            first.insertUnlocks.map { StoredUnlock(it.achievementId, it.celebratedAt) },
            listOf(StoredProgressEvent(marker.dedupeKey, marker.kind, marker.celebratedAt))
        )
        assertTrue(again.insertEvents.none { it.kind == ProgressEventKind.JOURNEY_MARKER })
        assertTrue(again.insertUnlocks.isEmpty())
    }

    private fun request(
        initialized: Boolean = false,
        trigger: String? = null,
        journey: List<JourneyQualification> = emptyList(),
        recordMonthly: Boolean = false
    ): ReconcileRequest {
        return ReconcileRequest(
            initialized = initialized,
            completedWorkoutCount = 0,
            achievedWeeks = emptyList(),
            nowMillis = now,
            triggerClientWorkoutId = trigger,
            journeyQualifications = journey,
            recordMonthlyReportMarker = recordMonthly
        )
    }
}
