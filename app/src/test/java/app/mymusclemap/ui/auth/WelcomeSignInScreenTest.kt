package app.mymusclemap.ui.auth

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mymusclemap.R
import app.mymusclemap.testString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WelcomeSignInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun continueWithGoogleIsTheOnlyWayIn() {
        var continued = 0
        composeRule.setContent {
            WelcomeSignInScreen(
                busy = false,
                message = null,
                claimOpen = false,
                onContinue = { continued += 1 },
                onConfirmClaim = {},
                onCancelClaim = {}
            )
        }
        composeRule.onNodeWithTag(WELCOME_ROOT).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.welcome_continue)).assertIsDisplayed().performClick()
        assertEquals(1, continued)
        composeRule.onAllNodesWithTag(WELCOME_CLAIM).assertCountEquals(0)
    }

    @Test
    fun privacyPolicyOpensWithoutSigningIn() {
        var opened = 0
        composeRule.setContent {
            WelcomeSignInScreen(
                busy = false,
                message = null,
                claimOpen = false,
                onContinue = {},
                onConfirmClaim = {},
                onCancelClaim = {},
                onOpenPrivacy = { opened += 1 }
            )
        }
        composeRule.onNodeWithTag(WELCOME_PRIVACY).assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun claimConfirmationIsExplicit() {
        var confirmed = 0
        var canceled = 0
        composeRule.setContent {
            WelcomeSignInScreen(
                busy = false,
                message = null,
                claimOpen = true,
                onContinue = {},
                onConfirmClaim = { confirmed += 1 },
                onCancelClaim = { canceled += 1 }
            )
        }
        composeRule.onNodeWithText(testString(R.string.welcome_claim_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(WELCOME_CLAIM).performClick()
        assertEquals(1, confirmed)
        assertEquals(0, canceled)
    }
}
