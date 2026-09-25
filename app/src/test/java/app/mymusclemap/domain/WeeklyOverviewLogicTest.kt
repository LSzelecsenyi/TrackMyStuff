package app.mymusclemap.domain

import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.SessionProgress
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeeklyOverviewLogicTest {
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun windowIncludesTodayAndSixPreviousCalendarDays() {
        assertEquals(LocalDate.of(2026, 9, 10), WeeklyOverviewLogic.windowStart(today))
        assertEquals(true, WeeklyOverviewLogic.inWindow(today, today))
        assertEquals(true, WeeklyOverviewLogic.inWindow(today.minusDays(6), today))
        assertEquals(false, WeeklyOverviewLogic.inWindow(today.minusDays(7), today))
        assertEquals(false, WeeklyOverviewLogic.inWindow(today.plusDays(1), today))
    }

    @Test
    fun emptyWeekIsZeroWorkoutsAndZeroSets() {
        val overview = WeeklyOverviewLogic.assemble(today, emptyList(), emptyList())
        assertEquals(0, overview.workoutCount)
        assertEquals(0, overview.completedSetCount)
        assertNull(overview.weightChangeKg)
        assertEquals("0 edzés · 0 sorozat", WeeklyOverviewLogic.activityLine(overview))
        assertEquals("Nincs elég testsúlyadat", WeeklyOverviewLogic.weightChangeLabel(overview.weightChangeKg))
    }

    @Test
    fun onlyCompletedWorkoutsInsideTheWindowCount() {
        val overview = WeeklyOverviewLogic.assemble(
            today = today,
            sessions = listOf(
                summary(1, today.minusDays(7), SessionStatus.COMPLETED, SessionProgress(8, 0, 0, 8)),
                summary(2, today.minusDays(6), SessionStatus.COMPLETED, SessionProgress(3, 1, 0, 4)),
                summary(3, today.minusDays(2), SessionStatus.ABANDONED, SessionProgress(2, 0, 1, 3)),
                summary(4, today, SessionStatus.IN_PROGRESS, SessionProgress(1, 0, 2, 3)),
                summary(5, today, SessionStatus.COMPLETED, SessionProgress(4, 0, 0, 4)),
                summary(6, today.plusDays(1), SessionStatus.COMPLETED, SessionProgress(5, 0, 0, 5))
            ),
            measurements = emptyList()
        )
        assertEquals(2, overview.workoutCount)
        assertEquals(7, overview.completedSetCount)
        assertEquals("2 edzés · 7 sorozat", WeeklyOverviewLogic.activityLine(overview))
    }

    @Test
    fun skippedAndPendingSetsAreExcludedFromTheSetCount() {
        val overview = WeeklyOverviewLogic.assemble(
            today = today,
            sessions = listOf(
                summary(1, today, SessionStatus.COMPLETED, SessionProgress(2, 3, 4, 9))
            ),
            measurements = emptyList()
        )
        assertEquals(1, overview.workoutCount)
        assertEquals(2, overview.completedSetCount)
        assertEquals("1 edzés · 2 sorozat", WeeklyOverviewLogic.activityLine(overview))
    }

    @Test
    fun singularHungarianLabelsStayCorrect() {
        assertEquals("1 edzés", WeeklyOverviewLogic.workoutLabel(1))
        assertEquals("1 sorozat", WeeklyOverviewLogic.setLabel(1))
        assertEquals("2 edzés", WeeklyOverviewLogic.workoutLabel(2))
        assertEquals("2 sorozat", WeeklyOverviewLogic.setLabel(2))
    }

    @Test
    fun weightChangeUsesEarliestAndLatestMeasurementInsideTheWindow() {
        val overview = WeeklyOverviewLogic.assemble(
            today = today,
            sessions = emptyList(),
            measurements = listOf(
                weight(1, today.minusDays(7), 90.0),
                weight(2, today.minusDays(6), 80.0),
                weight(3, today.minusDays(1), 80.4),
                weight(4, today, 80.5),
                weight(5, today.plusDays(1), 70.0)
            )
        )
        assertEquals(0.5, overview.weightChangeKg!!, 0.0001)
        assertEquals("+0,5 kg", WeeklyOverviewLogic.weightChangeLabel(overview.weightChangeKg))
    }

    @Test
    fun weightDecreaseUsesMinusSign() {
        val overview = WeeklyOverviewLogic.assemble(
            today = today,
            sessions = emptyList(),
            measurements = listOf(
                weight(1, today.minusDays(5), 82.4),
                weight(2, today, 81.9)
            )
        )
        assertEquals(-0.5, overview.weightChangeKg!!, 0.0001)
        assertEquals("−0,5 kg", WeeklyOverviewLogic.weightChangeLabel(overview.weightChangeKg))
    }

    @Test
    fun unchangedWeightIsNeutralZero() {
        val overview = WeeklyOverviewLogic.assemble(
            today = today,
            sessions = emptyList(),
            measurements = listOf(
                weight(1, today.minusDays(3), 80.0),
                weight(2, today, 80.0)
            )
        )
        assertEquals(0.0, overview.weightChangeKg!!, 0.0001)
        assertEquals("0,0 kg", WeeklyOverviewLogic.weightChangeLabel(overview.weightChangeKg))
    }

    @Test
    fun oneMeasurementIsNotEnoughForWeightChange() {
        val overview = WeeklyOverviewLogic.assemble(
            today = today,
            sessions = emptyList(),
            measurements = listOf(weight(1, today, 80.4))
        )
        assertNull(overview.weightChangeKg)
        assertEquals("Nincs elég testsúlyadat", WeeklyOverviewLogic.weightChangeLabel(null))
    }

    @Test
    fun sameDayMeasurementsUseStableIdOrder() {
        val overview = WeeklyOverviewLogic.assemble(
            today = today,
            sessions = emptyList(),
            measurements = listOf(
                weight(20, today, 81.0),
                weight(10, today, 80.0)
            )
        )
        assertEquals(1.0, overview.weightChangeKg!!, 0.0001)
    }

    private fun summary(
        id: Long,
        date: LocalDate,
        status: SessionStatus,
        progress: SessionProgress
    ): WorkoutSessionSummary {
        return WorkoutSessionSummary(
            session = WorkoutSession(
                id = id,
                templateId = 1L,
                templateName = "Push A",
                status = status,
                workoutDate = date,
                startedAt = 1_000L,
                finishedAt = if (status == SessionStatus.COMPLETED) 2_000L else null,
                abandonedAt = if (status == SessionStatus.ABANDONED) 1_500L else null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1_000L,
                updatedAt = 1_000L
            ),
            progress = progress,
            exerciseCount = 1,
            primaryMuscles = listOf(MuscleGroup.CHEST),
            durationMillis = 1_000L
        )
    }

    private fun weight(id: Long, date: LocalDate, kg: Double): WeightMeasurement {
        return WeightMeasurement(id, date, kg, 1L, 1L)
    }
}
