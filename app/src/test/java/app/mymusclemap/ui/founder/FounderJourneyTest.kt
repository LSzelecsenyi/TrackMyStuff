package app.mymusclemap.ui.founder

import app.mymusclemap.FounderProgramRuleSelection
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.FounderQualification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FounderJourneyTest {
    private val fastRules = FounderProgramRules(
        temporaryProWorkoutCount = 1,
        founderWorkoutCount = 2,
        requiredDistinctWorkoutDays = 1,
        qualificationWindowDays = 45,
        feedbackRequired = true,
        testerAnalyticsReportRequired = true
    )

    @Test
    fun displayedRequirementsFollowTheRulesTheyAreGiven() {
        assertDisplayedRules(FounderProgramRules.Production)
        assertDisplayedRules(fastRules)
    }

    @Test
    fun activeRuleSelectionFeedsTheDisplayedRequirements() {
        val rules = FounderProgramRuleSelection.rules
        val journey = journey(FounderProgramStatus.NotEnrolled, rules)
        assertEquals(rules, journey.rules)
        assertEquals(rules.feedbackRequired, journey.checklist.any { it.kind == FounderChecklistKind.Feedback })
        assertEquals(rules.testerAnalyticsReportRequired, journey.checklist.any { it.kind == FounderChecklistKind.TesterReport })
    }

    @Test
    fun optionalRequirementsAreOmittedWhenTheRulesDoNotRequireThem() {
        val rules = fastRules.copy(feedbackRequired = false, testerAnalyticsReportRequired = false)
        val journey = journey(FounderProgramStatus.ActivePro, rules, workouts = 2, days = 1)
        assertTrue(journey.checklist.none { it.kind == FounderChecklistKind.Feedback })
        assertTrue(journey.checklist.none { it.kind == FounderChecklistKind.TesterReport })
        assertNull(journey.nextAction)
    }

    @Test
    fun activeFreePointsAtTheTemporaryProMilestone() {
        val production = journey(FounderProgramStatus.ActiveFree, FounderProgramRules.Production, workouts = 2, days = 2)
        assertEquals(FounderNextAction.UnlockTemporaryPro, production.nextAction)
        assertEquals(3, production.nextRemaining)
        assertFalse(production.temporaryProActive)
        assertNull(production.milestone)
        assertEquals(5, production.checklist.first { it.kind == FounderChecklistKind.TemporaryPro }.required)

        val fast = journey(FounderProgramStatus.ActiveFree, fastRules)
        assertEquals(FounderNextAction.UnlockTemporaryPro, fast.nextAction)
        assertEquals(1, fast.nextRemaining)
        assertEquals(2, fast.checklist.first { it.kind == FounderChecklistKind.QualifyingWorkouts }.required)
        assertEquals(1, fast.checklist.first { it.kind == FounderChecklistKind.TrainingDays }.required)
    }

    @Test
    fun activeProPrioritizesTheFirstIncompleteRequirement() {
        val workoutsFirst = journey(
            FounderProgramStatus.ActivePro,
            FounderProgramRules.Production,
            workouts = 7,
            days = 4
        )
        assertEquals(FounderNextAction.QualifyingWorkout, workoutsFirst.nextAction)
        assertEquals(3, workoutsFirst.nextRemaining)
        assertTrue(workoutsFirst.checklist.first { it.kind == FounderChecklistKind.TemporaryPro }.done)
        assertFalse(workoutsFirst.checklist.first { it.kind == FounderChecklistKind.QualifyingWorkouts }.done)
        assertFalse(workoutsFirst.checklist.first { it.kind == FounderChecklistKind.TrainingDays }.done)

        val daysFirst = journey(
            FounderProgramStatus.ActivePro,
            FounderProgramRules.Production,
            workouts = 10,
            days = 4
        )
        assertEquals(FounderNextAction.TrainingDay, daysFirst.nextAction)
        assertEquals(2, daysFirst.nextRemaining)

        val feedback = journey(
            FounderProgramStatus.ActivePro,
            fastRules,
            workouts = 2,
            days = 1
        )
        assertEquals(FounderNextAction.Feedback, feedback.nextAction)
        assertFalse(feedback.feedbackSaved)

        val report = journey(
            FounderProgramStatus.ActivePro,
            fastRules,
            workouts = 2,
            days = 1,
            feedback = true
        )
        assertEquals(FounderNextAction.TesterReport, report.nextAction)
        assertTrue(report.feedbackSaved)
        assertFalse(report.reportShared)
    }

    @Test
    fun milestonesAppearOnlyUntilThatMilestoneIsAcknowledged() {
        val unseen = FounderMilestoneAcknowledgements()
        val pro = journey(FounderProgramStatus.ActivePro, fastRules, workouts = 1, days = 1, acknowledgements = unseen)
        assertEquals(FounderMilestone.TemporaryProUnlocked, pro.milestone)
        val proSeen = journey(
            FounderProgramStatus.ActivePro,
            fastRules,
            workouts = 1,
            days = 1,
            acknowledgements = unseen.copy(temporaryProUnlocked = true)
        )
        assertNull(proSeen.milestone)
        assertEquals(pro.phase, proSeen.phase)
        assertEquals(pro.temporaryProActive, proSeen.temporaryProActive)

        val pending = journey(FounderProgramStatus.PendingApproval, fastRules, workouts = 2, days = 1, feedback = true, report = true)
        assertEquals(FounderMilestone.QualificationComplete, pending.milestone)
        assertEquals(FounderJourneyPhase.PendingReview, pending.phase)
        assertTrue(pending.temporaryProActive)
        assertFalse(pending.showDeadline)
        assertNull(pending.nextAction)
        assertFalse(pending.acceptsContribution)
        assertNull(
            journey(
                FounderProgramStatus.PendingApproval,
                fastRules,
                workouts = 2,
                days = 1,
                feedback = true,
                report = true,
                acknowledgements = unseen.copy(qualificationComplete = true)
            ).milestone
        )

        val approved = journey(FounderProgramStatus.Approved, fastRules, workouts = 2, days = 1, feedback = true, report = true)
        assertEquals(FounderMilestone.FounderApproved, approved.milestone)
        assertEquals(FounderJourneyPhase.FoundingMember, approved.phase)
        assertFalse(approved.temporaryProActive)
        assertNull(
            journey(
                FounderProgramStatus.Approved,
                fastRules,
                workouts = 2,
                days = 1,
                feedback = true,
                report = true,
                acknowledgements = unseen.copy(founderApproved = true)
            ).milestone
        )
    }

    @Test
    fun laterMilestoneWinsWhenEarlierOnesWereNeverAcknowledged() {
        val pending = journey(FounderProgramStatus.PendingApproval, fastRules, workouts = 2, days = 1, feedback = true, report = true)
        assertEquals(FounderMilestone.QualificationComplete, pending.milestone)
        val approved = journey(FounderProgramStatus.Approved, fastRules, workouts = 2, days = 1, feedback = true, report = true)
        assertEquals(FounderMilestone.FounderApproved, approved.milestone)
    }

    @Test
    fun acknowledgementFlagsDoNotResolveEntitlement() {
        val now = Instant.parse("2026-10-03T12:00:00Z")
        val enrolled = FounderProgramState(
            status = FounderProgramStatus.ActiveFree,
            enrolledOn = LocalDate.of(2026, 10, 1),
            deadline = LocalDate.of(2026, 11, 15)
        )
        val seen = FounderMilestoneAcknowledgements(
            temporaryProUnlocked = true,
            qualificationComplete = true,
            founderApproved = true
        )
        val free = EntitlementResolver.resolve(EntitlementSources.of(program = enrolled), now)
        assertEquals(EntitlementTier.Free, free.tier)
        assertFalse(free.temporaryTesterPro)
        assertFalse(free.founderLifetime)
        assertTrue(seen.founderApproved)
        assertEquals(free, EntitlementResolver.resolve(EntitlementSources.of(program = enrolled), now))

        val approved = enrolled.copy(status = FounderProgramStatus.Approved)
        val lifetime = EntitlementResolver.resolve(EntitlementSources.of(program = approved), now)
        assertFalse(lifetime.founderLifetime)
        assertEquals(EntitlementTier.Free, lifetime.tier)
        val forgotten = FounderMilestoneAcknowledgements()
        assertFalse(forgotten.founderApproved)
        assertFalse(forgotten.temporaryProUnlocked)
        assertEquals(lifetime, EntitlementResolver.resolve(EntitlementSources.of(program = approved), now))
    }

    @Test
    fun expiredAndRejectedJourneysStayExplanatory() {
        val expired = journey(FounderProgramStatus.Expired, fastRules, workouts = 1, days = 1)
        assertEquals(FounderJourneyPhase.Expired, expired.phase)
        assertNull(expired.milestone)
        assertNull(expired.nextAction)
        assertFalse(expired.showDeadline)
        assertFalse(expired.temporaryProActive)
        assertTrue(expired.checklist.any { !it.done })

        val rejected = journey(
            FounderProgramStatus.Rejected,
            fastRules,
            workouts = 2,
            days = 1,
            feedback = true,
            report = true
        )
        assertEquals(FounderJourneyPhase.Rejected, rejected.phase)
        assertNull(rejected.milestone)
        assertFalse(rejected.temporaryProActive)
    }

    @Test
    fun feedbackAndTheReportStayHiddenUntilTrainingIsComplete() {
        val enrolled = journey(FounderProgramStatus.ActiveFree, fastRules, feedback = true)
        assertFalse(enrolled.trainingComplete)
        assertTrue(enrolled.feedbackSaved)
        assertFalse(enrolled.showFeedback)
        assertFalse(enrolled.showReport)
        assertTrue(enrolled.presentedChecklist().none { it.kind == FounderChecklistKind.Feedback })

        val trainingLeft = journey(
            FounderProgramStatus.ActivePro,
            fastRules,
            workouts = 1,
            days = 1,
            feedback = true
        )
        assertFalse(trainingLeft.trainingComplete)
        assertFalse(trainingLeft.showFeedback)
        assertFalse(trainingLeft.showReport)
        assertEquals(FounderNextAction.QualifyingWorkout, trainingLeft.nextAction)

        val readyForFeedback = journey(FounderProgramStatus.ActivePro, fastRules, workouts = 2, days = 1)
        assertTrue(readyForFeedback.trainingComplete)
        assertTrue(readyForFeedback.showFeedback)
        assertFalse(readyForFeedback.showReport)

        val readyForReport = journey(
            FounderProgramStatus.ActivePro,
            fastRules,
            workouts = 2,
            days = 1,
            feedback = true
        )
        assertTrue(readyForReport.showFeedback)
        assertTrue(readyForReport.showReport)
        assertEquals(FounderNextAction.TesterReport, readyForReport.nextAction)
    }

    @Test
    fun feedbackAndReportCompletionFollowTheRecordedFlags() {
        val open = journey(FounderProgramStatus.ActivePro, fastRules, workouts = 2, days = 1)
        assertFalse(open.feedbackSaved)
        assertFalse(open.reportShared)
        assertFalse(open.checklist.first { it.kind == FounderChecklistKind.Feedback }.done)
        assertFalse(open.checklist.first { it.kind == FounderChecklistKind.TesterReport }.done)

        val done = journey(
            FounderProgramStatus.ActivePro,
            fastRules,
            workouts = 2,
            days = 1,
            feedback = true,
            report = true
        )
        assertTrue(done.feedbackSaved)
        assertTrue(done.reportShared)
        assertTrue(done.checklist.first { it.kind == FounderChecklistKind.Feedback }.done)
        assertTrue(done.checklist.first { it.kind == FounderChecklistKind.TesterReport }.done)
    }

    private fun assertDisplayedRules(rules: FounderProgramRules) {
        val journey = journey(FounderProgramStatus.NotEnrolled, rules)
        assertEquals(rules, journey.rules)
        assertEquals(rules.founderWorkoutCount, journey.checklist.first { it.kind == FounderChecklistKind.QualifyingWorkouts }.required)
        assertEquals(rules.requiredDistinctWorkoutDays, journey.checklist.first { it.kind == FounderChecklistKind.TrainingDays }.required)
        assertEquals(rules.temporaryProWorkoutCount, journey.checklist.first { it.kind == FounderChecklistKind.TemporaryPro }.required)
        assertEquals(rules.qualificationWindowDays, journey.rules.qualificationWindowDays)
        if (rules.feedbackRequired) {
            assertTrue(journey.checklist.any { it.kind == FounderChecklistKind.Feedback })
        }
        if (rules.testerAnalyticsReportRequired) {
            assertTrue(journey.checklist.any { it.kind == FounderChecklistKind.TesterReport })
        }
    }

    private fun journey(
        status: FounderProgramStatus,
        rules: FounderProgramRules,
        workouts: Int = 0,
        days: Int = 0,
        feedback: Boolean = false,
        report: Boolean = false,
        acknowledgements: FounderMilestoneAcknowledgements = FounderMilestoneAcknowledgements()
    ) = founderJourney(
        status = status,
        qualification = FounderQualification(
            nativeCompletedWorkouts = workouts,
            distinctNativeWorkoutDays = days,
            feedbackRecorded = feedback,
            testerAnalyticsReportSubmitted = report,
            rules = rules
        ),
        acknowledgements = acknowledgements
    )
}
