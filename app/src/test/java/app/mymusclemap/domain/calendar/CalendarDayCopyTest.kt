package app.mymusclemap.domain.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class CalendarDayCopyTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val date = LocalDate.parse("2026-09-15")

    @Test
    fun weightOnlyDescription() {
        assertEquals(
            resources.getString(R.string.calendar_has_weight),
            CalendarDayCopy.entryState(resources, true, 0)
        )
        val text = CalendarDayCopy.description(
            resources,
            date,
            hasMeasurement = true,
            completedWorkoutCount = 0
        )
        assertTrue(text.contains(resources.getString(R.string.calendar_has_weight)))
        assertFalse(text.contains("workout"))
    }

    @Test
    fun workoutOnlyDescription() {
        assertEquals(
            resources.getQuantityString(R.plurals.calendar_completed_workouts, 1, 1),
            CalendarDayCopy.entryState(resources, false, 1)
        )
    }

    @Test
    fun bothMarkersDescription() {
        val expected = resources.getString(
            R.string.calendar_entry_two,
            resources.getString(R.string.calendar_has_weight),
            resources.getQuantityString(R.plurals.calendar_completed_workouts, 1, 1)
        )
        assertEquals(expected, CalendarDayCopy.entryState(resources, true, 1))
        val text = CalendarDayCopy.description(resources, date, true, 1)
        assertTrue(text.contains(expected))
    }

    @Test
    fun multipleWorkoutsAggregate() {
        assertEquals(
            resources.getQuantityString(R.plurals.calendar_completed_workouts, 2, 2),
            CalendarDayCopy.entryState(resources, false, 2)
        )
        assertEquals(
            resources.getString(
                R.string.calendar_entry_two,
                resources.getString(R.string.calendar_has_weight),
                resources.getQuantityString(R.plurals.calendar_completed_workouts, 2, 2)
            ),
            CalendarDayCopy.entryState(resources, true, 2)
        )
    }

    @Test
    fun emptyDay() {
        assertEquals(
            resources.getString(R.string.calendar_no_entry),
            CalendarDayCopy.entryState(resources, false, 0)
        )
    }

    @Test
    fun plannedWorkoutDescription() {
        assertEquals(
            resources.getQuantityString(R.plurals.calendar_planned_workouts, 1, 1),
            CalendarDayCopy.entryState(resources, false, 0, 1)
        )
        assertEquals(
            resources.getString(
                R.string.calendar_entry_list,
                listOf(
                    resources.getString(R.string.calendar_has_weight),
                    resources.getQuantityString(R.plurals.calendar_completed_workouts, 1, 1)
                ).joinToString(", "),
                resources.getQuantityString(R.plurals.calendar_planned_workouts, 1, 1)
            ),
            CalendarDayCopy.entryState(resources, true, 1, 1)
        )
    }

    @Test
    fun futureDayStaysSelectableInCopy() {
        val text = CalendarDayCopy.description(
            resources,
            date,
            hasMeasurement = false,
            completedWorkoutCount = 0,
            isFuture = true
        )
        assertTrue(text.contains(resources.getString(R.string.calendar_future_label)))
        assertFalse(text.contains("cannot be recorded"))
    }

    @Test
    fun abandonedDoesNotCreateCompletedMarker() {
        val grid = MonthGridCalculator.grid(
            month = YearMonth.of(2026, 9),
            today = date,
            measuredDates = emptySet(),
            completedWorkoutCounts = emptyMap()
        )
        val cell = grid.cells.first { it.date == date }
        assertFalse(cell.hasCompletedWorkout)
        assertEquals(0, cell.completedWorkoutCount)
    }
}
