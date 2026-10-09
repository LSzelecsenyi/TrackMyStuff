package app.mymusclemap.data.billing

import android.app.Activity
import android.content.Context
import app.mymusclemap.domain.billing.BillingAvailability
import app.mymusclemap.domain.billing.SubscriptionOffer
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing Library client. A purchase result is forwarded for server
 * verification. This class does not write entitlement state.
 */
class PlayBillingGateway(
    context: Context,
    private val productIds: List<String>
) : BillingGateway {
    override val testOnly: Boolean = false

    private val appContext = context.applicationContext
    private val details = linkedMapOf<String, ProductDetails>()
    private var listener: (List<ClientPurchase>, LaunchOutcome?) -> Unit = { _, _ -> }
    private var activity: Activity? = null
    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK ->
                    listener(purchases.orEmpty().mapNotNull(::toClientPurchase), null)
                BillingClient.BillingResponseCode.USER_CANCELED ->
                    listener(emptyList(), LaunchOutcome.CANCELED)
                else -> listener(emptyList(), LaunchOutcome.FAILED)
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    override fun bind(activity: Activity?) {
        this.activity = activity
    }

    override fun setPurchaseListener(listener: (List<ClientPurchase>, LaunchOutcome?) -> Unit) {
        this.listener = listener
    }

    override suspend fun refresh(): BillingSnapshot {
        if (productIds.isEmpty()) {
            return BillingSnapshot(BillingAvailability.NOT_CONFIGURED)
        }
        if (!connect()) {
            return BillingSnapshot(BillingAvailability.UNAVAILABLE)
        }
        val offers = queryOffers()
        val purchases = queryPurchases()
        return BillingSnapshot(
            availability = if (offers.isEmpty()) BillingAvailability.UNAVAILABLE else BillingAvailability.READY,
            offers = offers,
            purchases = purchases
        )
    }

    override suspend fun launch(activity: Activity, productId: String, offerToken: String): LaunchOutcome {
        if (!connect()) {
            return LaunchOutcome.UNAVAILABLE
        }
        val product = details[productId] ?: return LaunchOutcome.UNAVAILABLE
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> LaunchOutcome.STARTED
            BillingClient.BillingResponseCode.USER_CANCELED -> LaunchOutcome.CANCELED
            else -> LaunchOutcome.FAILED
        }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) {
            return true
        }
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) {
                        continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                    }
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    private suspend fun queryOffers(): List<SubscriptionOffer> {
        val products = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        val queried = suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                if (!continuation.isActive) {
                    return@queryProductDetailsAsync
                }
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    continuation.resume(emptyList())
                } else {
                    continuation.resume(queryResult.productDetailsList)
                }
            }
        }
        details.clear()
        queried.forEach { details[it.productId] = it }
        return queried.flatMap { product ->
            product.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
                val phase = offer.pricingPhases.pricingPhaseList.firstOrNull() ?: return@mapNotNull null
                SubscriptionOffer(
                    productId = product.productId,
                    basePlanId = offer.basePlanId,
                    offerToken = offer.offerToken,
                    title = product.title,
                    formattedPrice = phase.formattedPrice,
                    billingPeriod = phase.billingPeriod
                )
            }
        }
    }

    private suspend fun queryPurchases(): List<ClientPurchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val purchases = suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(params) { result, owned ->
                if (!continuation.isActive) {
                    return@queryPurchasesAsync
                }
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    continuation.resume(emptyList())
                } else {
                    continuation.resume(owned)
                }
            }
        }
        return purchases.mapNotNull(::toClientPurchase)
    }

    private fun toClientPurchase(purchase: Purchase): ClientPurchase? {
        val productId = purchase.products.firstOrNull() ?: return null
        val pending = purchase.purchaseState == Purchase.PurchaseState.PENDING
        val purchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        if (!pending && !purchased) {
            return null
        }
        return ClientPurchase(token = purchase.purchaseToken, productId = productId, pending = pending)
    }
}
