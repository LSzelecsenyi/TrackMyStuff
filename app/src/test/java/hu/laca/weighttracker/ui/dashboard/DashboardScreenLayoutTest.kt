package hu.laca.weighttracker.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.domain.DashboardSnapshot
import hu.laca.weighttracker.domain.WeeklyOverview
import hu.laca.weighttracker.domain.calendar.MonthGridCalculator
import hu.laca.weighttracker.domain.model.ChartPoint
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.musclemap.MuscleHeatmapAssembler
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class DashboardScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun frontAndBackStaySideBySideOnNarrowPhone() {
        render()
        val front = composeRule.onNodeWithTag("muscle_map_front").getUnclippedBoundsInRoot()
        val back = composeRule.onNodeWithTag("muscle_map_back").getUnclippedBoundsInRoot()
        assertEquals(front.top.value, back.top.value, 1.5f)
        assertTrue(front.right.value <= back.left.value + 1.5f)
        assertTrue(front.right.value - front.left.value > 0f)
    }

    @Test
    fun sectionOrderIsHeatmapThenCalendarThenWeightChart() {
        render()
        val heatmap = composeRule.onNodeWithTag("dashboard_heatmap").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo()
        val calendar = composeRule.onNodeWithTag("dashboard_calendar").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("dashboard_weight_chart").performScrollTo()
        val chart = composeRule.onNodeWithTag("dashboard_weight_chart").getUnclippedBoundsInRoot()
        assertTrue(heatmap.top.value < calendar.top.value)
        assertTrue(calendar.top.value < chart.top.value)
        composeRule.onNodeWithText("Izomterhelés").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_calendar_title").assertExists()
        composeRule.onNodeWithText("Testsúlygörbe").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detailsActionOpensWeightDetails() {
        val opened = AtomicBoolean(false)
        render(onOpenWeightDetails = { opened.set(true) })
        composeRule.onNodeWithTag("dashboard_weight_details").performScrollTo().performClick()
        assertTrue(opened.get())
    }

    @Test
    fun calendarDaySelectionStillWorksAndFutureDaysStayBlocked() {
        val selected = AtomicReference<LocalDate?>(null)
        render(onDaySelected = { selected.set(it) })
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo()
        composeRule.onNode(hasContentDescription("2026. március 11.", substring = true)).performClick()
        assertEquals(today, selected.get())
        selected.set(null)
        composeRule.onAllNodes(hasContentDescription("jövőbeli nap, nem rögzíthető", substring = true))
            .onFirst()
            .performClick()
        assertEquals(null, selected.get())
    }

    @Test
    fun fontScale13KeepsSectionsUsableWithoutClippingKeyActions() {
        render(fontScale = 1.3f)
        composeRule.onNodeWithText("Izomterhelés").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_weight_details").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Részletek").assertIsDisplayed()
        composeRule.onNodeWithText("Legutóbbi").assertExists()
        composeRule.onNodeWithText("Időszak átlaga").assertExists()
    }

    @Test
    fun heatmapLegendContainsAllSevenCategories() {
        render()
        composeRule.onNodeWithTag("heatmap_legend").performScrollTo()
        listOf(
            "Ma",
            "1–2 napja",
            "3–4 napja",
            "5–6 napja",
            "7–13 napja",
            "14+ napja",
            "Még nem volt edzve"
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
    }

    private fun render(
        fontScale: Float = 1f,
        onDaySelected: (LocalDate) -> Unit = {},
        onOpenWeightDetails: () -> Unit = {}
    ) {
        val measurement = WeightMeasurement(1, today, 82.4, 0, 0)
        val month = YearMonth.from(today)
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
                        DashboardScreen(
                            state = DashboardUiState(
                                snapshot = DashboardSnapshot(
                                    isEmpty = false,
                                    latest = measurement,
                                    changeFromPreviousKg = 0.4,
                                    currentWeek = null,
                                    previousWeekChangeKg = -0.3,
                                    recentWeeks = emptyList(),
                                    chartPoints = listOf(
                                        ChartPoint(today.minusDays(2), 81.8),
                                        ChartPoint(today, 82.4)
                                    ),
                                    recentItems = emptyList(),
                                    todayHasMeasurement = true,
                                    measurementDates = setOf(today),
                                    chartRangeAverageKg = 82.1
                                ),
                                today = today,
                                weeklyOverview = WeeklyOverview(3, 18, 0.4),
                                displayedMonth = month,
                                monthGrid = MonthGridCalculator.grid(month, today, setOf(today)),
                                heatmap = MuscleHeatmapAssembler.assemble(emptyList(), today)
                            ),
                            onPreviousMonth = {},
                            onNextMonth = {},
                            onDaySelected = onDaySelected,
                            onDismissDaySheet = {},
                            onRecordSelectedDay = {},
                            onRequestDayDelete = {},
                            onDismissDayDelete = {},
                            onConfirmDayDelete = {},
                            onEditorDateChange = {},
                            onEditorWeightChange = {},
                            onSave = {},
                            onDismissEditor = {},
                            onDeleteRequest = {},
                            onDeleteDismiss = {},
                            onDeleteConfirm = {},
                            onMessageConsumed = {},
                            onOpenSettings = {},
                            onOpenWorkout = {},
                            onOpenWeightDetails = onOpenWeightDetails
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
