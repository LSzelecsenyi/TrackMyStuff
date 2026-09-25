package app.mymusclemap.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
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
@Config(qualifiers = "w360dp-h800dp")
class AppBottomBarLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenNoActiveSessionWhenBarAppearsThenMiddleActionStartsWorkout() {
        var workouts = 0
        render(hasActiveSession = false, onWorkoutAction = { workouts += 1 })
        composeRule.onNodeWithText("Áttekintés").assertIsDisplayed()
        composeRule.onNodeWithText("Napló").assertIsDisplayed()
        composeRule.onAllNodesWithText("Edzés").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Edzés indítása").assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).assert(hasRole(Role.Button))
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).performClick()
        assertEquals(1, workouts)
    }

    @Test
    fun givenActiveSessionWhenBarAppearsThenMiddleActionResumesWorkout() {
        render(hasActiveSession = true)
        composeRule.onNodeWithContentDescription("Edzés folytatása").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edzés indítása").assertDoesNotExist()
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun given360DpAndLargeFontWhenBarAppearsThenItemsDoNotOverlapAndStay48Dp() {
        render(fontScale = 1.3f)
        val overview = composeRule.onNodeWithTag(BOTTOM_OVERVIEW).getBoundsInRoot()
        val action = composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).getBoundsInRoot()
        val journal = composeRule.onNodeWithTag(BOTTOM_JOURNAL).getBoundsInRoot()
        assertFalse("overview and action overlap $overview $action", overlaps(overview, action))
        assertFalse("action and journal overlap $action $journal", overlaps(action, journal))
        listOf(overview, action, journal).forEach { bounds ->
            assertTrue("touch width $bounds", bounds.right - bounds.left >= 48.dp)
            assertTrue("touch height $bounds", bounds.bottom - bounds.top >= 48.dp)
            assertTrue("stays on 360dp $bounds", bounds.right <= 360.dp + 1.dp)
            assertTrue("stays on screen $bounds", bounds.left >= (-1).dp)
        }
    }

    private fun render(
        hasActiveSession: Boolean = false,
        fontScale: Float = 1f,
        onWorkoutAction: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxSize()
                    ) {
                        AppBottomBar(
                            selectedRoute = AppRoutes.OVERVIEW,
                            hasActiveSession = hasActiveSession,
                            onOverview = {},
                            onJournal = {},
                            onWorkoutAction = onWorkoutAction
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hasRole(role: Role): SemanticsMatcher {
        return SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    }

    private fun overlaps(first: DpRect, second: DpRect): Boolean {
        return first.left < second.right - 1.dp &&
            second.left < first.right - 1.dp &&
            first.top < second.bottom - 1.dp &&
            second.top < first.bottom - 1.dp
    }
}
