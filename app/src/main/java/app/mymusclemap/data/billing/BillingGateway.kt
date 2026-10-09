package app.mymusclemap.data.billing

import android.app.Activity
import app.mymusclemap.domain.billing.BillingAvailability
import app.mymusclemap.domain.billing.SubscriptionOffer

enum class LaunchOutcome {
    STARTED,
    CANCELED,
    FAILED,
    UNAVAILABLE
}

data class ClientPurchase(
    val token: String,
    val productId: String,
    val pending: Boolean
)

data class BillingSnapshot(
    val availability: BillingAvailability,
    val offers: List<SubscriptionOffer> = emptyList(),
    val purchases: List<ClientPurchase> = emptyList(),
    val pending: Boolean = false,
    val canceled: Boolean = false,
    val verificationFailed: Boolean = false,
    val testActive: Boolean = false,
    val testExpired: Boolean = false
)

/**
 * Play Billing boundary. Implementations must not grant Pro.
 * [testOnly] gateways never talk to Google Play or the production backend.
 */
interface BillingGateway {
    val testOnly: Boolean

    fun bind(activity: Activity?)

    suspend fun refresh(): BillingSnapshot

    suspend fun launch(activity: Activity, productId: String, offerToken: String): LaunchOutcome

    fun setPurchaseListener(listener: (List<ClientPurchase>, LaunchOutcome?) -> Unit)
}
