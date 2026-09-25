package app.mymusclemap.domain.calendar

import app.mymusclemap.measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthGridCalculatorTest {
    @Test
    fun monthStartingOnMondayHasFirstCellOnTheFirst() {
        val month = YearMonth.of(2026, 6)
        val today = LocalDate.of(2026, 6, 15)
        val grid = MonthGridCalculator.grid(month, today, emptySet())
        assertEquals(42, grid.cells.size)
        assertEquals(LocalDate.of(2026, 6, 1), grid.cells.first().date)
        assertEquals(DayOfWeek.MONDAY, grid.cells.first().date.dayOfWeek)
        assertTrue(grid.cells.first().inDisplayedMonth)
    }

    @Test
    fun monthStartingOnSundayPadsFromPreviousMonday() {
        val month = YearMonth.of(2026, 2)
        val today = LocalDate.of(2026, 2, 10)
        val grid = MonthGridCalculator.grid(month, today, emptySet())
        assertEquals(DayOfWeek.SUNDAY, month.atDay(1).dayOfWeek)
        assertEquals(LocalDate.of(2026, 1, 26), grid.cells.first().date)
        assertEquals(DayOfWeek.MONDAY, grid.cells.first().date.dayOfWeek)
        assertFalse(grid.cells.first().inDisplayedMonth)
        val firstOfMonth = grid.cells.first { it.date == LocalDate.of(2026, 2, 1) }
        assertTrue(firstOfMonth.inDisplayedMonth)
    }

    @Test
    fun februaryContainsLeapDayOnlyInLeapYears() {
        val leap = MonthGridCalculator.grid(
            YearMonth.of(2024, 2),
            LocalDate.of(2024, 2, 20),
            emptySet()
        )
        val nonLeap = MonthGridCalculator.grid(
            YearMonth.of(2025, 2),
            LocalDate.of(2025, 2, 20),
            emptySet()
        )
        assertTrue(leap.cells.any { it.date == LocalDate.of(2024, 2, 29) && it.inDisplayedMonth })
        assertFalse(nonLeap.cells.any { it.date.monthValue == 2 && it.date.dayOfMonth == 29 })
        assertEquals(LocalDate.of(2025, 3, 1), nonLeap.cells.first { it.date == LocalDate.of(2025, 3, 1) }.date)
    }

    @Test
    fun decemberToJanuaryNavigation() {
        val december = YearMonth.of(2026, 12)
        val january = december.plusMonths(1)
        assertEquals(YearMonth.of(2027, 1), january)
        val grid = MonthGridCalculator.grid(january, LocalDate.of(2027, 1, 5), emptySet())
        assertTrue(grid.cells.any { it.date == LocalDate.of(2027, 1, 1) && it.inDisplayedMonth })
        assertFalse(grid.cells.any { it.date == LocalDate.of(2026, 12, 31) && it.inDisplayedMonth })
    }

    @Test
    fun measurementMarkersMapToCorrectDays() {
        val month = YearMonth.of(2026, 3)
        val today = LocalDate.of(2026, 3, 11)
        val measured = setOf(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 3))
        val grid = MonthGridCalculator.grid(month, today, measured)
        assertTrue(grid.cells.first { it.date == LocalDate.of(2026, 3, 11) }.hasMeasurement)
        assertTrue(grid.cells.first { it.date == LocalDate.of(2026, 3, 3) }.hasMeasurement)
        assertFalse(grid.cells.first { it.date == LocalDate.of(2026, 3, 4) }.hasMeasurement)
        assertTrue(grid.cells.first { it.date == today }.isToday)
    }

    @Test
    fun futureDatesCanBeOpened() {
        val today = LocalDate.of(2026, 3, 11)
        assertTrue(MonthGridCalculator.canOpenDay(today.plusDays(1), today))
        assertTrue(MonthGridCalculator.canOpenDay(today, today))
        assertTrue(MonthGridCalculator.canOpenDay(today.minusDays(1), today))
    }

    @Test
    fun weightOnlyMarkerDoesNotCreateWorkoutDot() {
        val today = LocalDate.of(2026, 9, 15)
        val grid = MonthGridCalculator.grid(
            month = YearMonth.of(2026, 9),
            today = today,
            measuredDates = setOf(today),
            completedWorkoutCounts = emptyMap()
        )
        val cell = grid.cells.first { it.date == today }
        assertTrue(cell.hasMeasurement)
        assertFalse(cell.hasCompletedWorkout)
        assertEquals(0, cell.completedWorkoutCount)
    }

    @Test
    fun workoutOnlyMarkerDoesNotCreateWeightDot() {
        val today = LocalDate.of(2026, 9, 15)
        val grid = MonthGridCalculator.grid(
            month = YearMonth.of(2026, 9),
            today = today,
            measuredDates = emptySet(),
            completedWorkoutCounts = mapOf(today to 1)
        )
        val cell = grid.cells.first { it.date == today }
        assertFalse(cell.hasMeasurement)
        assertTrue(cell.hasCompletedWorkout)
        assertEquals(1, cell.completedWorkoutCount)
    }

    @Test
    fun bothMarkersRemainVisibleAndMultipleWorkoutsStayOneCount() {
        val today = LocalDate.of(2026, 9, 15)
        val grid = MonthGridCalculator.grid(
            month = YearMonth.of(2026, 9),
            today = today,
            measuredDates = setOf(today),
            completedWorkoutCounts = mapOf(today to 3)
        )
        val cell = grid.cells.first { it.date == today }
        assertTrue(cell.hasMeasurement)
        assertTrue(cell.hasCompletedWorkout)
        assertEquals(3, cell.completedWorkoutCount)
    }

    @Test
    fun plannedAndCompletedMarkersRemainIndependent() {
        val today = LocalDate.of(2026, 9, 15)
        val grid = MonthGridCalculator.grid(
            month = YearMonth.of(2026, 9),
            today = today,
            measuredDates = emptySet(),
            completedWorkoutCounts = mapOf(today to 1),
            plannedWorkoutCounts = mapOf(today to 2)
        )
        val cell = grid.cells.first { it.date == today }
        assertTrue(cell.hasCompletedWorkout)
        assertTrue(cell.hasPlannedWorkout)
        assertEquals(1, cell.completedWorkoutCount)
        assertEquals(2, cell.plannedWorkoutCount)
    }

    @Test
    fun dstTransitionDayAppearsOnceInTheMonthGrid() {
        val month = YearMonth.of(2026, 3)
        val today = LocalDate.of(2026, 3, 29)
        val grid = MonthGridCalculator.grid(month, today, emptySet())
        val dates = grid.cells.map { it.date }
        assertEquals(42, dates.size)
        assertEquals(dates.distinct(), dates)
        assertEquals(1, dates.count { it == LocalDate.of(2026, 3, 29) })
        assertEquals(LocalDate.of(2026, 3, 28), dates[dates.indexOf(today) - 1])
        assertEquals(LocalDate.of(2026, 3, 30), dates[dates.indexOf(today) + 1])
    }

    @Test
    fun allMarkerCombinationsStayIndependentBooleans() {
        val today = LocalDate.of(2026, 9, 15)
        val weightOnly = LocalDate.of(2026, 9, 1)
        val completedOnly = LocalDate.of(2026, 9, 2)
        val plannedOnly = LocalDate.of(2026, 9, 3)
        val weightPlanned = LocalDate.of(2026, 9, 4)
        val weightCompleted = LocalDate.of(2026, 9, 5)
        val plannedCompleted = LocalDate.of(2026, 9, 6)
        val allThree = LocalDate.of(2026, 9, 7)
        val manyPlanned = LocalDate.of(2026, 9, 8)
        val grid = MonthGridCalculator.grid(
            month = YearMonth.of(2026, 9),
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
        fun cell(date: LocalDate) = grid.cells.first { it.date == date }
        assertTrue(cell(weightOnly).hasMeasurement && !cell(weightOnly).hasPlannedWorkout && !cell(weightOnly).hasCompletedWorkout)
        assertTrue(!cell(completedOnly).hasMeasurement && !cell(completedOnly).hasPlannedWorkout && cell(completedOnly).hasCompletedWorkout)
        assertTrue(!cell(plannedOnly).hasMeasurement && cell(plannedOnly).hasPlannedWorkout && !cell(plannedOnly).hasCompletedWorkout)
        assertTrue(cell(weightPlanned).hasMeasurement && cell(weightPlanned).hasPlannedWorkout && !cell(weightPlanned).hasCompletedWorkout)
        assertTrue(cell(weightCompleted).hasMeasurement && !cell(weightCompleted).hasPlannedWorkout && cell(weightCompleted).hasCompletedWorkout)
        assertTrue(!cell(plannedCompleted).hasMeasurement && cell(plannedCompleted).hasPlannedWorkout && cell(plannedCompleted).hasCompletedWorkout)
        assertTrue(cell(allThree).hasMeasurement && cell(allThree).hasPlannedWorkout && cell(allThree).hasCompletedWorkout)
        assertTrue(cell(manyPlanned).hasPlannedWorkout)
        assertEquals(3, cell(manyPlanned).plannedWorkoutCount)
    }

    @Test
    fun plannedOnlyMarkerDoesNotCreateCompletedDot() {
        val today = LocalDate.of(2026, 9, 15)
        val grid = MonthGridCalculator.grid(
            month = YearMonth.of(2026, 9),
            today = today,
            measuredDates = emptySet(),
            completedWorkoutCounts = emptyMap(),
            plannedWorkoutCounts = mapOf(today to 1)
        )
        val cell = grid.cells.first { it.date == today }
        assertFalse(cell.hasCompletedWorkout)
        assertTrue(cell.hasPlannedWorkout)
    }
}

