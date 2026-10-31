package app.mymusclemap.domain

import app.mymusclemap.domain.billing.PlaySubscriptionClock
import kotlinx.coroutines.CancellationException
import java.time.Instant

/**
 * Applies one wall-clock sample to entitlement.
 * Re-evaluation happens before Play is contacted, so a missing billing response
 * still drops a subscription whose cached expiry has passed. Other grants are untouched.
 */
suspend fun applyEntitlementClock(
    effect: WallClockMonitor.Effect,
    now: Instant,
    playExpiresAt: Instant?,
    refreshFounderProgram: suspend () -> Unit,
    reevaluateEntitlement: () -> Unit,
    refreshBilling: suspend () -> Unit,
    rearm: () -> Unit
) {
    if (effect.dayChanged) {
        refreshFounderProgram()
    }
    if (effect.dayChanged || effect.boundaryReached) {
        reevaluateEntitlement()
    }
    if (!effect.boundaryReached || !PlaySubscriptionClock.expiryDue(now, playExpiresAt)) {
        return
    }
    try {
        refreshBilling()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // The cached expiry was already re-evaluated.
    }
    rearm()
}
