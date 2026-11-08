package app.mymusclemap.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DeleteAccountScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "hu-rHU-w360dp-h2000dp")
    fun givenHungarian_whenTheScreenOpens_thenTheApprovedCopyAndEmailAreShown() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                DeleteAccountScreen(
                    onDelete = { DeleteAccountResult.Failed },
                    onFinished = {},
                    onBack = {},
                    accountEmail = "owner@example.com"
                )
            }
        }
        assertEquals("Fiók törlése", testString(R.string.delete_account_title))
        assertEquals("Biztosan törölni szeretnéd a fiókodat?", testString(R.string.delete_account_heading))
        assertEquals(
            "A Strict-fiókod és az edzésadataid véglegesen törlődnek. A művelet nem vonható vissza.",
            testString(R.string.delete_account_description)
        )
        assertEquals("Bejelentkezett fiók", testString(R.string.delete_account_signed_in_label))
        assertEquals(
            "A korábban készített mentéseid megmaradhatnak. Az aktív Google Play-előfizetésedet külön kell lemondanod.",
            testString(R.string.delete_account_additional)
        )
        assertEquals("Adatvédelmi tájékoztató", testString(R.string.action_privacy_policy))
        assertEquals("Megértettem, hogy a törlés végleges.", testString(R.string.delete_account_confirm))
        assertEquals("Fiók végleges törlése", testString(R.string.delete_account_action))
        composeRule.onNodeWithText("Fiók törlése").assertIsDisplayed()
        composeRule.onNodeWithText("Biztosan törölni szeretnéd a fiókodat?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "A Strict-fiókod és az edzésadataid véglegesen törlődnek. A művelet nem vonható vissza."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Bejelentkezett fiók").assertIsDisplayed()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_EMAIL).assertIsDisplayed()
        composeRule.onNodeWithText("owner@example.com").assertIsDisplayed()
        composeRule.onNodeWithText(
            "A korábban készített mentéseid megmaradhatnak. Az aktív Google Play-előfizetésedet külön kell lemondanod."
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Adatvédelmi tájékoztató").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Megértettem, hogy a törlés végleges.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Fiók végleges törlése").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Google-fiókot válaszd", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Google-bejelentkezést kér", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_CONFIRM).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h2000dp")
    fun givenEnglish_whenTheScreenOpens_thenTheSameMeaningIsShownAndTheButtonStaysOff() {
        var privacy = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                DeleteAccountScreen(
                    onDelete = { DeleteAccountResult.Failed },
                    onFinished = {},
                    onBack = {},
                    onOpenPrivacy = { privacy += 1 },
                    accountEmail = "owner@example.com"
                )
            }
        }
        assertEquals("Delete account", testString(R.string.delete_account_title))
        assertEquals(
            "Are you sure you want to delete your account?",
            testString(R.string.delete_account_heading)
        )
        assertEquals(
            "Your Strict account and your workout data will be permanently deleted. This cannot be undone.",
            testString(R.string.delete_account_description)
        )
        assertEquals("Signed-in account", testString(R.string.delete_account_signed_in_label))
        assertEquals(
            "Backups you made earlier may remain. You have to cancel your active Google Play subscription separately.",
            testString(R.string.delete_account_additional)
        )
        assertEquals("Privacy policy", testString(R.string.action_privacy_policy))
        assertEquals("I understand that deletion is permanent.", testString(R.string.delete_account_confirm))
        assertEquals("Permanently delete account", testString(R.string.delete_account_action))
        composeRule.onNodeWithText("Signed-in account").assertIsDisplayed()
        composeRule.onNodeWithText("owner@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("choose the Google account", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Sign in with Google again", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_CONFIRM).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_CONFIRM).performClick()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_PRIVACY).performScrollTo().performClick()
        assertEquals(1, privacy)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h2000dp")
    fun givenTheServerRejectsDeletion_whenTheButtonIsUsed_thenTheScreenDoesNotFinish() {
        var finished = 0
        var deletes = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                DeleteAccountScreen(
                    onDelete = {
                        deletes += 1
                        DeleteAccountResult.Failed
                    },
                    onFinished = { finished += 1 },
                    onBack = {},
                    accountEmail = "owner@example.com"
                )
            }
        }
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_CONFIRM).performScrollTo().performClick()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, deletes)
        assertEquals(0, finished)
        composeRule.onNodeWithText(testString(R.string.delete_account_failed)).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h2000dp")
    fun givenTheServerConfirmsDeletion_whenTheButtonIsUsed_thenTheScreenFinishes() {
        var finished = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                DeleteAccountScreen(
                    onDelete = { DeleteAccountResult.Deleted },
                    onFinished = { finished += 1 },
                    onBack = {},
                    accountEmail = "owner@example.com"
                )
            }
        }
        composeRule.onNodeWithTag(DELETE_ACCOUNT_CONFIRM).performScrollTo().performClick()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, finished)
    }

    @Test
    @Config(qualifiers = "hu-rHU-w360dp-h2000dp")
    fun givenTheSessionIsExpired_whenDeletionReturns_thenNothingIsDescribedAsDeleted() {
        var finished = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                DeleteAccountScreen(
                    onDelete = { DeleteAccountResult.ReauthenticationRequired },
                    onFinished = { finished += 1 },
                    onBack = {},
                    accountEmail = "owner@example.com"
                )
            }
        }
        composeRule.onNodeWithTag(DELETE_ACCOUNT_CONFIRM).performScrollTo().performClick()
        composeRule.onNodeWithTag(DELETE_ACCOUNT_ACTION).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ez a munkamenet már nem érvényes. Semmi sem lett törölve.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Google-fiókot válaszd", substring = true).assertDoesNotExist()
        assertEquals(0, finished)
    }
}
