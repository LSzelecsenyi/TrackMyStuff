package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.model.ChartScale
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class VolumeTrendResolutionTest {
    @Test
    fun eachRangeSelectsItsResolution() {
        assertEquals(VolumeTrendResolution.Daily, VolumeTrendResolution.forRange(StatisticsRange.Days30))
        assertEquals(VolumeTrendResolution.Weekly, VolumeTrendResolution.forRange(StatisticsRange.Months3))
        assertEquals(VolumeTrendResolution.Weekly, VolumeTrendResolution.forRange(StatisticsRange.Months6))
        assertEquals(VolumeTrendResolution.Monthly, VolumeTrendResolution.forRange(StatisticsRange.Year1))
        assertEquals(VolumeTrendResolution.Monthly, VolumeTrendResolution.forRange(StatisticsRange.All))
    }

    @Test
    fun thirtyDaysUsesOneBucketPerCalendarDayAndKeepsZeros() {
        val today = LocalDate.of(2026, 9, 16)
        val stats = assemble(
            listOf(
                today.minusDays(40) to 100.0,
                today.minusDays(9) to 30.0,
                today.minusDays(9) to 20.0,
                today.minusDays(2) to 50.0
            ),
            today,
            StatisticsRange.Days30
        )
        val trend = stats.volume.trend
        assertEquals(100.0, stats.volume.totalKg!!, 0.0)
        assertEquals(30, trend.size)
        assertEquals(today.minusDays(29), trend.first().date)
        assertEquals(today, trend.last().date)
        assertTrue(trend.zipWithNext().all { (earlier, later) -> later.date == earlier.date.plusDays(1) })
        assertEquals(50.0, trend.single { it.date == today.minusDays(9) }.value, 0.0)
        assertEquals(50.0, trend.single { it.date == today.minusDays(2) }.value, 0.0)
        assertTrue(trend.any { it.value == 0.0 })
        assertEquals(100.0, trend.sumOf { it.value }, 0.001)
        assertNonNegative(trend.map { it.value })
    }

    @Test
    fun threeAndSixMonthsUseIsoWeeksAndKeepEmptyWeeks() {
        val today = LocalDate.of(2026, 9, 16)
        val workouts = listOf(
            LocalDate.of(2026, 7, 1) to 100.0,
            LocalDate.of(2026, 9, 7) to 200.0,
            LocalDate.of(2026, 9, 14) to 50.0
        )
        val three = assemble(workouts, today, StatisticsRange.Months3).volume
        val six = assemble(workouts, today, StatisticsRange.Months6).volume
        assertTrue(three.trend.size > 2)
        assertTrue(six.trend.size > three.trend.size)
        assertMondayWeeks(three.trend.map { it.date })
        assertMondayWeeks(six.trend.map { it.date })
        assertEquals(100.0, three.trend.single { it.date == LocalDate.of(2026, 6, 29) }.value, 0.0)
        assertEquals(0.0, three.trend.single { it.date == LocalDate.of(2026, 7, 6) }.value, 0.0)
        assertEquals(200.0, three.trend.single { it.date == LocalDate.of(2026, 9, 7) }.value, 0.0)
        assertEquals(50.0, three.trend.single { it.date == LocalDate.of(2026, 9, 14) }.value, 0.0)
        assertEquals(350.0, three.trend.sumOf { it.value }, 0.001)
        assertEquals(350.0, six.trend.sumOf { it.value }, 0.001)
        assertEquals(three.totalKg!!, three.trend.sumOf { it.value }, 0.001)
        assertNonNegative(three.trend.map { it.value })
        assertNonNegative(six.trend.map { it.value })
    }

    @Test
    fun isoWeekCrossingTheYearKeepsOneMondayAndTheFollowingEmptyWeek() {
        val today = LocalDate.of(2026, 1, 18)
        val stats = assemble(
            listOf(
                LocalDate.of(2025, 12, 31) to 40.0,
                LocalDate.of(2026, 1, 1) to 100.0,
                LocalDate.of(2026, 1, 12) to 25.0
            ),
            today,
            StatisticsRange.Months3
        )
        val trend = stats.volume.trend
        assertMondayWeeks(trend.map { it.date })
        assertEquals(140.0, trend.single { it.date == LocalDate.of(2025, 12, 29) }.value, 0.0)
        assertEquals(0.0, trend.single { it.date == LocalDate.of(2026, 1, 5) }.value, 0.0)
        assertEquals(25.0, trend.single { it.date == LocalDate.of(2026, 1, 12) }.value, 0.0)
        assertEquals(165.0, trend.sumOf { it.value }, 0.001)
        assertEquals(LocalDate.of(2025, 10, 13), trend.first().date)
        assertEquals(LocalDate.of(2026, 1, 12), trend.last().date)
        assertNonNegative(trend.map { it.value })
    }

    @Test
    fun yearUsesCalendarMonthsAndKeepsTheEmptyMonthAcrossTheYearBoundary() {
        val today = LocalDate.of(2026, 2, 10)
        val stats = assemble(
            listOf(
                LocalDate.of(2025, 11, 20) to 30.0,
                LocalDate.of(2026, 1, 2) to 20.0
            ),
            today,
            StatisticsRange.Year1
        )
        val trend = stats.volume.trend
        assertTrue(trend.all { it.date.dayOfMonth == 1 })
        assertEquals(today.minusYears(1).withDayOfMonth(1), trend.first().date)
        assertEquals(LocalDate.of(2026, 2, 1), trend.last().date)
        assertEquals(30.0, trend.single { it.date == LocalDate.of(2025, 11, 1) }.value, 0.0)
        assertEquals(0.0, trend.single { it.date == LocalDate.of(2025, 12, 1) }.value, 0.0)
        assertEquals(20.0, trend.single { it.date == LocalDate.of(2026, 1, 1) }.value, 0.0)
        assertEquals(0.0, trend.single { it.date == LocalDate.of(2026, 2, 1) }.value, 0.0)
        assertEquals(50.0, trend.sumOf { it.value }, 0.001)
        assertEquals(13, trend.size)
        assertNonNegative(trend.map { it.value })
    }

    @Test
    fun allUsesMonthsFromTheFirstVolumeThroughToday() {
        val today = LocalDate.of(2026, 2, 10)
        val stats = assemble(
            listOf(
                LocalDate.of(2025, 11, 20) to 30.0,
                LocalDate.of(2026, 1, 2) to 20.0
            ),
            today,
            StatisticsRange.All
        )
        val trend = stats.volume.trend
        assertEquals(LocalDate.of(2025, 11, 1), trend.first().date)
        assertTrue(trend.first().value > 0.0)
        assertFalse(trend.any { it.date.isBefore(LocalDate.of(2025, 11, 1)) })
        assertEquals(LocalDate.of(2026, 2, 1), trend.last().date)
        assertEquals(0.0, trend.single { it.date == LocalDate.of(2025, 12, 1) }.value, 0.0)
        assertEquals(0.0, trend.single { it.date == LocalDate.of(2026, 2, 1) }.value, 0.0)
        assertEquals(50.0, trend.sumOf { it.value }, 0.001)
        assertEquals(4, trend.size)
        assertNonNegative(trend.map { it.value })
    }

    @Test
    fun selectedRangeBoundariesStayUnchanged() {
        val today = LocalDate.of(2026, 9, 26)
        assertEquals(LocalDate.of(2026, 8, 28), StatisticsRange.Days30.startInclusive(today))
        assertEquals(LocalDate.of(2026, 6, 26), StatisticsRange.Months3.startInclusive(today))
        assertEquals(LocalDate.of(2026, 3, 26), StatisticsRange.Months6.startInclusive(today))
        assertEquals(LocalDate.of(2025, 9, 26), StatisticsRange.Year1.startInclusive(today))
        assertNull(StatisticsRange.All.startInclusive(today))

        val stats = assemble(
            listOf(
                LocalDate.of(2026, 8, 27) to 10.0,
                LocalDate.of(2026, 8, 28) to 20.0
            ),
            today,
            StatisticsRange.Days30
        )
        assertEquals(20.0, stats.volume.totalKg!!, 0.0)
        assertEquals(LocalDate.of(2026, 8, 28), stats.volume.trend.first().date)
        assertFalse(stats.volume.trend.any { it.date.isBefore(LocalDate.of(2026, 8, 28)) })
        assertEquals(20.0, stats.volume.trend.sumOf { it.value }, 0.001)
    }

    @Test
    fun axisLabelsFollowResolutionAndAddYearContextWhenTheSeriesCrossesYears() {
        assertEquals(
            "Sep 16",
            VolumeTrendAxis.label(
                LocalDate.of(2026, 9, 16),
                VolumeTrendResolution.Daily,
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 9, 16)
            )
        )
        assertEquals(
            "Jan 2, 26",
            VolumeTrendAxis.label(
                LocalDate.of(2026, 1, 2),
                VolumeTrendResolution.Daily,
                LocalDate.of(2025, 12, 20),
                LocalDate.of(2026, 1, 18)
            )
        )
        assertEquals(
            "Sep 14",
            VolumeTrendAxis.label(
                LocalDate.of(2026, 9, 14),
                VolumeTrendResolution.Weekly,
                LocalDate.of(2026, 6, 22),
                LocalDate.of(2026, 9, 21)
            )
        )
        assertEquals(
            "Dec 29, 25",
            VolumeTrendAxis.label(
                LocalDate.of(2025, 12, 29),
                VolumeTrendResolution.Weekly,
                LocalDate.of(2025, 12, 29),
                LocalDate.of(2026, 1, 12)
            )
        )
        assertEquals(
            "Mar",
            VolumeTrendAxis.label(
                LocalDate.of(2026, 3, 1),
                VolumeTrendResolution.Monthly,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 1)
            )
        )
        assertEquals(
            "Sep 2025",
            VolumeTrendAxis.label(
                LocalDate.of(2025, 9, 1),
                VolumeTrendResolution.Monthly,
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 9, 1)
            )
        )
    }

    private fun assertMondayWeeks(dates: List<LocalDate>) {
        assertTrue(dates.all { it.dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(dates.zipWithNext().all { (earlier, later) -> later == earlier.plusWeeks(1) })
    }

    private fun assertNonNegative(values: List<Double>) {
        assertTrue(values.all { it >= 0.0 })
        val bounds = ChartScale.yBounds(values, ChartValueDomain.NonNegative)
        assertEquals(0.0, bounds.min, 0.0)
        assertTrue(bounds.max >= 0.0)
    }

    private fun assemble(
        workouts: List<Pair<LocalDate, Double>>,
        today: LocalDate,
        range: StatisticsRange
    ): TrainingStatistics {
        return TrainingStatisticsLogic.assemble(
            workouts.mapIndexed { index, (date, kg) -> weighted(index + 1L, date, kg) },
            emptyList(),
            today,
            range
        )
    }

    private fun weighted(id: Long, date: LocalDate, weightKg: Double): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = id,
                templateId = 1L,
                templateName = "Session $id",
                status = SessionStatus.COMPLETED,
                workoutDate = date,
                startedAt = 1L,
                finishedAt = 2L,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = 80.0,
                bodyWeightSource = BodyWeightSource.MANUAL,
                bodyWeightSourceDate = date,
                createdAt = 1L,
                updatedAt = 1L
            ),
            exercises = listOf(
                SessionExerciseItem(
                    exercise = SessionExercise(
                        id = id * 10,
                        sessionId = id,
                        exerciseId = 1L,
                        position = 0,
                        name = "Bench press",
                        category = ExerciseCategory.STRENGTH,
                        movementPattern = MovementPattern.OTHER,
                        measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                        resistanceBasis = ResistanceBasis.EXTERNAL,
                        weightInterpretation = WeightInterpretation.TOTAL,
                        primaryMuscle = MuscleGroup.CHEST,
                        secondaryMuscles = emptyList(),
                        notes = null
                    ),
                    sets = listOf(
                        SessionSet(
                            id = id,
                            sessionExerciseId = id * 10,
                            position = 0,
                            plannedMinReps = 1,
                            plannedMaxReps = 1,
                            plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                            plannedWeightKg = weightKg,
                            plannedDurationSeconds = null,
                            plannedDistanceMeters = null,
                            actualReps = 1,
                            actualLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                            actualWeightKg = weightKg,
                            actualDurationSeconds = null,
                            actualDistanceMeters = null,
                            status = SessionSetStatus.COMPLETED,
                            completedAt = 1L,
                            addedDuringWorkout = false
                        )
                    )
                )
            )
        )
    }
}
