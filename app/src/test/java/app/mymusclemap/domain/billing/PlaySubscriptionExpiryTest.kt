package app.mymusclemap.domain.billing

import app.mymusclemap.domain.WallClockMonitor
import app.mymusclemap.domain.applyEntitlementClock
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.FounderLifetimeEntitlement
import app.mymusclemap.domain.entitlement.PromotionalProEntitlement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PlaySubscriptionExpiryTest {
    private val opened = Instant.parse("2026-06-01T12:00:00Z")
    private val expiry = opened.plusSeconds(300)
    private val renewedUntil = expiry.plusSeconds(300)

    @Test
    fun playProExpiresWhileTheAppStaysOpen() = runBlocking {
        val session = openSession(expiresAt = expiry)
        session.sample(opened)
        assertTrue(session.resolve(opened.plusSeconds(60)).grantsPro)
        assertEquals(0, session.refreshes)

        session.sample(expiry)
        assertFalse(session.resolve(expiry).grantsPro)
        assertFalse(session.resolve(expiry).subscriptionValid)
        assertEquals(1, session.refreshes)
        assertEquals(1, session.revision)
        assertEquals(expiry, session.subscription.expiresAt)
        assertEquals(0, session.clears)
    }

    @Test
    fun anotherValidEntitlementRemainsProAfterPlayExpires() = runBlocking {
        val session = openSession(expiresAt = expiry, founderLifetime = true)
        session.sample(opened)
        session.sample(expiry)
        val resolved = session.resolve(expiry)
        assertTrue(resolved.grantsPro)
        assertFalse(resolved.subscriptionValid)
        assertTrue(resolved.founderLifetime)
    }

    @Test
    fun aRenewedPurchaseStaysProWithTheUpdatedExpiry() = runBlocking {
        val session = openSession(expiresAt = expiry)
        session.onRefresh = {
            session.subscription = paid(renewedUntil)
            session.revision += 1
        }
        session.sample(opened)
        session.sample(expiry)
        val duringRenewal = session.resolve(expiry.plusSeconds(30))
        assertTrue(duringRenewal.grantsPro)
        assertTrue(duringRenewal.subscriptionValid)
        assertEquals(renewedUntil, session.subscription.expiresAt)
        assertEquals(1, session.refreshes)

        session.sample(expiry.plusSeconds(60))
        assertEquals(1, session.refreshes)
        session.sample(renewedUntil)
        assertFalse(session.resolve(renewedUntil).subscriptionValid)
        assertEquals(2, session.refreshes)
    }

    @Test
    fun anEmptyPlayResultAfterExpiryLeavesTheCacheAndEndsPlayPro() = runBlocking {
        val session = openSession(expiresAt = expiry)
        session.sample(opened)
        session.sample(expiry)
        assertEquals(paid(expiry), session.subscription)
        assertEquals(0, session.clears)
        assertFalse(session.resolve(expiry).grantsPro)
    }

    @Test
    fun aFutureExpiryDoesNotDowngradeOrRefresh() = runBlocking {
        val session = openSession(expiresAt = expiry)
        session.sample(opened)
        session.sample(opened.plusSeconds(60))
        assertTrue(session.resolve(opened.plusSeconds(60)).grantsPro)
        assertEquals(0, session.refreshes)
        assertEquals(0, session.revision)
        assertEquals(expiry, PlaySubscriptionClock.nextBoundary(opened, expiry, emptyList()))
        assertEquals(null, PlaySubscriptionClock.nextBoundary(expiry, expiry, emptyList()))
    }

    @Test
    fun theEntitlementRevisionUpdatesBeforeABillingResponse() = runBlocking {
        val session = openSession(expiresAt = expiry)
        session.onRefresh = { error("billing unavailable") }
        session.sample(opened)
        session.sample(expiry)
        assertEquals(1, session.revision)
        assertFalse(session.resolve(expiry).grantsPro)
        assertEquals(1, session.refreshes)
    }

    @Test
    fun aPromotionBoundaryDoesNotRefreshPlayBilling() = runBlocking {
        val trialEnd = opened.plusSeconds(30)
        val session = openSession(expiresAt = expiry, otherBoundaries = listOf(trialEnd))
        session.sample(opened)
        session.sample(trialEnd)
        assertEquals(1, session.revision)
        assertEquals(0, session.refreshes)
        assertTrue(session.resolve(trialEnd).subscriptionValid)
    }

    private fun paid(until: Instant) = VerifiedPaidSubscription("strict_pro", until, true, "ACTIVE", true)

    private fun openSession(
        expiresAt: Instant,
        founderLifetime: Boolean = false,
        otherBoundaries: List<Instant> = emptyList()
    ): Session {
        return Session(paid(expiresAt), founderLifetime, otherBoundaries)
    }

    private class Session(
        initial: VerifiedPaidSubscription,
        private val founderLifetime: Boolean,
        private val otherBoundaries: List<Instant>
    ) {
        var subscription: VerifiedPaidSubscription = initial
        var revision: Int = 0
        var refreshes: Int = 0
        var clears: Int = 0
        var onRefresh: () -> Unit = {}
        private val monitor = WallClockMonitor { now ->
            PlaySubscriptionClock.nextBoundary(
                now,
                PlaySubscriptionClock.expiryBoundary(subscription),
                otherBoundaries
            )
        }

        fun resolve(now: Instant) = EntitlementResolver.resolve(
            EntitlementSources.of(
                subscription = paidSubscriptionEntitlement(subscription),
                founderLifetime = FounderLifetimeEntitlement(active = founderLifetime),
                promotionalPro = PromotionalProEntitlement()
            ),
            now
        )

        fun sample(now: Instant) = runBlocking {
            applyEntitlementClock(
                effect = monitor.onSample(now, LocalDate.of(2026, 6, 1)),
                now = now,
                playExpiresAt = PlaySubscriptionClock.expiryBoundary(subscription),
                refreshFounderProgram = {},
                reevaluateEntitlement = { revision += 1 },
                refreshBilling = {
                    refreshes += 1
                    onRefresh()
                },
                rearm = { monitor.rearm(now) }
            )
        }
    }
}
