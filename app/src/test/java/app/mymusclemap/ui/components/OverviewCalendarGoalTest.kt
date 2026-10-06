package app.mymusclemap.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mymusclemap.R
import app.mymusclemap.domain.calendar.MonthGridCalculator
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import app.mymusclemap.testQuantity
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverviewCalendarGoalTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 3, 12)

    @Test
    fun noGoalOffersTheSameConfigurationEntryPoint() {
        val opens = AtomicInteger(0)
        render(WeeklyGoalLogic.evaluate(emptyList(), emptyMap(), today), onConfigureGoal = { opens.incrementAndGet() })
        composeRule.onNodeWithText(testString(R.string.weekly_goal_set_cta)).assertIsDisplayed().performClick()
        assertEquals(1, opens.get())
        composeRule.onAllNodesWithTag("overview-weekly-streak").assertCountEquals(0)
    }

    @Test
    fun incompleteWeekShowsProgressWithoutTheCarriedStreakCount() {
        render(incompleteWeek())
        composeRule.onNodeWithText(testString(R.string.weekly_goal_progress, 2, 4)).assertIsDisplayed()
        composeRule.onNodeWithTag("overview-weekly-streak").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-trophy-pending", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.weekly_goal_streak_pending)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testQuantity(R.plurals.weekly_goal_streak, 1)).assertDoesNotExist()
        composeRule.onAllNodesWithTag("calendar-trophy-count", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("0").assertCountEquals(0)
    }

    @Test
    fun reachedGoalShowsTheUpdatedStreak() {
        val status = WeeklyGoalLogic.evaluate(
            listOf(WeeklyGoalRevision(LocalDate.of(2026, 3, 9), 2, graceWeek = true)),
            mapOf(LocalDate.of(2026, 3, 10) to 1, LocalDate.of(2026, 3, 11) to 1),
            today
        )
        render(status)
        composeRule.onNodeWithText(testString(R.string.weekly_goal_progress, 2, 2)).assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-trophy-achieved", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testQuantity(R.plurals.weekly_goal_streak, 1)).assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-trophy-count", useUnmergedTree = true).assertTextEquals("1")
        composeRule.onAllNodesWithTag("calendar-trophy-pending", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun expandedMonthShowsAStreakOnEachSuccessfulWeek() {
        val status = WeeklyGoalLogic.evaluate(
            listOf(WeeklyGoalRevision(LocalDate.of(2026, 3, 2), 1, graceWeek = false)),
            mapOf(
                LocalDate.of(2026, 3, 3) to 1,
                LocalDate.of(2026, 3, 10) to 1
            ),
            today
        )
        render(status, expanded = true)
        composeRule.onNodeWithContentDescription(testQuantity(R.plurals.weekly_goal_streak, 1)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testQuantity(R.plurals.weekly_goal_streak, 2)).assertIsDisplayed()
        val counts = composeRule.onAllNodesWithTag("calendar-trophy-count", useUnmergedTree = true)
        counts.assertCountEquals(2)
        counts[0].assertTextEquals("1")
        counts[1].assertTextEquals("2")
        composeRule.onAllNodesWithTag("calendar-trophy-pending", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun currentWeekBelowGoalShowsAnOutlineTrophyAndKeepsThePreviousWeekCount() {
        render(incompleteWeek(), expanded = true)
        composeRule.onAllNodesWithTag("calendar-trophy-pending", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithContentDescription(testString(R.string.weekly_goal_streak_pending)).assertIsDisplayed()
        composeRule.onAllNodesWithTag("calendar-trophy-achieved", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription(testQuantity(R.plurals.weekly_goal_streak, 1)).assertCountEquals(1)
        val counts = composeRule.onAllNodesWithTag("calendar-trophy-count", useUnmergedTree = true)
        counts.assertCountEquals(1)
        counts[0].assertTextEquals("1")
    }

    @Test
    fun futureWeeksStayUnmarkedWhileTheCurrentWeekIsStillOpen() {
        render(incompleteWeek(), expanded = true)
        composeRule.onAllNodesWithTag("calendar-trophy-pending", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("calendar-trophy-achieved", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun plannedAndCompletedDaysStayDistinctWhenTodayIsSelected() {
        render(
            status = WeeklyGoalLogic.evaluate(emptyList(), emptyMap(), today),
            planned = mapOf(today to 1),
            completed = mapOf(LocalDate.of(2026, 3, 11) to 1),
            selectedDate = today
        )
        composeRule.onAllNodesWithTag("calendar-planned-outline", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("calendar-completed-fill", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("calendar-today-marker", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("calendar-selection-ring", useUnmergedTree = true).assertCountEquals(1)
    }

    private fun incompleteWeek(): app.mymusclemap.domain.workout.WeeklyGoalStatus {
        return WeeklyGoalLogic.evaluate(
            listOf(WeeklyGoalRevision(LocalDate.of(2026, 3, 2), 4, graceWeek = false)),
            mapOf(
                LocalDate.of(2026, 3, 3) to 1,
                LocalDate.of(2026, 3, 4) to 1,
                LocalDate.of(2026, 3, 5) to 1,
                LocalDate.of(2026, 3, 6) to 1,
                LocalDate.of(2026, 3, 10) to 1,
                LocalDate.of(2026, 3, 11) to 1
            ),
            today
        )
    }

    private fun render(
        status: app.mymusclemap.domain.workout.WeeklyGoalStatus,
        expanded: Boolean = false,
        onConfigureGoal: () -> Unit = {},
        planned: Map<LocalDate, Int> = emptyMap(),
        completed: Map<LocalDate, Int> = emptyMap(),
        selectedDate: LocalDate? = null
    ) {
        val month = java.time.YearMonth.from(today)
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                OverviewCalendar(
                    monthGrid = MonthGridCalculator.grid(
                        month = month,
                        today = today,
                        completedWorkoutCounts = completed,
                        plannedWorkoutCounts = planned
                    ),
                    displayedMonth = month,
                    today = today,
                    selectedDate = selectedDate,
                    onDisplayedMonthChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayClick = {},
                    startExpanded = expanded,
                    weeklyGoal = status,
                    onConfigureGoal = onConfigureGoal
                )
            }
        }
    }
}
