package hu.laca.weighttracker.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.domain.DashboardSnapshot
import hu.laca.weighttracker.domain.WeeklyOverview
import hu.laca.weighttracker.domain.WeeklyOverviewLogic
import hu.laca.weighttracker.domain.calendar.MonthGridCalculator
import hu.laca.weighttracker.domain.model.ChartPoint
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.musclemap.MuscleHeatmapAssembler
import hu.laca.weighttracker.ui.components.UiFormatters
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
    fun weeklyOverviewColumnsAreEqualWidthWithLabelsAboveValues() {
        render()
        val workouts = composeRule.onNodeWithTag("dashboard_stat_workouts").getBoundsInRoot()
        val sets = composeRule.onNodeWithTag("dashboard_stat_sets").getBoundsInRoot()
        val weight = composeRule.onNodeWithTag("dashboard_stat_weight").getBoundsInRoot()
        val workoutWidth = workouts.right - workouts.left
        val setWidth = sets.right - sets.left
        val weightWidth = weight.right - weight.left
        assertEquals("workout vs sets width", workoutWidth.value, setWidth.value, 2f)
        assertEquals("sets vs weight width", setWidth.value, weightWidth.value, 2f)
        assertTrue("columns should not overlap", workouts.right <= sets.left + 1.dp)
        assertTrue("columns should not overlap", sets.right <= weight.left + 1.dp)
        assertLabelAboveValue("dashboard_stat_workouts")
        assertLabelAboveValue("dashboard_stat_sets")
        assertLabelAboveValue("dashboard_stat_weight")
        composeRule.onNodeWithTag("dashboard_stat_workouts_value").assertTextEquals("3")
        composeRule.onNodeWithTag("dashboard_stat_sets_value").assertTextEquals("18")
        composeRule.onNodeWithTag("dashboard_stat_weight_value").assertTextEquals("+0,4 kg")
    }

    @Test
    fun weeklyOverviewKeepsSettingsOnTheHeaderRight() {
        render()
        val header = composeRule.onNodeWithTag("dashboard_weekly_header").getBoundsInRoot()
        val kicker = composeRule.onNodeWithTag("dashboard_weekly_kicker").getBoundsInRoot()
        val settings = composeRule.onNodeWithTag("dashboard_weekly_settings").getBoundsInRoot()
        val stats = composeRule.onNodeWithTag("dashboard_weekly_stats").getBoundsInRoot()
        composeRule.onNodeWithContentDescription("Beállítások megnyitása").assertIsDisplayed()
        assertTrue("settings should sit right of the title", settings.left >= kicker.right - 1.dp)
        assertTrue("settings should stay in the header row", settings.top >= header.top - 1.dp)
        assertTrue("settings should stay in the header row", settings.bottom <= header.bottom + 1.dp)
        assertTrue("settings should not float into the stats band", settings.bottom <= stats.top + 1.dp)
        assertTrue("settings should stay on screen", settings.right <= 360.dp + 1.dp)
        assertTrue(settings.right - settings.left >= 48.dp)
        assertTrue(settings.bottom - settings.top >= 48.dp)
    }

    @Test
    fun missingWeightShowsEmDashWithoutExplanation() {
        render(weeklyOverview = WeeklyOverview())
        composeRule.onNodeWithTag("dashboard_stat_workouts_value").assertTextEquals("0")
        composeRule.onNodeWithTag("dashboard_stat_sets_value").assertTextEquals("0")
        composeRule.onNodeWithTag("dashboard_stat_weight_value").assertTextEquals("—")
        composeRule.onAllNodesWithText("Nincs elég testsúlyadat").assertCountEquals(0)
        composeRule.onAllNodesWithText("Nincs elég mérés a változáshoz").assertCountEquals(0)
        composeRule.onAllNodesWithText("Nincs elég mérés", substring = true).assertCountEquals(0)
    }

    @Test
    fun weeklyOverviewAnnouncesPeriodAndAllThreeValues() {
        render()
        val range = UiFormatters.inclusiveDateRange(WeeklyOverviewLogic.windowStart(today), today)
        composeRule.onNodeWithTag("dashboard_weekly_range").assertTextEquals(range)
        composeRule.onNodeWithTag("dashboard_weekly_overview")
            .assert(hasContentDescription("Elmúlt 7 nap", substring = true))
            .assert(hasContentDescription(range, substring = true))
            .assert(hasContentDescription("3 edzés", substring = true))
            .assert(hasContentDescription("18 sorozat", substring = true))
            .assert(hasContentDescription("+0,4 kg", substring = true))
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
        assertWeeklyOverviewFitsWithoutClipOrOverlap()
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

    private fun assertLabelAboveValue(prefix: String) {
        val label = composeRule.onNodeWithTag("${prefix}_label").getBoundsInRoot()
        val value = composeRule.onNodeWithTag("${prefix}_value").getBoundsInRoot()
        assertTrue("$prefix label should sit above value", label.bottom <= value.top + 2.dp)
        assertTrue(
            "$prefix label and value should share a column",
            label.left < value.right && value.left < label.right
        )
    }

    private fun assertNotClipped(tag: String) {
        val clipped = composeRule.onNodeWithTag(tag).getBoundsInRoot()
        val unclipped = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertEquals("$tag clipped width", clipped.right.value - clipped.left.value, unclipped.right.value - unclipped.left.value, 1.5f)
        assertEquals("$tag clipped height", clipped.bottom.value - clipped.top.value, unclipped.bottom.value - unclipped.top.value, 1.5f)
        assertTrue("$tag should stay on the 360dp canvas: $clipped", clipped.right <= 360.dp + 1.dp)
        assertTrue("$tag should not overflow left: $clipped", clipped.left >= (-1).dp)
    }

    private fun assertWeeklyOverviewFitsWithoutClipOrOverlap() {
        val header = composeRule.onNodeWithTag("dashboard_weekly_header").getBoundsInRoot()
        val kicker = composeRule.onNodeWithTag("dashboard_weekly_kicker").getBoundsInRoot()
        val settings = composeRule.onNodeWithTag("dashboard_weekly_settings").getBoundsInRoot()
        val workouts = composeRule.onNodeWithTag("dashboard_stat_workouts").getBoundsInRoot()
        val sets = composeRule.onNodeWithTag("dashboard_stat_sets").getBoundsInRoot()
        val weight = composeRule.onNodeWithTag("dashboard_stat_weight").getBoundsInRoot()
        listOf(
            "dashboard_weekly_overview",
            "dashboard_weekly_header",
            "dashboard_weekly_kicker",
            "dashboard_weekly_range",
            "dashboard_weekly_settings",
            "dashboard_weekly_stats",
            "dashboard_stat_workouts",
            "dashboard_stat_sets",
            "dashboard_stat_weight",
            "dashboard_stat_workouts_label",
            "dashboard_stat_workouts_value",
            "dashboard_stat_sets_label",
            "dashboard_stat_sets_value",
            "dashboard_stat_weight_label",
            "dashboard_stat_weight_value"
        ).forEach(::assertNotClipped)
        assertTrue("header and settings should not overlap", kicker.right <= settings.left + 1.dp)
        assertTrue("settings should stay in the header", settings.bottom <= header.bottom + 1.dp)
        assertTrue("workout and set columns should not overlap", !overlaps(workouts, sets))
        assertTrue("set and weight columns should not overlap", !overlaps(sets, weight))
        assertEquals((workouts.right - workouts.left).value, (sets.right - sets.left).value, 2f)
        assertEquals((sets.right - sets.left).value, (weight.right - weight.left).value, 2f)
        assertLabelAboveValue("dashboard_stat_workouts")
        assertLabelAboveValue("dashboard_stat_sets")
        assertLabelAboveValue("dashboard_stat_weight")
        composeRule.onNodeWithTag("dashboard_stat_workouts_value").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_stat_sets_value").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_stat_weight_value").assertIsDisplayed()
    }

    private fun overlaps(first: DpRect, second: DpRect): Boolean {
        return first.left < second.right - 1.dp &&
            second.left < first.right - 1.dp &&
            first.top < second.bottom - 1.dp &&
            second.top < first.bottom - 1.dp
    }

    private fun render(
        fontScale: Float = 1f,
        weeklyOverview: WeeklyOverview = WeeklyOverview(3, 18, 0.4),
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
                                weeklyOverview = weeklyOverview,
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
