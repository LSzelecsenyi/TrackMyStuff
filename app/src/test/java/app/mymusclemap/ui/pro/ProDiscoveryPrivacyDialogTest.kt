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
    fun notNowDoesNotEnableActivitySynchronization() {
        var enabled = 0
        var declined = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProDiscoverySyncPromptDialog(
                    onEnable = { enabled += 1 },
                    onNotNow = { declined += 1 },
                    onViewPolicy = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_discovery_sync_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_DISCOVERY_SYNC_NOT_NOW).performClick()
        assertEquals(0, enabled)
        assertEquals(1, declined)
    }

    @Test
    fun benefitsCanShowHowToEnableSynchronizationWithoutForcingIt() {
        var enabled = 0
        var activated = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBenefitsScreen(
                    status = ProBenefitsStatus(),
                    onBack = {},
                    activitySyncEnabled = false,
                    onEnableActivitySync = { enabled += 1 },
                    onActivateTrial = { activated += 1 }
                )
            }
        }
        composeRule.onNodeWithTag(PRO_DISCOVERY_SYNC_SETTING).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_DISCOVERY_SYNC_ENABLE).performScrollTo().performClick()
        assertEquals(1, enabled)
        assertEquals(0, activated)
    }

    @Test
    fun aFailedServerDeletionIsNotShownAsCompleted() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                AccountMembershipScreen(
                    onBack = {},
                    activitySyncEnabled = false,
                    activitySyncDeletionFailed = true
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.pro_discovery_sync_deletion_failed))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_discovery_sync_enabled)).assertDoesNotExist()
    }
}
