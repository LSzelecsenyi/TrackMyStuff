package app.mymusclemap.ui.entitlement

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
@Config(qualifiers = "w360dp-h2000dp")
class FreeAccessSelectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationDialogUsesTheFinalCopyAndDoesNotConfirmOnDismiss() {
        var confirmed = 0
        var dismissed = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                FreeAccessSelectionScreen(
                    state = FreeAccessSelectionUiState(
                        plans = listOf(FreeAccessChoiceItem(1L, "Push", "Bench")),
                        exercises = listOf(FreeAccessChoiceItem(8L, "Curl", "Biceps")),
                        draftPlanIds = setOf(1L),
                        draftExerciseIds = setOf(8L),
                        planLimit = 3,
                        exerciseLimit = 5,
                        plansRequireChoice = true,
                        exercisesRequireChoice = true,
                        confirmEnabled = true,
                        confirmVisible = true
                    ),
                    onBack = {},
                    onTogglePlan = {},
                    onToggleExercise = {},
                    onRequestConfirm = {},
                    onDismissConfirm = { dismissed += 1 },
                    onConfirm = { confirmed += 1 },
                    onExplorePro = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.free_access_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_confirm_body)).assertIsDisplayed()
        composeRule.onNodeWithTag("free-access-dialog-cancel").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
        composeRule.onNodeWithTag("free-access-dialog-confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun confirmStaysDisabledUntilTheDraftFits() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                FreeAccessSelectionScreen(
                    state = FreeAccessSelectionUiState(
                        plansRequireChoice = true,
                        exercisesRequireChoice = true,
                        confirmEnabled = false
                    ),
                    onBack = {},
                    onTogglePlan = {},
                    onToggleExercise = {},
                    onRequestConfirm = {},
                    onDismissConfirm = {},
                    onConfirm = {},
                    onExplorePro = {}
                )
            }
        }
        composeRule.onNodeWithTag("free-access-confirm").assertIsNotEnabled()
    }
}
