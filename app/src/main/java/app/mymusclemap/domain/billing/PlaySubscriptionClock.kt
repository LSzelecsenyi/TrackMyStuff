package app.mymusclemap.domain.billing

import java.time.Instant

/**
 * The cached Play expiry is a clock boundary, like a trial. A past instant is not armed.
 * Entitlement still ends by comparing the clock with that instant; this does not grant Pro.
 */
object PlaySubscriptionClock {
    fun expiryBoundary(subscription: VerifiedPaidSubscription?): Instant? {
        if (subscription == null || !subscription.entitled) {
            return null
        }
        return subscription.expiresAt
    }

    fun nextBoundary(now: Instant, playExpiresAt: Instant?, otherBoundaries: List<Instant>): Instant? {
        return (otherBoundaries + listOfNotNull(playExpiresAt))
            .filter { now.isBefore(it) }
            .minOrNull()
    }

    fun expiryDue(now: Instant, playExpiresAt: Instant?): Boolean {
        return playExpiresAt != null && !now.isBefore(playExpiresAt)
    }
}
