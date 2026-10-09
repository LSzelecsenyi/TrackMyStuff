package app.mymusclemap.data.billing

import android.app.Activity
import app.mymusclemap.domain.billing.BillingAvailability
import app.mymusclemap.domain.billing.SubscriptionOffer

/**
 * Debug-only billing preview. It never contacts Google Play or the backend and
 * never writes [VerifiedSubscriptionStore].
 */
class FakeBillingGateway(
    private val mode: String
) : BillingGateway {
    override val testOnly: Boolean = true

    override fun bind(activity: Activity?) = Unit

    override fun setPurchaseListener(listener: (List<ClientPurchase>, LaunchOutcome?) -> Unit) = Unit

    override suspend fun refresh(): BillingSnapshot {
        val offer = SubscriptionOffer(
            productId = "test.strict.pro",
            basePlanId = "test-base",
            offerToken = "test-offer",
            title = "TEST Strict Pro",
            formattedPrice = "TEST 4.99",
            billingPeriod = "P1M"
        )
        return when (mode) {
            "LOADING" -> BillingSnapshot(BillingAvailability.LOADING)
            "UNAVAILABLE" -> BillingSnapshot(BillingAvailability.UNAVAILABLE)
            "PENDING" -> BillingSnapshot(BillingAvailability.READY, listOf(offer), pending = true)
            "CANCELED" -> BillingSnapshot(BillingAvailability.READY, listOf(offer), canceled = true)
            "VERIFY_FAIL" -> BillingSnapshot(
                BillingAvailability.READY,
                listOf(offer),
                verificationFailed = true
            )
            "ACTIVE" -> BillingSnapshot(BillingAvailability.READY, listOf(offer), testActive = true)
            "EXPIRED" -> BillingSnapshot(BillingAvailability.READY, listOf(offer), testExpired = true)
            else -> BillingSnapshot(BillingAvailability.READY, listOf(offer))
        }
    }

    override suspend fun launch(activity: Activity, productId: String, offerToken: String): LaunchOutcome {
        return when (mode) {
            "CANCELED" -> LaunchOutcome.CANCELED
            "UNAVAILABLE", "LOADING" -> LaunchOutcome.UNAVAILABLE
            "VERIFY_FAIL" -> LaunchOutcome.FAILED
            else -> LaunchOutcome.STARTED
        }
    }
}
