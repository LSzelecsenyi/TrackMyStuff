package app.mymusclemap.ui.founder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FounderApprovalCelebration
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
@Config(sdk = [33], qualifiers = "w360dp-h2000dp")
class FounderApprovalCelebrationDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun celebrationShowsTheBackendExpiryAndTheFounderBadge() {
        val expiresAt = Instant.parse("2027-06-01T12:00:00Z")
        val expiresOn = UiFormatters.longDate(
            expiresAt.atZone(ZoneId.systemDefault()).toLocalDate()
        )
        var started = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                FounderApprovalCelebrationDialog(
                    celebration = FounderApprovalCelebration(proExpiresAt = expiresAt),
                    onGetStarted = { started += 1 },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.founder_approval_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_approval_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_approval_reward)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_approval_until, expiresOn)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_approval_explanation)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_approval_badge))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_approval_start)).assertIsDisplayed()
        composeRule.onNodeWithText("Let's get started!").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(testString(R.string.founder_badge)).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_APPROVAL_BADGE).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_APPROVAL_START).performClick()
        composeRule.waitForIdle()
        assertEquals(1, started)
    }
}
