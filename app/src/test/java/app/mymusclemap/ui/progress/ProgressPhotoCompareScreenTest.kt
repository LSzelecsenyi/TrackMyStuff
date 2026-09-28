package app.mymusclemap.ui.progress

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import app.mymusclemap.R
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ProgressPhotoCompareScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val earlier = LocalDate.of(2026, 9, 1)
    private val later = LocalDate.of(2026, 9, 27)

    @Test
    fun sliderIsTheDefaultAndDividerStartsAtHalf() {
        var backed = false
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                compareScreen(onBack = { backed = true })
            }
        }
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_SLIDER).assertIsSelected()
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_VIEWPORT).assertIsDisplayed()
        composeRule.onNodeWithText("Sep 1").assertIsDisplayed()
        composeRule.onNodeWithText("Sep 27").assertIsDisplayed()
        composeRule.onNodeWithText("|").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            testString(R.string.progress_photos_compare_position, "Sep 1", "Sep 27")
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            testString(R.string.progress_photos_compare_earlier, "Sep 1"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            testString(R.string.progress_photos_compare_later, "Sep 27"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        assertEquals(0.5f, reveal(), 0.001f)
        val viewport = composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_VIEWPORT).getBoundsInRoot()
        val divider = composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_DIVIDER).getBoundsInRoot()
        val viewportMid = (viewport.left.value + viewport.right.value) / 2f
        val dividerMid = (divider.left.value + divider.right.value) / 2f
        assertEquals(viewportMid, dividerMid, 1.5f)
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertTrue(backed)
    }

    @Test
    fun draggingAndSemanticsMoveTheRevealWithoutResizingPhotos() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                compareScreen()
            }
        }
        val leftBefore = composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_LEFT).getBoundsInRoot()
        val rightBefore = composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_RIGHT).getBoundsInRoot()
        assertEquals(leftBefore, rightBefore)
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_VIEWPORT).performTouchInput {
            swipe(
                start = Offset(width * 0.5f, height / 2f),
                end = Offset(width * 0.8f, height / 2f),
                durationMillis = 200
            )
        }
        assertEquals(0.8f, reveal(), 0.08f)
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_VIEWPORT)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.6f) }
        assertEquals(1f, reveal(), 0.001f)
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_VIEWPORT)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-0.4f) }
        assertEquals(0f, reveal(), 0.001f)
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_VIEWPORT)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
        assertEquals(0.25f, reveal(), 0.001f)
        assertEquals(leftBefore, composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_LEFT).getBoundsInRoot())
        assertEquals(rightBefore, composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_RIGHT).getBoundsInRoot())
    }

    @Test
    fun sideBySideKeepsTheSamePhotos() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                compareScreen()
            }
        }
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_SIDE_BY_SIDE).performClick()
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_SIDE_BY_SIDE).assertIsSelected()
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_VIEWPORT).assertDoesNotExist()
        composeRule.onNodeWithText("Sep 1").assertIsDisplayed()
        composeRule.onNodeWithText("Sep 27").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            testString(R.string.progress_photos_compare_earlier, "Sep 1"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            testString(R.string.progress_photos_compare_later, "Sep 27"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_SLIDER).performClick()
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_SLIDER).assertIsSelected()
        composeRule.onNodeWithText("Sep 1").assertIsDisplayed()
        composeRule.onNodeWithText("Sep 27").assertIsDisplayed()
        assertEquals(0.5f, reveal(), 0.001f)
    }

    private fun reveal(): Float {
        val node = composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_VIEWPORT).fetchSemanticsNode()
        return node.config[SemanticsProperties.ProgressBarRangeInfo].current
    }

    @androidx.compose.runtime.Composable
    private fun compareScreen(onBack: () -> Unit = {}) {
        ProgressPhotoCompareScreen(
            beforeDate = earlier,
            before = bitmap(8, 4, Color.RED),
            beforeMissing = false,
            afterDate = later,
            after = bitmap(4, 8, Color.BLUE),
            afterMissing = false,
            onBack = onBack
        )
    }

    private fun bitmap(width: Int, height: Int, color: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    }
}
