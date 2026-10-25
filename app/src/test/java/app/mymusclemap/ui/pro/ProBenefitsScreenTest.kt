package app.mymusclemap.ui.pro

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FeatureAccessPolicy
import app.mymusclemap.domain.entitlement.ProBenefitsStatus
import app.mymusclemap.domain.entitlement.ProDiscoverySnapshot
import app.mymusclemap.domain.entitlement.WorkoutPlanAccess
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import android.content.res.Configuration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w320dp-h480dp")
class ProBenefitsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeAccessShowsTheProIntroductionWithoutAPurchaseAction() {
        val expiresAt = Instant.parse("2027-06-01T12:00:00Z")
        var backs = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = true, expiresAt = expiresAt),
                    onBack = { backs += 1 }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_benefits_yours_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_yours_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_access_pro)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_active)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_intro_title)).assertDoesNotExist()
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
        composeRule.onNodeWithTag(PRO_BENEFITS_TIMERS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_cell_included))
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
        composeRule.onNodeWithText(testString(R.string.pro_benefits_intro_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_intro_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_access_free)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_yours_title)).assertDoesNotExist()
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

    @Test
    fun paidSubscriptionStaysSeparateFromFreeEffectiveAccess() {
        val expiresAt = Instant.parse("2026-11-11T00:00:00Z")
        var managed = ""
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = false),
                    onBack = {},
                    plan = app.mymusclemap.domain.billing.ProPlanPresentation.Paid(
                        productId = "strict_pro",
                        expiresAt = expiresAt,
                        renews = false,
                        testOnly = false
                    ),
                    subscription = app.mymusclemap.domain.billing.SubscriptionFacts.Active(
                        productId = "strict_pro",
                        expiresAt = expiresAt,
                        autoRenewing = false
                    ),
                    onManageSubscription = { managed = it }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_benefits_access_free)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_access_pro)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_subscription))
            .performScrollTo()
            .assertIsDisplayed()
        val ends = testString(
            R.string.pro_plan_paid_ends,
            app.mymusclemap.ui.pro.proDiscoveryDateTime(expiresAt)
        )
        composeRule.onNodeWithText(ends).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Access continues until", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_BENEFITS_MANAGE).performScrollTo().performClick()
        assertEquals("strict_pro", managed)
    }

    @Test
    fun founderDoesNotDescribeAPaidSubscription() {
        val expiresAt = Instant.parse("2027-01-01T00:00:00Z")
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = true),
                    onBack = {},
                    plan = app.mymusclemap.domain.billing.ProPlanPresentation.Founder(
                        expiresAt = expiresAt,
                        lifetime = false
                    ),
                    subscription = app.mymusclemap.domain.billing.SubscriptionFacts.None
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_benefits_access_pro)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_subscription_none))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Active subscription", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_BENEFITS_MANAGE).assertDoesNotExist()
    }

    @Test
    fun loadingBillingDoesNotClaimThereIsNoSubscription() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(proActive = false),
                    onBack = {},
                    plan = app.mymusclemap.domain.billing.ProPlanPresentation.Loading,
                    subscription = app.mymusclemap.domain.billing.SubscriptionFacts.Loading
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_plan_loading))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_benefits_subscription_none)).assertDoesNotExist()
    }

    @Test
    fun comparisonFits320AtFontScale13() {
        showComparison(1.3f)
        assertComparisonStaysInsideTheTable()
    }

    @Test
    fun comparisonFits320AtFontScale16() {
        showComparison(1.6f)
        assertComparisonStaysInsideTheTable()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h640dp")
    fun comparisonFits360AtFontScale13() {
        showComparison(1.3f)
        assertComparisonStaysInsideTheTable()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h640dp")
    fun comparisonFits360AtFontScale16() {
        showComparison(1.6f)
        assertComparisonStaysInsideTheTable()
    }

    @Test
    fun hungarianComparisonFits320AtFontScale13() {
        composeRule.setContent {
            val base = LocalContext.current
            val hungarian = remember(base) {
                val configuration = Configuration(base.resources.configuration)
                configuration.setLocale(Locale.forLanguageTag("hu"))
                base.createConfigurationContext(configuration)
            }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides hungarian,
                LocalDensity provides Density(density.density, fontScale = 1.3f)
            ) {
                WeightTrackerThemeForPreview {
                    ProBenefitsScreen(
                        status = ProBenefitsStatus(proActive = false),
                        onBack = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText("Hozd ki a legtöbbet az edzéseidből").assertIsDisplayed()
        val timers = composeRule.onNodeWithText("Időzített sorozatok és időzítők").performScrollTo()
        timers.assertIsDisplayed()
        val table = composeRule.onNodeWithTag(PRO_BENEFITS_TABLE).getBoundsInRoot()
        val bounds = timers.getBoundsInRoot()
        assertTrue(bounds.left >= table.left - 1.dp)
        assertTrue(bounds.right <= table.right + 1.dp)
        composeRule.onNodeWithText("Előfizetés").performScrollTo().assertIsDisplayed()
    }

    private fun showComparison(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    ProBenefitsScreen(
                        status = ProBenefitsStatus(proActive = false),
                        onBack = {}
                    )
                }
            }
        }
    }

    private fun assertComparisonStaysInsideTheTable() {
        listOf(
            R.string.pro_benefits_plans_title,
            R.string.pro_benefits_timers,
            R.string.pro_benefits_cell_included
        ).forEach { id ->
            val node = composeRule.onNodeWithText(testString(id)).performScrollTo()
            node.assertIsDisplayed()
            val table = composeRule.onNodeWithTag(PRO_BENEFITS_TABLE).getBoundsInRoot()
            val bounds = node.getBoundsInRoot()
            assertTrue(bounds.left >= table.left - 1.dp)
            assertTrue(bounds.right <= table.right + 1.dp)
        }
    }
}
