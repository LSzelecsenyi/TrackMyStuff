package app.mymusclemap.ui.pro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.PrivacyPolicyNotice
import app.mymusclemap.domain.entitlement.ProBenefitsStatus
import app.mymusclemap.testString
import app.mymusclemap.ui.settings.AccountMembershipScreen
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w320dp-h640dp")
class ProDiscoveryPrivacyDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun thePolicyNoticeIsShownOnceForTheCurrentVersionAndIsNotSyncPermission() {
        var continued = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                PrivacyPolicyNoticeDialog(
                    onContinue = { continued += 1 },
                    onViewPolicy = {}
                )
            }
        }
        composeRule.onNodeWithTag(PRIVACY_NOTICE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.privacy_notice_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRIVACY_NOTICE_CONTINUE).performClick()
        assertEquals(1, continued)
        assertFalse(PrivacyPolicyNotice.due(PrivacyPolicyNotice.CURRENT_VERSION))
        assertTrue(PrivacyPolicyNotice.due(PrivacyPolicyNotice.CURRENT_VERSION - 1))
    }

    @Test
    fun theTrialOfferDoesNotStartUntilThePrimaryButton() {
        var activated = 0
        var declined = 0
        var pro = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProDiscoveryOfferDialog(
                    kind = app.mymusclemap.domain.entitlement.ProDiscoveryOfferKind.TRIAL,
                    onActivate = { activated += 1 },
                    onNotNow = { declined += 1 },
                    onViewPro = { pro += 1 }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_discovery_offer_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_DISCOVERY_OFFER_PRO).performClick()
        assertEquals(0, activated)
        assertEquals(1, pro)
        composeRule.onNodeWithTag(PRO_DISCOVERY_NOT_NOW).performClick()
        assertEquals(0, activated)
        assertEquals(1, declined)
    }

    @Test
    fun theReturningOfferAsksForASubscriptionAndDoesNotActivateATrial() {
        var activated = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProDiscoveryOfferDialog(
                    kind = app.mymusclemap.domain.entitlement.ProDiscoveryOfferKind.SUBSCRIPTION,
                    onActivate = { activated += 1 },
                    onNotNow = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_discovery_subscription_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_discovery_subscribe)).performClick()
        assertEquals(1, activated)
        composeRule.onNodeWithText(testString(R.string.pro_discovery_activate)).assertDoesNotExist()
    }
}
