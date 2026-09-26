package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.WeeklyAverageCalculator
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.ScheduledWorkout
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
import java.time.LocalDate

class TrainingStatisticsLogicTest {
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun emptyHistoryIsEmptyDashboardWithNoAdherence() {
        val stats = stats()
        assertFalse(stats.hasCompletedWorkouts)
        assertEquals(0, stats.activity.workoutCount)
        assertNull(stats.volume.totalKg)
        assertTrue(stats.volume.trend.isEmpty())
        assertTrue(stats.exercises.isEmpty())
        assertTrue(stats.muscleDistribution.isEmpty())
        assertTrue(stats.restBetweenSessions.isEmpty())
        assertNull(stats.adherence.percent)
        assertEquals(0, stats.adherence.plannedCount)
    }

    @Test
    fun oneCompletedWorkoutCountsActivityWithoutTrendOrRest() {
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today,
                    listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 8)),
                    startedAt = 0L,
                    finishedAt = 3_600_000L
                )
            )
        )
        assertEquals(1, stats.activity.workoutCount)
        assertEquals(1, stats.activity.completedSetCount)
        assertEquals(1, stats.activity.trainingDayCount)
        assertEquals(3_600_000L, stats.activity.durationMillis)
        assertNull(stats.volume.totalKg)
        assertFalse(stats.volume.hasTrend)
        assertEquals(MuscleGroup.LATS, stats.muscleDistribution.single().muscle)
        assertEquals(1, stats.muscleDistribution.single().completedSetCount)
        assertTrue(stats.restBetweenSessions.isEmpty())
        assertEquals(1, stats.exercises.size)
        assertFalse(stats.exercises.single().showsBestSeparately)
        assertFalse(stats.exercises.single().hasProgression)
    }

    @Test
    fun subSecondSessionDurationIsOmitted() {
        val stats = stats(listOf(workout(1L, today, listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 8)))))
        assertNull(stats.activity.durationMillis)
    }

    @Test
    fun multipleWorkoutsAcrossDatesCountDistinctTrainingDays() {
        val stats = stats(
            listOf(
                workout(1L, today.minusDays(8), listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 5))),
                workout(2L, today.minusDays(2), listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 6))),
                workout(3L, today.minusDays(2), listOf(repsItem(2L, "Squat", MuscleGroup.QUADRICEPS, 8))),
                workout(4L, today, listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 7)))
            )
        )
        assertEquals(4, stats.activity.workoutCount)
        assertEquals(4, stats.activity.completedSetCount)
        assertEquals(3, stats.activity.trainingDayCount)
    }

    @Test
    fun abandonedInProgressAndFutureSessionsAreExcluded() {
        val stats = stats(
            listOf(
                workout(1L, today, listOf(repsItem(1L, "A", MuscleGroup.CHEST, 8)), SessionStatus.ABANDONED),
                workout(2L, today, listOf(repsItem(1L, "A", MuscleGroup.CHEST, 8)), SessionStatus.IN_PROGRESS),
                workout(3L, today.plusDays(1), listOf(repsItem(1L, "A", MuscleGroup.CHEST, 8))),
                workout(4L, today, listOf(repsItem(1L, "A", MuscleGroup.CHEST, 8)))
            )
        )
        assertEquals(1, stats.activity.workoutCount)
        assertEquals(1, stats.activity.trainingDayCount)
    }

    @Test
    fun skippedAndPendingSetsAreExcludedFromVolumeProgressAndMuscles() {
        val completed = set(1L, SessionSetStatus.COMPLETED, reps = 5, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 80.0)
        val skipped = set(2L, SessionSetStatus.SKIPPED, reps = 5, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 120.0)
        val pending = set(3L, SessionSetStatus.PENDING, reps = 5, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 100.0)
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today,
                    listOf(
                        item(
                            exerciseId = 10L,
                            name = "Bench press",
                            measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
                            primary = MuscleGroup.CHEST,
                            sets = listOf(completed, skipped, pending),
                            interpretation = WeightInterpretation.TOTAL
                        )
                    )
                )
            )
        )
        assertEquals(1, stats.activity.workoutCount)
        assertEquals(1, stats.activity.completedSetCount)
        assertEquals(400.0, stats.volume.totalKg!!, 0.0)
        assertEquals(1, stats.volume.completedSetCount)
        assertFalse(stats.volume.hasTrend)
        val progress = stats.exercises.single()
        val best = progress.best as ExerciseBest.WeightedSet
        assertEquals(80.0, best.effectiveKg, 0.0)
        assertEquals(5, best.reps)
        assertFalse(progress.showsBestSeparately)
        assertEquals(1, stats.muscleDistribution.single().completedSetCount)
    }

    @Test
    fun bodyweightAndAssistanceSetsAreNotKilogramVolume() {
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today,
                    listOf(
                        item(
                            1L,
                            "Pull-up",
                            MeasurementType.REPETITIONS_AND_WEIGHT,
                            MuscleGroup.LATS,
                            listOf(
                                set(1L, reps = 8, load = PlannedLoadKind.BODYWEIGHT_ONLY, weight = 0.0),
                                set(2L, reps = 8, load = PlannedLoadKind.ASSISTANCE, weight = 12.0)
                            ),
                            WeightInterpretation.TOTAL
                        )
                    )
                )
            )
        )
        assertNull(stats.volume.totalKg)
        assertTrue(stats.volume.trend.isEmpty())
        val progress = stats.exercises.single()
        assertTrue(progress.best is ExerciseBest.Reps)
        assertEquals(8, (progress.best as ExerciseBest.Reps).reps)
    }

    @Test
    fun durationAndWeightIsNotKilogramVolume() {
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today,
                    listOf(
                        item(
                            1L,
                            "Farmer carry",
                            MeasurementType.DURATION_AND_WEIGHT,
                            MuscleGroup.FOREARMS,
                            listOf(set(1L, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 24.0, duration = 40)),
                            WeightInterpretation.TOTAL
                        )
                    )
                )
            )
        )
        assertNull(stats.volume.totalKg)
        assertEquals(40, (stats.exercises.single().best as ExerciseBest.Duration).seconds)
    }

    @Test
    fun perSideExternalWeightIsDoubledForVolumeAndBest() {
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today,
                    listOf(
                        item(
                            1L,
                            "Dumbbell press",
                            MeasurementType.REPETITIONS_AND_WEIGHT,
                            MuscleGroup.CHEST,
                            listOf(set(1L, reps = 10, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 20.0)),
                            WeightInterpretation.PER_SIDE
                        )
                    )
                )
            )
        )
        assertEquals(400.0, stats.volume.totalKg!!, 0.0)
        val best = stats.exercises.single().best as ExerciseBest.WeightedSet
        assertEquals(40.0, best.effectiveKg, 0.0)
        assertEquals(20.0, best.recordedKg, 0.0)
        assertTrue(best.perSide)
    }

    @Test
    fun addedWeightVolumeUsesRecordedLoadNotBodyMass() {
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today,
                    listOf(
                        item(
                            1L,
                            "Weighted pull-up",
                            MeasurementType.REPETITIONS_AND_WEIGHT,
                            MuscleGroup.LATS,
                            listOf(set(1L, reps = 5, load = PlannedLoadKind.ADDED_WEIGHT, weight = 10.0)),
                            WeightInterpretation.TOTAL
                        )
                    )
                )
            )
        )
        assertEquals(50.0, stats.volume.totalKg!!, 0.0)
    }

    @Test
    fun volumeTrendUsesPositiveIsoWeeksAndDoesNotPadZeros() {
        val thisMonday = LocalDate.of(2026, 9, 14)
        val lastMonday = LocalDate.of(2026, 9, 7)
        val older = LocalDate.of(2026, 7, 1)
        val thirtyDay = stats(
            listOf(
                weightedWorkout(1L, older, 100.0),
                weightedWorkout(2L, lastMonday, 200.0),
                weightedWorkout(3L, thisMonday, 50.0)
            )
        )
        assertEquals(250.0, thirtyDay.volume.totalKg!!, 0.0)
        assertEquals(2, thirtyDay.volume.trend.size)
        assertEquals(listOf(200.0, 50.0), thirtyDay.volume.trend.map { it.value })
        assertFalse(thirtyDay.volume.trend.any { it.value == 0.0 })
        assertEquals(lastMonday, thirtyDay.volume.trend.first().date)
        assertEquals(thisMonday, thirtyDay.volume.trend.last().date)

        val allTime = stats(
            listOf(
                weightedWorkout(1L, older, 100.0),
                weightedWorkout(2L, lastMonday, 200.0),
                weightedWorkout(3L, thisMonday, 50.0)
            ),
            range = StatisticsRange.All
        )
        assertEquals(350.0, allTime.volume.totalKg!!, 0.0)
        assertEquals(3, allTime.volume.trend.size)
        val olderWeek = WeeklyAverageCalculator.isoWeekKey(older)
        assertEquals(
            WeeklyAverageCalculator.weekStart(olderWeek.year, olderWeek.week),
            allTime.volume.trend.first().date
        )
    }

    @Test
    fun onePositiveVolumeWeekIsNotATrend() {
        val stats = stats(listOf(weightedWorkout(1L, today, 80.0)))
        assertEquals(80.0, stats.volume.totalKg!!, 0.0)
        assertFalse(stats.volume.hasTrend)
        assertTrue(stats.volume.trend.isEmpty())
    }

    @Test
    fun rangeFiltersVolumeToTheSelectedWindow() {
        val old = weightedWorkout(1L, today.minusDays(40), 100.0)
        val recent = weightedWorkout(2L, today, 50.0)
        assertEquals(50.0, stats(listOf(old, recent)).volume.totalKg!!, 0.0)
        assertEquals(150.0, stats(listOf(old, recent), range = StatisticsRange.All).volume.totalKg!!, 0.0)
    }

    @Test
    fun bestWeightedSetPrefersHeavierLoadThenMoreReps() {
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today.minusDays(3),
                    listOf(
                        item(
                            1L,
                            "Squat",
                            MeasurementType.REPETITIONS_AND_WEIGHT,
                            MuscleGroup.QUADRICEPS,
                            listOf(set(1L, reps = 8, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 100.0)),
                            WeightInterpretation.TOTAL
                        )
                    )
                ),
                workout(
                    2L,
                    today,
                    listOf(
                        item(
                            1L,
                            "Squat",
                            MeasurementType.REPETITIONS_AND_WEIGHT,
                            MuscleGroup.QUADRICEPS,
                            listOf(
                                set(2L, reps = 3, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 120.0),
                                set(3L, reps = 5, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 120.0)
                            ),
                            WeightInterpretation.TOTAL
                        )
                    )
                )
            )
        )
        val progress = stats.exercises.single()
        val best = progress.best as ExerciseBest.WeightedSet
        val recent = progress.recent as ExerciseBest.WeightedSet
        assertEquals(120.0, best.effectiveKg, 0.0)
        assertEquals(5, best.reps)
        assertEquals(120.0, recent.effectiveKg, 0.0)
        assertEquals(5, recent.reps)
        assertTrue(progress.hasProgression)
        assertFalse(progress.showsBestSeparately)
        assertEquals(listOf(100.0, 120.0), progress.history.map { it.value })
    }

    @Test
    fun durationAndDistanceUseLongestCompletedSet() {
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today.minusDays(1),
                    listOf(
                        item(
                            2L,
                            "Plank",
                            MeasurementType.DURATION,
                            MuscleGroup.ABS,
                            listOf(set(1L, duration = 30), set(2L, duration = 45)),
                            WeightInterpretation.NOT_APPLICABLE
                        ),
                        item(
                            3L,
                            "Run",
                            MeasurementType.DISTANCE_AND_DURATION,
                            MuscleGroup.CARDIOVASCULAR,
                            listOf(set(3L, duration = 600, distance = 2000.0)),
                            WeightInterpretation.NOT_APPLICABLE
                        )
                    )
                ),
                workout(
                    2L,
                    today,
                    listOf(
                        item(
                            2L,
                            "Plank",
                            MeasurementType.DURATION,
                            MuscleGroup.ABS,
                            listOf(set(4L, duration = 40)),
                            WeightInterpretation.NOT_APPLICABLE
                        ),
                        item(
                            3L,
                            "Run",
                            MeasurementType.DISTANCE_AND_DURATION,
                            MuscleGroup.CARDIOVASCULAR,
                            listOf(set(5L, duration = 700, distance = 2500.0)),
                            WeightInterpretation.NOT_APPLICABLE
                        )
                    )
                )
            )
        )
        val plank = stats.exercises.first { it.name == "Plank" }
        val run = stats.exercises.first { it.name == "Run" }
        assertEquals(45, (plank.best as ExerciseBest.Duration).seconds)
        assertEquals(40, (plank.recent as ExerciseBest.Duration).seconds)
        assertTrue(plank.showsBestSeparately)
        assertEquals(2500.0, (run.best as ExerciseBest.Distance).meters, 0.0)
        assertEquals(700, (run.best as ExerciseBest.Distance).durationSeconds)
        assertEquals(2500.0, (run.recent as ExerciseBest.Distance).meters, 0.0)
    }

    @Test
    fun completionOnlyCountsTrainingDaysNotANumericBestLoad() {
        val stats = stats(
            listOf(
                workout(
                    1L,
                    today.minusDays(4),
                    listOf(
                        item(
                            9L,
                            "Stretch",
                            MeasurementType.COMPLETION_ONLY,
                            MuscleGroup.FULL_BODY,
                            listOf(set(1L, SessionSetStatus.COMPLETED)),
                            WeightInterpretation.NOT_APPLICABLE
                        )
                    )
                ),
                workout(
                    2L,
                    today,
                    listOf(
                        item(
                            9L,
                            "Stretch",
                            MeasurementType.COMPLETION_ONLY,
                            MuscleGroup.FULL_BODY,
                            listOf(set(2L, SessionSetStatus.COMPLETED)),
                            WeightInterpretation.NOT_APPLICABLE
                        )
                    )
                )
            )
        )
        val progress = stats.exercises.single()
        assertEquals(2, (progress.best as ExerciseBest.Completions).count)
        assertEquals(ExerciseHistoryKind.COMPLETIONS, progress.historyKind)
        assertEquals(2, progress.history.size)
    }

    @Test
    fun muscleDistributionUsesPrimaryMusclesOnlyAndRanksTiesDeterministically() {
        val stats = stats(
            listOf(
                workout(1L, today.minusDays(20), listOf(repsItem(1L, "Row", MuscleGroup.LATS, 10))),
                workout(2L, today, listOf(repsItem(2L, "Bench", MuscleGroup.CHEST, 8))),
                workout(3L, today, listOf(repsItem(3L, "Press", MuscleGroup.CHEST, 6)))
            )
        )
        assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.LATS), stats.muscleDistribution.map { it.muscle })
        assertEquals(2, stats.muscleDistribution.first().completedSetCount)
        assertEquals(2, stats.muscleDistribution.first().workoutCount)
        val tied = stats(
            listOf(
                workout(1L, today.minusDays(1), listOf(repsItem(1L, "Curl", MuscleGroup.BICEPS, 8))),
                workout(2L, today, listOf(repsItem(2L, "Pushdown", MuscleGroup.TRICEPS, 8)))
            )
        )
        assertEquals(1, tied.muscleDistribution[0].completedSetCount)
        assertEquals(1, tied.muscleDistribution[1].completedSetCount)
        assertEquals(MuscleGroup.TRICEPS, tied.muscleDistribution.first().muscle)
        assertEquals(today, tied.muscleDistribution.first().lastTrained)
    }

    @Test
    fun workoutsOlderThanTheSelectedRangeAreExcluded() {
        val old = workout(1L, today.minusDays(40), listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 8)))
        val thirty = stats(listOf(old))
        assertEquals(0, thirty.activity.workoutCount)
        assertTrue(thirty.muscleDistribution.isEmpty())
        val all = stats(listOf(old), range = StatisticsRange.All)
        assertEquals(1, all.activity.workoutCount)
        assertEquals(MuscleGroup.LATS, all.muscleDistribution.single().muscle)
    }

    @Test
    fun planAdherenceUsesScheduledLinkNotWorkoutName() {
        val completedPlan = plan(1L, today.minusDays(2), SessionStatus.COMPLETED)
        val missed = plan(2L, today.minusDays(1), null)
        val todayPlanned = plan(3L, today, null)
        val inProgress = plan(4L, today, SessionStatus.IN_PROGRESS)
        val future = plan(5L, today.plusDays(1), null)
        val outside = plan(6L, today.minusDays(40), SessionStatus.COMPLETED)
        val stats = stats(
            aggregates = listOf(
                workout(10L, today, listOf(repsItem(1L, "Push A", MuscleGroup.CHEST, 8)))
            ),
            scheduled = listOf(completedPlan, missed, todayPlanned, inProgress, future, outside)
        )
        assertEquals(4, stats.adherence.plannedCount)
        assertEquals(1, stats.adherence.completedCount)
        assertEquals(1, stats.adherence.missedCount)
        assertEquals(1, stats.adherence.inProgressCount)
        assertEquals(25, stats.adherence.percent)
        assertEquals(1, stats.activity.workoutCount)
    }

    @Test
    fun planAdherencePercentRoundsAndIgnoresUnlinkedCompletions() {
        val scheduled = listOf(
            plan(1L, today.minusDays(11), SessionStatus.COMPLETED),
            plan(2L, today.minusDays(10), SessionStatus.COMPLETED),
            plan(3L, today.minusDays(9), SessionStatus.COMPLETED),
            plan(4L, today.minusDays(8), SessionStatus.COMPLETED),
            plan(5L, today.minusDays(7), SessionStatus.COMPLETED),
            plan(6L, today.minusDays(6), SessionStatus.COMPLETED),
            plan(7L, today.minusDays(5), SessionStatus.COMPLETED),
            plan(8L, today.minusDays(4), SessionStatus.COMPLETED),
            plan(9L, today.minusDays(3), SessionStatus.COMPLETED),
            plan(10L, today.minusDays(2), SessionStatus.COMPLETED),
            plan(11L, today.minusDays(1), null),
            plan(12L, today, null)
        )
        val stats = stats(scheduled = scheduled)
        assertEquals(12, stats.adherence.plannedCount)
        assertEquals(10, stats.adherence.completedCount)
        assertEquals(83, stats.adherence.percent)
        val twoThirds = stats(
            scheduled = listOf(
                plan(1L, today.minusDays(2), SessionStatus.COMPLETED),
                plan(2L, today.minusDays(1), SessionStatus.COMPLETED),
                plan(3L, today, null)
            )
        )
        assertEquals(67, twoThirds.adherence.percent)
    }

    @Test
    fun lateCompletionCountsAsFullyCompletedWithoutAMiss() {
        val monday = plan(1L, today.minusDays(4), SessionStatus.COMPLETED)
        val wednesday = plan(
            id = 2L,
            date = today.minusDays(2),
            sessionStatus = SessionStatus.COMPLETED,
            originalScheduledDate = today.minusDays(2)
        )
        val friday = plan(3L, today, null)
        val stats = stats(scheduled = listOf(monday, wednesday, friday))
        assertEquals(3, stats.adherence.plannedCount)
        assertEquals(2, stats.adherence.completedCount)
        assertEquals(0, stats.adherence.missedCount)
        assertEquals(67, stats.adherence.percent)
    }

    @Test
    fun completionAfterTheSelectedRangeStillCompletesAnInRangeOccurrence() {
        val start = StatisticsRange.Days30.startInclusive(today)!!
        val stats = stats(
            scheduled = listOf(plan(1L, start, SessionStatus.COMPLETED))
        )
        assertEquals(1, stats.adherence.plannedCount)
        assertEquals(1, stats.adherence.completedCount)
        assertEquals(100, stats.adherence.percent)
        assertEquals(0, stats.adherence.missedCount)
    }

    @Test
    fun inProgressAndPastPendingArePlannedButNotCompleted() {
        val pending = plan(1L, today.minusDays(1), null)
        val inProgress = plan(2L, today, SessionStatus.IN_PROGRESS)
        val stats = stats(scheduled = listOf(pending, inProgress))
        assertEquals(2, stats.adherence.plannedCount)
        assertEquals(0, stats.adherence.completedCount)
        assertEquals(1, stats.adherence.missedCount)
        assertEquals(1, stats.adherence.inProgressCount)
        assertEquals(0, stats.adherence.percent)
    }

    @Test
    fun cancelledOccurrenceIsExcludedFromNumeratorAndDenominator() {
        val kept = plan(1L, today.minusDays(1), SessionStatus.COMPLETED)
        val cancelled = plan(2L, today.minusDays(1), null, cancelledAt = 99L)
        val stats = stats(scheduled = listOf(kept, cancelled))
        assertEquals(1, stats.adherence.plannedCount)
        assertEquals(1, stats.adherence.completedCount)
        assertEquals(100, stats.adherence.percent)
        assertEquals(0, stats.adherence.missedCount)
    }

    @Test
    fun rescheduleUsesCurrentScheduledDateOnce() {
        val movedToThursday = plan(
            id = 1L,
            date = today.minusDays(1),
            sessionStatus = null,
            originalScheduledDate = today.minusDays(2)
        )
        val stats = stats(scheduled = listOf(movedToThursday))
        assertEquals(1, stats.adherence.plannedCount)
        assertEquals(0, stats.adherence.completedCount)
        assertEquals(1, stats.adherence.missedCount)
    }

    @Test
    fun originalDateInsideRangeDoesNotCountWhenCurrentDateIsOutside() {
        val start = StatisticsRange.Days30.startInclusive(today)!!
        val movedOut = plan(
            id = 1L,
            date = start.minusDays(1),
            sessionStatus = SessionStatus.COMPLETED,
            originalScheduledDate = start
        )
        val stats = stats(scheduled = listOf(movedOut))
        assertEquals(0, stats.adherence.plannedCount)
        assertNull(stats.adherence.percent)
    }

    @Test
    fun originalDateOutsideRangeCountsWhenCurrentDateIsInside() {
        val start = StatisticsRange.Days30.startInclusive(today)!!
        val movedIn = plan(
            id = 1L,
            date = start,
            sessionStatus = SessionStatus.COMPLETED,
            originalScheduledDate = start.minusDays(3)
        )
        val stats = stats(scheduled = listOf(movedIn))
        assertEquals(1, stats.adherence.plannedCount)
        assertEquals(1, stats.adherence.completedCount)
        assertEquals(100, stats.adherence.percent)
    }

    @Test
    fun deletedTemplateOccurrenceStillCounts() {
        val orphan = plan(
            id = 1L,
            date = today.minusDays(1),
            sessionStatus = SessionStatus.COMPLETED,
            templateId = null
        )
        val stats = stats(scheduled = listOf(orphan))
        assertEquals(1, stats.adherence.plannedCount)
        assertEquals(1, stats.adherence.completedCount)
        assertEquals(100, stats.adherence.percent)
    }

    @Test
    fun multipleOccurrencesOfTheSameTemplateAreIndependent() {
        val first = plan(1L, today.minusDays(2), SessionStatus.COMPLETED, templateId = 8L)
        val second = plan(2L, today.minusDays(1), null, templateId = 8L)
        val stats = stats(scheduled = listOf(first, second))
        assertEquals(2, stats.adherence.plannedCount)
        assertEquals(1, stats.adherence.completedCount)
        assertEquals(50, stats.adherence.percent)
    }

    @Test
    fun futureOccurrenceIsExcludedFromAllRanges() {
        val future = plan(1L, today.plusDays(1), null)
        assertEquals(0, stats(scheduled = listOf(future)).adherence.plannedCount)
        assertEquals(
            0,
            stats(scheduled = listOf(future), range = StatisticsRange.All).adherence.plannedCount
        )
        assertNull(stats(scheduled = listOf(future)).adherence.percent)
    }

    @Test
    fun allRangeIncludesOlderDueOccurrencesAndStillExcludesFuture() {
        val old = plan(1L, today.minusDays(40), SessionStatus.COMPLETED)
        val recent = plan(2L, today, null)
        val future = plan(3L, today.plusDays(2), null)
        val thirty = stats(scheduled = listOf(old, recent, future))
        assertEquals(1, thirty.adherence.plannedCount)
        val all = stats(scheduled = listOf(old, recent, future), range = StatisticsRange.All)
        assertEquals(2, all.adherence.plannedCount)
        assertEquals(1, all.adherence.completedCount)
        assertEquals(50, all.adherence.percent)
    }

    @Test
    fun adherenceBoundariesFollowEachStatisticsRange() {
        val start30 = StatisticsRange.Days30.startInclusive(today)!!
        val start3m = StatisticsRange.Months3.startInclusive(today)!!
        val start6m = StatisticsRange.Months6.startInclusive(today)!!
        val start1y = StatisticsRange.Year1.startInclusive(today)!!
        val at30 = plan(1L, start30, SessionStatus.COMPLETED)
        val before30 = plan(2L, start30.minusDays(1), SessionStatus.COMPLETED)
        assertEquals(1, stats(scheduled = listOf(at30, before30)).adherence.plannedCount)
        assertEquals(
            1,
            stats(scheduled = listOf(plan(1L, start3m, null)), range = StatisticsRange.Months3)
                .adherence.plannedCount
        )
        assertEquals(
            0,
            stats(
                scheduled = listOf(plan(1L, start3m.minusDays(1), null)),
                range = StatisticsRange.Months3
            ).adherence.plannedCount
        )
        assertEquals(
            1,
            stats(scheduled = listOf(plan(1L, start6m, null)), range = StatisticsRange.Months6)
                .adherence.plannedCount
        )
        assertEquals(
            1,
            stats(scheduled = listOf(plan(1L, start1y, null)), range = StatisticsRange.Year1)
                .adherence.plannedCount
        )
        assertEquals(
            0,
            stats(
                scheduled = listOf(plan(1L, start1y.minusDays(1), null)),
                range = StatisticsRange.Year1
            ).adherence.plannedCount
        )
    }

    @Test
    fun abandonedLinkedSessionCountsAsPlannedNotCompleted() {
        val stats = stats(scheduled = listOf(plan(1L, today.minusDays(1), SessionStatus.ABANDONED)))
        assertEquals(1, stats.adherence.plannedCount)
        assertEquals(0, stats.adherence.completedCount)
        assertEquals(1, stats.adherence.missedCount)
        assertEquals(0, stats.adherence.percent)
    }

    @Test
    fun restBetweenSessionsUsesDistinctDatesAndNeedsTwoObservations() {
        val sameDay = stats(
            listOf(
                workout(1L, today, listOf(repsItem(1L, "Curl", MuscleGroup.BICEPS, 8))),
                workout(2L, today, listOf(repsItem(2L, "Hammer curl", MuscleGroup.BICEPS, 8)))
            )
        )
        assertTrue(sameDay.restBetweenSessions.isEmpty())

        val spaced = stats(
            listOf(
                workout(1L, today.minusDays(6), listOf(repsItem(1L, "Curl", MuscleGroup.BICEPS, 8))),
                workout(2L, today.minusDays(4), listOf(repsItem(1L, "Curl", MuscleGroup.BICEPS, 8))),
                workout(3L, today, listOf(repsItem(1L, "Curl", MuscleGroup.BICEPS, 8)))
            )
        )
        val rest = spaced.restBetweenSessions.single()
        assertEquals(MuscleGroup.BICEPS, rest.muscle)
        assertEquals(3, rest.sessionDates)
        assertEquals(3.0, rest.averageDays, 0.0)
        assertEquals(2, rest.shortestDays)
        assertEquals(4, rest.longestDays)
        assertEquals(today, rest.lastTrained)
    }

    @Test
    fun restBetweenSessionsRespectsTheSelectedRange() {
        val workouts = listOf(
            workout(1L, today.minusDays(40), listOf(repsItem(1L, "Curl", MuscleGroup.BICEPS, 8))),
            workout(2L, today, listOf(repsItem(1L, "Curl", MuscleGroup.BICEPS, 8)))
        )
        assertTrue(stats(workouts).restBetweenSessions.isEmpty())
        val all = stats(workouts, range = StatisticsRange.All).restBetweenSessions.single()
        assertEquals(2, all.sessionDates)
        assertEquals(40, all.shortestDays)
        assertEquals(40, all.longestDays)
        assertEquals(40.0, all.averageDays, 0.0)
    }

    @Test
    fun exercisePerformanceRespectsRangeAndOneObservation() {
        val old = workout(
            1L,
            today.minusDays(40),
            listOf(
                item(
                    1L,
                    "Bench press",
                    MeasurementType.REPETITIONS_AND_WEIGHT,
                    MuscleGroup.CHEST,
                    listOf(set(1L, reps = 5, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 80.0)),
                    WeightInterpretation.TOTAL
                )
            )
        )
        val recent = workout(
            2L,
            today,
            listOf(
                item(
                    1L,
                    "Bench press",
                    MeasurementType.REPETITIONS_AND_WEIGHT,
                    MuscleGroup.CHEST,
                    listOf(set(2L, reps = 4, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 75.0)),
                    WeightInterpretation.TOTAL
                )
            )
        )
        val thirty = stats(listOf(old, recent)).exercises.single()
        assertFalse(thirty.showsBestSeparately)
        assertEquals(75.0, (thirty.best as ExerciseBest.WeightedSet).effectiveKg, 0.0)
        val all = stats(listOf(old, recent), range = StatisticsRange.All).exercises.single()
        assertTrue(all.showsBestSeparately)
        assertEquals(80.0, (all.best as ExerciseBest.WeightedSet).effectiveKg, 0.0)
        assertEquals(75.0, (all.recent as ExerciseBest.WeightedSet).effectiveKg, 0.0)
    }

    private fun stats(
        aggregates: List<WorkoutSessionAggregate> = emptyList(),
        scheduled: List<ScheduledWorkout> = emptyList(),
        range: StatisticsRange = StatisticsRange.Days30
    ): TrainingStatistics {
        return TrainingStatisticsLogic.assemble(aggregates, scheduled, today, range)
    }

    private fun plan(
        id: Long,
        date: LocalDate,
        sessionStatus: SessionStatus?,
        originalScheduledDate: LocalDate = date,
        cancelledAt: Long? = null,
        templateId: Long? = 1L
    ): ScheduledWorkout {
        return ScheduledWorkout(
            id = id,
            scheduledDate = date,
            templateId = templateId,
            templateName = "Plan $id",
            exerciseCount = 1,
            plannedSetCount = 1,
            templateArchived = templateId == null,
            sessionId = sessionStatus?.let { id * 100 },
            sessionStatus = sessionStatus,
            createdAt = 1L,
            originalScheduledDate = originalScheduledDate,
            cancelledAt = cancelledAt
        )
    }

    private fun weightedWorkout(id: Long, date: LocalDate, weightKg: Double): WorkoutSessionAggregate {
        return workout(
            id,
            date,
            listOf(
                item(
                    1L,
                    "Bench press",
                    MeasurementType.REPETITIONS_AND_WEIGHT,
                    MuscleGroup.CHEST,
                    listOf(set(id, reps = 1, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = weightKg)),
                    WeightInterpretation.TOTAL
                )
            )
        )
    }

    private fun workout(
        id: Long,
        date: LocalDate,
        items: List<SessionExerciseItem>,
        status: SessionStatus = SessionStatus.COMPLETED,
        startedAt: Long = 1L,
        finishedAt: Long? = if (status == SessionStatus.COMPLETED) 2L else null
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = id,
                templateId = 1L,
                templateName = "Session $id",
                status = status,
                workoutDate = date,
                startedAt = startedAt,
                finishedAt = finishedAt,
                abandonedAt = if (status == SessionStatus.ABANDONED) 2L else null,
                notes = null,
                bodyWeightKg = 80.0,
                bodyWeightSource = BodyWeightSource.MANUAL,
                bodyWeightSourceDate = date,
                createdAt = 1L,
                updatedAt = 1L
            ),
            exercises = items
        )
    }

    private fun repsItem(
        exerciseId: Long,
        name: String,
        primary: MuscleGroup,
        completedReps: Int
    ): SessionExerciseItem {
        return item(
            exerciseId = exerciseId,
            name = name,
            measurement = MeasurementType.REPETITIONS,
            primary = primary,
            sets = listOf(set(exerciseId, reps = completedReps, load = PlannedLoadKind.BODYWEIGHT_ONLY)),
            interpretation = WeightInterpretation.NOT_APPLICABLE
        )
    }

    private fun item(
        exerciseId: Long,
        name: String,
        measurement: MeasurementType,
        primary: MuscleGroup,
        sets: List<SessionSet>,
        interpretation: WeightInterpretation
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = exerciseId * 10,
                sessionId = 1L,
                exerciseId = exerciseId,
                position = 0,
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.OTHER,
                measurementType = measurement,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = interpretation,
                primaryMuscle = primary,
                secondaryMuscles = listOf(MuscleGroup.TRICEPS),
                notes = null
            ),
            sets = sets
        )
    }

    private fun set(
        id: Long,
        status: SessionSetStatus = SessionSetStatus.COMPLETED,
        reps: Int? = null,
        load: PlannedLoadKind? = null,
        weight: Double? = null,
        duration: Int? = null,
        distance: Double? = null
    ): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = 1L,
            position = id.toInt(),
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = load ?: PlannedLoadKind.NONE,
            plannedWeightKg = weight,
            plannedDurationSeconds = duration,
            plannedDistanceMeters = distance,
            actualReps = reps,
            actualLoadKind = load,
            actualWeightKg = weight,
            actualDurationSeconds = duration,
            actualDistanceMeters = distance,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) 1L else null,
            addedDuringWorkout = false
        )
    }
}
