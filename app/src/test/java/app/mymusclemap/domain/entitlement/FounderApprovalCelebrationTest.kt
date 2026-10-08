package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FounderApprovalCelebrationTest {
    private val userId = "account-a"
    private val now = Instant.parse("2026-10-08T16:00:00Z")
    private val expiresAt = Instant.parse("2027-06-01T12:00:00Z")

    @Test
    fun firstBackendConfirmedApprovalShowsTheCelebration() {
        val celebration = pending(
            backendOwned = true,
            status = FounderProgramStatus.Approved,
            founderProExpiresAt = expiresAt
        )
        assertEquals(expiresAt, celebration?.proExpiresAt)
    }

    @Test
    fun acknowledgementPreventsRepeatedDisplay() {
        assertNull(
            pending(
                backendOwned = true,
                status = FounderProgramStatus.Approved,
                founderProExpiresAt = expiresAt,
                acknowledged = true
            )
        )
    }

    @Test
    fun temporaryProDoesNotTriggerIt() {
        assertNull(
            pending(
                backendOwned = true,
                status = FounderProgramStatus.ActivePro,
                founderProExpiresAt = expiresAt
            )
        )
    }

    @Test
    fun anUnapprovedApplicationDoesNotTriggerIt() {
        assertNull(
            pending(
                backendOwned = true,
                status = FounderProgramStatus.PendingApproval,
                founderProExpiresAt = expiresAt
            )
        )
    }

    @Test
    fun debugFounderOverrideDoesNotTriggerIt() {
        assertNull(
            pending(
                backendOwned = false,
                status = FounderProgramStatus.NotEnrolled,
                founderProExpiresAt = Instant.parse("9999-12-31T00:00:00Z")
            )
        )
    }

    @Test
    fun approvalDiscoveredOnRefreshShowsTheCelebration() {
        val celebration = pending(
            backendOwned = true,
            status = FounderProgramStatus.Approved,
            founderProExpiresAt = expiresAt,
            acknowledged = false
        )
        assertEquals(expiresAt, celebration?.proExpiresAt)
    }

    @Test
    fun expiredFounderProDoesNotOfferAnActiveReward() {
        assertNull(
            pending(
                backendOwned = true,
                status = FounderProgramStatus.Approved,
                founderProExpiresAt = now
            )
        )
        assertNull(
            pending(
                backendOwned = true,
                status = FounderProgramStatus.Approved,
                founderProExpiresAt = now.minusSeconds(1)
            )
        )
    }

    @Test
    fun aLegacyLifetimeGrantWithoutFounderProDoesNotCelebrate() {
        assertNull(
            pending(
                backendOwned = true,
                status = FounderProgramStatus.Approved,
                founderLifetime = true,
                founderProExpiresAt = null
            )
        )
        assertNull(
            pending(
                backendOwned = true,
                status = FounderProgramStatus.Approved,
                founderLifetime = true,
                founderProExpiresAt = expiresAt
            )
        )
    }

    @Test
    fun aSignedOutSessionDoesNotCelebrate() {
        assertNull(
            pending(
                userId = null,
                backendOwned = true,
                status = FounderProgramStatus.Approved,
                founderProExpiresAt = expiresAt
            )
        )
    }

    private fun pending(
        userId: String? = this.userId,
        backendOwned: Boolean,
        status: FounderProgramStatus,
        founderLifetime: Boolean = false,
        founderProExpiresAt: Instant? = null,
        acknowledged: Boolean = false
    ) = pendingFounderApprovalCelebration(
        userId = userId,
        backendOwned = backendOwned,
        status = status,
        founderLifetime = founderLifetime,
        founderProExpiresAt = founderProExpiresAt,
        now = now,
        acknowledged = acknowledged
    )
}
