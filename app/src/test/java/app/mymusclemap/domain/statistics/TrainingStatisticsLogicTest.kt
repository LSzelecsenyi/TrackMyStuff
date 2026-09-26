package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
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
import java.time.LocalDate

class TrainingStatisticsLogicTest {
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun emptyHistoryIsEmptyDashboard() {
        val stats = TrainingStatisticsLogic.assemble(emptyList(), today)
        assertFalse(stats.hasCompletedWorkouts)
        assertEquals(0, stats.completedWorkoutCount)
        assertNull(stats.weightedLoad)
        assertTrue(stats.weeklyVolume.isEmpty())
        assertTrue(stats.exerciseProgress.isEmpty())
        assertTrue(stats.recentlyTrainedMuscles.isEmpty())
    }

    @Test
    fun oneCompletedWorkoutCountsInWindows() {
        val stats = TrainingStatisticsLogic.assemble(
            listOf(workout(1L, today, listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, completedReps = 8)))),
            today
        )
        assertEquals(1, stats.completedWorkoutCount)
        assertEquals(1, stats.workoutsLast7Days)
        assertEquals(1, stats.trainingDaysLast7Days)
        assertEquals(1, stats.workoutsLast30Days)
        assertEquals(0.25, stats.recentWeeklyFrequency!!, 0.0)
        assertNull(stats.weightedLoad)
        assertEquals(MuscleGroup.LATS, stats.recentlyTrainedMuscles.single().muscle)
        assertEquals(1, stats.recentlyTrainedMuscles.single().completedSetCount)
    }

    @Test
    fun multipleWorkoutsAcrossDatesCountDistinctTrainingDays() {
        val stats = TrainingStatisticsLogic.assemble(
            listOf(
                workout(1L, today.minusDays(8), listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 5))),
                workout(2L, today.minusDays(2), listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 6))),
                workout(3L, today.minusDays(2), listOf(repsItem(2L, "Squat", MuscleGroup.QUADRICEPS, 8))),
                workout(4L, today, listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 7)))
            ),
            today
        )
        assertEquals(4, stats.completedWorkoutCount)
        assertEquals(3, stats.workoutsLast7Days)
        assertEquals(2, stats.trainingDaysLast7Days)
        assertEquals(4, stats.workoutsLast30Days)
        assertEquals(3, stats.trainingDaysLast30Days)
    }

    @Test
    fun abandonedInProgressAndFutureSessionsAreExcluded() {
        val stats = TrainingStatisticsLogic.assemble(
            listOf(
                workout(1L, today, listOf(repsItem(1L, "A", MuscleGroup.CHEST, 8)), SessionStatus.ABANDONED),
                workout(2L, today, listOf(repsItem(1L, "A", MuscleGroup.CHEST, 8)), SessionStatus.IN_PROGRESS),
                workout(3L, today.plusDays(1), listOf(repsItem(1L, "A", MuscleGroup.CHEST, 8))),
                workout(4L, today, listOf(repsItem(1L, "A", MuscleGroup.CHEST, 8)))
            ),
            today
        )
        assertEquals(1, stats.completedWorkoutCount)
        assertEquals(1, stats.workoutsLast7Days)
    }

    @Test
    fun skippedAndPendingSetsAreExcludedFromVolumeProgressAndMuscles() {
        val completed = set(1L, SessionSetStatus.COMPLETED, reps = 5, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 80.0)
        val skipped = set(2L, SessionSetStatus.SKIPPED, reps = 5, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 120.0)
        val pending = set(3L, SessionSetStatus.PENDING, reps = 5, load = PlannedLoadKind.EXTERNAL_WEIGHT, weight = 100.0)
        val stats = TrainingStatisticsLogic.assemble(
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
            ),
            today
        )
        assertEquals(1, stats.completedWorkoutCount)
        assertEquals(400.0, stats.weightedLoad!!.last7DaysKg, 0.0)
        assertEquals(1, stats.weightedLoad!!.completedSetCountLast7Days)
        val progress = stats.exerciseProgress.single()
        val best = progress.best as ExerciseBest.WeightedSet
        assertEquals(80.0, best.effectiveKg, 0.0)
        assertEquals(5, best.reps)
        assertEquals(1, stats.recentlyTrainedMuscles.single().completedSetCount)
    }

    @Test
    fun bodyweightAndAssistanceSetsAreNotKilogramVolume() {
        val stats = TrainingStatisticsLogic.assemble(
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
            ),
            today
        )
        assertNull(stats.weightedLoad)
        assertTrue(stats.weeklyVolume.isEmpty())
        val progress = stats.exerciseProgress.single()
        assertTrue(progress.best is ExerciseBest.Reps)
        assertEquals(8, (progress.best as ExerciseBest.Reps).reps)
    }

    @Test
    fun durationAndWeightIsNotKilogramVolume() {
        val stats = TrainingStatisticsLogic.assemble(
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
            ),
            today
        )
        assertNull(stats.weightedLoad)
        assertEquals(40, (stats.exerciseProgress.single().best as ExerciseBest.Duration).seconds)
    }

    @Test
    fun perSideExternalWeightIsDoubledForVolumeAndBest() {
        val stats = TrainingStatisticsLogic.assemble(
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
            ),
            today
        )
        assertEquals(400.0, stats.weightedLoad!!.last7DaysKg, 0.0)
        val best = stats.exerciseProgress.single().best as ExerciseBest.WeightedSet
        assertEquals(40.0, best.effectiveKg, 0.0)
        assertEquals(20.0, best.recordedKg, 0.0)
        assertTrue(best.perSide)
    }

    @Test
    fun addedWeightVolumeUsesRecordedLoadNotBodyMass() {
        val stats = TrainingStatisticsLogic.assemble(
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
            ),
            today
        )
        assertEquals(50.0, stats.weightedLoad!!.last7DaysKg, 0.0)
    }

    @Test
    fun weeklyVolumeUsesIsoWeeksAndOmitsWeeksOutsideTheTrendWindow() {
        val thisMonday = LocalDate.of(2026, 9, 14)
        val lastMonday = LocalDate.of(2026, 9, 7)
        val older = LocalDate.of(2026, 7, 1)
        val stats = TrainingStatisticsLogic.assemble(
            listOf(
                weightedWorkout(1L, older, 100.0),
                weightedWorkout(2L, lastMonday, 200.0),
                weightedWorkout(3L, thisMonday, 50.0)
            ),
            today
        )
        assertEquals(250.0, stats.weightedLoad!!.last30DaysKg, 0.0)
        assertEquals(50.0, stats.weightedLoad!!.last7DaysKg, 0.0)
        assertEquals(TrainingStatisticsLogic.VOLUME_TREND_WEEKS, stats.weeklyVolume.size)
        assertEquals(thisMonday, stats.weeklyVolume.last().weekStart)
        assertEquals(50.0, stats.weeklyVolume.last().volumeKg, 0.0)
        assertEquals(200.0, stats.weeklyVolume[stats.weeklyVolume.lastIndex - 1].volumeKg, 0.0)
        assertFalse(stats.weeklyVolume.any { it.weekStart == older.with(java.time.DayOfWeek.MONDAY) && it.volumeKg == 100.0 })
        assertEquals(0.0, stats.weeklyVolume.first().volumeKg, 0.0)
    }

    @Test
    fun bestWeightedSetPrefersHeavierLoadThenMoreReps() {
        val stats = TrainingStatisticsLogic.assemble(
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
            ),
            today
        )
        val progress = stats.exerciseProgress.single()
        val best = progress.best as ExerciseBest.WeightedSet
        val recent = progress.recent as ExerciseBest.WeightedSet
        assertEquals(120.0, best.effectiveKg, 0.0)
        assertEquals(5, best.reps)
        assertEquals(120.0, recent.effectiveKg, 0.0)
        assertEquals(5, recent.reps)
        assertTrue(progress.hasProgression)
        assertEquals(listOf(100.0, 120.0), progress.history.map { it.value })
    }

    @Test
    fun durationAndDistanceUseLongestCompletedSet() {
        val stats = TrainingStatisticsLogic.assemble(
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
            ),
            today
        )
        val plank = stats.exerciseProgress.first { it.name == "Plank" }
        val run = stats.exerciseProgress.first { it.name == "Run" }
        assertEquals(45, (plank.best as ExerciseBest.Duration).seconds)
        assertEquals(40, (plank.recent as ExerciseBest.Duration).seconds)
        assertEquals(2500.0, (run.best as ExerciseBest.Distance).meters, 0.0)
        assertEquals(700, (run.best as ExerciseBest.Distance).durationSeconds)
        assertEquals(2500.0, (run.recent as ExerciseBest.Distance).meters, 0.0)
    }

    @Test
    fun completionOnlyCountsTrainingDaysNotANumericBestLoad() {
        val stats = TrainingStatisticsLogic.assemble(
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
            ),
            today
        )
        val progress = stats.exerciseProgress.single()
        assertEquals(2, (progress.best as ExerciseBest.Completions).count)
        assertEquals(ExerciseHistoryKind.COMPLETIONS, progress.historyKind)
        assertEquals(2, progress.history.size)
    }

    @Test
    fun muscleSummaryUsesPrimaryMusclesOnlyAndWindows() {
        val stats = TrainingStatisticsLogic.assemble(
            listOf(
                workout(1L, today.minusDays(20), listOf(repsItem(1L, "Row", MuscleGroup.LATS, 10))),
                workout(2L, today, listOf(repsItem(2L, "Bench", MuscleGroup.CHEST, 8))),
                workout(3L, today, listOf(repsItem(3L, "Press", MuscleGroup.CHEST, 6)))
            ),
            today
        )
        assertEquals(listOf(MuscleGroup.CHEST), stats.recentlyTrainedMuscles.map { it.muscle })
        assertEquals(2, stats.recentlyTrainedMuscles.single().completedSetCount)
        assertEquals(2, stats.recentlyTrainedMuscles.single().workoutCount)
        assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.LATS), stats.mostTrainedMuscles.map { it.muscle })
    }

    @Test
    fun workoutsOlderThanThirtyDaysStillCountAllTimeButNotRecentWindows() {
        val stats = TrainingStatisticsLogic.assemble(
            listOf(workout(1L, today.minusDays(40), listOf(repsItem(1L, "Pull-up", MuscleGroup.LATS, 8)))),
            today
        )
        assertEquals(1, stats.completedWorkoutCount)
        assertEquals(0, stats.workoutsLast7Days)
        assertEquals(0, stats.workoutsLast30Days)
        assertEquals(0.0, stats.recentWeeklyFrequency!!, 0.0)
        assertTrue(stats.recentlyTrainedMuscles.isEmpty())
        assertTrue(stats.mostTrainedMuscles.isEmpty())
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
        status: SessionStatus = SessionStatus.COMPLETED
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = id,
                templateId = 1L,
                templateName = "Session $id",
                status = status,
                workoutDate = date,
                startedAt = 1L,
                finishedAt = if (status == SessionStatus.COMPLETED) 2L else null,
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
