package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * Isolated subscription stand-in for tests. The running app uses
 * [InactiveSubscriptionProvider] until Billing exists, so Founder Temporary Pro is
 * observable. Feature code must not branch on build type.
 *
 * A later store provider replaces this object. It is not a product id and not a
 * persisted Pro flag.
 */
object DevelopmentSubscriptionProvider : SubscriptionEntitlementProvider {
    val paidUntilInclusive: Instant = Instant.parse("9999-01-01T00:00:00Z")

    override fun current(): SubscriptionEntitlement {
        return SubscriptionEntitlement(paidUntilInclusive = paidUntilInclusive)
    }
}
