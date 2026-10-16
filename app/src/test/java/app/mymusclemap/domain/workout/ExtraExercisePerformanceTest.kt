package app.mymusclemap.domain.workout

import app.mymusclemap.domain.achievements.ExerciseMasteryEvaluator
import app.mymusclemap.domain.achievements.PerformanceRecordEvaluator
import app.mymusclemap.domain.achievements.PerformanceRecordKind
import app.mymusclemap.domain.achievements.SecretAchievementEvaluator
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.statistics.WorkoutSetVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExtraExercisePerformanceTest {
    @Test
    fun completedExtraExerciseCountsAsRealPerformance() {
        val planned = item(
            sessionExerciseId = 1L,
            exerciseId = 10L,
            position = 0,
            addedDuringWorkout = false,
            sets = listOf(performed(1L, 1L, reps = 5, weight = 40.0, addedDuringWorkout = false, completedAt = 100L))
        )
        val extra = item(
            sessionExerciseId = 2L,
            exerciseId = 11L,
            position = 1,
            addedDuringWorkout = true,
            sets = listOf(
                performed(2L, 2L, reps = 5, weight = 20.0, addedDuringWorkout = true, completedAt = 200L),
                performed(
                    3L,
                    2L,
                    reps = 5,
                    weight = 20.0,
                    addedDuringWorkout = true,
                    completedAt = null,
                    status = SessionSetStatus.SKIPPED
                )
            )
        )
        val workout = aggregate("extra", listOf(planned, extra), templateId = 7L)
        val summary = WorkoutCompletionLogic.from(workout)!!
        assertEquals(2, summary.exerciseCount)
        assertEquals(2, summary.completedSetCount)
        assertEquals(100.0, WorkoutSetVolume.volumeKg(extra.exercise, extra.sets.first())!!, 0.0)
        assertEquals(
            200.0,
            WorkoutSetVolume.volumeKg(
                extra.exercise.copy(weightInterpretation = WeightInterpretation.PER_SIDE),
                extra.sets.first()
            )!!,
            0.0
        )
        assertEquals(null, WorkoutSetVolume.volumeKg(extra.exercise, extra.sets.last()))
        assertEquals(1, ExerciseMasteryEvaluator.leadingCount(listOf(workout.copy(
            exercises = listOf(extra)
        ))))
        assertFalse(SecretAchievementEvaluator.oneMoreThing(workout))
    }

    @Test
    fun extraExerciseCanSetAPerformanceRecordWithoutChangingOneMoreThing() {
        val baseline = aggregate(
            "baseline",
            listOf(
                item(
                    sessionExerciseId = 1L,
                    exerciseId = 10L,
                    position = 0,
                    addedDuringWorkout = false,
                    sets = listOf(performed(1L, 1L, reps = 5, weight = 40.0, addedDuringWorkout = false, completedAt = 50L))
                )
            ),
            finishedAt = 100L
        )
        val heavierExtra = aggregate(
            "heavier",
            listOf(
                item(
                    sessionExerciseId = 2L,
                    exerciseId = 10L,
                    position = 0,
                    addedDuringWorkout = true,
                    sets = listOf(performed(2L, 2L, reps = 5, weight = 50.0, addedDuringWorkout = true, completedAt = 150L))
                )
            ),
            finishedAt = 200L,
            templateId = 4L
        )
        val events = PerformanceRecordEvaluator.events(listOf(baseline, heavierExtra))
        assertTrue(events.any { it.kind == PerformanceRecordKind.WEIGHT && it.clientWorkoutId == "heavier" })
        assertFalse(SecretAchievementEvaluator.oneMoreThing(heavierExtra))

        val prescribedAndExtraSet = aggregate(
            "one-more",
            listOf(
                item(
                    sessionExerciseId = 3L,
                    exerciseId = 10L,
                    position = 0,
                    addedDuringWorkout = false,
                    sets = listOf(
                        performed(3L, 3L, reps = 5, weight = 40.0, addedDuringWorkout = false, completedAt = 100L),
                        performed(4L, 3L, reps = 5, weight = 40.0, addedDuringWorkout = true, completedAt = 300L)
                    )
                )
            ),
            finishedAt = 400L,
            templateId = 4L
        )
        assertTrue(SecretAchievementEvaluator.oneMoreThing(prescribedAndExtraSet))
    }

    private fun aggregate(
        clientWorkoutId: String,
        exercises: List<SessionExerciseItem>,
        finishedAt: Long = 500L,
        templateId: Long? = 1L
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = clientWorkoutId.hashCode().toLong(),
                templateId = templateId,
                templateName = "Plan",
                status = SessionStatus.COMPLETED,
                workoutDate = LocalDate.of(2026, 10, 16),
                startedAt = finishedAt - 30L,
                finishedAt = finishedAt,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = finishedAt,
                clientWorkoutId = clientWorkoutId
            ),
            exercises = exercises
        )
    }

    private fun item(
        sessionExerciseId: Long,
        exerciseId: Long,
        position: Int,
        addedDuringWorkout: Boolean,
        sets: List<SessionSet>
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = sessionExerciseId,
                sessionId = 1L,
                exerciseId = exerciseId,
                position = position,
                name = "Bench",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = WeightInterpretation.TOTAL,
                primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = emptyList(),
                notes = null,
                addedDuringWorkout = addedDuringWorkout
            ),
            sets = sets
        )
    }

    private fun performed(
        id: Long,
        sessionExerciseId: Long,
        reps: Int,
        weight: Double,
        addedDuringWorkout: Boolean,
        completedAt: Long?,
        status: SessionSetStatus = SessionSetStatus.COMPLETED
    ): SessionSet {
        val completed = status == SessionSetStatus.COMPLETED
        return SessionSet(
            id = id,
            sessionExerciseId = sessionExerciseId,
            position = id.toInt(),
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            plannedWeightKg = weight,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = if (completed) reps else null,
            actualLoadKind = if (completed) PlannedLoadKind.EXTERNAL_WEIGHT else null,
            actualWeightKg = if (completed) weight else null,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = status,
            completedAt = completedAt,
            addedDuringWorkout = addedDuringWorkout
        )
    }
}
