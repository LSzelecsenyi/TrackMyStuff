package app.mymusclemap.data.billing

import android.app.Activity
import app.mymusclemap.domain.billing.BillingScreenState

/**
 * Connects Play Billing to backend verification. Pro is recorded only after a
 * verified response. A test-only gateway cannot write the verified store.
 */
class BillingCoordinator(
    private val gateway: BillingGateway,
    private val verifier: suspend (token: String, productId: String) -> BillingVerification,
    private val store: VerifiedSubscriptionCache
) {
    private val screenState = kotlinx.coroutines.flow.MutableStateFlow(BillingScreenState())
    val screen = screenState
    private var selectedToken: String? = null

    fun selectOffer(offerToken: String) {
        selectedToken = offerToken
        screenState.value = screenState.value.copy(selectedOfferToken = offerToken)
    }

    suspend fun refresh() {
        val snapshot = gateway.refresh()
        publish(snapshot)
        if (!gateway.testOnly) {
            snapshot.purchases.forEach { onPurchase(it) }
        }
    }

    suspend fun launch(activity: Activity) {
        val state = screenState.value
        val offer = state.offers.firstOrNull { it.offerToken == state.selectedOfferToken } ?: return
        when (gateway.launch(activity, offer.productId, offer.offerToken)) {
            LaunchOutcome.CANCELED -> screenState.value = state.copy(canceled = true, verificationFailed = false)
            LaunchOutcome.FAILED, LaunchOutcome.UNAVAILABLE ->
                screenState.value = state.copy(verificationFailed = true, canceled = false)
            LaunchOutcome.STARTED -> Unit
        }
    }

    /**
     * Applies a Play purchase update. Pending and canceled updates change billing
     * UI only. A purchased token is verified before anything is stored.
     */
    suspend fun onUpdate(purchases: List<ClientPurchase>, outcome: LaunchOutcome?) {
        if (gateway.testOnly) {
            return
        }
        when (outcome) {
            LaunchOutcome.CANCELED -> screenState.value = screenState.value.copy(canceled = true, verificationFailed = false)
            LaunchOutcome.FAILED -> screenState.value = screenState.value.copy(verificationFailed = true, canceled = false)
            else -> Unit
        }
        purchases.forEach { onPurchase(it) }
    }

    private suspend fun onPurchase(purchase: ClientPurchase) {
        if (gateway.testOnly) {
            return
        }
        if (purchase.pending) {
            screenState.value = screenState.value.copy(pending = true, canceled = false, verificationFailed = false)
            return
        }
        when (val result = verifier(purchase.token, purchase.productId)) {
            is BillingVerification.Verified -> {
                store.save(result.subscription)
                screenState.value = screenState.value.copy(
                    pending = false,
                    verificationFailed = false,
                    ownershipConflict = false,
                    canceled = false
                )
            }
            BillingVerification.OwnershipConflict ->
                screenState.value = screenState.value.copy(
                    ownershipConflict = true,
                    verificationFailed = false,
                    pending = false
                )
            BillingVerification.Failed, BillingVerification.NotConfigured ->
                screenState.value = screenState.value.copy(verificationFailed = true, pending = false)
        }
    }

    private fun publish(snapshot: BillingSnapshot) {
        val selected = selectedToken?.takeIf { token -> snapshot.offers.any { it.offerToken == token } }
            ?: snapshot.offers.firstOrNull()?.offerToken
        selectedToken = selected
        screenState.value = BillingScreenState(
            availability = snapshot.availability,
            offers = snapshot.offers,
            selectedOfferToken = selected,
            pending = snapshot.pending,
            verificationFailed = snapshot.verificationFailed,
            ownershipConflict = false,
            canceled = snapshot.canceled,
            testOnly = gateway.testOnly,
            testActive = snapshot.testActive,
            testExpired = snapshot.testExpired
        )
    }
}
