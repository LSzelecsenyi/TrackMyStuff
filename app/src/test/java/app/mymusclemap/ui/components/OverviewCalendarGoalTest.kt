package app.mymusclemap.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun incompleteWeekShowsProgressAndKeepsTheExistingStreak() {
        val monday = LocalDate.of(2026, 3, 2)
        val status = WeeklyGoalLogic.evaluate(
            listOf(WeeklyGoalRevision(monday, 4, graceWeek = false)),
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
        render(status)
        composeRule.onNodeWithText(testString(R.string.weekly_goal_progress, 2, 4)).assertIsDisplayed()
        composeRule.onNodeWithTag("overview-weekly-streak").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testQuantity(R.plurals.weekly_goal_streak, 1)).assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription(testQuantity(R.plurals.weekly_goal_streak, 1)).assertIsDisplayed()
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
    }

    private fun render(
        status: app.mymusclemap.domain.workout.WeeklyGoalStatus,
        expanded: Boolean = false,
        onConfigureGoal: () -> Unit = {}
    ) {
        val month = java.time.YearMonth.from(today)
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                OverviewCalendar(
                    monthGrid = MonthGridCalculator.grid(month, today, emptySet(), emptyMap(), emptyMap()),
                    displayedMonth = month,
                    today = today,
                    selectedDate = null,
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
