package app.mymusclemap.ui.components

import androidx.compose.foundation.layout.width as layoutWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.calendar.MonthGridCalculator
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import app.mymusclemap.domain.workout.WeeklyGoalStatus
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h2000dp")
class OverviewCalendarLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 3, 12)

    @Test
    fun expandedMonthColumnsUseANarrowWidth() {
        val columns = weekdayWidths(320.dp, WeeklyGoalStatus.none(today))
        assertEquals(7, columns.size)
        assertEquals(320f / 7f, columns.first(), 1.5f)
    }

    @Test
    fun expandedMonthColumnsUseAWideWidth() {
        val columns = weekdayWidths(480.dp, WeeklyGoalStatus.none(today))
        assertEquals(7, columns.size)
        assertEquals(480f / 7f, columns.first(), 1.5f)
        assertTrue(columns.first() > 60f)
    }

    @Test
    fun expandedMonthWithTrophiesKeepsSevenAlignedColumnsWiderThanTheOldLane() {
        val status = WeeklyGoalLogic.evaluate(
            listOf(WeeklyGoalRevision(LocalDate.of(2026, 3, 2), 4, graceWeek = false)),
            mapOf(
                LocalDate.of(2026, 3, 3) to 1,
                LocalDate.of(2026, 3, 4) to 1,
                LocalDate.of(2026, 3, 5) to 1,
                LocalDate.of(2026, 3, 6) to 1
            ),
            today
        )
        render(status, expanded = true, width = 360.dp)
        val grid = composeRule.onNodeWithTag("calendar-month-grid").getBoundsInRoot()
        composeRule.onNodeWithTag("calendar-trophy-lane").assertExists()
        val columns = weekdayWidths(alreadyRendered = true)
        assertEquals(7, columns.size)
        val column = columns.first()
        val lane = columnLeft(0) - grid.left.value
        assertEquals(28f, lane, 1.5f)
        assertTrue(lane < 52f)
        assertTrue(column > (360f - 52f) / 7f + 1f)
        assertEquals(grid.right.value, columnLeft(6) + column, 1.5f)
        assertDayAlignedWithColumn("February 23, 2026", 0)
        assertDayAlignedWithColumn("March 1, 2026", 6)
    }

    @Test
    fun collapsedWeekKeepsSevenEqualColumnsAcrossTheFullWidth() {
        render(WeeklyGoalStatus.none(today), expanded = false, width = 360.dp)
        composeRule.onNodeWithTag("overview-calendar-pager").assertExists()
        composeRule.onNodeWithTag("calendar-month-grid").assertDoesNotExist()
        composeRule.onNodeWithTag("calendar-trophy-lane").assertDoesNotExist()
        val row = composeRule.onNodeWithTag("calendar-week-row").getBoundsInRoot()
        assertEquals(360f, (row.right - row.left).value, 1.5f)
        val days = composeRule.onAllNodesWithTag("calendar-week-day")
        assertEquals(7, days.fetchSemanticsNodes().size)
        val width = (days[0].getBoundsInRoot().let { it.right - it.left }).value
        val tops = (0 until 7).map { days[it].getBoundsInRoot() }
        tops.forEach { day ->
            assertEquals(width, (day.right - day.left).value, 1.5f)
            assertEquals(tops.first().top.value, day.top.value, 1.5f)
            assertTrue((day.bottom - day.top).value <= 36f)
        }
        assertEquals(360f / 7f, width, 2f)
        for (index in 1 until tops.size) {
            assertEquals(tops[index - 1].right.value, tops[index].left.value, 1.5f)
        }
    }

    private fun weekdayWidths(width: Dp, status: WeeklyGoalStatus): List<Float> {
        render(status, expanded = true, width = width)
        return weekdayWidths(alreadyRendered = true)
    }

    private fun weekdayWidths(alreadyRendered: Boolean): List<Float> {
        check(alreadyRendered)
        val nodes = composeRule.onAllNodesWithTag("calendar-weekday")
        assertEquals(7, nodes.fetchSemanticsNodes().size)
        val widths = (0 until 7).map { index ->
            val bounds = nodes[index].getBoundsInRoot()
            (bounds.right - bounds.left).value
        }
        val first = widths.first()
        widths.forEach { assertEquals(first, it, 1f) }
        for (index in 1 until 7) {
            assertEquals(columnLeft(index - 1) + first, columnLeft(index), 1.5f)
        }
        return widths
    }

    private fun assertDayAlignedWithColumn(date: String, index: Int) {
        val column = composeRule.onAllNodesWithTag("calendar-weekday")[index].getBoundsInRoot()
        val day = composeRule.onNode(hasContentDescription(date, substring = true)).getBoundsInRoot()
        val columnCenter = (column.left.value + column.right.value) / 2f
        val dayCenter = (day.left.value + day.right.value) / 2f
        assertEquals(columnCenter, dayCenter, 2f)
    }

    private fun columnLeft(index: Int): Float {
        return composeRule.onAllNodesWithTag("calendar-weekday")[index].getBoundsInRoot().left.value
    }

    private fun render(status: WeeklyGoalStatus, expanded: Boolean, width: Dp) {
        val month = YearMonth.from(today)
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                OverviewCalendar(
                    monthGrid = MonthGridCalculator.grid(month, today),
                    displayedMonth = month,
                    today = today,
                    selectedDate = null,
                    onDisplayedMonthChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayClick = {},
                    modifier = Modifier.layoutWidth(width),
                    startExpanded = expanded,
                    weeklyGoal = status
                )
            }
        }
    }
}
