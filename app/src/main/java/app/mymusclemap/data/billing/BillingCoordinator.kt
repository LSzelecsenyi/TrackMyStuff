package app.mymusclemap.data.billing

import android.app.Activity
import app.mymusclemap.domain.billing.BillingScreenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Connects Play Billing to backend verification. Pro is recorded only after a
 * verified response. A test-only gateway cannot write the verified store.
 * An empty Play purchase list does not clear a cached subscription. While that
 * cache is still valid, the linked backend record is re-read at most once a minute.
 */
class BillingCoordinator(
    private val gateway: BillingGateway,
    private val verifier: suspend (token: String, productId: String) -> BillingVerification,
    private val store: VerifiedSubscriptionCache,
    private val current: suspend () -> BillingVerification = { BillingVerification.Absent },
    private val clock: Clock = Clock.systemUTC()
) {
    private val screenState = kotlinx.coroutines.flow.MutableStateFlow(BillingScreenState())
    val screen = screenState
    private var selectedToken: String? = null
    private val gate = Mutex()
    private var playReturnedPurchase = true
    private var lastPlayQueryMinute: Instant? = null
    private var lastConfirmMinute: Instant? = null

    fun selectOffer(offerToken: String) {
        selectedToken = offerToken
        screenState.value = screenState.value.copy(selectedOfferToken = offerToken)
    }

    suspend fun refresh() {
        gate.withLock {
            refreshLocked(clock.instant(), verifyPurchases = true)
        }
    }

    /**
     * Foreground minute sample. The first Play query of a session stays with [refresh].
     * Later minutes look again while cached Play Pro is still valid. A missing purchase
     * re-reads the backend once per minute. A failure does not call it again until the next minute.
     */
    suspend fun onForegroundSample(now: Instant) {
        if (!cachedPlayStillValid(now)) {
            return
        }
        if (!gate.tryLock()) {
            return
        }
        try {
            val minute = now.truncatedTo(ChronoUnit.MINUTES)
            if (playReturnedPurchase) {
                if (lastPlayQueryMinute == minute) {
                    return
                }
                refreshLocked(now, verifyPurchases = false)
            } else {
                confirmLinked(now)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next minute may look again. This sample does not retry.
        } finally {
            gate.unlock()
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
            BillingVerification.Absent -> Unit
        }
    }

    private suspend fun refreshLocked(now: Instant, verifyPurchases: Boolean) {
        lastPlayQueryMinute = now.truncatedTo(ChronoUnit.MINUTES)
        val snapshot = gateway.refresh()
        if (verifyPurchases) {
            publish(snapshot)
        }
        if (gateway.testOnly) {
            return
        }
        val active = snapshot.purchases.filter { !it.pending }
        playReturnedPurchase = active.isNotEmpty()
        if (verifyPurchases) {
            active.forEach { onPurchase(it) }
        }
        if (active.isEmpty()) {
            confirmLinked(now)
        }
    }

    private suspend fun confirmLinked(now: Instant) {
        if (!cachedPlayStillValid(now)) {
            return
        }
        val minute = now.truncatedTo(ChronoUnit.MINUTES)
        if (lastConfirmMinute == minute) {
            return
        }
        lastConfirmMinute = minute
        val result = try {
            current()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        if (result is BillingVerification.Verified) {
            store.save(result.subscription)
        }
    }

    private fun cachedPlayStillValid(now: Instant): Boolean {
        val cached = store.snapshot() ?: return false
        val expiry = cached.expiresAt ?: return false
        return cached.entitled && now.isBefore(expiry)
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
