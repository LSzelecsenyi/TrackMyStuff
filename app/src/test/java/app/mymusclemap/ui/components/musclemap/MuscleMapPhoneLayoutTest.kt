package app.mymusclemap.ui.components.musclemap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.TemplateMuscleMapState
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp")
class MuscleMapPhoneLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onePlusWidthKeepsFrontAndBackSideBySide() {
        assertSideBySideMap(width = 280.dp, fontScale = 1f)
    }

    @Test
    fun emulatorWidthKeepsFrontAndBackSideBySide() {
        assertSideBySideMap(width = 331.dp, fontScale = 1f)
    }

    @Test
    fun fontScaleDoesNotStackFigures() {
        assertSideBySideMap(width = 280.dp, fontScale = 1.3f)
    }

    @Test
    fun heatmapLegendStaysBelowSideBySideFigures() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MuscleHeatmapCard(
                        state = MuscleHeatmapAssembler.assemble(emptyList(), LocalDate.parse("2026-09-16"))
                    )
                }
            }
        }
        val front = composeRule.onNodeWithTag("muscle_map_front").getUnclippedBoundsInRoot()
        val back = composeRule.onNodeWithTag("muscle_map_back").getUnclippedBoundsInRoot()
        val legend = composeRule.onNodeWithTag("heatmap_legend").getUnclippedBoundsInRoot()
        assertEquals(front.top.value, back.top.value, 1.5f)
        assertTrue(front.right.value <= back.left.value + 1.5f)
        val figuresBottom = maxOf(front.bottom.value, back.bottom.value)
        assertTrue(
            "legend.top=${legend.top.value} figures.bottom=$figuresBottom",
            legend.top.value + 8f >= figuresBottom
        )
        composeRule.onNodeWithTag("heatmap_legend").performScrollTo()
        composeRule.onNodeWithText("Még nem volt edzve").assertIsDisplayed()
    }

    @Test
    fun templatePreviewStaysSideBySideAndUsable() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TemplateMuscleMapCard(
                        state = TemplateMuscleMapState(
                            emphasis = emptyMap(),
                            hasFullBody = false,
                            hasCardiovascular = false
                        )
                    )
                }
            }
        }
        val front = composeRule.onNodeWithTag("muscle_map_front").getUnclippedBoundsInRoot()
        val back = composeRule.onNodeWithTag("muscle_map_back").getUnclippedBoundsInRoot()
        assertTrue(frontWidth(front) > 0.dp)
        assertEquals(frontWidth(front).value, frontWidth(back).value, 1.5f)
        assertEquals(front.top.value, back.top.value, 1.5f)
        assertTrue(front.right.value <= back.left.value + 1.5f)
        composeRule.onNodeWithText("Elsődleges").assertIsDisplayed()
        composeRule.onNodeWithText("Másodlagos").assertIsDisplayed()
    }

    private fun assertSideBySideMap(width: Dp, fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Column(modifier = Modifier.width(width)) {
                        MuscleMap(
                            fills = emptyMap(),
                            unmappedFill = Color.LightGray,
                            outline = Color.DarkGray,
                            mappedOutline = Color.DarkGray,
                            selectedOutline = Color.Black,
                            selected = null,
                            onSelect = {},
                            contentDescription = "map",
                            modifier = Modifier.testTag("muscle_map_host")
                        )
                    }
                }
            }
        }
        val front = composeRule.onNodeWithTag("muscle_map_front").getUnclippedBoundsInRoot()
        val back = composeRule.onNodeWithTag("muscle_map_back").getUnclippedBoundsInRoot()
        assertTrue(frontWidth(front) > 0.dp)
        assertTrue(frontWidth(back) > 0.dp)
        assertEquals(frontWidth(front).value, frontWidth(back).value, 1.5f)
        assertEquals(front.top.value, back.top.value, 1.5f)
        assertTrue("stacked vertically at $width", front.right.value <= back.left.value + 1.5f)
        val expectedAspect = BodyMusclesArtworkAspect
        assertTrue(abs(frontHeight(front).value / frontWidth(front).value - expectedAspect) < 0.05f)
        assertTrue(abs(frontHeight(back).value / frontWidth(back).value - expectedAspect) < 0.05f)
        val occupied = frontWidth(front).value + frontWidth(back).value
        assertTrue(occupied <= width.value + 1.5f)
    }
}

private fun frontWidth(bounds: androidx.compose.ui.unit.DpRect): Dp = bounds.right - bounds.left

private fun frontHeight(bounds: androidx.compose.ui.unit.DpRect): Dp = bounds.bottom - bounds.top

private val BodyMusclesArtworkAspect =
    app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesArtwork.VIEW_HEIGHT /
        app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesArtwork.VIEW_WIDTH
