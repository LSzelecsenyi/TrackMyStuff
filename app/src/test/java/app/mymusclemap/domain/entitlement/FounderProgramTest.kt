package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FounderProgramTest {
    private val rules = FounderProgramRules.Production
    private val logic = FounderProgramLogic(rules)
    private val enrolledOn = LocalDate.of(2026, 1, 1)
    private val deadline = rules.deadline(enrolledOn)

    @Test
    fun enrollmentStartsActiveFreeInsideAFortyFiveDayWindow() {
        val enrolled = enroll()
        assertEquals(FounderProgramStatus.ActiveFree, enrolled.status)
        assertEquals(enrolledOn, enrolled.enrolledOn)
        assertEquals(deadline, enrolled.deadline)
        assertEquals(45, deadline.toEpochDay() - enrolledOn.toEpochDay())
        assertFalse(enrolled.status.grantsTemporaryPro())
    }

    @Test
    fun fifthNativeWorkoutUnlocksTemporaryPro() {
        val refreshed = logic.refresh(enroll(), nativeWorkouts(count = 5), enrolledOn.plusDays(4))
        assertEquals(FounderProgramStatus.ActivePro, refreshed.state.status)
        assertTrue(refreshed.state.status.grantsTemporaryPro())
    }

    @Test
    fun fourthNativeWorkoutDoesNotUnlockTemporaryPro() {
        val refreshed = logic.refresh(enroll(), nativeWorkouts(count = 4), enrolledOn.plusDays(4))
        assertEquals(FounderProgramStatus.ActiveFree, refreshed.state.status)
    }

    @Test
    fun healthConnectWorkoutsDoNotCountTowardUnlockOrFounderRequirements() {
        val external = List(12) { index ->
            CompletedWorkout(enrolledOn.plusDays(index.toLong()), WorkoutOrigin.HealthConnect)
        }
        val fourNative = nativeWorkouts(count = 4) + external
        val stillFree = logic.refresh(enroll(), fourNative, enrolledOn.plusDays(10))
        assertEquals(FounderProgramStatus.ActiveFree, stillFree.state.status)

        val tenNativeOnFiveDays = nativeOnDistinctDays(days = 5, total = 10) + external
        val withTesterInput = recordTesterInput(enroll(), tenNativeOnFiveDays, enrolledOn.plusDays(10))
        assertEquals(FounderProgramStatus.ActivePro, withTesterInput.status)
        assertFalse(withTesterInput.status == FounderProgramStatus.PendingApproval)
    }

    @Test
    fun tenWorkoutsOnFewerThanSixDaysDoNotQualify() {
        val workouts = nativeOnDistinctDays(days = 5, total = 10)
        val state = recordTesterInput(enroll(), workouts, enrolledOn.plusDays(10))
        assertEquals(FounderProgramStatus.ActivePro, state.status)
        val qualification = rules.qualify(workouts, true, true)
        assertEquals(10, qualification.nativeCompletedWorkouts)
        assertEquals(5, qualification.distinctNativeWorkoutDays)
        assertFalse(qualification.testerRequirementsComplete)
    }

    @Test
    fun feedbackIsRequiredAndBlankFeedbackDoesNotCount() {
        val workouts = qualifyingWorkouts()
        val reportedOnly = logic.submitTesterAnalyticsReport(enroll(), workouts, enrolledOn.plusDays(10))
        assertEquals(FounderProgramStatus.ActivePro, reportedOnly.state.status)
        assertFalse(reportedOnly.state.feedbackRecorded)

        val blank = logic.recordFeedback(enroll(), "   ", workouts, enrolledOn.plusDays(10))
        assertFalse(blank.state.feedbackRecorded)
        assertEquals(FounderProgramStatus.ActiveFree, blank.state.status)
    }

    @Test
    fun testerAnalyticsReportIsRequired() {
        val workouts = qualifyingWorkouts()
        val feedbackOnly = logic.recordFeedback(
            enroll(),
            "Sets and rest timers match how I train.",
            workouts,
            enrolledOn.plusDays(10)
        )
        assertEquals(FounderProgramStatus.ActivePro, feedbackOnly.state.status)
        assertFalse(feedbackOnly.state.testerAnalyticsReportSubmitted)
    }

    @Test
    fun completedQualificationBeforeTheDeadlineBecomesPendingApproval() {
        val today = deadline.minusDays(1)
        val state = recordTesterInput(enroll(), qualifyingWorkouts(), today)
        assertEquals(FounderProgramStatus.PendingApproval, state.status)
        assertTrue(state.status.grantsTemporaryPro())
    }

    @Test
    fun qualificationOnTheDeadlineDayBecomesPendingApproval() {
        val workouts = qualifyingWorkouts()
        val withFeedback = logic.recordFeedback(
            enroll(),
            "The plan editor is usable on a phone.",
            workouts,
            deadline.minusDays(1)
        ).state
        val submitted = logic.submitTesterAnalyticsReport(withFeedback, workouts, deadline)
        assertEquals(FounderProgramStatus.PendingApproval, submitted.state.status)
    }

    @Test
    fun incompleteQualificationOnTheDeadlineExpires() {
        val refreshed = logic.refresh(enroll(), nativeWorkouts(count = 9), deadline)
        assertEquals(FounderProgramStatus.Expired, refreshed.state.status)
        assertFalse(refreshed.state.status.grantsTemporaryPro())
    }

    @Test
    fun workoutsOutsideTheWindowDoNotQualify() {
        val before = List(10) { CompletedWorkout(enrolledOn.minusDays(3), WorkoutOrigin.NativeStrict) }
        val state = recordTesterInput(enroll(), before, enrolledOn.plusDays(2))
        assertEquals(FounderProgramStatus.ActiveFree, state.status)
    }

    @Test
    fun pendingApprovalDoesNotExpireWhileReviewIsDelayed() {
        val pending = recordTesterInput(enroll(), qualifyingWorkouts(), enrolledOn.plusDays(12))
        val later = logic.refresh(pending, qualifyingWorkouts(), deadline.plusDays(90))
        assertEquals(FounderProgramStatus.PendingApproval, later.state.status)
        assertTrue(later is FounderProgramResult.Unchanged)
    }

    @Test
    fun approvalCorrespondsToFounderLifetime() {
        val pending = recordTesterInput(enroll(), qualifyingWorkouts(), enrolledOn.plusDays(12))
        val approved = logic.approve(pending)
        assertEquals(FounderProgramStatus.Approved, approved.state.status)
        val resolved = EntitlementResolver.resolve(
            EntitlementSources.of(program = approved.state),
            Instant.parse("2026-06-01T00:00:00Z")
        )
        assertTrue(resolved.founderLifetime)
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(logic.approve(enroll()) is FounderProgramResult.Unchanged)
    }

    @Test
    fun rejectionRemovesTemporaryProUnlessAnotherSourceRemains() {
        val pending = recordTesterInput(enroll(), qualifyingWorkouts(), enrolledOn.plusDays(12))
        val rejected = logic.reject(pending, "invalid report")
        assertEquals(FounderProgramStatus.Rejected, rejected.state.status)
        assertEquals("invalid report", rejected.state.rejectionReason)
        val now = Instant.parse("2026-03-01T00:00:00Z")
        assertEquals(
            EntitlementTier.Free,
            EntitlementResolver.resolve(EntitlementSources.of(program = rejected.state), now).tier
        )
        val subscribed = EntitlementResolver.resolve(
            EntitlementSources.of(
                subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(3600)),
                program = rejected.state
            ),
            now
        )
        assertEquals(EntitlementTier.Pro, subscribed.tier)
        assertTrue(logic.reject(pending, "  ") is FounderProgramResult.Unchanged)
    }

    @Test
    fun expiredProgramDoesNotReactivateWhenFeedbackArrivesLate() {
        val expired = logic.refresh(enroll(), nativeWorkouts(count = 5), deadline).state
        assertEquals(FounderProgramStatus.Expired, expired.status)
        val late = logic.recordFeedback(
            expired,
            "Feedback after the window.",
            qualifyingWorkouts(),
            deadline.plusDays(1)
        )
        assertTrue(late is FounderProgramResult.Unchanged)
        assertEquals(FounderProgramStatus.Expired, late.state.status)
    }

    private fun enroll(): FounderProgramState {
        return (logic.enroll(FounderProgramState(), enrolledOn) as FounderProgramResult.Changed).state
    }

    private fun qualifyingWorkouts(): List<CompletedWorkout> {
        return nativeOnDistinctDays(days = rules.requiredDistinctWorkoutDays, total = rules.founderWorkoutCount)
    }

    private fun nativeWorkouts(count: Int): List<CompletedWorkout> {
        return List(count) { index ->
            CompletedWorkout(enrolledOn.plusDays((index % 3).toLong()), WorkoutOrigin.NativeStrict)
        }
    }

    private fun nativeOnDistinctDays(days: Int, total: Int): List<CompletedWorkout> {
        val firstDayExtras = total - days
        val first = List(1 + firstDayExtras) { CompletedWorkout(enrolledOn, WorkoutOrigin.NativeStrict) }
        val rest = (1 until days).map { offset ->
            CompletedWorkout(enrolledOn.plusDays(offset.toLong()), WorkoutOrigin.NativeStrict)
        }
        return first + rest
    }

    private fun recordTesterInput(
        state: FounderProgramState,
        workouts: List<CompletedWorkout>,
        today: LocalDate
    ): FounderProgramState {
        val withFeedback = logic.recordFeedback(
            state,
            "The plan editor is usable on a phone.",
            workouts,
            today
        )
        return logic.submitTesterAnalyticsReport(withFeedback.state, workouts, today).state
    }
}
