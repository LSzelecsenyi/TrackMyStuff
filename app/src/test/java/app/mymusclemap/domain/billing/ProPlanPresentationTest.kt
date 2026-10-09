package app.mymusclemap.domain.billing

import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProPlanPresentationTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val offer = SubscriptionOffer(
        productId = "strict_pro",
        basePlanId = "monthly",
        offerToken = "token-a",
        title = "Strict Pro",
        formattedPrice = "€4.99",
        billingPeriod = "P1M"
    )

    @Test
    fun readyBillingShowsThePlayPriceAndSelectedOffer() {
        val shown = proPlanPresentation(
            founderProActive = false,
            founderLifetime = false,
            founderExpiresAt = null,
            trialExpiresAt = now.plusSeconds(3_600),
            paid = null,
            billing = BillingScreenState(
                availability = BillingAvailability.READY,
                offers = listOf(offer),
                selectedOfferToken = "missing"
            ),
            now = now
        ) as ProPlanPresentation.Offers
        assertEquals("€4.99", shown.offers.single().formattedPrice)
        assertEquals("token-a", shown.selectedOfferToken)
        assertEquals(now.plusSeconds(3_600), shown.trialExpiresAt)
    }

    @Test
    fun unavailableAndEmptyConfigurationDoNotInventOffers() {
        assertEquals(
            ProPlanPresentation.Unavailable,
            proPlanPresentation(false, false, null, null, null, BillingScreenState(BillingAvailability.UNAVAILABLE), now)
        )
        assertEquals(
            ProPlanPresentation.NotConfigured,
            proPlanPresentation(false, false, null, null, null, BillingScreenState(), now)
        )
    }

    @Test
    fun activeFounderProHidesPurchaseOffers() {
        val shown = proPlanPresentation(
            founderProActive = true,
            founderLifetime = false,
            founderExpiresAt = now.plusSeconds(86_400),
            trialExpiresAt = null,
            paid = null,
            billing = BillingScreenState(BillingAvailability.READY, offers = listOf(offer)),
            now = now
        )
        assertTrue(shown is ProPlanPresentation.Founder)
    }

    @Test
    fun aVerifiedPaidSubscriptionIsManagedUntilItsInstant() {
        val paid = VerifiedPaidSubscription("strict_pro", now, true, "ACTIVE", true)
        val shown = proPlanPresentation(false, false, null, null, paid, BillingScreenState(), now)
        assertTrue(shown is ProPlanPresentation.Paid)
        assertFalse(paidSubscriptionEntitlement(paid).isValid(now))
        assertTrue(paidSubscriptionEntitlement(paid).isValid(now.minusMillis(1)))
    }

    @Test
    fun testActivePreviewIsMarkedAndDoesNotLookLikeARealGrant() {
        val shown = proPlanPresentation(
            founderProActive = false,
            founderLifetime = false,
            founderExpiresAt = null,
            trialExpiresAt = null,
            paid = null,
            billing = BillingScreenState(testOnly = true, testActive = true),
            now = now
        ) as ProPlanPresentation.Paid
        assertTrue(shown.testOnly)
        assertEquals(SubscriptionEntitlement(), paidSubscriptionEntitlement(null))
    }
}
