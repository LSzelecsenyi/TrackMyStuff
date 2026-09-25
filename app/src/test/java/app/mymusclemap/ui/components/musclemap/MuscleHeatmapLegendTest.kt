package app.mymusclemap.ui.components.musclemap

import app.mymusclemap.R
import app.mymusclemap.testString
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.musclemap.MuscleRecencyBand
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class MuscleHeatmapLegendTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun legendShowsAllSevenCategoriesAtNarrowWidthAndLargeFont() {
        renderLegend(fontScale = 1.3f)
        legendLabels.forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun legendWrapsInRowsWithoutOverflowAt360dpAndFontScale13() {
        renderLegend(fontScale = 1.3f)
        val legendClipped = composeRule.onNodeWithTag("heatmap_legend").getBoundsInRoot()
        val legendUnclipped = composeRule.onNodeWithTag("heatmap_legend").getUnclippedBoundsInRoot()
        assertEquals(legendClipped.right.value - legendClipped.left.value, legendUnclipped.right.value - legendUnclipped.left.value, 1.5f)
        assertEquals(legendClipped.bottom.value - legendClipped.top.value, legendUnclipped.bottom.value - legendUnclipped.top.value, 1.5f)
        assertTrue("legend should stay within 360dp: $legendClipped", legendClipped.right <= 360.dp + 1.dp)
        assertTrue(legendClipped.left >= (-1).dp)

        val items = MuscleRecencyBand.entries.map { band ->
            composeRule.onNodeWithTag("heatmap_legend_${band.name}").getBoundsInRoot()
        }
        items.forEachIndexed { index, bounds ->
            val unclipped = composeRule.onNodeWithTag("heatmap_legend_${MuscleRecencyBand.entries[index].name}")
                .getUnclippedBoundsInRoot()
            assertEquals(bounds.right.value - bounds.left.value, unclipped.right.value - unclipped.left.value, 1.5f)
            assertTrue("item $index should stay on canvas: $bounds", bounds.right <= 360.dp + 1.dp)
            assertTrue(bounds.left >= (-1).dp)
        }
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                assertTrue(
                    "legend items $i and $j should not overlap: ${items[i]} vs ${items[j]}",
                    !overlaps(items[i], items[j])
                )
            }
        }
        legendLabels.forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
            val clipped = composeRule.onNodeWithText(label).getBoundsInRoot()
            val unclipped = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertEquals("$label clipped width", clipped.right.value - clipped.left.value, unclipped.right.value - unclipped.left.value, 1.5f)
            assertTrue("$label should stay on canvas: $clipped", clipped.right <= 360.dp + 1.dp)
        }
    }

    private fun renderLegend(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Column(modifier = Modifier.width(360.dp)) {
                        HeatmapRecencyLegend()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun overlaps(first: DpRect, second: DpRect): Boolean {
        return first.left < second.right - 1.dp &&
            second.left < first.right - 1.dp &&
            first.top < second.bottom - 1.dp &&
            second.top < first.bottom - 1.dp
    }

    private val legendLabels = listOf(
        testString(R.string.heatmap_band_today),
        testString(R.string.heatmap_band_recent),
        testString(R.string.heatmap_band_days_3_4),
        testString(R.string.heatmap_band_days_5_6),
        testString(R.string.heatmap_band_old),
        testString(R.string.heatmap_band_inactive),
        testString(R.string.heatmap_band_never)
    )
}
