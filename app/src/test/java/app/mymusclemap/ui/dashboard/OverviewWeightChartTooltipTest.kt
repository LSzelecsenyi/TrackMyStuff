package app.mymusclemap.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.DashboardSnapshot
import app.mymusclemap.domain.model.ChartPoint
import app.mymusclemap.domain.model.ChartScale
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.domain.units.MeasurementText
import app.mymusclemap.domain.units.UnitSystem
import app.mymusclemap.ui.components.ChartCanvasMetrics
import app.mymusclemap.ui.components.ChartLayout
import app.mymusclemap.ui.components.OVERVIEW_WEIGHT_CHART_TOOLTIP
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.components.WeightChart
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import app.mymusclemap.ui.units.LocalUnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class OverviewWeightChartTooltipTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val firstDate = LocalDate.of(2026, 11, 1)
    private val middleDate = LocalDate.of(2026, 11, 5)
    private val lastDate = LocalDate.of(2026, 11, 8)
    private val points = listOf(
        ChartPoint(firstDate, 96.2),
        ChartPoint(middleDate, 88.5),
        ChartPoint(lastDate, 80.0)
    )

    @Test
    fun tappingAPointShowsTheStoredWeightAndEmptySpaceClearsIt() {
        renderOverview()
        composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_TOOLTIP).assertDoesNotExist()

        val middle = tapPoint(OVERVIEW_WEIGHT_CHART_PLOT, 1)
        assertTooltip(middleDate, 88.5, UnitSystem.METRIC)
        assertTooltipClearsPoint(middle)
        val axisMid = MeasurementText.axisWeight(
            ChartScale.yBounds(points.map { it.weightKg }, ChartValueDomain.Padded).let { (it.min + it.max) / 2.0 },
            UnitSystem.METRIC
        )
        composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_TOOLTIP)
            .assert(hasText(axisMid, substring = true).not())

        val last = tapPoint(OVERVIEW_WEIGHT_CHART_PLOT, 2)
        assertTooltip(lastDate, 80.0, UnitSystem.METRIC)
        assertTooltipClearsPoint(last)
        composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_TOOLTIP)
            .assert(hasText(UiFormatters.weightKg(88.5, UnitSystem.METRIC), substring = true).not())

        val first = tapPoint(OVERVIEW_WEIGHT_CHART_PLOT, 0)
        assertTooltip(firstDate, 96.2, UnitSystem.METRIC)
        assertTooltipClearsPoint(first)

        tapEmpty()
        composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_TOOLTIP).assertDoesNotExist()

        val again = tapPoint(OVERVIEW_WEIGHT_CHART_PLOT, 1)
        assertTooltip(middleDate, 88.5, UnitSystem.METRIC)
        assertTooltipClearsPoint(again)
    }

    @Test
    fun tooltipUsesTheConfiguredWeightUnit() {
        renderOverview(UnitSystem.IMPERIAL)
        tapPoint(OVERVIEW_WEIGHT_CHART_PLOT, 1)
        assertTooltip(middleDate, 88.5, UnitSystem.IMPERIAL)
    }

    @Test
    fun bodyProgressChartReportsThePointWithoutATooltip() {
        val selected = AtomicReference<ChartPoint?>(null)
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                Box(Modifier.width(360.dp)) {
                    WeightChart(
                        points = points,
                        contentDescription = "Body-weight chart",
                        onPointSelected = { selected.set(it) },
                        modifier = Modifier.testTag(OVERVIEW_WEIGHT_CHART_PLOT)
                    )
                }
            }
        }
        composeRule.waitForIdle()
        tapPoint(OVERVIEW_WEIGHT_CHART_PLOT, 1)
        tapPoint(OVERVIEW_WEIGHT_CHART_PLOT, 0)
        tapPoint(OVERVIEW_WEIGHT_CHART_PLOT, 2)
        assertEquals(lastDate, selected.get()?.date)
        assertEquals(80.0, selected.get()?.weightKg ?: 0.0, 0.0)
        composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_TOOLTIP).assertDoesNotExist()
    }

    private fun renderOverview(system: UnitSystem = UnitSystem.METRIC) {
        composeRule.setContent {
            CompositionLocalProvider(LocalUnitSystem provides system) {
                WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                    Box(Modifier.width(360.dp)) {
                        CompactWeightChartSection(
                            snapshot = DashboardSnapshot(
                                isEmpty = false,
                                latest = WeightMeasurement(1, lastDate, 80.0, 0, 0),
                                changeFromPreviousKg = null,
                                currentWeek = null,
                                previousWeekChangeKg = null,
                                recentWeeks = emptyList(),
                                chartPoints = points,
                                recentItems = emptyList(),
                                todayHasMeasurement = true,
                                chartRangeAverageKg = 91.3
                            ),
                            onOpenDetails = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapPoint(tag: String, index: Int): Offset {
        val node = composeRule.onNodeWithTag(tag)
        val bounds = node.getUnclippedBoundsInRoot()
        val layout = chartLayout(bounds)
        val offset = layout.mapped[index].offset
        node.performTouchInput { click(offset) }
        composeRule.waitForIdle()
        return offset
    }

    private fun tapEmpty() {
        val node = composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_PLOT)
        val bounds = node.getUnclippedBoundsInRoot()
        val heightPx = with(composeRule.density) { (bounds.bottom - bounds.top).toPx() }
        node.performTouchInput { click(Offset(2f, heightPx / 2f)) }
        composeRule.waitForIdle()
    }

    private fun chartLayout(bounds: DpRect): ChartLayout {
        val density = composeRule.density
        return with(density) {
            ChartLayout.from(
                width = (bounds.right - bounds.left).toPx(),
                height = (bounds.bottom - bounds.top).toPx(),
                points = points.map { it.toSeriesPoint() },
                left = ChartCanvasMetrics.plotLeft.toPx(),
                rightInset = ChartCanvasMetrics.plotRightInset.toPx(),
                top = ChartCanvasMetrics.plotTop.toPx(),
                bottomInset = ChartCanvasMetrics.plotBottomInset.toPx(),
                valueDomain = ChartValueDomain.Padded
            )
        }
    }

    private fun assertTooltip(date: LocalDate, kilograms: Double, system: UnitSystem) {
        composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_TOOLTIP)
            .assertIsDisplayed()
            .assert(hasText(UiFormatters.compactDate(date), substring = true))
            .assert(hasText(UiFormatters.weightKg(kilograms, system), substring = true))
    }

    private fun assertTooltipClearsPoint(anchorInPlot: Offset) {
        val plot = composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_PLOT).getUnclippedBoundsInRoot()
        val tip = composeRule.onNodeWithTag(OVERVIEW_WEIGHT_CHART_TOOLTIP).getUnclippedBoundsInRoot()
        assertTrue(tip.left >= plot.left - 1.dp)
        assertTrue(tip.top >= plot.top - 1.dp)
        assertTrue(tip.right <= plot.right + 1.dp)
        assertTrue(tip.bottom <= plot.bottom + 1.dp)
        val density = composeRule.density
        val x = plot.left + with(density) { anchorInPlot.x.toDp() }
        val y = plot.top + with(density) { anchorInPlot.y.toDp() }
        val marker = DpRect(x - 8.dp, y - 8.dp, x + 8.dp, y + 8.dp)
        val overlaps = tip.left < marker.right &&
            tip.right > marker.left &&
            tip.top < marker.bottom &&
            tip.bottom > marker.top
        assertFalse(overlaps)
    }
}
