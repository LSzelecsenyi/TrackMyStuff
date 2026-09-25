package app.mymusclemap.ui.dashboard

import app.mymusclemap.R
import app.mymusclemap.testQuantity
import app.mymusclemap.testString
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
import androidx.compose.ui.test.onAllNodesWithTag
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
import app.mymusclemap.domain.DashboardSnapshot
import app.mymusclemap.domain.WeeklyOverview
import app.mymusclemap.domain.WeeklyOverviewLogic
import app.mymusclemap.domain.calendar.MonthGrid
import app.mymusclemap.domain.calendar.MonthGridCalculator
import app.mymusclemap.domain.model.ChartPoint
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.onboarding.OnboardingChecklist
import app.mymusclemap.domain.onboarding.OnboardingFacts
import app.mymusclemap.domain.onboarding.OnboardingFlags
import app.mymusclemap.domain.onboarding.OnboardingGuide
import app.mymusclemap.domain.onboarding.OnboardingResumeTarget
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.onboarding.ONBOARDING_CALENDAR_COACH
import app.mymusclemap.ui.onboarding.ONBOARDING_HEATMAP_COACH
import app.mymusclemap.ui.onboarding.ONBOARDING_REMINDER
import app.mymusclemap.ui.onboarding.ONBOARDING_REMINDER_CONTINUE
import app.mymusclemap.ui.onboarding.ONBOARDING_REMINDER_DISMISS
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        composeRule.onNodeWithText(testString(R.string.heatmap_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_calendar_title").assertExists()
        composeRule.onNodeWithText(testString(R.string.chart_title)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Muscle load").assertCountEquals(0)
    }

    @Test
    fun muscleHeatmapSectionUsesRenamedTitle() {
        render()
        composeRule.onNodeWithText("Muscle heatmap").assertIsDisplayed()
        composeRule.onAllNodesWithText("Muscle load").assertCountEquals(0)
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
        composeRule.onNodeWithTag("dashboard_stat_weight_value").assertTextEquals("+0.4 kg")
    }

    @Test
    fun weeklyOverviewKeepsOverflowOnTheHeaderRight() {
        render()
        val header = composeRule.onNodeWithTag("dashboard_weekly_header").getBoundsInRoot()
        val kicker = composeRule.onNodeWithTag("dashboard_weekly_kicker").getBoundsInRoot()
        val overflow = composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_BUTTON).getBoundsInRoot()
        val stats = composeRule.onNodeWithTag("dashboard_weekly_stats").getBoundsInRoot()
        composeRule.onNodeWithContentDescription(testString(R.string.action_more_overview)).assertIsDisplayed()
        assertTrue("overflow should sit right of the title", overflow.left >= kicker.right - 1.dp)
        assertTrue("overflow should stay in the header row", overflow.top >= header.top - 1.dp)
        assertTrue("overflow should stay in the header row", overflow.bottom <= header.bottom + 1.dp)
        assertTrue("overflow should not float into the stats band", overflow.bottom <= stats.top + 1.dp)
        assertTrue("overflow should stay on screen", overflow.right <= 360.dp + 1.dp)
        assertTrue(overflow.right - overflow.left >= 48.dp)
        assertTrue(overflow.bottom - overflow.top >= 48.dp)
    }

    @Test
    fun missingWeightShowsEmDashWithoutExplanation() {
        render(weeklyOverview = WeeklyOverview())
        composeRule.onNodeWithTag("dashboard_stat_workouts_value").assertTextEquals("0")
        composeRule.onNodeWithTag("dashboard_stat_sets_value").assertTextEquals("0")
        composeRule.onNodeWithTag("dashboard_stat_weight_value").assertTextEquals("—")
        composeRule.onAllNodesWithText("Not enough body-weight data").assertCountEquals(0)
        composeRule.onAllNodesWithText("Not enough measurements for a change").assertCountEquals(0)
        composeRule.onAllNodesWithText("Not enough measurements", substring = true).assertCountEquals(0)
    }

    @Test
    fun weeklyOverviewAnnouncesPeriodAndAllThreeValues() {
        render()
        val range = UiFormatters.inclusiveDateRange(WeeklyOverviewLogic.windowStart(today), today)
        composeRule.onNodeWithTag("dashboard_weekly_range").assertTextEquals(range)
        composeRule.onNodeWithTag("dashboard_weekly_overview")
            .assert(hasContentDescription("Last 7 days", substring = true))
            .assert(hasContentDescription(range, substring = true))
            .assert(hasContentDescription(testQuantity(R.plurals.weekly_overview_workouts, 3), substring = true))
            .assert(hasContentDescription(testQuantity(R.plurals.weekly_overview_sets, 18), substring = true))
            .assert(hasContentDescription("+0.4 kg", substring = true))
    }

    @Test
    fun detailsActionOpensWeightDetails() {
        val opened = AtomicBoolean(false)
        render(onOpenWeightDetails = { opened.set(true) })
        composeRule.onNodeWithTag("dashboard_weight_details").performScrollTo().performClick()
        assertTrue(opened.get())
    }

    @Test
    fun calendarDaySelectionStillWorksAndFutureDaysAreSelectable() {
        val selected = AtomicReference<LocalDate?>(null)
        render(onDaySelected = { selected.set(it) })
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo()
        composeRule.onNode(hasContentDescription("March 11, 2026", substring = true)).performClick()
        assertEquals(today, selected.get())
        selected.set(null)
        composeRule.onAllNodes(hasContentDescription(testString(R.string.calendar_future_label), substring = true))
            .onFirst()
            .performClick()
        assertEquals(LocalDate.of(2026, 3, 12), selected.get())
        composeRule.onNodeWithText(testString(R.string.calendar_legend_planned)).assertExists()
    }

    @Test
    fun fontScale13KeepsSectionsUsableWithoutClippingKeyActions() {
        render(fontScale = 1.3f)
        composeRule.onNodeWithText(testString(R.string.heatmap_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_weight_details").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_open_details)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.weight_stat_latest_label)).assertExists()
        composeRule.onNodeWithText(testString(R.string.weight_stat_average_label)).assertExists()
        assertWeeklyOverviewFitsWithoutClipOrOverlap()
    }

    @Test
    fun givenOverflowMenuWhenOpenedThenItStaysOnTheRightAndRoutesFireOnce() {
        var templates = 0
        var catalog = 0
        var settings = 0
        render(
            onOpenTemplates = { templates += 1 },
            onOpenCatalog = { catalog += 1 },
            onOpenSettings = { settings += 1 }
        )
        val header = composeRule.onNodeWithTag("dashboard_weekly_header").getBoundsInRoot()
        val anchor = composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_ANCHOR, useUnmergedTree = true).getBoundsInRoot()
        val button = composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_BUTTON).getBoundsInRoot()
        assertTrue(
            "anchor should wrap the overflow icon, not the header: anchor=$anchor header=$header",
            anchor.right - anchor.left < (header.right - header.left) / 2
        )
        assertTrue(
            "anchor should sit on the right of the header: anchor=$anchor header=$header",
            anchor.left > (header.left + header.right) / 2
        )
        composeRule.onNodeWithContentDescription(testString(R.string.action_more_overview)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_MENU).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.templates_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.exercises_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.settings_title)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.nav_journal)).assertCountEquals(0)
        val menu = composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_MENU).getBoundsInRoot()
        val popup = popupWindowLayoutParams().maxByOrNull { params -> params.x }
            ?: error("expected a DropdownMenu popup window")
        val density = composeRule.density.density
        val popupLeft = (popup.x / density).dp
        val popupRight = popupLeft + (menu.right - menu.left)
        assertTrue(
            "menu must not open at the left edge: popupLeft=$popupLeft popupRight=$popupRight button=$button",
            popupLeft > 40.dp
        )
        assertTrue(
            "menu should stay on the right near the overflow icon: popupRight=$popupRight header=$header",
            popupRight > (header.left + header.right) / 2
        )
        composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_TEMPLATES).performClick()
        composeRule.onNodeWithContentDescription(testString(R.string.action_more_overview)).performClick()
        composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_EXERCISES).performClick()
        composeRule.onNodeWithContentDescription(testString(R.string.action_more_overview)).performClick()
        composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_SETTINGS).performClick()
        assertEquals(1, templates)
        assertEquals(1, catalog)
        assertEquals(1, settings)
    }

    @Test
    fun calendarShowsPlannedAndCompletedMarkersSeparately() {
        render(
            monthGrid = MonthGridCalculator.grid(
                month = YearMonth.from(today),
                today = today,
                measuredDates = setOf(today),
                completedWorkoutCounts = mapOf(today to 1),
                plannedWorkoutCounts = mapOf(today to 1)
            )
        )
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo()
        composeRule.onNodeWithTag("calendar-legend-planned").assertExists()
        composeRule.onNodeWithText(testString(R.string.calendar_legend_planned)).assertExists()
        composeRule.onAllNodesWithTag("calendar-planned-dot", useUnmergedTree = true).onFirst().assertExists()
        composeRule.onAllNodesWithTag("calendar-completed-dot", useUnmergedTree = true).onFirst().assertExists()
        composeRule.onAllNodesWithTag("calendar-weight-dot", useUnmergedTree = true).onFirst().assertExists()
        val todayCell = composeRule.onNode(hasContentDescription("March 11, 2026", substring = true))
        todayCell.assert(hasContentDescription(testQuantity(R.plurals.calendar_completed_workouts, 1), substring = true))
        todayCell.assert(hasContentDescription(testQuantity(R.plurals.calendar_planned_workouts, 1), substring = true))
        val cellBounds = todayCell.getBoundsInRoot()
        assertTrue(cellBounds.bottom - cellBounds.top <= 48.dp)
    }

    @Test
    fun calendarMarkerCombinationsStayInOneRowAtPhoneWidthAndFontScale() {
        val month = YearMonth.from(today)
        val weightOnly = LocalDate.of(2026, 3, 1)
        val completedOnly = LocalDate.of(2026, 3, 2)
        val plannedOnly = LocalDate.of(2026, 3, 3)
        val weightPlanned = LocalDate.of(2026, 3, 4)
        val weightCompleted = LocalDate.of(2026, 3, 5)
        val plannedCompleted = LocalDate.of(2026, 3, 6)
        val allThree = LocalDate.of(2026, 3, 7)
        val manyPlanned = LocalDate.of(2026, 3, 8)
        render(
            fontScale = 1.3f,
            monthGrid = MonthGridCalculator.grid(
                month = month,
                today = today,
                measuredDates = setOf(weightOnly, weightPlanned, weightCompleted, allThree),
                completedWorkoutCounts = mapOf(
                    completedOnly to 1,
                    weightCompleted to 1,
                    plannedCompleted to 1,
                    allThree to 2
                ),
                plannedWorkoutCounts = mapOf(
                    plannedOnly to 1,
                    weightPlanned to 1,
                    plannedCompleted to 1,
                    allThree to 1,
                    manyPlanned to 3
                )
            )
        )
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo()
        composeRule.onAllNodesWithTag("calendar-planned-dot", useUnmergedTree = true).assertCountEquals(5)
        composeRule.onAllNodesWithTag("calendar-completed-dot", useUnmergedTree = true).assertCountEquals(4)
        composeRule.onAllNodesWithTag("calendar-weight-dot", useUnmergedTree = true).assertCountEquals(4)
        val crowded = composeRule.onNode(hasContentDescription("March 7, 2026", substring = true))
        val bounds = crowded.getBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top <= 48.dp)
        crowded.assert(hasContentDescription(testQuantity(R.plurals.calendar_planned_workouts, 1), substring = true))
        crowded.assert(hasContentDescription(testQuantity(R.plurals.calendar_completed_workouts, 2), substring = true))
        val many = composeRule.onNode(hasContentDescription("March 8, 2026", substring = true))
        many.assert(hasContentDescription(testQuantity(R.plurals.calendar_planned_workouts, 3), substring = true))
        assertNotClipped("calendar-legend")
        composeRule.onNodeWithTag("calendar-legend-planned").assertExists()
        composeRule.onNodeWithTag("calendar-legend-completed").assertExists()
        composeRule.onNodeWithTag("calendar-legend-weight").assertExists()
    }

    @Test
    fun calendarMarkersRemainDistinctInDarkTheme() {
        render(
            darkTheme = true,
            monthGrid = MonthGridCalculator.grid(
                month = YearMonth.from(today),
                today = today,
                measuredDates = setOf(today),
                completedWorkoutCounts = mapOf(today to 1),
                plannedWorkoutCounts = mapOf(today to 1)
            )
        )
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo()
        composeRule.onAllNodesWithTag("calendar-planned-dot", useUnmergedTree = true).onFirst().assertExists()
        composeRule.onAllNodesWithTag("calendar-completed-dot", useUnmergedTree = true).onFirst().assertExists()
        composeRule.onAllNodesWithTag("calendar-weight-dot", useUnmergedTree = true).onFirst().assertExists()
        composeRule.onNodeWithText(testString(R.string.calendar_legend_planned)).assertExists()
        composeRule.onNodeWithText(testString(R.string.nav_workout)).assertExists()
    }

    @Test
    fun heatmapLegendContainsAllSevenCategories() {
        render()
        composeRule.onNodeWithTag("heatmap_legend").performScrollTo()
        listOf(
            testString(R.string.heatmap_band_today),
            testString(R.string.heatmap_band_recent),
            testString(R.string.heatmap_band_days_3_4),
            testString(R.string.heatmap_band_days_5_6),
            testString(R.string.heatmap_band_old),
            testString(R.string.heatmap_band_inactive),
            testString(R.string.heatmap_band_never)
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
        val overflow = composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_BUTTON).getBoundsInRoot()
        val workouts = composeRule.onNodeWithTag("dashboard_stat_workouts").getBoundsInRoot()
        val sets = composeRule.onNodeWithTag("dashboard_stat_sets").getBoundsInRoot()
        val weight = composeRule.onNodeWithTag("dashboard_stat_weight").getBoundsInRoot()
        listOf(
            "dashboard_weekly_overview",
            "dashboard_weekly_header",
            "dashboard_weekly_kicker",
            "dashboard_weekly_range",
            OVERVIEW_OVERFLOW_BUTTON,
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
        assertTrue("header and overflow should not overlap", kicker.right <= overflow.left + 1.dp)
        assertTrue("overflow should stay in the header", overflow.bottom <= header.bottom + 1.dp)
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

    @Test
    fun skippedOnboardingShowsContinuationCardUntilDismissed() {
        val continued = AtomicBoolean(false)
        val dismissed = AtomicBoolean(false)
        render(
            onboarding = reminderGuide(),
            onContinueOnboarding = { continued.set(true) },
            onDismissOnboardingReminder = { dismissed.set(true) }
        )
        composeRule.onNodeWithTag(ONBOARDING_REMINDER).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.onboarding_reminder_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.onboarding_step_plan), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.onboarding_step_workout), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_plan)}").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performClick()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_DISMISS).performClick()
        assertTrue(continued.get())
        assertTrue(dismissed.get())
    }

    @Test
    fun reminderMarksPlanCreatedFromDomainState() {
        render(
            onboarding = OnboardingGuide(
                flags = OnboardingFlags(started = true),
                facts = OnboardingFacts(hasPlan = true),
                reminderVisible = true,
                checklist = OnboardingChecklist(planCreated = true),
                resumeTarget = OnboardingResumeTarget.StartWorkout
            )
        )
        composeRule.onNodeWithTag(ONBOARDING_REMINDER).assertIsDisplayed()
        composeRule.onNodeWithText("●  ${testString(R.string.onboarding_step_plan)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_workout)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_heatmap)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_weight)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_history)}").assertIsDisplayed()
    }

    @Test
    fun reminderMarksCompletedWorkoutBeforeHeatmapDiscovery() {
        render(
            onboarding = OnboardingGuide(
                flags = OnboardingFlags(started = true),
                facts = OnboardingFacts(hasPlan = true, hasCompletedWorkout = true),
                reminderVisible = true,
                checklist = OnboardingChecklist(planCreated = true, firstWorkoutDone = true),
                resumeTarget = OnboardingResumeTarget.Heatmap,
                showHeatmapCoach = true
            )
        )
        composeRule.onNodeWithText("●  ${testString(R.string.onboarding_step_plan)}").assertIsDisplayed()
        composeRule.onNodeWithText("●  ${testString(R.string.onboarding_step_workout)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_heatmap)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_weight)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_history)}").assertIsDisplayed()
    }

    @Test
    fun reminderMarksHeatmapDoneAfterAcknowledgement() {
        render(
            onboarding = OnboardingGuide(
                flags = OnboardingFlags(started = true, heatmapSeen = true),
                facts = OnboardingFacts(hasPlan = true, hasCompletedWorkout = true),
                reminderVisible = true,
                checklist = OnboardingChecklist(
                    planCreated = true,
                    firstWorkoutDone = true,
                    heatmapDone = true
                ),
                resumeTarget = OnboardingResumeTarget.WeightPrompt
            )
        )
        composeRule.onNodeWithText("●  ${testString(R.string.onboarding_step_plan)}").assertIsDisplayed()
        composeRule.onNodeWithText("●  ${testString(R.string.onboarding_step_workout)}").assertIsDisplayed()
        composeRule.onNodeWithText("●  ${testString(R.string.onboarding_step_heatmap)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_weight)}").assertIsDisplayed()
        composeRule.onNodeWithText("○  ${testString(R.string.onboarding_step_history)}").assertIsDisplayed()
    }

    @Test
    fun existingUserStateDoesNotShowOnboardingCard() {
        render()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_COACH).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_CALENDAR_COACH).assertDoesNotExist()
    }

    @Test
    fun heatmapCoachDoesNotAutoAppearOnTheRealHeatmap() {
        render(
            onboarding = OnboardingGuide(
                flags = OnboardingFlags(started = true),
                facts = OnboardingFacts(hasPlan = true, hasCompletedWorkout = true),
                reminderVisible = true,
                checklist = OnboardingChecklist(planCreated = true, firstWorkoutDone = true),
                resumeTarget = OnboardingResumeTarget.Heatmap,
                showHeatmapCoach = true,
                showHeatmapCompletionCta = true
            )
        )
        composeRule.onNodeWithTag("dashboard_heatmap").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_COACH).assertDoesNotExist()
    }

    @Test
    fun heatmapRevealRequestScrollsToTheRealHeatmapAndReportsBounds() {
        val revealed = AtomicBoolean(false)
        val bounds = AtomicReference<androidx.compose.ui.geometry.Rect?>(null)
        render(
            onboarding = OnboardingGuide(
                flags = OnboardingFlags(started = true),
                facts = OnboardingFacts(hasPlan = true, hasCompletedWorkout = true),
                reminderVisible = true,
                checklist = OnboardingChecklist(planCreated = true, firstWorkoutDone = true),
                resumeTarget = OnboardingResumeTarget.Heatmap,
                showHeatmapCoach = true,
                heatmapRevealRequested = true
            ),
            heatmapRevealRequested = true,
            onHeatmapBounds = { bounds.set(it) },
            onHeatmapRevealed = { revealed.set(true) }
        )
        composeRule.waitUntil(timeoutMillis = 5_000) { revealed.get() }
        composeRule.onNodeWithTag("dashboard_heatmap").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.heatmap_title)).assertIsDisplayed()
        val rect = bounds.get()
        assertNotNull(rect)
        assertTrue(rect!!.width > 1f)
        assertTrue(rect.height > 1f)
    }

    @Test
    fun calendarCoachIsShownOnceOnTheRealCalendar() {
        val confirmed = AtomicBoolean(false)
        render(
            onboarding = OnboardingGuide(
                flags = OnboardingFlags(started = true, heatmapSeen = true, weightIntroduced = true),
                showCalendarCoach = true
            ),
            onConfirmCalendarCoach = { confirmed.set(true) }
        )
        composeRule.onNodeWithTag("dashboard_calendar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_CALENDAR_COACH).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.onboarding_calendar_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.onboarding_got_it)).performClick()
        assertTrue(confirmed.get())
    }

    private fun reminderGuide(): OnboardingGuide {
        return OnboardingGuide(
            flags = OnboardingFlags(started = true),
            reminderVisible = true,
            checklist = OnboardingChecklist(),
            resumeTarget = OnboardingResumeTarget.CreatePlan
        )
    }

    private fun overlaps(first: DpRect, second: DpRect): Boolean {
        return first.left < second.right - 1.dp &&
            second.left < first.right - 1.dp &&
            first.top < second.bottom - 1.dp &&
            second.top < first.bottom - 1.dp
    }

    private fun render(
        fontScale: Float = 1f,
        darkTheme: Boolean = false,
        weeklyOverview: WeeklyOverview = WeeklyOverview(3, 18, 0.4),
        monthGrid: MonthGrid? = null,
        onDaySelected: (LocalDate) -> Unit = {},
        onOpenWeightDetails: () -> Unit = {},
        onOpenTemplates: () -> Unit = {},
        onOpenCatalog: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onboarding: OnboardingGuide = OnboardingGuide.Inactive,
        onContinueOnboarding: () -> Unit = {},
        onDismissOnboardingReminder: () -> Unit = {},
        onConfirmCalendarCoach: () -> Unit = {},
        heatmapRevealRequested: Boolean = false,
        onHeatmapBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
        onHeatmapRevealed: () -> Unit = {}
    ) {
        val measurement = WeightMeasurement(1, today, 82.4, 0, 0)
        val month = YearMonth.from(today)
        val grid = monthGrid ?: MonthGridCalculator.grid(month, today, setOf(today))
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview(
                    seeds = if (darkTheme) ThemeSeeds.DefaultDark else ThemeSeeds.DefaultLight,
                    darkTheme = darkTheme
                ) {
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
                                monthGrid = grid,
                                heatmap = MuscleHeatmapAssembler.assemble(emptyList(), today),
                                onboarding = onboarding
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
                            onOpenSettings = onOpenSettings,
                            onOpenTemplates = onOpenTemplates,
                            onOpenCatalog = onOpenCatalog,
                            onOpenWorkout = {},
                            onOpenWeightDetails = onOpenWeightDetails,
                            onContinueOnboarding = onContinueOnboarding,
                            onDismissOnboardingReminder = onDismissOnboardingReminder,
                            onConfirmCalendarCoach = onConfirmCalendarCoach,
                            heatmapRevealRequested = heatmapRevealRequested,
                            onHeatmapBounds = onHeatmapBounds,
                            onHeatmapRevealed = onHeatmapRevealed
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun popupWindowLayoutParams(): List<android.view.WindowManager.LayoutParams> {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val shadow = org.robolectric.Shadows.shadowOf(wm)
        val viewsMethod = generateSequence(shadow.javaClass as Class<*>?) { type -> type.superclass }
            .mapNotNull { type ->
                type.methods.firstOrNull { method ->
                    method.name == "getViews" && method.parameterCount == 0
                }
            }
            .firstOrNull()
        val views = viewsMethod?.invoke(shadow) as? List<*> ?: emptyList<Any>()
        return views.mapNotNull { view ->
            (view as? android.view.View)?.layoutParams as? android.view.WindowManager.LayoutParams
        }
    }
}
