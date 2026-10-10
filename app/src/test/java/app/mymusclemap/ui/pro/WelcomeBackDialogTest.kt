package app.mymusclemap.ui.pro

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.mymusclemap.domain.entitlement.WelcomeBackSnapshot
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WelcomeBackDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notNowDoesNotActivateAndShowProPlanLeavesTheWarning() {
        var activations = 0
        var dismissals = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                WelcomeBackOfferDialog(
                    onActivate = { activations += 1 },
                    onNotNow = { dismissals += 1 }
                )
            }
        }
        composeRule.onNodeWithTag(WELCOME_BACK_OFFER).assertIsDisplayed()
        composeRule.onNodeWithTag(WELCOME_BACK_NOT_NOW).performClick()
        assertEquals(0, activations)
        assertEquals(1, dismissals)
    }

    @Test
    fun showProPlanIsTheWarningAction() {
        var plans = 0
        val expiresAt = Instant.parse("2026-10-16T08:00:00Z")
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                WelcomeBackWarningDialog(
                    expiresAt = expiresAt,
                    now = expiresAt.minusSeconds(60),
                    onViewPlans = { plans += 1 },
                    onMaybeLater = {}
                )
            }
        }
        composeRule.onNodeWithTag(WELCOME_BACK_VIEW_PLANS).performClick()
        assertEquals(1, plans)
    }

    @Test
    fun benefitsKeepsADismissedOfferAndDoesNotOfferItToPaidPro() {
        var activations = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = app.mymusclemap.domain.entitlement.ProBenefitsStatus(proActive = false),
                    onBack = {},
                    welcomeBack = WelcomeBackSnapshot(offerAvailable = true),
                    onActivateWelcomeBack = { activations += 1 }
                )
            }
        }
        composeRule.onNodeWithTag(WELCOME_BACK_ACTIVATE).performClick()
        assertEquals(1, activations)
    }

    @Test
    fun paidProDoesNotShowAWelcomeBackActivation() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = app.mymusclemap.domain.entitlement.ProBenefitsStatus(proActive = true),
                    onBack = {},
                    welcomeBack = WelcomeBackSnapshot(trialExpired = true)
                )
            }
        }
        composeRule.onAllNodesWithTag(WELCOME_BACK_ACTIVATE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(WELCOME_BACK_STATUS).assertCountEquals(0)
    }
}
