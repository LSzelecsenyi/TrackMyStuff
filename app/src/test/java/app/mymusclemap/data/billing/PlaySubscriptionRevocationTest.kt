package app.mymusclemap.data.billing

import android.app.Activity
import app.mymusclemap.domain.billing.BillingAvailability
import app.mymusclemap.domain.billing.SubscriptionOffer
import app.mymusclemap.domain.billing.VerifiedPaidSubscription
import app.mymusclemap.domain.billing.paidSubscriptionEntitlement
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.FounderLifetimeEntitlement
import app.mymusclemap.domain.entitlement.PromotionalProEntitlement
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PlaySubscriptionRevocationTest {
    private val now = Instant.parse("2026-06-01T12:00:30Z")
    private val expiry = Instant.parse("2026-06-01T12:05:00Z")
    private val renewedUntil = expiry.plusSeconds(300)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val offer = SubscriptionOffer("strict_pro", "monthly", "offer-1", "Strict Pro", "1490 Ft", "P1M")

    @Test
    fun anEmptyPlayListAndConfirmedRevocationEndsPlayPro() = runBlocking {
        val harness = harness(current = { revoked() })
        harness.store.save(active())
        harness.refresh()
        val resolved = harness.resolve(founder = false, discovery = null, welcome = null)
        assertFalse(resolved.grantsPro)
        assertFalse(resolved.subscriptionValid)
        assertEquals("EXPIRED", harness.store.saved?.state)
        assertEquals(0, harness.store.clears)
    }

    @Test
    fun confirmedRevocationUpdatesTheEntitlementRevision() = runBlocking {
        val harness = harness(current = { revoked() })
        harness.store.save(active())
        assertEquals(1, harness.store.revisions)
        harness.refresh()
        assertEquals(2, harness.store.revisions)
        assertFalse(harness.store.saved!!.entitled)
    }

    @Test
    fun founderStaysProAfterPlayRevocation() = runBlocking {
        val harness = harness(current = { revoked() })
        harness.store.save(active())
        harness.refresh()
        val resolved = harness.resolve(founder = true, discovery = null, welcome = null)
        assertTrue(resolved.grantsPro)
        assertTrue(resolved.founderLifetime)
        assertFalse(resolved.subscriptionValid)
    }

    @Test
    fun proDiscoveryStaysProAfterPlayRevocation() = runBlocking {
        val harness = harness(current = { revoked() })
        harness.store.save(active())
        harness.refresh()
        val resolved = harness.resolve(founder = false, discovery = expiry.plusSeconds(60), welcome = null)
        assertTrue(resolved.grantsPro)
        assertTrue(resolved.promotionalProActive)
        assertFalse(resolved.subscriptionValid)
    }

    @Test
    fun welcomeBackStaysProAfterPlayRevocation() = runBlocking {
        val harness = harness(current = { revoked() })
        harness.store.save(active())
        harness.refresh()
        val resolved = harness.resolve(founder = false, discovery = null, welcome = expiry.plusSeconds(60))
        assertTrue(resolved.grantsPro)
        assertTrue(resolved.welcomeBackActive)
        assertFalse(resolved.subscriptionValid)
    }

    @Test
    fun backendUnavailableKeepsCachedPlayProUntilExpiry() = runBlocking {
        val harness = harness(current = { BillingVerification.NotConfigured })
        harness.store.save(active())
        harness.refresh()
        assertEquals(active(), harness.store.saved)
        assertTrue(harness.resolve(false, null, null).grantsPro)
        assertTrue(paidSubscriptionEntitlement(harness.store.saved).isValid(now))
        assertFalse(paidSubscriptionEntitlement(harness.store.saved).isValid(expiry))
    }

    @Test
    fun aNetworkFailureKeepsCachedPlayProUntilExpiry() = runBlocking {
        val harness = harness(current = { throw IOException("offline") })
        harness.store.save(active())
        harness.refresh()
        assertEquals(active(), harness.store.saved)
        assertTrue(paidSubscriptionEntitlement(harness.store.saved).isValid(now))
        assertFalse(paidSubscriptionEntitlement(harness.store.saved).isValid(expiry))
        assertEquals(0, harness.store.clears)
    }

    @Test
    fun anAbsentBackendResultDoesNotRevokePro() = runBlocking {
        val harness = harness(current = { BillingVerification.Absent })
        harness.store.save(active())
        val revisions = harness.store.revisions
        harness.refresh()
        assertEquals(active(), harness.store.saved)
        assertEquals(revisions, harness.store.revisions)
        assertTrue(harness.resolve(false, null, null).grantsPro)
    }

    @Test
    fun anEmptyPlayListAloneDoesNotRevokePro() = runBlocking {
        var currentCalls = 0
        val harness = harness(current = {
            currentCalls += 1
            BillingVerification.Absent
        })
        harness.store.save(active())
        harness.gateway.snapshot = BillingSnapshot(BillingAvailability.READY, listOf(offer), purchases = emptyList())
        harness.refresh()
        assertEquals(1, currentCalls)
        assertEquals(active(), harness.store.saved)
        assertEquals(0, harness.store.clears)
        assertTrue(harness.resolve(false, null, null).grantsPro)
    }

    @Test
    fun aFutureExpiryWithoutConfirmedRevocationStaysPro() = runBlocking {
        val harness = harness(current = { BillingVerification.Failed })
        harness.store.save(active())
        harness.onForegroundSample(now.minusSeconds(20))
        assertEquals(active(), harness.store.saved)
        assertTrue(paidSubscriptionEntitlement(harness.store.saved).isValid(now.minusSeconds(20)))
    }

    @Test
    fun revocationChecksDoNotRunConcurrently() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val harness = harness(current = {
            calls += 1
            started.complete(Unit)
            release.await()
            BillingVerification.Failed
        })
        harness.store.save(active())
        val first = async { harness.refresh() }
        started.await()
        val second = async { harness.refresh() }
        yield()
        assertEquals(1, calls)
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, calls)
        assertEquals(active(), harness.store.saved)
    }

    @Test
    fun aFailedRefreshDoesNotRetryUntilTheNextMinute() = runBlocking {
        var calls = 0
        val harness = harness(current = {
            calls += 1
            BillingVerification.Failed
        })
        harness.store.save(active())
        harness.onForegroundSample(now)
        harness.onForegroundSample(now.plusSeconds(20))
        assertEquals(1, calls)
        harness.onForegroundSample(now.plusSeconds(60))
        assertEquals(2, calls)
        assertEquals(active(), harness.store.saved)
    }

    @Test
    fun cancellationKeepsProUntilExpiryAndDoesNotAskForTheLinkedRecord() = runBlocking {
        var currentCalls = 0
        val harness = harness(
            verifier = { _, _ ->
                BillingVerification.Verified(
                    VerifiedPaidSubscription("strict_pro", expiry, autoRenewing = false, state = "CANCELED", entitled = true)
                )
            },
            current = {
                currentCalls += 1
                error("linked record must stay unread")
            }
        )
        harness.gateway.snapshot = BillingSnapshot(
            BillingAvailability.READY,
            listOf(offer),
            purchases = listOf(ClientPurchase("token", "strict_pro", pending = false))
        )
        harness.refresh()
        assertEquals(0, currentCalls)
        assertEquals("CANCELED", harness.store.saved?.state)
        assertTrue(harness.store.saved!!.entitled)
        assertTrue(paidSubscriptionEntitlement(harness.store.saved).isValid(now))
        assertFalse(paidSubscriptionEntitlement(harness.store.saved).isValid(expiry))
    }

    @Test
    fun renewalStoresTheNewExpiryWithoutALinkedReread() = runBlocking {
        var currentCalls = 0
        val harness = harness(
            verifier = { _, _ ->
                BillingVerification.Verified(
                    VerifiedPaidSubscription("strict_pro", renewedUntil, autoRenewing = true, state = "ACTIVE", entitled = true)
                )
            },
            current = {
                currentCalls += 1
                error("linked record must stay unread")
            }
        )
        harness.gateway.snapshot = BillingSnapshot(
            BillingAvailability.READY,
            listOf(offer),
            purchases = listOf(ClientPurchase("token", "strict_pro", pending = false))
        )
        harness.refresh()
        assertEquals(0, currentCalls)
        assertEquals(renewedUntil, harness.store.saved?.expiresAt)
        assertTrue(harness.store.saved!!.entitled)
        assertTrue(paidSubscriptionEntitlement(harness.store.saved).isValid(expiry))
    }

    private fun active() = VerifiedPaidSubscription("strict_pro", expiry, autoRenewing = true, state = "ACTIVE", entitled = true)

    private fun revoked() = BillingVerification.Verified(
        VerifiedPaidSubscription("strict_pro", expiry, autoRenewing = false, state = "EXPIRED", entitled = false)
    )

    private fun harness(
        verifier: suspend (String, String) -> BillingVerification = { _, _ -> error("purchase verifier must not run") },
        current: suspend () -> BillingVerification
    ): Harness {
        val store = MemoryStore()
        val gateway = ScriptedGateway()
        val coordinator = BillingCoordinator(
            gateway = gateway,
            verifier = verifier,
            store = store,
            current = current,
            clock = clock
        )
        return Harness(coordinator, store, gateway)
    }

    private inner class Harness(
        private val coordinator: BillingCoordinator,
        val store: MemoryStore,
        val gateway: ScriptedGateway
    ) {
        suspend fun refresh() = coordinator.refresh()

        suspend fun onForegroundSample(instant: Instant) = coordinator.onForegroundSample(instant)

        fun resolve(founder: Boolean, discovery: Instant?, welcome: Instant?) = EntitlementResolver.resolve(
            EntitlementSources.of(
                subscription = paidSubscriptionEntitlement(store.saved),
                founderLifetime = FounderLifetimeEntitlement(active = founder),
                promotionalPro = PromotionalProEntitlement(expiresAt = discovery),
                welcomeBack = PromotionalProEntitlement(expiresAt = welcome)
            ),
            now
        )
    }

    private class MemoryStore : VerifiedSubscriptionCache {
        var saved: VerifiedPaidSubscription? = null
        var clears = 0
        var revisions = 0
        override fun current(): SubscriptionEntitlement = paidSubscriptionEntitlement(saved)
        override fun snapshot(): VerifiedPaidSubscription? = saved
        override suspend fun load() = Unit
        override suspend fun save(subscription: VerifiedPaidSubscription) {
            saved = subscription
            revisions += 1
        }
        override suspend fun clear() {
            clears += 1
            saved = null
            revisions += 1
        }
    }

    private class ScriptedGateway : BillingGateway {
        override var testOnly: Boolean = false
        var snapshot = BillingSnapshot(BillingAvailability.READY, purchases = emptyList())
        override fun bind(activity: Activity?) = Unit
        override suspend fun refresh(): BillingSnapshot = snapshot
        override suspend fun launch(activity: Activity, productId: String, offerToken: String) = LaunchOutcome.STARTED
        override fun setPurchaseListener(listener: (List<ClientPurchase>, LaunchOutcome?) -> Unit) = Unit
    }
}
