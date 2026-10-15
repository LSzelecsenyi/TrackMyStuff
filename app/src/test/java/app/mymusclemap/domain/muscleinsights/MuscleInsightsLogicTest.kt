package app.mymusclemap.domain.muscleinsights

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseEnumCodec
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.statistics.StatisticsRange
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class MuscleInsightsLogicTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val now = today.atTime(LocalTime.of(18, 0))
    private val zone = ZoneOffset.UTC

    @Test
    fun emptyHistoryHasNoStimulusAndInsufficientBaseline() {
        val insight = insight(MuscleGroup.CHEST, emptyList())
        assertEquals(StimulusAge.None, insight.lastStimulus)
        assertEquals(0, insight.recentPrimarySets)
        assertNull(insight.baselineWeeklySets)
        assertEquals(LoadComparison.INSUFFICIENT, insight.comparison)
        assertEquals(BaselineCoverage.INSUFFICIENT, insight.coverage)
        assertNull(insight.partialHistoryDays)
        assertTrue(insight.weeklySets.isEmpty())
        assertNull(insight.spacing)
    }

    @Test
    fun lastStimulusUsesMinutesHoursThenCompleteDays() {
        assertEquals(StimulusAge.Minutes(35), stimulusAge(minutesAgo = 35))
        assertEquals(StimulusAge.Hours(8), stimulusAge(hoursAgo = 8))
        assertEquals(StimulusAge.Hours(23), stimulusAge(hoursAgo = 23))
        assertEquals(StimulusAge.Days(1), stimulusAge(hoursAgo = 24))
        assertEquals(StimulusAge.Days(1), stimulusAge(hoursAgo = 47))
        assertEquals(StimulusAge.Days(2), stimulusAge(hoursAgo = 48))
        assertEquals(StimulusAge.Days(2), stimulusAge(hoursAgo = 59))
        assertEquals(StimulusAge.Days(2), stimulusAge(hoursAgo = 60))
        assertEquals(StimulusAge.Days(3), stimulusAge(hoursAgo = 72))
    }

    @Test
    fun rollingWindowsKeepTodayAndExcludeTheBoundaryDayFromTheOtherWindow() {
        val sets = listOf(
            workout(1L, today.minusDays(6), MuscleGroup.CHEST, completedSets = 2),
            workout(2L, today.minusDays(7), MuscleGroup.CHEST, completedSets = 4),
            workout(3L, today.minusDays(34), MuscleGroup.LATS, completedSets = 1),
            workout(4L, today.minusDays(35), MuscleGroup.CHEST, completedSets = 9)
        )
        val insight = insight(MuscleGroup.CHEST, sets)
        assertEquals(2, insight.recentPrimarySets)
        assertEquals(1.0, insight.baselineWeeklySets!!, 0.001)
        assertEquals(LoadComparison.ABOVE, insight.comparison)
    }

    @Test
    fun missingCoverageIsNotTreatedAsAZeroBaseline() {
        val insight = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, today.minusDays(3), MuscleGroup.CHEST, completedSets = 4))
        )
        assertEquals(4, insight.recentPrimarySets)
        assertEquals(LoadComparison.INSUFFICIENT, insight.comparison)
        assertNull(insight.baselineWeeklySets)
    }

    @Test
    fun coveredZeroBaselineWithNoRecentSetsIsNear() {
        val insight = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, today.minusDays(40), MuscleGroup.LATS, completedSets = 1))
        )
        assertEquals(0, insight.recentPrimarySets)
        assertEquals(0.0, insight.baselineWeeklySets!!, 0.0)
        assertEquals(LoadComparison.NEAR, insight.comparison)
    }

    @Test
    fun nearBandIsWithinFifteenPercentOfTheWeeklyEquivalent() {
        val baseline = (0 until 4).map { week ->
            workout(week.toLong() + 1, today.minusDays(10L + week * 7), MuscleGroup.CHEST, completedSets = 4)
        }
        val coverage = workout(90L, today.minusDays(40), MuscleGroup.LATS, completedSets = 1)
        val recent = workout(91L, today.minusDays(1), MuscleGroup.CHEST, completedSets = 4)
        val insight = insight(MuscleGroup.CHEST, baseline + coverage + recent)
        assertEquals(4.0, insight.baselineWeeklySets!!, 0.001)
        assertEquals(4, insight.recentPrimarySets)
        assertEquals(LoadComparison.NEAR, insight.comparison)
    }

    @Test
    fun belowBaselineWhenRecentSetsAreMoreThanFifteenPercentLower() {
        val history = listOf(
            workout(1L, today.minusDays(40), MuscleGroup.CHEST, completedSets = 1),
            workout(2L, today.minusDays(10), MuscleGroup.CHEST, completedSets = 8)
        )
        val insight = insight(MuscleGroup.CHEST, history)
        assertEquals(0, insight.recentPrimarySets)
        assertEquals(2.0, insight.baselineWeeklySets!!, 0.001)
        assertEquals(LoadComparison.BELOW, insight.comparison)
    }

    @Test
    fun abandonedWorkoutsAndUnfinishedSetsAreExcluded() {
        val abandoned = workout(1L, today, MuscleGroup.CHEST, completedSets = 3, status = SessionStatus.ABANDONED)
        val mixed = workout(
            2L,
            today,
            MuscleGroup.CHEST,
            completedSets = 1,
            extraStatuses = listOf(SessionSetStatus.PENDING, SessionSetStatus.SKIPPED)
        )
        val insight = insight(MuscleGroup.CHEST, listOf(abandoned, mixed))
        assertEquals(1, insight.recentPrimarySets)
        assertEquals(1, insight.periodPrimarySets)
    }

    @Test
    fun secondaryInvolvementDoesNotAddToPrimaryVolumeAndIsNotDoubleCounted() {
        val both = workout(
            1L,
            today.minusDays(1),
            MuscleGroup.CHEST,
            completedSets = 2,
            secondary = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS, MuscleGroup.TRICEPS)
        )
        val chest = insight(MuscleGroup.CHEST, listOf(both))
        val triceps = insight(MuscleGroup.TRICEPS, listOf(both))
        assertEquals(2, chest.recentPrimarySets)
        assertEquals(0, chest.periodSecondarySets)
        assertEquals(0, triceps.recentPrimarySets)
        assertEquals(2, triceps.periodSecondarySets)
        assertEquals(StimulusAge.Days(1), triceps.lastStimulus)
    }

    @Test
    fun legacyTrapsIdentifierCountsAsUpperBack() {
        val muscle = ExerciseEnumCodec.muscleOrFallback("TRAPS")
        assertEquals(MuscleGroup.UPPER_BACK, muscle)
        val insight = insight(
            MuscleGroup.UPPER_BACK,
            listOf(workout(1L, today, muscle, completedSets = 3))
        )
        assertEquals(3, insight.recentPrimarySets)
        assertNotEquals(MuscleGroup.SERRATUS, muscle)
    }

    @Test
    fun serratusIsTrackedOnItsOwn() {
        val insight = insight(
            MuscleGroup.SERRATUS,
            listOf(
                workout(1L, today, MuscleGroup.SERRATUS, completedSets = 2),
                workout(2L, today, MuscleGroup.OBLIQUES, completedSets = 5)
            )
        )
        assertEquals(2, insight.recentPrimarySets)
        assertEquals(0, insight(MuscleGroup.OBLIQUES, listOf(
            workout(1L, today, MuscleGroup.SERRATUS, completedSets = 2)
        )).recentPrimarySets)
    }

    @Test
    fun fullBodyAndCardioDoNotBecomeAnatomicalVolume() {
        val workouts = listOf(
            workout(1L, today, MuscleGroup.FULL_BODY, completedSets = 4, secondary = listOf(MuscleGroup.CHEST)),
            workout(2L, today, MuscleGroup.CARDIOVASCULAR, completedSets = 3)
        )
        val chest = insight(MuscleGroup.CHEST, workouts)
        assertEquals(0, chest.recentPrimarySets)
        assertEquals(4, chest.periodSecondarySets)
        assertEquals(MuscleInsight.empty(MuscleGroup.FULL_BODY).periodPrimarySets, insight(MuscleGroup.FULL_BODY, workouts).periodPrimarySets)
    }

    @Test
    fun timezoneBoundaryUsesTheLocalCompletionDate() {
        val budapest = ZoneId.of("Europe/Budapest")
        val utcEvening = today.minusDays(7).atTime(22, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val session = workout(1L, today.minusDays(7), MuscleGroup.CHEST, completedSets = 1, completedAt = utcEvening)
        val local = MuscleInsightsLogic.index(listOf(session), budapest)
            .insight(MuscleGroup.CHEST, today, now, MuscleInsightsPeriod.Weeks4)
        val utc = insight(MuscleGroup.CHEST, listOf(session))
        assertEquals(1, local.recentPrimarySets)
        assertEquals(0, utc.recentPrimarySets)
    }

    @Test
    fun weeklyChartIsMondayAlignedAndIncludesEmptyWeeks() {
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insight = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, monday.minusWeeks(1), MuscleGroup.CHEST, completedSets = 2))
        )
        assertEquals(4, insight.weeklySets.size)
        assertEquals(monday.minusWeeks(3), insight.weeklySets.first().weekStart)
        assertEquals(2, insight.weeklySets[2].primarySets)
        assertEquals(0, insight.weeklySets.last().primarySets)
    }

    @Test
    fun periodSelectionDropsSetsOutsideTheWindow() {
        val old = workout(1L, today.minusMonths(8), MuscleGroup.CHEST, completedSets = 6)
        val recent = workout(2L, today.minusDays(1), MuscleGroup.CHEST, completedSets = 1)
        val year = insight(MuscleGroup.CHEST, listOf(old, recent), MuscleInsightsPeriod.Year1)
        val four = insight(MuscleGroup.CHEST, listOf(old, recent), MuscleInsightsPeriod.Weeks4)
        assertEquals(7, year.periodPrimarySets)
        assertEquals(1, four.periodPrimarySets)
        assertEquals(2, year.spacing!!.sessionDates)
        assertNull(four.spacing)
    }

    @Test
    fun spacingNeedsTwoDistinctDates() {
        val sameDay = listOf(
            workout(1L, today.minusDays(3), MuscleGroup.CHEST, completedSets = 1),
            workout(2L, today.minusDays(3), MuscleGroup.CHEST, completedSets = 1)
        )
        assertNull(insight(MuscleGroup.CHEST, sameDay).spacing)
        val spaced = insight(
            MuscleGroup.CHEST,
            listOf(
                workout(1L, today.minusDays(10), MuscleGroup.CHEST, completedSets = 1),
                workout(2L, today.minusDays(4), MuscleGroup.CHEST, completedSets = 1)
            ),
            MuscleInsightsPeriod.All
        )
        assertEquals(6, spaced.spacing!!.shortestDays)
        assertEquals(6.0, spaced.spacing.averageDays, 0.001)
    }

    @Test
    fun demoPreviewIsStableAndIgnoresAnEmptyUserHistory() {
        val demo = MuscleInsightsDemo.preview(today, now, zone)
        val user = MuscleInsightsLogic.index(emptyList(), zone).preview(today, now, StatisticsRange.Days30)
        assertTrue(demo.demo)
        assertEquals(MuscleGroup.CHEST, demo.featuredMuscle)
        assertTrue(demo.bars.isNotEmpty())
        assertTrue(user.bars.isEmpty())
        assertNotEquals(demo.bars, user.bars)
        val again = MuscleInsightsDemo.preview(today, now, zone)
        assertEquals(demo, again)
        val chest = MuscleInsightsDemo.insight(
            MuscleGroup.CHEST,
            today,
            now,
            zone,
            MuscleInsightsPeriod.Weeks4
        )
        assertEquals(LoadComparison.ABOVE, chest.comparison)
        assertEquals(4, chest.recentPrimarySets)
    }

    @Test
    fun coverageFollowsTheSevenDayToleranceAroundTheFixedBaseline() {
        val baselineStart = LocalDate.of(2026, 9, 11)
        val baselineEnd = LocalDate.of(2026, 10, 8)
        assertEquals(baselineStart, today.minusDays(34))
        assertEquals(baselineEnd, today.minusDays(7))

        val onStart = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, baselineStart, MuscleGroup.CHEST, completedSets = 4))
        )
        assertEquals(BaselineCoverage.FULL, onStart.coverage)
        assertEquals(1.0, onStart.baselineWeeklySets!!, 0.001)
        assertNull(onStart.partialHistoryDays)

        val plusTwo = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, baselineStart.plusDays(2), MuscleGroup.LATS, completedSets = 1))
        )
        assertEquals(BaselineCoverage.PARTIAL, plusTwo.coverage)
        assertEquals(0.0, plusTwo.baselineWeeklySets!!, 0.0)
        assertEquals(25, plusTwo.partialHistoryDays)

        val plusSeven = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, baselineStart.plusDays(7), MuscleGroup.CHEST, completedSets = 8))
        )
        assertEquals(BaselineCoverage.PARTIAL, plusSeven.coverage)
        assertEquals(2.0, plusSeven.baselineWeeklySets!!, 0.001)
        assertEquals(LoadComparison.BELOW, plusSeven.comparison)

        val plusEight = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, baselineStart.plusDays(8), MuscleGroup.CHEST, completedSets = 8))
        )
        assertEquals(BaselineCoverage.INSUFFICIENT, plusEight.coverage)
        assertEquals(LoadComparison.INSUFFICIENT, plusEight.comparison)
        assertNull(plusEight.baselineWeeklySets)

        val onEnd = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, baselineEnd, MuscleGroup.CHEST, completedSets = 4))
        )
        assertEquals(BaselineCoverage.INSUFFICIENT, onEnd.coverage)
        assertNull(onEnd.baselineWeeklySets)
    }

    @Test
    fun recordedChestHistoryStartingTwoDaysIntoTheBaselineIsAboveAverage() {
        val baselineStart = LocalDate.of(2026, 9, 11)
        val history = listOf(
            workout(1L, baselineStart.plusDays(2), MuscleGroup.QUADRICEPS, completedSets = 1),
            workout(2L, baselineStart.plusDays(3), MuscleGroup.CHEST, completedSets = 40),
            workout(3L, LocalDate.of(2026, 10, 9), MuscleGroup.CHEST, completedSets = 14)
        )
        val insight = insight(MuscleGroup.CHEST, history)
        assertEquals(BaselineCoverage.PARTIAL, insight.coverage)
        assertEquals(14, insight.recentPrimarySets)
        assertEquals(10.0, insight.baselineWeeklySets!!, 0.001)
        assertEquals(LoadComparison.ABOVE, insight.comparison)
        assertEquals(25, insight.partialHistoryDays)
        val otherMuscle = insight(MuscleGroup.QUADRICEPS, history)
        assertEquals(BaselineCoverage.PARTIAL, otherMuscle.coverage)
        assertEquals(0.25, otherMuscle.baselineWeeklySets!!, 0.001)
    }

    @Test
    fun zeroBaselineWithRecentSetsIsAboveWithoutAPercentage() {
        val baselineStart = today.minusDays(34)
        val insight = insight(
            MuscleGroup.CHEST,
            listOf(
                workout(1L, baselineStart, MuscleGroup.LATS, completedSets = 1),
                workout(2L, today, MuscleGroup.CHEST, completedSets = 3)
            )
        )
        assertEquals(BaselineCoverage.FULL, insight.coverage)
        assertEquals(0.0, insight.baselineWeeklySets!!, 0.0)
        assertEquals(3, insight.recentPrimarySets)
        assertEquals(LoadComparison.ABOVE, insight.comparison)
    }

    @Test
    fun zeroBaselineAndZeroRecentSetsStayNear() {
        val insight = insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, today.minusDays(34), MuscleGroup.LATS, completedSets = 2))
        )
        assertEquals(BaselineCoverage.FULL, insight.coverage)
        assertEquals(0, insight.recentPrimarySets)
        assertEquals(0.0, insight.baselineWeeklySets!!, 0.0)
        assertEquals(LoadComparison.NEAR, insight.comparison)
        assertNull(insight.partialHistoryDays)
    }

    @Test
    fun statisticsDays30OpensOnTheFourWeekPeriod() {
        assertEquals(MuscleInsightsPeriod.Weeks4, MuscleInsightsPeriod.fromStatistics(StatisticsRange.Days30))
        assertEquals(MuscleInsightsPeriod.All, MuscleInsightsPeriod.fromStatistics(StatisticsRange.All))
    }

    private fun stimulusAge(hoursAgo: Long = 0, minutesAgo: Long = 0): StimulusAge {
        val at = now.minusHours(hoursAgo).minusMinutes(minutesAgo)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        return insight(
            MuscleGroup.CHEST,
            listOf(workout(1L, today, MuscleGroup.CHEST, completedSets = 1, completedAt = at))
        ).lastStimulus
    }

    private fun insight(
        muscle: MuscleGroup,
        workouts: List<WorkoutSessionAggregate>,
        period: MuscleInsightsPeriod = MuscleInsightsPeriod.Weeks4
    ): MuscleInsight {
        return MuscleInsightsLogic.index(workouts, zone).insight(muscle, today, now, period)
    }

    private fun workout(
        id: Long,
        date: LocalDate,
        primary: MuscleGroup,
        completedSets: Int,
        status: SessionStatus = SessionStatus.COMPLETED,
        secondary: List<MuscleGroup> = emptyList(),
        extraStatuses: List<SessionSetStatus> = emptyList(),
        hour: Int = 18,
        completedAt: Long? = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    ): WorkoutSessionAggregate {
        val sets = ArrayList<SessionSet>()
        repeat(completedSets) { index ->
            sets += set(id * 100 + index, SessionSetStatus.COMPLETED, completedAt)
        }
        extraStatuses.forEachIndexed { index, setStatus ->
            sets += set(id * 100 + completedSets + index, setStatus, null)
        }
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = id,
                templateId = null,
                templateName = "Session",
                status = status,
                workoutDate = date,
                startedAt = 1L,
                finishedAt = if (status == SessionStatus.COMPLETED) completedAt else null,
                abandonedAt = if (status == SessionStatus.ABANDONED) 2L else null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.MANUAL,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 1L
            ),
            exercises = listOf(
                SessionExerciseItem(
                    exercise = SessionExercise(
                        id = id,
                        sessionId = id,
                        exerciseId = id,
                        position = 0,
                        name = "Exercise",
                        category = ExerciseCategory.STRENGTH,
                        movementPattern = MovementPattern.OTHER,
                        measurementType = MeasurementType.REPETITIONS,
                        resistanceBasis = ResistanceBasis.BODYWEIGHT,
                        weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                        primaryMuscle = primary,
                        secondaryMuscles = secondary,
                        notes = null
                    ),
                    sets = sets
                )
            )
        )
    }

    private fun set(id: Long, status: SessionSetStatus, completedAt: Long?): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = id,
            position = 0,
            plannedMinReps = 8,
            plannedMaxReps = 8,
            plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            plannedWeightKg = null,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = if (status == SessionSetStatus.COMPLETED) 8 else null,
            actualLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            actualWeightKg = null,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) completedAt else null,
            addedDuringWorkout = false
        )
    }
}
