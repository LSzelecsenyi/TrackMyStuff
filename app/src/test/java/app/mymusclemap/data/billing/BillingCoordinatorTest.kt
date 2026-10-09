package app.mymusclemap.data.billing

import android.app.Activity
import app.mymusclemap.domain.billing.BillingAvailability
import app.mymusclemap.domain.billing.SubscriptionOffer
import app.mymusclemap.domain.billing.VerifiedPaidSubscription
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BillingCoordinatorTest {
    private val offer = SubscriptionOffer("strict_pro", "monthly", "offer-1", "Strict Pro", "€4.99", "P1M")
    private val store = MemoryStore()
    private val gateway = ScriptedGateway()

    @Test
    fun productsLoadWithTheLocalizedPrice() = runBlocking {
        gateway.snapshot = BillingSnapshot(BillingAvailability.READY, listOf(offer))
        val coordinator = coordinator { _, _ -> BillingVerification.Failed }
        coordinator.refresh()
        val screen = coordinator.screen.value
        assertEquals("€4.99", screen.offers.single().formattedPrice)
        assertEquals("offer-1", screen.selectedOfferToken)
        assertNull(store.saved)
    }

    @Test
    fun noProductsAndUnavailableStayEmpty() = runBlocking {
        val coordinator = coordinator { _, _ -> BillingVerification.Failed }
        gateway.snapshot = BillingSnapshot(BillingAvailability.NOT_CONFIGURED)
        coordinator.refresh()
        assertTrue(coordinator.screen.value.offers.isEmpty())
        gateway.snapshot = BillingSnapshot(BillingAvailability.UNAVAILABLE)
        coordinator.refresh()
        assertEquals(BillingAvailability.UNAVAILABLE, coordinator.screen.value.availability)
    }

    @Test
    fun aVerifiedPurchaseIsStoredOnceAcrossDuplicateCallbacks() = runBlocking {
        val verified = VerifiedPaidSubscription("strict_pro", Instant.parse("2026-12-01T00:00:00Z"), true, "ACTIVE", true)
        val coordinator = coordinator { _, _ -> BillingVerification.Verified(verified) }
        val purchase = ClientPurchase("token", "strict_pro", pending = false)
        coordinator.onUpdate(listOf(purchase), null)
        coordinator.onUpdate(listOf(purchase), null)
        assertEquals(verified, store.saved)
        assertEquals(2, store.saves)
        assertTrue(store.current().isValid(Instant.parse("2026-11-01T00:00:00Z")))
    }

    @Test
    fun pendingCanceledAndFailedPurchasesDoNotStorePro() = runBlocking {
        val coordinator = coordinator { _, _ -> BillingVerification.Failed }
        coordinator.onUpdate(listOf(ClientPurchase("token", "strict_pro", pending = true)), null)
        assertTrue(coordinator.screen.value.pending)
        assertNull(store.saved)
        coordinator.onUpdate(emptyList(), LaunchOutcome.CANCELED)
        assertTrue(coordinator.screen.value.canceled)
        assertNull(store.saved)
        coordinator.onUpdate(listOf(ClientPurchase("token", "strict_pro", pending = false)), null)
        assertTrue(coordinator.screen.value.verificationFailed)
        assertNull(store.saved)
    }

    @Test
    fun aTestGatewayCannotPersistAPurchase() = runBlocking {
        gateway.testOnly = true
        gateway.snapshot = BillingSnapshot(BillingAvailability.READY, listOf(offer), testActive = true)
        val coordinator = coordinator { _, _ -> error("verifier must not run") }
        coordinator.refresh()
        coordinator.onUpdate(listOf(ClientPurchase("token", "strict_pro", false)), null)
        assertTrue(coordinator.screen.value.testOnly)
        assertTrue(coordinator.screen.value.testActive)
        assertNull(store.saved)
        assertFalse(store.current().isValid(Instant.now()))
    }

    @Test
    fun anOwnershipConflictDoesNotStorePro() = runBlocking {
        val coordinator = coordinator { _, _ -> BillingVerification.OwnershipConflict }
        coordinator.onUpdate(listOf(ClientPurchase("token", "strict_pro", pending = false)), null)
        assertTrue(coordinator.screen.value.ownershipConflict)
        assertFalse(coordinator.screen.value.verificationFailed)
        assertNull(store.saved)
    }

    private fun coordinator(
        verifier: suspend (String, String) -> BillingVerification
    ) = BillingCoordinator(gateway, verifier, store)

    private class MemoryStore : VerifiedSubscriptionCache {
        var saved: VerifiedPaidSubscription? = null
        var saves = 0
        override fun current(): SubscriptionEntitlement =
            app.mymusclemap.domain.billing.paidSubscriptionEntitlement(saved)
        override fun snapshot(): VerifiedPaidSubscription? = saved
        override suspend fun load() = Unit
        override suspend fun save(subscription: VerifiedPaidSubscription) {
            saves += 1
            saved = subscription
        }
        override suspend fun clear() {
            saved = null
        }
    }

    private class ScriptedGateway : BillingGateway {
        override var testOnly: Boolean = false
        var snapshot = BillingSnapshot(BillingAvailability.NOT_CONFIGURED)
        var launch = LaunchOutcome.STARTED
        override fun bind(activity: Activity?) = Unit
        override suspend fun refresh(): BillingSnapshot = snapshot
        override suspend fun launch(activity: Activity, productId: String, offerToken: String) = launch
        override fun setPurchaseListener(listener: (List<ClientPurchase>, LaunchOutcome?) -> Unit) = Unit
    }
}
