package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.domain.workout.WorkoutCompletionSummary
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class WorkoutCompletionScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successfulSummaryAndActionsAreReadableAtFontScale13() {
        var back = 0
        render(
            WorkoutCompletionSummary(4, 14, 3_020_000L),
            width = 360.dp,
            fontScale = 1.3f,
            onBack = { back += 1 }
        )
        composeRule.onNodeWithText("Edzés teljesítve").assertIsDisplayed()
        composeRule.onNodeWithText("4 gyakorlat · 14 sorozat · 50:20").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edzés sikeresen teljesítve").assertIsDisplayed()
        val title = composeRule.onNodeWithTag(WORKOUT_COMPLETE_TITLE).getBoundsInRoot()
        val stats = composeRule.onNodeWithTag(WORKOUT_COMPLETE_SUMMARY).getBoundsInRoot()
        val action = composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).getBoundsInRoot()
        val mark = composeRule.onNodeWithTag(WORKOUT_COMPLETE_MARK).getBoundsInRoot()
        assertTrue("title=$title stats=$stats", title.bottom <= stats.top + 1.dp || title.right <= stats.left + 1.dp)
        assertTrue("stats=$stats action=$action", stats.bottom <= action.top + 1.dp)
        assertTrue("mark=$mark title=$title", mark.bottom <= title.top + 1.dp)
        assertTrue(action.right - action.left >= 48.dp)
        assertTrue(action.bottom - action.top >= 48.dp)
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).performClick()
        assertEquals(1, back)
    }

    @Test
    fun reducedMotionShowsStableEndStateAndKeepsNavigation() {
        var back = 0
        render(
            WorkoutCompletionSummary(1, 1, 80_000L),
            width = 360.dp,
            fontScale = 1f,
            onBack = { back += 1 },
            playAnimation = false
        )
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_MARK).assertIsDisplayed()
        composeRule.onNodeWithText("1 gyakorlat · 1 sorozat · 1:20").assertIsDisplayed()
        composeRule.onNodeWithTag(WORKOUT_COMPLETE_BACK).performClick()
        assertEquals(1, back)
    }

    private fun render(
        summary: WorkoutCompletionSummary,
        width: Dp,
        fontScale: Float,
        onBack: () -> Unit,
        playAnimation: Boolean = false
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
                        WorkoutCompletionScreen(
                            summary = summary,
                            onBackToOverview = onBack,
                            playAnimation = playAnimation
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
