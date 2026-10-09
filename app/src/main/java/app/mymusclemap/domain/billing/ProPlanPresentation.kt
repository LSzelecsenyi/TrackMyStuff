package app.mymusclemap.domain.billing

import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import java.time.Instant

enum class BillingAvailability {
    LOADING,
    READY,
    UNAVAILABLE,
    NOT_CONFIGURED
}

data class SubscriptionOffer(
    val productId: String,
    val basePlanId: String,
    val offerToken: String,
    val title: String,
    val formattedPrice: String,
    val billingPeriod: String
)

data class VerifiedPaidSubscription(
    val productId: String,
    val expiresAt: Instant?,
    val autoRenewing: Boolean,
    val state: String,
    val entitled: Boolean
)

/** Play Billing screen state. This is not an entitlement and does not grant Pro. */
data class BillingScreenState(
    val availability: BillingAvailability = BillingAvailability.NOT_CONFIGURED,
    val offers: List<SubscriptionOffer> = emptyList(),
    val selectedOfferToken: String? = null,
    val pending: Boolean = false,
    val canceled: Boolean = false,
    val verificationFailed: Boolean = false,
    val ownershipConflict: Boolean = false,
    val testOnly: Boolean = false,
    val testActive: Boolean = false,
    val testExpired: Boolean = false
)

sealed interface ProPlanPresentation {
    data object Loading : ProPlanPresentation
    data object Unavailable : ProPlanPresentation
    data object NotConfigured : ProPlanPresentation

    data class Offers(
        val offers: List<SubscriptionOffer>,
        val selectedOfferToken: String?,
        val trialExpiresAt: Instant?,
        val pending: Boolean,
        val canceled: Boolean,
        val verificationFailed: Boolean,
        val ownershipConflict: Boolean = false,
        val testOnly: Boolean,
        val expiredPreview: Boolean = false
    ) : ProPlanPresentation

    data class Paid(
        val productId: String,
        val expiresAt: Instant?,
        val renews: Boolean,
        val testOnly: Boolean
    ) : ProPlanPresentation

    data class Founder(
        val expiresAt: Instant?,
        val lifetime: Boolean
    ) : ProPlanPresentation
}

fun paidSubscriptionEntitlement(subscription: VerifiedPaidSubscription?): SubscriptionEntitlement {
    if (subscription == null || !subscription.entitled) {
        return SubscriptionEntitlement()
    }
    val until = subscription.expiresAt ?: return SubscriptionEntitlement()
    return SubscriptionEntitlement(
        paidUntilInclusive = until,
        renewalCancelled = !subscription.autoRenewing
    )
}

fun proPlanPresentation(
    founderProActive: Boolean,
    founderLifetime: Boolean,
    founderExpiresAt: Instant?,
    trialExpiresAt: Instant?,
    paid: VerifiedPaidSubscription?,
    billing: BillingScreenState,
    now: Instant
): ProPlanPresentation {
    val paidStillValid = paid != null &&
        paid.entitled &&
        paid.expiresAt != null &&
        !now.isAfter(paid.expiresAt)
    if (paidStillValid) {
        return ProPlanPresentation.Paid(
            productId = paid.productId,
            expiresAt = paid.expiresAt,
            renews = paid.autoRenewing,
            testOnly = false
        )
    }
    if (billing.testOnly && billing.testActive) {
        return ProPlanPresentation.Paid(
            productId = billing.offers.firstOrNull()?.productId ?: "test.strict.pro",
            expiresAt = now.plusSeconds(3600),
            renews = true,
            testOnly = true
        )
    }
    if (founderLifetime || founderProActive) {
        return ProPlanPresentation.Founder(
            expiresAt = if (founderLifetime) null else founderExpiresAt,
            lifetime = founderLifetime
        )
    }
    if (billing.testOnly && billing.testExpired) {
        return offers(billing, trialExpiresAt = null).let { shown ->
            shown.copy(expiredPreview = true)
        }
    }
    return when (billing.availability) {
        BillingAvailability.LOADING -> ProPlanPresentation.Loading
        BillingAvailability.UNAVAILABLE -> ProPlanPresentation.Unavailable
        BillingAvailability.NOT_CONFIGURED -> ProPlanPresentation.NotConfigured
        BillingAvailability.READY -> offers(billing, trialExpiresAt)
    }
}

private fun offers(billing: BillingScreenState, trialExpiresAt: Instant?): ProPlanPresentation.Offers {
    val selected = billing.selectedOfferToken?.takeIf { token ->
        billing.offers.any { it.offerToken == token }
    } ?: billing.offers.firstOrNull()?.offerToken
    return ProPlanPresentation.Offers(
        offers = billing.offers,
        selectedOfferToken = selected,
        trialExpiresAt = trialExpiresAt,
        pending = billing.pending,
        canceled = billing.canceled,
        verificationFailed = billing.verificationFailed,
        ownershipConflict = billing.ownershipConflict,
        testOnly = billing.testOnly
    )
}

fun manageSubscriptionUrl(packageName: String, productId: String): String {
    return "https://play.google.com/store/account/subscriptions?package=$packageName&sku=$productId"
}
