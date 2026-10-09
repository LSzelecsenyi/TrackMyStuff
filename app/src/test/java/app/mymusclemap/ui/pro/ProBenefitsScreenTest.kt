package app.mymusclemap.ui.pro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FeatureAccessPolicy
import app.mymusclemap.domain.entitlement.ProBenefitsStatus
import app.mymusclemap.domain.entitlement.ProDiscoverySnapshot
import app.mymusclemap.domain.entitlement.WorkoutPlanAccess
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.testString
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w320dp-h480dp")
class ProBenefitsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeAccessShowsTheExpirationWithoutAPurchaseAction() {
        val expiresAt = Instant.parse("2027-06-01T12:00:00Z")
        val expiresOn = UiFormatters.longDate(
            expiresAt.atZone(ZoneId.systemDefault()).toLocalDate()
        )
        var backs = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = true, expiresAt = expiresAt),
                    onBack = { backs += 1 }
                )
            }
        }
        composeRule.onNodeWithText(
            testString(R.string.pro_benefits_active_until, expiresOn)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.pro_benefits_cell_count, FeatureAccessPolicy.FREE_CUSTOM_EXERCISE_LIMIT)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.pro_benefits_cell_count, WorkoutPlanAccess.FREE_PLAN_LIMIT)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.pro_benefits_cell_days, StatisticsRange.DAYS_30)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_cell_all_time))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_cell_reports_pro))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_BENEFITS_TABLE).assertExists()
        composeRule.onNodeWithTag(PRO_BENEFITS_ACHIEVEMENTS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Renew").assertDoesNotExist()
        composeRule.onNodeWithText("Purchase").assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_BENEFITS_BACK).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun freeAccessExplainsTheDifferenceWithoutClaimingActivePro() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = false),
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_benefits_lead)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_active)).assertDoesNotExist()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_BENEFITS_ACHIEVEMENTS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_BENEFITS_SUBSCRIPTION).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun offerActiveWarningAndExpiredStatesStayOnTheBenefitsScreenWithoutAPurchase() {
        val expiresAt = Instant.parse("2026-08-22T15:00:00Z")
        var activations = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = true),
                    onBack = {},
                    discovery = ProDiscoverySnapshot(
                        offerAvailable = false,
                        trialActive = true,
                        trialExpiresAt = expiresAt,
                        warningOnBenefits = true
                    ),
                    onActivateTrial = { activations += 1 }
                )
            }
        }
        composeRule.onNodeWithTag(PRO_BENEFITS_TRIAL).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_BENEFITS_WARNING).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_plan_not_configured))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Purchase").assertDoesNotExist()
        composeRule.onNodeWithText("Buy Pro").assertDoesNotExist()
        assertEquals(0, activations)
    }

    @Test
    fun aDismissedOfferCanStillBeActivatedFromBenefits() {
        var activations = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(),
                    onBack = {},
                    discovery = ProDiscoverySnapshot(offerAvailable = true),
                    onActivateTrial = { activations += 1 }
                )
            }
        }
        composeRule.onNodeWithTag(PRO_BENEFITS_OFFER).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_DISCOVERY_ACTIVATE).performScrollTo().performClick()
        assertEquals(1, activations)
        composeRule.onNodeWithText(testString(R.string.pro_plan_not_configured))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun anExpiredTrialKeepsTheDataSafeMessageAndDoesNotOfferAnotherActivation() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = false),
                    onBack = {},
                    discovery = ProDiscoverySnapshot(trialExpired = true)
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_discovery_trial_expired))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_discovery_activate)).assertDoesNotExist()
        composeRule.onNodeWithText("Purchase").assertDoesNotExist()
    }

    @Test
    fun aLocalizedOfferCanBeSelectedAndSubscribed() {
        val offer = app.mymusclemap.domain.billing.SubscriptionOffer(
            productId = "strict_pro",
            basePlanId = "monthly",
            offerToken = "offer-1",
            title = "Strict Pro",
            formattedPrice = "€4.99",
            billingPeriod = "P1M"
        )
        var subscribed = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = false),
                    onBack = {},
                    plan = app.mymusclemap.domain.billing.ProPlanPresentation.Offers(
                        offers = listOf(offer),
                        selectedOfferToken = offer.offerToken,
                        trialExpiresAt = null,
                        pending = false,
                        canceled = false,
                        verificationFailed = false,
                        testOnly = false
                    ),
                    onSubscribe = { subscribed += 1 }
                )
            }
        }
        composeRule.onNodeWithText("€4.99").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Every month").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_BENEFITS_SUBSCRIBE).performScrollTo().performClick()
        assertEquals(1, subscribed)
    }

    @Test
    fun paidProOffersManagementWithoutAnotherPurchase() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = true),
                    onBack = {},
                    plan = app.mymusclemap.domain.billing.ProPlanPresentation.Paid(
                        productId = "strict_pro",
                        expiresAt = Instant.parse("2026-12-01T00:00:00Z"),
                        renews = true,
                        testOnly = false
                    )
                )
            }
        }
        composeRule.onNodeWithTag(PRO_BENEFITS_MANAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_plan_subscribe)).assertDoesNotExist()
    }

    @Test
    fun founderProDoesNotAskForAPurchase() {
        val expiresAt = Instant.parse("2027-01-01T00:00:00Z")
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = true),
                    onBack = {},
                    plan = app.mymusclemap.domain.billing.ProPlanPresentation.Founder(
                        expiresAt = expiresAt,
                        lifetime = false
                    )
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_plan_subscribe)).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_BENEFITS_MANAGE).assertDoesNotExist()
        composeRule.onNodeWithText(
            testString(R.string.pro_plan_founder, proDiscoveryDateTime(expiresAt))
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun comparisonStaysReadableWithLargeFontOnASmallScreen() {
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = density.density,
                    fontScale = 1.8f
                )
            ) {
                WeightTrackerThemeForPreview {
                    ProBenefitsScreen(
                        status = ProBenefitsStatus(proActive = false),
                        onBack = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag(PRO_BENEFITS_TABLE).assertExists()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_column_feature))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.pro_benefits_cell_unlimited))[0]
            .performScrollTo()
            .assertIsDisplayed()
    }
}
