package app.mymusclemap.ui.navigation

import app.mymusclemap.R
import app.mymusclemap.testString
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
        composeRule.onNodeWithText(testString(R.string.nav_dashboard)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.nav_library)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.nav_journal)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.nav_workout)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(testString(R.string.action_start_workout)).assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).assert(hasRole(Role.Button))
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).performClick()
        assertEquals(1, workouts)
    }

    @Test
    fun highlightedWorkoutActionStillInvokesTheExistingClickHandler() {
        var workouts = 0
        render(highlightWorkoutAction = true, onWorkoutAction = { workouts += 1 })
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).performClick()
        assertEquals(1, workouts)
    }

    @Test
    fun givenActiveSessionWhenBarAppearsThenMiddleActionResumesWorkout() {
        render(hasActiveSession = true)
        composeRule.onNodeWithContentDescription(testString(R.string.action_resume_workout)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.action_start_workout)).assertDoesNotExist()
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun given360DpAndLargeFontWhenBarAppearsThenItemsDoNotOverlapAndStay48Dp() {
        render(fontScale = 1.3f)
        val overview = composeRule.onNodeWithTag(BOTTOM_OVERVIEW).getBoundsInRoot()
        val library = composeRule.onNodeWithTag(BOTTOM_LIBRARY).getBoundsInRoot()
        val action = composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).getBoundsInRoot()
        val statistics = composeRule.onNodeWithTag(BOTTOM_STATISTICS).getBoundsInRoot()
        val journal = composeRule.onNodeWithTag(BOTTOM_JOURNAL).getBoundsInRoot()
        assertTrue(overview.left < library.left)
        assertTrue(library.left < action.left)
        assertTrue(action.left < statistics.left)
        assertTrue(statistics.left < journal.left)
        val center = (action.left + action.right) / 2
        assertTrue("workout should sit in the center", center > 160.dp && center < 200.dp)
        assertFalse("overview and library overlap $overview $library", overlaps(overview, library))
        assertFalse("library and action overlap $library $action", overlaps(library, action))
        assertFalse("action and statistics overlap $action $statistics", overlaps(action, statistics))
        assertFalse("statistics and journal overlap $statistics $journal", overlaps(statistics, journal))
        listOf(overview, library, action, statistics, journal).forEach { bounds ->
            assertTrue("touch width $bounds", bounds.right - bounds.left >= 48.dp)
            assertTrue("touch height $bounds", bounds.bottom - bounds.top >= 48.dp)
            assertTrue("stays on 360dp $bounds", bounds.right <= 360.dp + 1.dp)
            assertTrue("stays on screen $bounds", bounds.left >= (-1).dp)
        }
    }

    @Test
    fun fiveItemsStayApartOnANarrowScreen() {
        render(width = 320.dp)
        val overview = composeRule.onNodeWithTag(BOTTOM_OVERVIEW).getBoundsInRoot()
        val library = composeRule.onNodeWithTag(BOTTOM_LIBRARY).getBoundsInRoot()
        val action = composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).getBoundsInRoot()
        val statistics = composeRule.onNodeWithTag(BOTTOM_STATISTICS).getBoundsInRoot()
        val journal = composeRule.onNodeWithTag(BOTTOM_JOURNAL).getBoundsInRoot()
        listOf(
            overview to library,
            library to action,
            action to statistics,
            statistics to journal
        ).forEach { (first, second) ->
            assertFalse("overlap $first $second", overlaps(first, second))
        }
        listOf(overview, library, action, statistics, journal).forEach { bounds ->
            assertTrue("touch width $bounds", bounds.right - bounds.left >= 48.dp)
            assertTrue("stays on 320dp $bounds", bounds.right <= 320.dp + 1.dp)
        }
    }

    @Test
    fun libraryTabSelectsTheLibraryDestination() {
        var opened = 0
        render(selectedRoute = AppRoutes.LIBRARY, onLibrary = { opened += 1 })
        composeRule.onNodeWithTag(BOTTOM_LIBRARY).performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithTag(BOTTOM_LIBRARY)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithTag(BOTTOM_OVERVIEW)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    @Test
    fun statisticsTabUsesTheStatisticsDestinationAndShowsSelected() {
        var opened = 0
        render(selectedRoute = AppRoutes.STATISTICS, onStatistics = { opened += 1 })
        composeRule.onNodeWithTag(BOTTOM_STATISTICS).performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithTag(BOTTOM_STATISTICS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithTag(BOTTOM_OVERVIEW)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    private fun render(
        hasActiveSession: Boolean = false,
        fontScale: Float = 1f,
        highlightWorkoutAction: Boolean = false,
        selectedRoute: String = AppRoutes.OVERVIEW,
        width: androidx.compose.ui.unit.Dp = 360.dp,
        onLibrary: () -> Unit = {},
        onStatistics: () -> Unit = {},
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
                            .width(width)
                            .fillMaxSize()
                    ) {
                        AppBottomBar(
                            selectedRoute = selectedRoute,
                            hasActiveSession = hasActiveSession,
                            highlightWorkoutAction = highlightWorkoutAction,
                            onOverview = {},
                            onLibrary = onLibrary,
                            onStatistics = onStatistics,
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
