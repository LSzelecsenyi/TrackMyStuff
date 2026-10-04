package app.mymusclemap.ui.founder

import app.mymusclemap.FounderProgramAvailabilitySelection
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FounderProgramAvailabilityTest {
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val enrolledOn = LocalDate.of(2026, 10, 1)
    private val deadline = LocalDate.of(2026, 11, 15)

    @Test
    fun pilotConfigurationIsOpenAndSeparateFromRules() {
        assertEquals(FounderProgramAvailability.Open, FounderProgramAvailabilitySelection.availability)
        assertEquals(5, FounderProgramRules.Production.temporaryProWorkoutCount)
        assertEquals(10, FounderProgramRules.Production.founderWorkoutCount)
        assertEquals(6, FounderProgramRules.Production.requiredDistinctWorkoutDays)
        assertEquals(45, FounderProgramRules.Production.qualificationWindowDays)
    }

    @Test
    fun openAllowsANewInvitationAndClosedDoesNot() {
        val fresh = FounderMilestoneAcknowledgements()
        val handled = fresh.copy(invitationHandled = true)
        assertTrue(
            shouldOfferFounderOnboardingInvitation(
                FounderProgramAvailability.Open,
                FounderProgramStatus.NotEnrolled,
                fresh
            )
        )
        assertFalse(
            shouldOfferFounderOnboardingInvitation(
                FounderProgramAvailability.Closed,
                FounderProgramStatus.NotEnrolled,
                fresh
            )
        )
        assertFalse(
            shouldOfferFounderOnboardingInvitation(
                FounderProgramAvailability.Open,
                FounderProgramStatus.NotEnrolled,
                handled
            )
        )
        FounderProgramStatus.values().filter { it != FounderProgramStatus.NotEnrolled }.forEach { status ->
            assertFalse(
                shouldOfferFounderOnboardingInvitation(
                    FounderProgramAvailability.Open,
                    status,
                    fresh
                )
            )
        }
    }

    @Test
    fun closedBlocksOnlyNewEnrollmentAndLeavesParticipantsVisible() {
        assertTrue(founderEnrollmentAllowed(FounderProgramAvailability.Open, FounderProgramStatus.NotEnrolled))
        assertFalse(founderEnrollmentAllowed(FounderProgramAvailability.Closed, FounderProgramStatus.NotEnrolled))
        FounderProgramStatus.values().filter { it != FounderProgramStatus.NotEnrolled }.forEach { status ->
            assertFalse(founderEnrollmentAllowed(FounderProgramAvailability.Open, status))
            assertFalse(founderEnrollmentAllowed(FounderProgramAvailability.Closed, status))
            assertTrue(founderSettingsEntryVisible(FounderProgramAvailability.Closed, status, programReady = true))
        }
        assertTrue(
            founderSettingsEntryVisible(
                FounderProgramAvailability.Open,
                FounderProgramStatus.NotEnrolled,
                programReady = true
            )
        )
        assertFalse(
            founderSettingsEntryVisible(
                FounderProgramAvailability.Closed,
                FounderProgramStatus.NotEnrolled,
                programReady = true
            )
        )
        assertFalse(
            founderSettingsEntryVisible(
                FounderProgramAvailability.Closed,
                FounderProgramStatus.NotEnrolled,
                programReady = false
            )
        )
    }

    @Test
    fun closingAvailabilityDoesNotGrantOrRevokePro() {
        val approved = FounderProgramState(
            status = FounderProgramStatus.Approved,
            enrolledOn = enrolledOn,
            deadline = deadline
        )
        val lifetime = EntitlementResolver.resolve(EntitlementSources.of(program = approved), now)
        assertFalse(lifetime.founderLifetime)
        assertEquals(EntitlementTier.Free, lifetime.tier)
        assertEquals(FounderProgramStatus.Approved, EntitlementSources.of(program = approved).founderProgram.status)

        val activePro = approved.copy(status = FounderProgramStatus.ActivePro)
        val temporary = EntitlementResolver.resolve(EntitlementSources.of(program = activePro), now)
        assertFalse(temporary.temporaryTesterPro)
        assertEquals(EntitlementTier.Free, temporary.tier)
        assertFalse(temporary.founderLifetime)

        val pending = approved.copy(status = FounderProgramStatus.PendingApproval)
        val reviewing = EntitlementResolver.resolve(EntitlementSources.of(program = pending), now)
        assertFalse(reviewing.temporaryTesterPro)
        assertEquals(EntitlementTier.Free, reviewing.tier)

        val granted = EntitlementResolver.resolve(
            EntitlementSources.of(
                program = activePro,
                backendFounder = app.mymusclemap.domain.entitlement.BackendFounderEntitlement(
                    temporaryFounderPro = true,
                    validUntil = now.plusSeconds(60)
                )
            ),
            now
        )
        assertTrue(granted.temporaryTesterPro)
        assertEquals(EntitlementTier.Pro, granted.tier)

        val activeFree = approved.copy(status = FounderProgramStatus.ActiveFree)
        val free = EntitlementResolver.resolve(EntitlementSources.of(program = activeFree), now)
        assertEquals(EntitlementTier.Free, free.tier)
        assertFalse(free.temporaryTesterPro)
    }
}
