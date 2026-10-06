package app.mymusclemap.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.calendar.CalendarWorkoutMark
import app.mymusclemap.domain.workout.WeekProgress
import app.mymusclemap.domain.workout.WeekVerdict
import app.mymusclemap.ui.theme.StrictBrand
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarPresentationTest {
    private val week = LocalDate.of(2026, 3, 9)

    @Test
    fun plannedOutlineIsStrictLimeAndCompletedStaysALimeFill() {
        assertEquals(0xFFE2FD6D.toInt(), StrictBrand.lime.toArgb())
        val planned = calendarDayChrome(
            mark = CalendarWorkoutMark.Planned,
            selected = false,
            selectionColor = Color.Blue,
            selectionRingColor = Color.Black
        )
        val completed = calendarDayChrome(
            mark = CalendarWorkoutMark.Completed,
            selected = false,
            selectionColor = Color.Blue,
            selectionRingColor = Color.Black
        )
        assertEquals(StrictBrand.lime, planned.outline)
        assertNull(planned.fill)
        assertEquals(PlannedOutlineWidth, planned.outlineWidth)
        assertEquals(StrictBrand.lime, completed.fill)
        assertNull(completed.outline)
        assertEquals(StrictBrand.dark, calendarDayChrome(
            mark = CalendarWorkoutMark.Completed,
            selected = true,
            selectionColor = Color.Blue,
            selectionRingColor = Color.Black
        ).selectionRing)
    }

    @Test
    fun plannedSelectionAndTodayCuesStaySeparateFromTheLimeOutline() {
        val selected = Color.Blue
        val ring = Color.Black
        val plannedSelected = calendarDayChrome(
            mark = CalendarWorkoutMark.Planned,
            selected = true,
            selectionColor = selected,
            selectionRingColor = ring
        )
        val selectedEmpty = calendarDayChrome(
            mark = CalendarWorkoutMark.None,
            selected = true,
            selectionColor = selected,
            selectionRingColor = ring
        )
        assertEquals(StrictBrand.lime, plannedSelected.outline)
        assertEquals(ring, plannedSelected.selectionRing)
        assertEquals(selected, selectedEmpty.outline)
        assertNull(selectedEmpty.selectionRing)
        assertEquals(1.5.dp, selectedEmpty.outlineWidth)
        assertEquals(true, plannedSelected.outlineWidth > selectedEmpty.outlineWidth)
    }

    @Test
    fun currentWeekBelowGoalIsPendingAndPastWeeksKeepTheirStreak() {
        val open = progress(streak = 2, achieved = false, verdict = WeekVerdict.InProgress)
        val reached = progress(streak = 3, achieved = true, verdict = WeekVerdict.Achieved)
        val past = progress(streak = 2, achieved = true, verdict = WeekVerdict.Achieved)
        val grace = progress(streak = 2, achieved = false, verdict = WeekVerdict.GraceMiss)
        val missed = progress(streak = 0, achieved = false, verdict = WeekVerdict.Missed)
        val future = progress(streak = 0, achieved = false, verdict = WeekVerdict.InProgress)
        val unset = progress(streak = 0, achieved = false, goal = null, verdict = WeekVerdict.NoGoal)

        assertEquals(WeekTrophyTreatment.Pending, weekTrophyTreatment(open, isCurrentWeek = true))
        assertEquals(WeekTrophyTreatment.Achieved, weekTrophyTreatment(reached, isCurrentWeek = true))
        assertEquals(WeekTrophyTreatment.Achieved, weekTrophyTreatment(past, isCurrentWeek = false))
        assertEquals(WeekTrophyTreatment.Achieved, weekTrophyTreatment(grace, isCurrentWeek = false))
        assertEquals(WeekTrophyTreatment.Hidden, weekTrophyTreatment(missed, isCurrentWeek = false))
        assertEquals(WeekTrophyTreatment.Hidden, weekTrophyTreatment(future, isCurrentWeek = false))
        assertEquals(WeekTrophyTreatment.Hidden, weekTrophyTreatment(unset, isCurrentWeek = true))
    }

    private fun progress(
        streak: Int,
        achieved: Boolean,
        verdict: WeekVerdict,
        goal: Int? = 4
    ): WeekProgress {
        return WeekProgress(
            weekStart = week,
            goal = goal,
            completed = if (achieved) goal ?: 0 else 1,
            streak = streak,
            achieved = achieved,
            verdict = verdict
        )
    }
}
