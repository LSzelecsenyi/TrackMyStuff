package app.mymusclemap.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class OnboardingScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeIntroducesAppAndStarterCatalog() {
        var continued = 0
        render(OnboardingStep.Welcome, onContinue = { continued += 1 })
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("MY MUSCLE MAP").assertIsDisplayed()
        composeRule.onNodeWithText("Track your training").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Build workout plans, log sets, and see which muscles you train. A starter catalog of common exercises is ready to use.",
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_SKIP).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        assertEquals(1, continued)
    }

    @Test
    fun setupStepGuidesUserToExistingWorkoutPlanCreation() {
        var createPlan = 0
        var skip = 0
        render(
            OnboardingStep.CreatePlan,
            onCreatePlan = { createPlan += 1 },
            onSkip = { skip += 1 }
        )
        composeRule.onNodeWithText("Create your first workout").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Add exercises from the starter catalog to a plan. After you save it, you can start it anytime from Workout.",
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Create workout plan").assertIsDisplayed()
        composeRule.onNodeWithText("Skip for now").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        assertEquals(1, createPlan)
        composeRule.onNodeWithTag(ONBOARDING_SKIP).performClick()
        assertEquals(1, skip)
    }

    private fun render(
        step: OnboardingStep,
        onContinue: () -> Unit = {},
        onCreatePlan: () -> Unit = {},
        onSkip: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxSize()
                    ) {
                        OnboardingScreen(
                            step = step,
                            onContinue = onContinue,
                            onCreatePlan = onCreatePlan,
                            onSkip = onSkip
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
