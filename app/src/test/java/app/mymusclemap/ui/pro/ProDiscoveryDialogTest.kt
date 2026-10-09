package app.mymusclemap.ui.pro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.formatTrialRemaining
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w320dp-h480dp")
class ProDiscoveryDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notNowDoesNotActivateAndTheOfferRemainsReadableOnASmallScreen() {
        var activations = 0
        var dismissals = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProDiscoveryOfferDialog(
                    onActivate = { activations += 1 },
                    onNotNow = { dismissals += 1 }
                )
            }
        }
        composeRule.onNodeWithTag(PRO_DISCOVERY_OFFER).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_discovery_offer_message))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_discovery_offer_note))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_DISCOVERY_NOT_NOW).performScrollTo().performClick()
        assertEquals(0, activations)
        assertEquals(1, dismissals)
    }

    @Test
    fun activateIsExplicitAndViewPlansOnlyNavigates() {
        val expiresAt = Instant.parse("2026-08-22T15:00:00Z")
        val now = expiresAt.minusSeconds(125)
        var plans = 0
        var later = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProDiscoveryWarningDialog(
                    expiresAt = expiresAt,
                    now = now,
                    onViewPlans = { plans += 1 },
                    onMaybeLater = { later += 1 }
                )
            }
        }
        composeRule.onNodeWithText(
            testString(R.string.pro_discovery_warning_title, formatTrialRemaining(now, expiresAt))
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Your Pro trial ends in 2 days").assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_discovery_view_plans))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_discovery_warning_message))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.pro_discovery_warning_when, proDiscoveryDateTime(expiresAt))
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Purchase").assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_DISCOVERY_VIEW_PLANS).performScrollTo().performClick()
        assertEquals(1, plans)
        assertEquals(0, later)
    }
}
