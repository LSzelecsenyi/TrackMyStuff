package app.mymusclemap.ui.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetApplyActionsLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionsStayReadableAt320dp() {
        assertReadableLayout(width = 320.dp, fontScale = 1f)
    }

    @Test
    fun actionsStayReadableAt360dp() {
        assertReadableLayout(width = 360.dp, fontScale = 1f)
    }

    @Test
    fun actionsStayReadableAtNord2tWidth() {
        assertReadableLayout(width = 412.dp, fontScale = 1f)
    }

    @Test
    fun actionsStayReadableAtIncreasedFontScale() {
        assertReadableLayout(width = 360.dp, fontScale = 1.3f, maxActionHeight = 120.dp)
    }

    private fun assertReadableLayout(
        width: Dp,
        fontScale: Float,
        maxActionHeight: Dp = 96.dp
    ) {
        val remaining = "Alkalmazás a további sorozatokra"
        val all = "Alkalmazás minden sorozatra"
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Column(
                        modifier = Modifier
                            .width(width)
                            .height(640.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SetApplyActions(onApplyRemaining = {}, onApplyAll = {})
                        }
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AppDimens.minTouch)
                        ) {
                            Text("Mentés")
                        }
                    }
                }
            }
        }
        val remainingNode = composeRule.onNodeWithText(remaining)
        val allNode = composeRule.onNodeWithText(all)
        remainingNode.assertIsDisplayed()
        allNode.assertIsDisplayed()
        val remainingBounds = remainingNode.getBoundsInRoot()
        val allBounds = allNode.getBoundsInRoot()
        val remainingHeight = remainingBounds.bottom - remainingBounds.top
        val allHeight = allBounds.bottom - allBounds.top
        assertTrue(remainingHeight <= maxActionHeight)
        assertTrue(allHeight <= maxActionHeight)
        assertTrue(remainingBounds.bottom <= allBounds.top)
        val saveBounds = composeRule.onNodeWithText("Mentés").assertIsDisplayed().getBoundsInRoot()
        assertTrue(saveBounds.bottom <= 640.dp)
        assertTrue((saveBounds.bottom - saveBounds.top) >= 48.dp)
    }
}
