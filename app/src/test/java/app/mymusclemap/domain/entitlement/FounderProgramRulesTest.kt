package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FounderProgramRulesTest {
    private val enrolledOn = LocalDate.of(2026, 3, 1)

    @Test
    fun productionDefaultsAreThePublishedThresholds() {
        val rules = FounderProgramRules.Production
        assertEquals(5, rules.temporaryProWorkoutCount)
        assertEquals(10, rules.founderWorkoutCount)
        assertEquals(6, rules.requiredDistinctWorkoutDays)
        assertEquals(45, rules.qualificationWindowDays)
        assertTrue(rules.feedbackRequired)
        assertTrue(rules.testerAnalyticsReportRequired)
        assertEquals(enrolledOn.plusDays(45), rules.deadline(enrolledOn))
    }

    @Test
    fun oneConfiguredWorkoutUnlocksTemporaryPro() {
        val logic = FounderProgramLogic(fastRules())
        val enrolled = enroll(logic)
        val unlocked = logic.refresh(enrolled, native(enrolledOn), enrolledOn)
        assertEquals(FounderProgramStatus.ActivePro, unlocked.state.status)
    }

    @Test
    fun twoConfiguredWorkoutsCanReachPendingApproval() {
        val logic = FounderProgramLogic(fastRules(requiredDistinctWorkoutDays = 1))
        val day = enrolledOn
        val workouts = listOf(nativeWorkout(day), nativeWorkout(day))
        val pending = submitTesterInput(logic, enroll(logic), workouts, day)
        assertEquals(FounderProgramStatus.PendingApproval, pending.status)
    }

    @Test
    fun twoWorkoutsOnOneDateCountAsOneDistinctDay() {
        val rules = fastRules(founderWorkoutCount = 2, requiredDistinctWorkoutDays = 2)
        val day = enrolledOn
        val qualification = rules.qualify(
            workouts = listOf(nativeWorkout(day), nativeWorkout(day)),
            feedbackRecorded = true,
            testerAnalyticsReportSubmitted = true
        )
        assertEquals(2, qualification.nativeCompletedWorkouts)
        assertEquals(1, qualification.distinctNativeWorkoutDays)
        assertFalse(qualification.testerRequirementsComplete)

        val logic = FounderProgramLogic(rules)
        val refreshed = logic.refresh(enroll(logic), listOf(nativeWorkout(day), nativeWorkout(day)), day)
        assertEquals(FounderProgramStatus.ActivePro, refreshed.state.status)
    }

    @Test
    fun twoWorkoutsOnTwoSuppliedDatesCountAsTwoDistinctDays() {
        val oneDayRules = fastRules(founderWorkoutCount = 2, requiredDistinctWorkoutDays = 1)
        val twoDayRules = oneDayRules.copy(requiredDistinctWorkoutDays = 2)
        val workouts = listOf(nativeWorkout(enrolledOn), nativeWorkout(enrolledOn.plusDays(1)))
        val today = enrolledOn.plusDays(1)

        val oneDay = FounderProgramLogic(oneDayRules)
        val twoDay = FounderProgramLogic(twoDayRules)
        assertEquals(
            FounderProgramStatus.PendingApproval,
            submitTesterInput(oneDay, enroll(oneDay), workouts, today).status
        )
        assertEquals(
            FounderProgramStatus.PendingApproval,
            submitTesterInput(twoDay, enroll(twoDay), workouts, today).status
        )
        assertEquals(oneDay::class, twoDay::class)
    }

    @Test
    fun customQualificationWindowIsHonored() {
        val rules = fastRules(qualificationWindowDays = 3)
        val logic = FounderProgramLogic(rules)
        val enrolled = enroll(logic)
        assertEquals(enrolledOn.plusDays(3), enrolled.deadline)
        val expired = logic.refresh(enrolled, emptyList(), enrolledOn.plusDays(3))
        assertEquals(FounderProgramStatus.Expired, expired.state.status)
    }

    @Test
    fun feedbackCanBeOptionalWhileProductionStillRequiresIt() {
        val optional = FounderProgramLogic(fastRules(feedbackRequired = false))
        val workouts = listOf(nativeWorkout(enrolledOn), nativeWorkout(enrolledOn))
        val reported = optional.submitTesterAnalyticsReport(enroll(optional), workouts, enrolledOn)
        assertEquals(FounderProgramStatus.PendingApproval, reported.state.status)
        assertFalse(reported.state.feedbackRecorded)

        val production = FounderProgramLogic(FounderProgramRules.Production)
        val productionWorkouts = nativeOnDistinctDays(
            days = FounderProgramRules.Production.requiredDistinctWorkoutDays,
            total = FounderProgramRules.Production.founderWorkoutCount
        )
        val reportedOnly = production.submitTesterAnalyticsReport(
            enroll(production),
            productionWorkouts,
            enrolledOn.plusDays(10)
        )
        assertEquals(FounderProgramStatus.ActivePro, reportedOnly.state.status)
        assertFalse(reportedOnly.state.feedbackRecorded)
    }

    @Test
    fun testerAnalyticsReportCanBeOptionalWhileProductionStillRequiresIt() {
        val optional = FounderProgramLogic(fastRules(testerAnalyticsReportRequired = false))
        val workouts = listOf(nativeWorkout(enrolledOn), nativeWorkout(enrolledOn))
        val withFeedback = optional.recordFeedback(
            enroll(optional),
            "The rest timer is easy to reach.",
            workouts,
            enrolledOn
        )
        assertEquals(FounderProgramStatus.PendingApproval, withFeedback.state.status)
        assertFalse(withFeedback.state.testerAnalyticsReportSubmitted)

        val production = FounderProgramLogic(FounderProgramRules.Production)
        val productionWorkouts = nativeOnDistinctDays(
            days = FounderProgramRules.Production.requiredDistinctWorkoutDays,
            total = FounderProgramRules.Production.founderWorkoutCount
        )
        val feedbackOnly = production.recordFeedback(
            enroll(production),
            "The rest timer is easy to reach.",
            productionWorkouts,
            enrolledOn.plusDays(10)
        )
        assertEquals(FounderProgramStatus.ActivePro, feedbackOnly.state.status)
        assertFalse(feedbackOnly.state.testerAnalyticsReportSubmitted)
    }

    @Test
    fun activeFreeAndActiveProExpireOnTheSuppliedDeadline() {
        val rules = FounderProgramRules(
            temporaryProWorkoutCount = 1,
            founderWorkoutCount = 3,
            requiredDistinctWorkoutDays = 3,
            qualificationWindowDays = 4,
            feedbackRequired = true,
            testerAnalyticsReportRequired = true
        )
        val logic = FounderProgramLogic(rules)
        val deadline = enrolledOn.plusDays(4)
        val stillFree = logic.refresh(enroll(logic), emptyList(), deadline)
        assertEquals(FounderProgramStatus.Expired, stillFree.state.status)

        val unlocked = logic.refresh(enroll(logic), native(enrolledOn), enrolledOn.plusDays(1)).state
        assertEquals(FounderProgramStatus.ActivePro, unlocked.status)
        val expiredPro = logic.refresh(unlocked, native(enrolledOn), deadline)
        assertEquals(FounderProgramStatus.Expired, expiredPro.state.status)
    }

    @Test
    fun qualificationOnTheConfiguredDeadlineBecomesPendingAndTheNextDayExpires() {
        val rules = fastRules(qualificationWindowDays = 2)
        val logic = FounderProgramLogic(rules)
        val deadline = rules.deadline(enrolledOn)
        val workouts = listOf(nativeWorkout(enrolledOn), nativeWorkout(deadline))
        val withFeedback = logic.recordFeedback(
            enroll(logic),
            "The plan editor is usable on a phone.",
            workouts,
            deadline.minusDays(1)
        ).state
        val pending = logic.submitTesterAnalyticsReport(withFeedback, workouts, deadline).state
        assertEquals(FounderProgramStatus.PendingApproval, pending.status)

        val qualifiedButLate = enroll(logic).copy(
            status = FounderProgramStatus.ActivePro,
            feedbackRecorded = true,
            testerAnalyticsReportSubmitted = true
        )
        val late = logic.refresh(qualifiedButLate, workouts, deadline.plusDays(1))
        assertEquals(FounderProgramStatus.Expired, late.state.status)
    }

    @Test
    fun pendingApprovalStaysPendingAfterTheConfiguredDeadline() {
        val rules = fastRules(qualificationWindowDays = 2)
        val logic = FounderProgramLogic(rules)
        val deadline = rules.deadline(enrolledOn)
        val workouts = listOf(nativeWorkout(enrolledOn), nativeWorkout(enrolledOn))
        val pending = submitTesterInput(logic, enroll(logic), workouts, enrolledOn)
        assertEquals(FounderProgramStatus.PendingApproval, pending.status)
        val later = logic.refresh(pending, workouts, deadline.plusDays(30))
        assertEquals(FounderProgramStatus.PendingApproval, later.state.status)
        assertTrue(later is FounderProgramResult.Unchanged)
    }

    private fun fastRules(
        founderWorkoutCount: Int = 2,
        requiredDistinctWorkoutDays: Int = 1,
        qualificationWindowDays: Int = 45,
        feedbackRequired: Boolean = true,
        testerAnalyticsReportRequired: Boolean = true
    ): FounderProgramRules {
        return FounderProgramRules(
            temporaryProWorkoutCount = 1,
            founderWorkoutCount = founderWorkoutCount,
            requiredDistinctWorkoutDays = requiredDistinctWorkoutDays,
            qualificationWindowDays = qualificationWindowDays,
            feedbackRequired = feedbackRequired,
            testerAnalyticsReportRequired = testerAnalyticsReportRequired
        )
    }

    private fun enroll(logic: FounderProgramLogic): FounderProgramState {
        return (logic.enroll(FounderProgramState(), enrolledOn) as FounderProgramResult.Changed).state
    }

    private fun native(day: LocalDate): List<CompletedWorkout> = listOf(nativeWorkout(day))

    private fun nativeWorkout(day: LocalDate): CompletedWorkout {
        return CompletedWorkout(day, WorkoutOrigin.NativeStrict)
    }

    private fun nativeOnDistinctDays(days: Int, total: Int): List<CompletedWorkout> {
        val firstDayExtras = total - days
        val first = List(1 + firstDayExtras) { nativeWorkout(enrolledOn) }
        val rest = (1 until days).map { offset -> nativeWorkout(enrolledOn.plusDays(offset.toLong())) }
        return first + rest
    }

    private fun submitTesterInput(
        logic: FounderProgramLogic,
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
