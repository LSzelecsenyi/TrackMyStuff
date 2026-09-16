package hu.laca.weighttracker.ui.components.musclemap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MuscleHeatmapLegendTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun legendShowsAllSevenCategoriesAtNarrowWidthAndLargeFont() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.3f)
            ) {
                WeightTrackerThemeForPreview {
                    Column(modifier = Modifier.width(360.dp)) {
                        HeatmapRecencyLegend()
                    }
                }
            }
        }
        listOf(
            "Ma",
            "1–2 napja",
            "3–4 napja",
            "5–6 napja",
            "7–13 napja",
            "14+ napja",
            "Még nem volt edzve"
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }
}
