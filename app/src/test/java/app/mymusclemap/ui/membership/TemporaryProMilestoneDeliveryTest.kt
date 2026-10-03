package app.mymusclemap.ui.membership

import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.CompletedWorkout
import app.mymusclemap.domain.entitlement.FounderProgramLogic
import app.mymusclemap.domain.entitlement.FounderProgramResult
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.WorkoutOrigin
import app.mymusclemap.ui.founder.FounderMilestone
import app.mymusclemap.ui.founder.founderJourney
import app.mymusclemap.ui.founder.temporaryProMilestonePending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TemporaryProMilestoneDeliveryTest {
    private val enrolledOn = LocalDate.of(2026, 3, 1)
    private val unseen = FounderMilestoneAcknowledgements()

    @Test
    fun activeFreeDoesNotDeliverTheMilestone() {
        assertFalse(temporaryProMilestonePending(FounderProgramStatus.ActiveFree, unseen))
        assertFalse(temporaryProMilestonePending(FounderProgramStatus.NotEnrolled, unseen))
        assertFalse(temporaryProMilestonePending(FounderProgramStatus.PendingApproval, unseen))
        assertFalse(temporaryProMilestonePending(FounderProgramStatus.Approved, unseen))
    }

    @Test
    fun nonQualifyingWorkoutStaysFreeAndShowsNothing() {
        val logic = FounderProgramLogic(rules(temporaryProWorkoutCount = 2))
        val enrolled = enroll(logic)
        val stillFree = logic.refresh(enrolled, listOf(native(enrolledOn)), enrolledOn)
        assertEquals(FounderProgramStatus.ActiveFree, stillFree.state.status)
        assertFalse(temporaryProMilestonePending(stillFree.state.status, unseen))
    }

    @Test
    fun healthConnectWorkoutDoesNotUnlockTemporaryPro() {
        val logic = FounderProgramLogic(rules())
        val enrolled = enroll(logic)
        val external = CompletedWorkout(enrolledOn, WorkoutOrigin.HealthConnect)
        val refreshed = logic.refresh(enrolled, listOf(external), enrolledOn)
        assertEquals(FounderProgramStatus.ActiveFree, refreshed.state.status)
        assertFalse(temporaryProMilestonePending(refreshed.state.status, unseen))
    }

    @Test
    fun qualifyingNativeWorkoutReachesTemporaryProAndIsDeliverable() {
        val logic = FounderProgramLogic(rules())
        val enrolled = enroll(logic)
        val unlocked = logic.refresh(enrolled, listOf(native(enrolledOn)), enrolledOn)
        assertEquals(FounderProgramStatus.ActivePro, unlocked.state.status)
        assertTrue(temporaryProMilestonePending(unlocked.state.status, unseen))
        assertEquals(
            FounderMilestone.TemporaryProUnlocked,
            journey(unlocked.state, workouts = 1).milestone
        )
    }

    @Test
    fun acknowledgementClosesOverviewAndTheFounderScreen() {
        val seen = unseen.copy(temporaryProUnlocked = true)
        assertFalse(temporaryProMilestonePending(FounderProgramStatus.ActivePro, seen))
        assertNull(journey(status(FounderProgramStatus.ActivePro), workouts = 1, acknowledgements = seen).milestone)
    }

    @Test
    fun restartAndProcessDeathStillDeliverAnUnacknowledgedMilestone() {
        val persisted = FounderProgramStatus.ActivePro
        val notYetSeen = FounderMilestoneAcknowledgements(temporaryProUnlocked = false)
        assertTrue(temporaryProMilestonePending(persisted, notYetSeen))
        assertEquals(
            FounderMilestone.TemporaryProUnlocked,
            journey(status(persisted), workouts = 1, acknowledgements = notYetSeen).milestone
        )
    }

    @Test
    fun secondWorkoutDoesNotDeliverTemporaryProAgain() {
        val logic = FounderProgramLogic(rules())
        val enrolled = enroll(logic)
        val first = logic.refresh(enrolled, listOf(native(enrolledOn)), enrolledOn).state
        assertEquals(FounderProgramStatus.ActivePro, first.status)
        val seen = unseen.copy(temporaryProUnlocked = true)
        val second = logic.refresh(
            first,
            listOf(native(enrolledOn), native(enrolledOn)),
            enrolledOn
        ).state
        assertEquals(FounderProgramStatus.ActivePro, second.status)
        assertFalse(temporaryProMilestonePending(second.status, seen))
        val journey = journey(second, workouts = 2, acknowledgements = seen)
        assertNull(journey.milestone)
        assertTrue(journey.trainingComplete)
        assertTrue(journey.showFeedback)
    }

    private fun rules(temporaryProWorkoutCount: Int = 1): FounderProgramRules {
        return FounderProgramRules(
            temporaryProWorkoutCount = temporaryProWorkoutCount,
            founderWorkoutCount = 2,
            requiredDistinctWorkoutDays = 1,
            qualificationWindowDays = 45,
            feedbackRequired = true,
            testerAnalyticsReportRequired = true
        )
    }

    private fun enroll(logic: FounderProgramLogic): FounderProgramState {
        return (logic.enroll(FounderProgramState(), enrolledOn) as FounderProgramResult.Changed).state
    }

    private fun native(day: LocalDate): CompletedWorkout {
        return CompletedWorkout(day, WorkoutOrigin.NativeStrict)
    }

    private fun status(status: FounderProgramStatus): FounderProgramState {
        return FounderProgramState(
            status = status,
            enrolledOn = enrolledOn,
            deadline = enrolledOn.plusDays(45)
        )
    }

    private fun journey(
        state: FounderProgramState,
        workouts: Int,
        acknowledgements: FounderMilestoneAcknowledgements = unseen
    ) = founderJourney(
        status = state.status,
        qualification = rules().qualify(
            workouts = List(workouts) { native(enrolledOn) },
            feedbackRecorded = false,
            testerAnalyticsReportSubmitted = false
        ),
        acknowledgements = acknowledgements
    )
}
