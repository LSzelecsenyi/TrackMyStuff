package app.mymusclemap.domain.entitlement

import java.time.Instant

/**
 * Isolated development entitlement. The shipping app has no Billing client yet, and
 * unfinished Pro features must stay usable. Feature code must not branch on build type;
 * only the composition root should reference this provider.
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
