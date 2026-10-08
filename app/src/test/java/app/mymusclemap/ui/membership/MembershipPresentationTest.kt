package app.mymusclemap.ui.membership

import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import app.mymusclemap.ui.founder.founderJourney
import app.mymusclemap.ui.founder.temporaryProMilestonePending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Badge mapping consumes resolved entitlement. Acknowledgement and availability are not inputs.
 */
class MembershipPresentationTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val later = now.plusSeconds(86_400)
    private val earlier = now.minusSeconds(86_400)

    @Test
    fun freeHasNoBadge() {
        val presentation = present(FounderProgramStatus.ActiveFree)
        assertEquals(MembershipPresentation.None, presentation)
        assertEquals(MembershipBadge.None, present(FounderProgramStatus.NotEnrolled).badge)
        assertEquals(MembershipBadge.None, present(FounderProgramStatus.Expired).badge)
        assertEquals(MembershipBadge.None, present(FounderProgramStatus.Rejected).badge)
    }

    @Test
    fun temporaryFounderProShowsPro() {
        val presentation = present(FounderProgramStatus.ActivePro)
        assertEquals(MembershipBadge.Pro, presentation.badge)
        assertEquals(MembershipDetail.TemporaryFounderPro, presentation.detail)
    }

    @Test
    fun pendingApprovalKeepsPro() {
        val presentation = present(FounderProgramStatus.PendingApproval)
        assertEquals(MembershipBadge.Pro, presentation.badge)
        assertEquals(MembershipDetail.PendingFounderReview, presentation.detail)
    }

    @Test
    fun genericSubscriptionShowsPro() {
        val presentation = present(
            status = FounderProgramStatus.NotEnrolled,
            subscriptionUntil = later
        )
        assertEquals(MembershipBadge.Pro, presentation.badge)
        assertEquals(MembershipDetail.Pro, presentation.detail)
    }

    @Test
    fun approvedFounderShowsFounderOnly() {
        val presentation = present(FounderProgramStatus.Approved)
        assertEquals(MembershipBadge.Founder, presentation.badge)
        assertEquals(MembershipDetail.FoundingMember, presentation.detail)
    }

    @Test
    fun approvedFounderPlusSubscriptionStaysFounder() {
        val presentation = present(
            status = FounderProgramStatus.Approved,
            subscriptionUntil = later
        )
        assertEquals(MembershipBadge.Founder, presentation.badge)
        assertEquals(MembershipDetail.FoundingMember, presentation.detail)
    }

    @Test
    fun rejectedFounderWithSubscriptionShowsPro() {
        val presentation = present(
            status = FounderProgramStatus.Rejected,
            subscriptionUntil = later
        )
        assertEquals(MembershipBadge.Pro, presentation.badge)
        assertEquals(MembershipDetail.Pro, presentation.detail)
    }

    @Test
    fun noRemainingProSourceRemovesTheBadge() {
        val expiredSubscription = present(
            status = FounderProgramStatus.NotEnrolled,
            subscriptionUntil = earlier
        )
        assertEquals(MembershipPresentation.None, expiredSubscription)
        assertEquals(MembershipBadge.None, present(FounderProgramStatus.Expired).badge)
    }

    @Test
    fun expiredSubscriptionWithFounderLifetimeKeepsFounder() {
        val presentation = present(
            status = FounderProgramStatus.Approved,
            subscriptionUntil = earlier
        )
        assertEquals(MembershipBadge.Founder, presentation.badge)
    }

    @Test
    fun temporaryProPlusSubscriptionStaysPro() {
        val presentation = present(
            status = FounderProgramStatus.ActivePro,
            subscriptionUntil = later
        )
        assertEquals(MembershipBadge.Pro, presentation.badge)
        assertEquals(MembershipDetail.TemporaryFounderPro, presentation.detail)
    }

    @Test
    fun closedAvailabilityDoesNotChangeAnExistingBadge() {
        // Availability is not a mapper input. Closing enrollment leaves these badges in place.
        assertEquals(FounderProgramAvailability.Closed.name, "Closed")
        assertEquals(MembershipBadge.Pro, present(FounderProgramStatus.ActivePro).badge)
        assertEquals(MembershipBadge.Founder, present(FounderProgramStatus.Approved).badge)
    }

    @Test
    fun badgeAndAcknowledgementDoNotGrantOrRemovePro() {
        val sources = EntitlementSources.of(
            program = FounderProgramState(status = FounderProgramStatus.ActivePro),
            backendFounder = BackendFounderEntitlement(
                temporaryFounderPro = true,
                validUntil = later
            )
        )
        val before = EntitlementResolver.resolve(sources, now)
        val acknowledged = FounderMilestoneAcknowledgements(temporaryProUnlocked = true)
        val cleared = FounderMilestoneAcknowledgements(temporaryProUnlocked = false)
        val afterAck = EntitlementResolver.resolve(sources, now)
        val afterClear = EntitlementResolver.resolve(sources, now)
        val badge = membershipPresentation(before, FounderProgramStatus.ActivePro)
        assertEquals(before, afterAck)
        assertEquals(before, afterClear)
        assertTrue(before.grantsPro)
        assertTrue(afterAck.grantsPro)
        assertTrue(temporaryProMilestonePending(FounderProgramStatus.ActivePro, cleared))
        assertFalse(temporaryProMilestonePending(FounderProgramStatus.ActivePro, acknowledged))
        assertEquals(MembershipBadge.Pro, badge.badge)
        assertEquals(before, EntitlementResolver.resolve(sources, now))
        assertFalse(
            AppBackupFormat.TABLE_NAMES.contains(FounderMilestoneAcknowledgementStore.PREFERENCES_NAME)
        )
    }

    @Test
    fun acknowledgedTemporaryProIsAbsentFromBothSurfaces() {
        val acknowledged = FounderMilestoneAcknowledgements(temporaryProUnlocked = true)
        assertFalse(temporaryProMilestonePending(FounderProgramStatus.ActivePro, acknowledged))
        val journey = founderJourney(
            status = FounderProgramStatus.ActivePro,
            qualification = app.mymusclemap.domain.entitlement.FounderProgramRules.Production.qualify(
                workouts = emptyList(),
                feedbackRecorded = false,
                testerAnalyticsReportSubmitted = false
            ),
            acknowledgements = acknowledged
        )
        assertEquals(null, journey.milestone)
    }

    @Test
    fun expiredFounderProDropsTheMembershipMark() {
        val expired = EntitlementResolver.resolve(
            EntitlementSources.of(
                backendFounder = BackendFounderEntitlement(
                    founderRecognized = true,
                    founderGrantedAt = earlier,
                    founderProExpiresAt = now,
                    validUntil = later
                )
            ),
            now
        )
        assertTrue(expired.founderRecognized)
        assertFalse(expired.grantsPro)
        assertFalse(expired.founderLifetime)
        assertEquals(
            MembershipPresentation.None,
            membershipPresentation(expired, FounderProgramStatus.Approved)
        )
    }

    private fun present(
        status: FounderProgramStatus,
        subscriptionUntil: Instant? = null
    ): MembershipPresentation {
        val temporary = status == FounderProgramStatus.ActivePro || status == FounderProgramStatus.PendingApproval
        val recognized = status == FounderProgramStatus.Approved
        val entitlement = EntitlementResolver.resolve(
            EntitlementSources.of(
                subscription = SubscriptionEntitlement(paidUntilInclusive = subscriptionUntil),
                program = FounderProgramState(status = status),
                backendFounder = BackendFounderEntitlement(
                    temporaryFounderPro = temporary,
                    founderRecognized = recognized,
                    founderGrantedAt = if (recognized) now else null,
                    founderProExpiresAt = if (recognized) later else null,
                    validUntil = if (temporary || recognized) later else null
                )
            ),
            now
        )
        return membershipPresentation(entitlement, status)
    }
}
