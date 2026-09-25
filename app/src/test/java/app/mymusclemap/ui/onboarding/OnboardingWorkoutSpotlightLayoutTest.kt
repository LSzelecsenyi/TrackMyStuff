package app.mymusclemap.ui.onboarding

import app.mymusclemap.R
import app.mymusclemap.testString
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.navigation.AppBottomBar
import app.mymusclemap.ui.navigation.AppRoutes
import app.mymusclemap.ui.navigation.BOTTOM_OVERVIEW
import app.mymusclemap.ui.navigation.BOTTOM_WORKOUT_ACTION
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class OnboardingWorkoutSpotlightLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun spotlightDimsScreenAndKeepsASingleRealWorkoutAction() {
        val workouts = AtomicInteger(0)
        val dimmed = AtomicInteger(0)
        render(onWorkout = { workouts.incrementAndGet() }, onDimmed = { dimmed.incrementAndGet() })
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_COACH).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.onboarding_start_workout_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.onboarding_start_workout_body)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(BOTTOM_WORKOUT_ACTION).assertCountEquals(1)
        val callout = composeRule.onNodeWithTag(ONBOARDING_WORKOUT_COACH).getBoundsInRoot()
        val action = composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).getBoundsInRoot()
        assertTrue("callout should sit above the workout action", callout.bottom <= action.top + 12.dp)
        assertTrue("callout stays on 360dp", callout.right <= 360.dp + 1.dp)
        assertTrue("callout stays on screen", callout.left >= (-1).dp)
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).performTouchInput {
            click(Offset(24f, 24f))
        }
        assertEquals(0, dimmed.get())
        assertEquals(0, workouts.get())
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).performClick()
        assertEquals(1, workouts.get())
    }

    @Test
    fun spotlightWorksInDarkThemeOnNarrowWidth() {
        render(darkTheme = true)
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_COACH).assertIsDisplayed()
        val callout = composeRule.onNodeWithTag(ONBOARDING_WORKOUT_COACH).getBoundsInRoot()
        assertTrue(callout.right <= 360.dp + 1.dp)
        assertTrue(callout.left >= (-1).dp)
    }

    @Test
    fun systemBackDismissesSpotlightWithoutStartingAWorkout() {
        val workouts = AtomicInteger(0)
        val dismissed = AtomicInteger(0)
        val dispatcher = AtomicReference<OnBackPressedDispatcher?>(null)
        render(
            onWorkout = { workouts.incrementAndGet() },
            onDismiss = { dismissed.incrementAndGet() },
            dispatcherOut = dispatcher
        )
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertIsDisplayed()
        composeRule.runOnIdle { dispatcher.get()!!.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertDoesNotExist()
        assertEquals(1, dismissed.get())
        assertEquals(0, workouts.get())
    }

    @Test
    fun tappingTheRealWorkoutActionUsesTheExistingHandler() {
        val workouts = AtomicInteger(0)
        render(onWorkout = { workouts.incrementAndGet() })
        composeRule.onNodeWithTag(BOTTOM_OVERVIEW).assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).performClick()
        assertEquals(1, workouts.get())
        composeRule.onAllNodesWithTag(BOTTOM_WORKOUT_ACTION).assertCountEquals(1)
    }

    private fun render(
        darkTheme: Boolean = false,
        onWorkout: () -> Unit = {},
        onDimmed: () -> Unit = {},
        onDismiss: () -> Unit = {},
        dispatcherOut: AtomicReference<OnBackPressedDispatcher?>? = null
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            var hole by remember { mutableStateOf(Rect.Zero) }
            var showSpotlight by remember { mutableStateOf(true) }
            dispatcherOut?.set(LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher)
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f)
            ) {
                WeightTrackerThemeForPreview(
                    seeds = if (darkTheme) ThemeSeeds.DefaultDark else ThemeSeeds.DefaultLight,
                    darkTheme = darkTheme
                ) {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxSize()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = onDimmed,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("dimmed-control")
                                ) {
                                    Text("Behind", modifier = Modifier)
                                }
                            }
                            AppBottomBar(
                                selectedRoute = AppRoutes.OVERVIEW,
                                hasActiveSession = false,
                                onOverview = {},
                                onJournal = {},
                                onWorkoutAction = {
                                    showSpotlight = false
                                    onWorkout()
                                },
                                onWorkoutActionBounds = { bounds ->
                                    if (bounds != hole) hole = bounds
                                }
                            )
                        }
                        if (showSpotlight) {
                            OnboardingWorkoutSpotlight(
                                targetInRoot = hole,
                                onDismiss = {
                                    showSpotlight = false
                                    onDismiss()
                                },
                                onTargetClick = {
                                    showSpotlight = false
                                    onWorkout()
                                }
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
