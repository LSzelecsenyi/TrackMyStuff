package app.mymusclemap.domain.reports

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.statistics.VolumeTrendResolution
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ReportLogicTest {
    private val today = LocalDate.of(2026, 9, 26)
    private val history = LocalDate.of(2020, 1, 1)

    @Test
    fun emptyClosedPeriodKeepsTheAxisAndHasNoAdherencePercent() {
        val summary = report(LocalDate.of(2026, 8, 1))
        assertEquals(0, summary.activity.workoutCount)
        assertEquals(0, summary.activity.trainingDayCount)
        assertEquals(0, summary.activity.completedSetCount)
        assertNull(summary.activity.durationMillis)
        assertEquals(0, summary.adherence.plannedCount)
        assertNull(summary.adherence.percent)
        assertNull(summary.volume.totalKg)
        assertEquals(31, summary.volume.trend.size)
        assertTrue(summary.volume.trend.all { it.value == 0.0 })
        assertEquals(VolumeTrendResolution.Daily, summary.volume.resolution)
        assertTrue(summary.muscles.isEmpty())
        assertTrue(summary.exerciseHighlights.isEmpty())
        assertNull(summary.bodyWeight)
        assertEquals(ReportHistoryCoverage.Complete, summary.coverage)
    }

    @Test
    fun oneCompletedWorkoutCountsActivityDurationSetsAndVolume() {
        val summary = report(
            LocalDate.of(2026, 6, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 6, 3),
                    startedAt = 0L,
                    finishedAt = 1_000L,
                    items = listOf(
                        exercise(
                            exerciseId = 1L,
                            name = "Bench Press",
                            muscle = MuscleGroup.CHEST,
                            sets = listOf(
                                weightedSet(1L, reps = 5, weight = 20.0),
                                weightedSet(2L, reps = 5, weight = 10.0, load = PlannedLoadKind.ADDED_WEIGHT),
                                weightedSet(3L, reps = 8, weight = 80.0, load = PlannedLoadKind.BODYWEIGHT_ONLY)
                            )
                        )
                    )
                )
            ),
            scheduled = listOf(plan(1L, LocalDate.of(2026, 6, 3), sessionStatus = SessionStatus.COMPLETED))
        )
        assertEquals(1, summary.activity.workoutCount)
        assertEquals(1, summary.activity.trainingDayCount)
        assertEquals(3, summary.activity.completedSetCount)
        assertEquals(1_000L, summary.activity.durationMillis)
        assertEquals(1, summary.adherence.plannedCount)
        assertEquals(1, summary.adherence.completedCount)
        assertEquals(100, summary.adherence.percent)
        assertEquals(150.0, summary.volume.totalKg!!, 0.0)
        assertEquals(2, summary.volume.completedSetCount)
        assertEquals(MuscleGroup.CHEST, summary.topMuscles().single().muscle)
        assertEquals(3, summary.topMuscles().single().completedSetCount)
    }

    @Test
    fun perSideLoadIsDoubledAndSubSecondDurationIsOmitted() {
        val summary = report(
            LocalDate.of(2026, 6, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 6, 2),
                    startedAt = 0L,
                    finishedAt = 999L,
                    items = listOf(
                        exercise(
                            exerciseId = 1L,
                            name = "Dumbbell press",
                            muscle = MuscleGroup.CHEST,
                            interpretation = WeightInterpretation.PER_SIDE,
                            sets = listOf(weightedSet(1L, reps = 4, weight = 12.5))
                        )
                    )
                )
            )
        )
        assertNull(summary.activity.durationMillis)
        assertEquals(100.0, summary.volume.totalKg!!, 0.0)
    }

    @Test
    fun lateCompletionCountsInTheScheduledMonthAndTheCompletionMonthSeparately() {
        val junePlan = plan(
            id = 1L,
            date = LocalDate.of(2026, 6, 30),
            sessionId = 7L,
            sessionStatus = SessionStatus.COMPLETED
        )
        val julyWorkout = workout(
            id = 7L,
            date = LocalDate.of(2026, 7, 2),
            scheduledWorkoutId = 1L,
            items = listOf(
                exercise(
                    exerciseId = 1L,
                    name = "Squat",
                    muscle = MuscleGroup.QUADRICEPS,
                    sets = listOf(weightedSet(1L, reps = 5, weight = 100.0))
                )
            )
        )
        val inputs = ReportInputs(sessions = listOf(julyWorkout), scheduled = listOf(junePlan))
        val june = ReportLogic.summarize(
            ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 6, 1)),
            inputs,
            today,
            history
        )
        val july = ReportLogic.summarize(
            ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 7, 1)),
            inputs,
            today,
            history
        )
        assertEquals(1, june.adherence.plannedCount)
        assertEquals(1, june.adherence.completedCount)
        assertEquals(0, june.activity.workoutCount)
        assertNull(june.volume.totalKg)
        assertEquals(0, july.adherence.plannedCount)
        assertEquals(1, july.activity.workoutCount)
        assertEquals(500.0, july.volume.totalKg!!, 0.0)
        assertEquals(MuscleGroup.QUADRICEPS, july.muscles.single().muscle)
    }

    @Test
    fun missedCancelledRescheduledNullTemplateAndUnplannedFollowAdherenceRules() {
        val sessions = listOf(
            workout(
                id = 2L,
                date = LocalDate.of(2026, 6, 12),
                scheduledWorkoutId = 2L,
                items = listOf(bench(2L, LocalDate.of(2026, 6, 12), 40.0))
            ),
            workout(
                id = 4L,
                date = LocalDate.of(2026, 7, 2),
                scheduledWorkoutId = 4L,
                items = listOf(bench(4L, LocalDate.of(2026, 7, 2), 50.0))
            ),
            workout(
                id = 5L,
                date = LocalDate.of(2026, 6, 18),
                scheduledWorkoutId = null,
                items = listOf(bench(5L, LocalDate.of(2026, 6, 18), 30.0))
            )
        )
        val scheduled = listOf(
            plan(1L, LocalDate.of(2026, 6, 10)),
            plan(2L, LocalDate.of(2026, 6, 12), templateId = null, sessionId = 2L, sessionStatus = SessionStatus.COMPLETED),
            plan(3L, LocalDate.of(2026, 6, 15), cancelledAt = 9L),
            plan(
                id = 4L,
                date = LocalDate.of(2026, 7, 2),
                original = LocalDate.of(2026, 6, 28),
                sessionId = 4L,
                sessionStatus = SessionStatus.COMPLETED
            ),
            plan(6L, LocalDate.of(2026, 6, 20), sessionStatus = SessionStatus.IN_PROGRESS),
            plan(7L, LocalDate.of(2026, 6, 21), sessionStatus = SessionStatus.ABANDONED)
        )
        val june = report(LocalDate.of(2026, 6, 1), sessions = sessions, scheduled = scheduled)
        assertEquals(4, june.adherence.plannedCount)
        assertEquals(1, june.adherence.completedCount)
        assertEquals(2, june.activity.workoutCount)
        assertEquals(70.0, june.volume.totalKg!!, 0.0)

        val july = report(LocalDate.of(2026, 7, 1), sessions = sessions, scheduled = scheduled)
        assertEquals(1, july.adherence.plannedCount)
        assertEquals(1, july.adherence.completedCount)
        assertEquals(1, july.activity.workoutCount)
    }

    @Test
    fun weeklyBucketsKeepAnEmptyWeekInsideAQuarter() {
        val summary = report(
            LocalDate.of(2026, 4, 1),
            kind = ReportKind.Quarterly,
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 4, 1),
                    items = listOf(bench(1L, LocalDate.of(2026, 4, 1), 10.0))
                ),
                workout(
                    id = 2L,
                    date = LocalDate.of(2026, 4, 20),
                    items = listOf(bench(2L, LocalDate.of(2026, 4, 20), 20.0))
                )
            )
        )
        assertEquals(VolumeTrendResolution.Weekly, summary.volume.resolution)
        assertTrue(summary.volume.trend.all { it.date.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(LocalDate.of(2026, 3, 30), summary.volume.trend.first().date)
        assertEquals(LocalDate.of(2026, 6, 29), summary.volume.trend.last().date)
        assertEquals(10.0, summary.volume.trend.single { it.date == LocalDate.of(2026, 3, 30) }.value, 0.0)
        assertEquals(0.0, summary.volume.trend.single { it.date == LocalDate.of(2026, 4, 6) }.value, 0.0)
        assertEquals(0.0, summary.volume.trend.single { it.date == LocalDate.of(2026, 4, 13) }.value, 0.0)
        assertEquals(20.0, summary.volume.trend.single { it.date == LocalDate.of(2026, 4, 20) }.value, 0.0)
        assertEquals(30.0, summary.volume.trend.sumOf { it.value }, 0.001)
        assertTrue(summary.volume.trend.all { it.value >= 0.0 })
        assertEquals(summary.volume.totalKg!!, summary.volume.trend.sumOf { it.value }, 0.001)
    }

    @Test
    fun yearlyTrendUsesCalendarMonthsIncludingAZeroMonth() {
        val summary = report(
            LocalDate.of(2025, 6, 1),
            kind = ReportKind.Yearly,
            today = LocalDate.of(2026, 1, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2025, 1, 15),
                    items = listOf(bench(1L, LocalDate.of(2025, 1, 15), 40.0))
                )
            )
        )
        assertEquals(VolumeTrendResolution.Monthly, summary.volume.resolution)
        assertEquals(12, summary.volume.trend.size)
        assertEquals(LocalDate.of(2025, 1, 1), summary.volume.trend.first().date)
        assertEquals(LocalDate.of(2025, 12, 1), summary.volume.trend.last().date)
        assertEquals(40.0, summary.volume.trend.first().value, 0.0)
        assertEquals(0.0, summary.volume.trend.single { it.date == LocalDate.of(2025, 2, 1) }.value, 0.0)
    }

    @Test
    fun musclesRankByCompletedSetsThenRecency() {
        val summary = report(
            LocalDate.of(2026, 8, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 8, 1),
                    items = listOf(
                        exercise(1L, "Squat", MuscleGroup.QUADRICEPS, listOf(weightedSet(1L, reps = 5, weight = 20.0))),
                        exercise(
                            2L,
                            "Row",
                            MuscleGroup.LATS,
                            listOf(
                                weightedSet(2L, reps = 5, weight = 20.0),
                                weightedSet(3L, reps = 5, weight = 20.0)
                            )
                        )
                    )
                ),
                workout(
                    id = 2L,
                    date = LocalDate.of(2026, 8, 20),
                    items = listOf(
                        exercise(3L, "Press", MuscleGroup.FRONT_DELTOID, listOf(weightedSet(4L, reps = 5, weight = 20.0)))
                    )
                ),
                workout(
                    id = 4L,
                    date = LocalDate.of(2026, 8, 3),
                    items = listOf(
                        exercise(4L, "Curl", MuscleGroup.BICEPS, listOf(weightedSet(5L, reps = 8, weight = 10.0)))
                    )
                ),
                workout(
                    id = 3L,
                    date = LocalDate.of(2026, 8, 10),
                    items = listOf(
                        exercise(5L, "Bench", MuscleGroup.CHEST, listOf(weightedSet(6L, reps = 5, weight = 20.0)))
                    )
                )
            )
        )
        assertEquals(
            listOf(MuscleGroup.LATS, MuscleGroup.FRONT_DELTOID, MuscleGroup.CHEST),
            summary.topMuscles().map { it.muscle }
        )
        assertEquals(5, summary.muscles.size)
    }

    @Test
    fun exerciseHighlightsUseFirstAndLastDayAndKeepRegressions() {
        val summary = report(
            LocalDate.of(2026, 8, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 8, 2),
                    items = listOf(
                        exercise(1L, "Bench Press", MuscleGroup.CHEST, listOf(weightedSet(1L, reps = 5, weight = 60.0))),
                        exercise(2L, "Squat", MuscleGroup.QUADRICEPS, listOf(weightedSet(2L, reps = 5, weight = 100.0)))
                    )
                ),
                workout(
                    id = 2L,
                    date = LocalDate.of(2026, 8, 20),
                    items = listOf(
                        exercise(1L, "Bench Press", MuscleGroup.CHEST, listOf(weightedSet(3L, reps = 3, weight = 70.0))),
                        exercise(2L, "Squat", MuscleGroup.QUADRICEPS, listOf(weightedSet(4L, reps = 5, weight = 90.0)))
                    )
                )
            )
        )
        val bench = summary.exerciseHighlights.single { it.name == "Bench Press" }
        val squat = summary.exerciseHighlights.single { it.name == "Squat" }
        assertEquals(ReportPerformanceKind.EffectiveKg, bench.kind)
        assertEquals(10.0, bench.delta, 0.0)
        assertEquals(-10.0, squat.delta, 0.0)
        assertEquals(bench, summary.exerciseHighlights.first())
    }

    @Test
    fun incompatibleMeasurementChangesAndMixedLoadEndpointsAreSkipped() {
        val changedType = report(
            LocalDate.of(2026, 8, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 8, 2),
                    items = listOf(
                        exercise(
                            1L,
                            "Pull-up",
                            MuscleGroup.LATS,
                            listOf(repsSet(1L, 8)),
                            measurement = MeasurementType.REPETITIONS
                        )
                    )
                ),
                workout(
                    id = 2L,
                    date = LocalDate.of(2026, 8, 16),
                    items = listOf(
                        exercise(
                            1L,
                            "Pull-up",
                            MuscleGroup.LATS,
                            listOf(weightedSet(2L, reps = 5, weight = 10.0))
                        )
                    )
                )
            )
        )
        assertTrue(changedType.exerciseHighlights.isEmpty())

        val mixed = report(
            LocalDate.of(2026, 8, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 8, 2),
                    items = listOf(bench(1L, LocalDate.of(2026, 8, 2), 40.0))
                ),
                workout(
                    id = 2L,
                    date = LocalDate.of(2026, 8, 16),
                    items = listOf(
                        exercise(
                            1L,
                            "Bench Press",
                            MuscleGroup.CHEST,
                            listOf(weightedSet(2L, reps = 8, weight = 0.0, load = PlannedLoadKind.BODYWEIGHT_ONLY))
                        )
                    )
                )
            )
        )
        assertTrue(mixed.exerciseHighlights.isEmpty())
    }

    @Test
    fun durationAndDistanceUseTheBestCompletedValueEachDay() {
        val summary = report(
            LocalDate.of(2026, 8, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 8, 2),
                    items = listOf(
                        exercise(
                            1L,
                            "Plank",
                            MuscleGroup.ABS,
                            listOf(timedSet(1L, 30), timedSet(2L, 45)),
                            measurement = MeasurementType.DURATION
                        ),
                        exercise(
                            2L,
                            "Run",
                            MuscleGroup.CARDIOVASCULAR,
                            listOf(distanceSet(3L, 1000.0)),
                            measurement = MeasurementType.DISTANCE_AND_DURATION
                        )
                    )
                ),
                workout(
                    id = 2L,
                    date = LocalDate.of(2026, 8, 18),
                    items = listOf(
                        exercise(
                            1L,
                            "Plank",
                            MuscleGroup.ABS,
                            listOf(timedSet(4L, 60)),
                            measurement = MeasurementType.DURATION
                        ),
                        exercise(
                            2L,
                            "Run",
                            MuscleGroup.CARDIOVASCULAR,
                            listOf(distanceSet(5L, 800.0)),
                            measurement = MeasurementType.DISTANCE_AND_DURATION
                        )
                    )
                )
            )
        )
        val plank = summary.exerciseHighlights.single { it.name == "Plank" }
        val run = summary.exerciseHighlights.single { it.name == "Run" }
        assertEquals(45.0, plank.baseline, 0.0)
        assertEquals(60.0, plank.latest, 0.0)
        assertEquals(ReportPerformanceKind.DurationSeconds, plank.kind)
        assertEquals(1000.0, run.baseline, 0.0)
        assertEquals(800.0, run.latest, 0.0)
        assertEquals(ReportPerformanceKind.DistanceMeters, run.kind)
    }

    @Test
    fun completionOnlyExerciseHasNoHighlight() {
        val summary = report(
            LocalDate.of(2026, 8, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2026, 8, 2),
                    items = listOf(
                        exercise(
                            1L,
                            "Stretch",
                            MuscleGroup.CHEST,
                            listOf(repsSet(1L, reps = null, status = SessionSetStatus.COMPLETED)),
                            measurement = MeasurementType.COMPLETION_ONLY
                        )
                    )
                ),
                workout(
                    id = 2L,
                    date = LocalDate.of(2026, 8, 9),
                    items = listOf(
                        exercise(
                            1L,
                            "Stretch",
                            MuscleGroup.CHEST,
                            listOf(repsSet(2L, reps = null, status = SessionSetStatus.COMPLETED)),
                            measurement = MeasurementType.COMPLETION_ONLY
                        )
                    )
                )
            )
        )
        assertEquals(2, summary.activity.workoutCount)
        assertTrue(summary.exerciseHighlights.isEmpty())
    }

    @Test
    fun atMostThreeHighlightsPreferLoadChanges() {
        val first = LocalDate.of(2026, 8, 2)
        val last = LocalDate.of(2026, 8, 20)
        val summary = report(
            LocalDate.of(2026, 8, 1),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = first,
                    items = listOf(
                        exercise(1L, "A", MuscleGroup.CHEST, listOf(weightedSet(1L, reps = 5, weight = 10.0))),
                        exercise(2L, "B", MuscleGroup.CHEST, listOf(weightedSet(2L, reps = 5, weight = 10.0))),
                        exercise(3L, "C", MuscleGroup.CHEST, listOf(weightedSet(3L, reps = 5, weight = 10.0))),
                        exercise(4L, "D", MuscleGroup.CHEST, listOf(weightedSet(4L, reps = 5, weight = 10.0))),
                        exercise(
                            5L,
                            "Pull-up",
                            MuscleGroup.LATS,
                            listOf(repsSet(5L, 5)),
                            measurement = MeasurementType.REPETITIONS
                        )
                    )
                ),
                workout(
                    id = 2L,
                    date = last,
                    items = listOf(
                        exercise(1L, "A", MuscleGroup.CHEST, listOf(weightedSet(6L, reps = 5, weight = 11.0))),
                        exercise(2L, "B", MuscleGroup.CHEST, listOf(weightedSet(7L, reps = 5, weight = 12.0))),
                        exercise(3L, "C", MuscleGroup.CHEST, listOf(weightedSet(8L, reps = 5, weight = 20.0))),
                        exercise(4L, "D", MuscleGroup.CHEST, listOf(weightedSet(9L, reps = 5, weight = 2.0))),
                        exercise(
                            5L,
                            "Pull-up",
                            MuscleGroup.LATS,
                            listOf(repsSet(10L, 12)),
                            measurement = MeasurementType.REPETITIONS
                        )
                    )
                )
            )
        )
        assertEquals(3, summary.exerciseHighlights.size)
        assertTrue(summary.exerciseHighlights.all { it.kind == ReportPerformanceKind.EffectiveKg })
        assertEquals(listOf("C", "D", "B"), summary.exerciseHighlights.map { it.name })
    }

    @Test
    fun bodyWeightUsesOnlyInPeriodMeasurements() {
        val outside = listOf(
            weight(1L, LocalDate.of(2026, 7, 31), 81.0),
            weight(4L, LocalDate.of(2026, 9, 1), 79.0)
        )
        assertNull(report(LocalDate.of(2026, 8, 1), weights = outside).bodyWeight)

        val one = report(
            LocalDate.of(2026, 8, 1),
            weights = outside + weight(2L, LocalDate.of(2026, 8, 10), 80.5)
        ).bodyWeight!!
        assertEquals(LocalDate.of(2026, 8, 10), one.firstDate)
        assertEquals(80.5, one.firstKg, 0.0)
        assertNull(one.lastKg)
        assertNull(one.changeKg)
        assertNull(one.averageKg)

        val many = report(
            LocalDate.of(2026, 8, 1),
            weights = outside + listOf(
                weight(2L, LocalDate.of(2026, 8, 2), 80.0),
                weight(3L, LocalDate.of(2026, 8, 20), 78.0)
            )
        ).bodyWeight!!
        assertEquals(80.0, many.firstKg, 0.0)
        assertEquals(78.0, many.lastKg!!, 0.0)
        assertEquals(-2.0, many.changeKg!!, 0.0)
        assertEquals(79.0, many.averageKg!!, 0.0)
    }

    @Test
    fun previousZeroBaselineOmitsPercentages() {
        val summary = report(
            LocalDate.of(2026, 6, 1),
            historyStart = LocalDate.of(2026, 5, 1),
            sessions = listOf(
                workout(
                    id = 2L,
                    date = LocalDate.of(2026, 6, 4),
                    items = listOf(bench(2L, LocalDate.of(2026, 6, 4), 50.0))
                )
            ),
            scheduled = listOf(
                plan(1L, LocalDate.of(2026, 5, 4)),
                plan(2L, LocalDate.of(2026, 6, 4), sessionId = 2L, sessionStatus = SessionStatus.COMPLETED)
            )
        )
        val previous = summary.previous!!
        assertEquals(LocalDate.of(2026, 5, 1), previous.period.startInclusive)
        assertEquals(1, previous.workoutCountDelta)
        assertNull(previous.workoutCountPercent)
        assertEquals(50.0, previous.volumeDeltaKg, 0.0)
        assertNull(previous.volumePercent)
        assertEquals(100, previous.adherencePointDelta)
    }

    @Test
    fun partialHistoryKeepsTheCalendarPeriodAndSkipsTheUnobservedPreviousPeriod() {
        val summary = report(
            LocalDate.of(2025, 3, 1),
            today = LocalDate.of(2025, 4, 1),
            historyStart = LocalDate.of(2025, 3, 26),
            sessions = listOf(
                workout(
                    id = 1L,
                    date = LocalDate.of(2025, 3, 28),
                    items = listOf(bench(1L, LocalDate.of(2025, 3, 28), 40.0))
                )
            )
        )
        assertEquals(LocalDate.of(2025, 3, 1), summary.period.startInclusive)
        assertEquals(LocalDate.of(2025, 3, 31), summary.period.endInclusive)
        assertEquals(ReportHistoryCoverage.Partial, summary.coverage)
        assertEquals(1, summary.activity.workoutCount)
        assertEquals(0.0, summary.volume.trend.single { it.date == LocalDate.of(2025, 3, 1) }.value, 0.0)
        assertEquals(40.0, summary.volume.trend.single { it.date == LocalDate.of(2025, 3, 28) }.value, 0.0)
        assertNull(summary.previous)
    }

    @Test
    fun noHistoryStillComputesTheClosedPeriodWithoutAComparison() {
        val summary = report(LocalDate.of(2026, 8, 1), historyStart = null)
        assertNull(summary.coverage)
        assertNull(summary.previous)
        assertEquals(0, summary.activity.workoutCount)
        assertEquals(31, summary.volume.trend.size)
    }

    @Test
    fun anOpenPeriodIsRejected() {
        try {
            report(LocalDate.of(2026, 9, 1))
            fail("September 2026 is still open on $today")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("closed"))
        }
    }

    private fun report(
        dateInPeriod: LocalDate,
        kind: ReportKind = ReportKind.Monthly,
        sessions: List<WorkoutSessionAggregate> = emptyList(),
        scheduled: List<ScheduledWorkout> = emptyList(),
        weights: List<WeightMeasurement> = emptyList(),
        today: LocalDate = this.today,
        historyStart: LocalDate? = history
    ): ReportSummary {
        return ReportLogic.summarize(
            ReportPeriod.containing(kind, dateInPeriod),
            ReportInputs(sessions, scheduled, weights),
            today,
            historyStart
        )
    }

    private fun bench(id: Long, date: LocalDate, weight: Double): SessionExerciseItem {
        return exercise(1L, "Bench Press", MuscleGroup.CHEST, listOf(weightedSet(id, reps = 1, weight = weight)))
    }

    private fun workout(
        id: Long,
        date: LocalDate,
        items: List<SessionExerciseItem>,
        status: SessionStatus = SessionStatus.COMPLETED,
        startedAt: Long = 0L,
        finishedAt: Long? = 60_000L,
        scheduledWorkoutId: Long? = id
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = id,
                templateId = 1L,
                templateName = "Session",
                status = status,
                workoutDate = date,
                startedAt = startedAt,
                finishedAt = if (status == SessionStatus.COMPLETED) finishedAt else null,
                abandonedAt = if (status == SessionStatus.ABANDONED) 2L else null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 1L,
                scheduledWorkoutId = scheduledWorkoutId
            ),
            exercises = items
        )
    }

    private fun exercise(
        exerciseId: Long,
        name: String,
        muscle: MuscleGroup,
        sets: List<SessionSet>,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = exerciseId * 10 + sets.first().id,
                sessionId = 1L,
                exerciseId = exerciseId,
                position = 0,
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.OTHER,
                measurementType = measurement,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = interpretation,
                primaryMuscle = muscle,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = sets
        )
    }

    private fun weightedSet(
        id: Long,
        reps: Int,
        weight: Double,
        load: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        status: SessionSetStatus = SessionSetStatus.COMPLETED
    ): SessionSet {
        return repsSet(id, reps, status, load, weight)
    }

    private fun repsSet(
        id: Long,
        reps: Int?,
        status: SessionSetStatus = SessionSetStatus.COMPLETED,
        load: PlannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
        weight: Double? = null
    ): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = 1L,
            position = id.toInt(),
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = load,
            plannedWeightKg = weight,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = reps,
            actualLoadKind = load,
            actualWeightKg = weight,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) 1L else null,
            addedDuringWorkout = false
        )
    }

    private fun timedSet(id: Long, seconds: Int): SessionSet {
        return repsSet(id, reps = null).copy(actualDurationSeconds = seconds)
    }

    private fun distanceSet(id: Long, meters: Double): SessionSet {
        return repsSet(id, reps = null).copy(actualDistanceMeters = meters, actualDurationSeconds = 600)
    }

    private fun plan(
        id: Long,
        date: LocalDate,
        templateId: Long? = 1L,
        sessionId: Long? = null,
        sessionStatus: SessionStatus? = null,
        original: LocalDate = date,
        cancelledAt: Long? = null
    ): ScheduledWorkout {
        return ScheduledWorkout(
            id = id,
            scheduledDate = date,
            templateId = templateId,
            templateName = "Plan",
            exerciseCount = 1,
            plannedSetCount = 1,
            templateArchived = false,
            sessionId = sessionId,
            sessionStatus = sessionStatus,
            createdAt = 1L,
            originalScheduledDate = original,
            cancelledAt = cancelledAt
        )
    }

    private fun weight(id: Long, date: LocalDate, kg: Double): WeightMeasurement {
        return WeightMeasurement(id = id, date = date, weightKg = kg, createdAt = 1L, updatedAt = 1L)
    }
}