class DaySheetFactoryTest {
    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun dateWithoutMeasurement() {
        val state = app.mymusclemap.domain.DaySheetFactory.create(
            date = today,
            measurements = emptyList(),
            today = today
        )
        assertEquals(today, state.date)
        assertFalse(state.hasMeasurement)
        assertNull(state.differenceFromPreviousKg)
    }

    @Test
    fun dateWithMeasurementIncludesPreviousDifference() {
        val measurements = listOf(
            measurement("2026-03-08", 81.0),
            measurement("2026-03-11", 81.4)
        )
        val state = app.mymusclemap.domain.DaySheetFactory.create(
            date = today,
            measurements = measurements,
            today = today
        )
        assertTrue(state.hasMeasurement)
        assertEquals(81.4, state.measurement!!.weightKg, 0.0)
        assertEquals(0.4, state.differenceFromPreviousKg!!, 0.0001)
    }

    @Test
    fun futureDateCanBeOpenedWithoutWeightRecording() {
        val state = app.mymusclemap.domain.DaySheetFactory.create(
            date = today.plusDays(1),
            measurements = listOf(measurement("2026-03-11", 80.0)),
            today = today
        )
        assertEquals(today.plusDays(1), state.date)
        assertFalse(state.canRecordWeight)
        assertFalse(state.hasMeasurement)
        assertFalse(state.hasScheduledWorkouts)
    }

