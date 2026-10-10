package app.mymusclemap.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.R
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeleteAccountScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theDestructiveActionStaysDisabledUntilTheBoxIsChecked() {
        var deletes = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                DeleteAccountScreen(
                    paidSubscriptionKnown = false,
                    onManageSubscription = {},
                    onDelete = {
                        deletes += 1
                        DeleteAccountResult.Failed
                    },
                    onFinished = {},
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.delete_account_subscription)).assertIsDisplayed()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_CONFIRM).performScrollTo().performClick()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(1, deletes)
        composeRule.onNodeWithText(testString(R.string.delete_account_failed)).assertIsDisplayed()
    }

    @Test
    fun aKnownSubscriptionWarnsWithoutClaimingItWasCancelled() {
        var managed = 0
        var privacy = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                DeleteAccountScreen(
                    paidSubscriptionKnown = true,
                    onManageSubscription = { managed += 1 },
                    onDelete = { DeleteAccountResult.NeedsNetwork },
                    onFinished = {},
                    onBack = {},
                    onOpenPrivacy = { privacy += 1 }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.delete_account_subscription_known))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_PRIVACY).performScrollTo().performClick()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_MANAGE).performScrollTo().performClick()
        assertEquals(1, managed)
        assertEquals(1, privacy)
    }
}
