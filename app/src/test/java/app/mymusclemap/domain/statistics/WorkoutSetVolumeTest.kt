package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSetVolumeTest {
    @Test
    fun externalTotalVolumeIsWeightTimesReps() {
        val volume = WorkoutSetVolume.volumeKg(
            exercise(WeightInterpretation.TOTAL),
            set(PlannedLoadKind.EXTERNAL_WEIGHT, 80.0, 5)
        )
        assertEquals(400.0, volume!!, 0.0)
    }

    @Test
    fun perSideVolumeDoublesRecordedWeight() {
        val volume = WorkoutSetVolume.volumeKg(
            exercise(WeightInterpretation.PER_SIDE),
            set(PlannedLoadKind.EXTERNAL_WEIGHT, 20.0, 10)
        )
        assertEquals(400.0, volume!!, 0.0)
    }

    @Test
    fun skippedPendingBodyweightAssistanceAndDurationAreNotVolume() {
        val eligible = exercise(WeightInterpretation.TOTAL)
        assertNull(
            WorkoutSetVolume.volumeKg(
                eligible,
                set(PlannedLoadKind.EXTERNAL_WEIGHT, 80.0, 5, SessionSetStatus.SKIPPED)
            )
        )
        assertNull(
            WorkoutSetVolume.volumeKg(
                eligible,
                set(PlannedLoadKind.BODYWEIGHT_ONLY, 80.0, 5)
            )
        )
        assertNull(
            WorkoutSetVolume.volumeKg(
                eligible,
                set(PlannedLoadKind.ASSISTANCE, 12.0, 5)
            )
        )
        assertNull(
            WorkoutSetVolume.volumeKg(
                exercise(WeightInterpretation.TOTAL, MeasurementType.DURATION_AND_WEIGHT),
                set(PlannedLoadKind.EXTERNAL_WEIGHT, 24.0, reps = null, duration = 40)
            )
        )
    }

    private fun exercise(
        interpretation: WeightInterpretation,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT
    ): SessionExercise {
        return SessionExercise(
            id = 1L,
            sessionId = 1L,
            exerciseId = 1L,
            position = 0,
            name = "Bench",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            measurementType = measurement,
            resistanceBasis = ResistanceBasis.EXTERNAL,
            weightInterpretation = interpretation,
            primaryMuscle = MuscleGroup.CHEST,
            secondaryMuscles = emptyList(),
            notes = null
        )
    }

    private fun set(
        kind: PlannedLoadKind,
        weight: Double?,
        reps: Int?,
        status: SessionSetStatus = SessionSetStatus.COMPLETED,
        duration: Int? = null
    ): SessionSet {
        return SessionSet(
            id = 1L,
            sessionExerciseId = 1L,
            position = 0,
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = kind,
            plannedWeightKg = weight,
            plannedDurationSeconds = duration,
            plannedDistanceMeters = null,
            actualReps = reps,
            actualLoadKind = kind,
            actualWeightKg = weight,
            actualDurationSeconds = duration,
            actualDistanceMeters = null,
            status = status,
            completedAt = 1L,
            addedDuringWorkout = false
        )
    }
}