    @Test
    fun selectedDayIncludesWeightAndCompletedWorkouts() {
        val workout = summary(1, today, app.mymusclemap.domain.workout.SessionStatus.COMPLETED)
        val abandoned = summary(2, today, app.mymusclemap.domain.workout.SessionStatus.ABANDONED)
        val otherDay = summary(3, today.minusDays(1), app.mymusclemap.domain.workout.SessionStatus.COMPLETED)
        val state = app.mymusclemap.domain.DaySheetFactory.create(
            date = today,
            measurements = listOf(measurement("2026-03-08", 81.0), measurement("2026-03-11", 81.4)),
            today = today,
            workouts = listOf(workout, abandoned, otherDay)
        )
        assertTrue(state.hasMeasurement)
        assertEquals(0.4, state.differenceFromPreviousKg!!, 0.0001)
        assertEquals(listOf(1L), state.workouts.map { it.session.id })
        assertTrue(state.hasWorkouts)
    }

    @Test
    fun selectedDayIncludesScheduledWorkoutsInGivenOrder() {
        val first = scheduled(1, today, "Push A")
        val second = scheduled(2, today, "Pull A")
        val state = app.mymusclemap.domain.DaySheetFactory.create(
            date = today,
            measurements = emptyList(),
            today = today,
            scheduledWorkouts = listOf(first, second)
        )
        assertEquals(listOf("Push A", "Pull A"), state.scheduledWorkouts.map { it.templateName })
        assertTrue(state.hasScheduledWorkouts)
        assertTrue(state.canRecordWeight)
    }

    private fun scheduled(
        id: Long,
        date: LocalDate,
        name: String
    ): app.mymusclemap.domain.workout.ScheduledWorkout {
        return app.mymusclemap.domain.workout.ScheduledWorkout(
            id = id,
            scheduledDate = date,
            templateId = id,
            templateName = name,
            exerciseCount = 1,
            plannedSetCount = 2,
            templateArchived = false,
            sessionId = null,
            sessionStatus = null,
            createdAt = id
        )
    }

    private fun summary(
        id: Long,
        date: LocalDate,
        status: app.mymusclemap.domain.workout.SessionStatus
    ): app.mymusclemap.domain.workout.WorkoutSessionSummary {
        return app.mymusclemap.domain.workout.WorkoutSessionSummary(
            session = app.mymusclemap.domain.workout.WorkoutSession(
                id = id,
                templateId = 1L,
                templateName = "Push A",
                status = status,
                workoutDate = date,
                startedAt = id * 1_000L,
                finishedAt = if (status == app.mymusclemap.domain.workout.SessionStatus.COMPLETED) id * 1_000L + 1 else null,
                abandonedAt = if (status == app.mymusclemap.domain.workout.SessionStatus.ABANDONED) id * 1_000L + 1 else null,
                notes = null,
                bodyWeightKg = 81.4,
                bodyWeightSource = app.mymusclemap.domain.workout.BodyWeightSource.MEASURED_SAME_DAY,
                bodyWeightSourceDate = date,
                createdAt = id,
                updatedAt = id
            ),
            progress = app.mymusclemap.domain.workout.SessionProgress(3, 0, 0, 3),
            exerciseCount = 1,
            primaryMuscles = emptyList(),
            durationMillis = 1_000L
        )
    }
}
