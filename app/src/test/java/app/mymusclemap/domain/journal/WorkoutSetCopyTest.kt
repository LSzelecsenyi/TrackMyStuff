package app.mymusclemap.domain.journal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutSetCopyTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    @Test
    fun exactRepetitionAndBodyweight() {
        val display = WorkoutSetCopy.display(
            resources,
            set = set(
                actualReps = 8,
                actualLoad = PlannedLoadKind.BODYWEIGHT_ONLY,
                plannedMin = 8,
                plannedMax = 8,
                plannedLoad = PlannedLoadKind.BODYWEIGHT_ONLY
            ),
            exercise = exercise()
        )
        assertEquals("8 reps · bodyweight", display.performed)
        assertFalse(display.valuesDiffer)
    }

    @Test
    fun repetitionRangePlanVersusActual() {
        val display = WorkoutSetCopy.display(
            resources,
            set = set(
                actualReps = 7,
                actualLoad = PlannedLoadKind.BODYWEIGHT_ONLY,
                plannedMin = 8,
                plannedMax = 10,
                plannedLoad = PlannedLoadKind.BODYWEIGHT_ONLY
            ),
            exercise = exercise()
        )
        assertEquals("8–10 reps · bodyweight", display.planned)
        assertEquals("7 reps · bodyweight", display.performed)
        assertTrue(display.valuesDiffer)
    }

    @Test
    fun addedWeightRendering() {
        val display = WorkoutSetCopy.performedValue(
            resources,
            set = set(
                actualReps = 7,
                actualLoad = PlannedLoadKind.ADDED_WEIGHT,
                actualWeight = 15.0
            ),
            interpretation = WeightInterpretation.TOTAL
        )
        assertEquals("7 reps · +15 kg", display)
    }

    @Test
    fun assistanceNeverUsesNegativeWeight() {
        val display = WorkoutSetCopy.performedValue(
            resources,
            set = set(
                actualReps = 6,
                actualLoad = PlannedLoadKind.ASSISTANCE,
                actualWeight = 10.0
            ),
            interpretation = WeightInterpretation.TOTAL
        )
        assertEquals("6 reps · 10 kg assistance", display)
        assertFalse(display.contains("−") || display.contains("-10"))
    }

    @Test
    fun externalPerHandRendering() {
        val display = WorkoutSetCopy.performedValue(
            resources,
            set = set(
                actualReps = 10,
                actualLoad = PlannedLoadKind.EXTERNAL_WEIGHT,
                actualWeight = 8.75
            ),
            interpretation = WeightInterpretation.PER_SIDE
        )
        assertEquals("10 reps · 8.75 kg per side", display)
    }

    @Test
    fun durationRendering() {
        val display = WorkoutSetCopy.performedValue(
            resources,
            set = set(
                actualReps = null,
                actualLoad = PlannedLoadKind.NONE,
                actualDuration = 52
            ),
            interpretation = WeightInterpretation.NOT_APPLICABLE
        )
        assertEquals("52 sec", display)
    }

    @Test
    fun distanceAndDurationRendering() {
        val display = WorkoutSetCopy.performedValue(
            resources,
            set = set(
                actualReps = null,
                actualLoad = PlannedLoadKind.NONE,
                actualDuration = 1720,
                actualDistance = 5200.0
            ),
            interpretation = WeightInterpretation.NOT_APPLICABLE
        )
        assertEquals("5.2 km · 28:40", display)
    }

    @Test
    fun skippedSetKeepsPlannedAndHasNoPerformedValue() {
        val display = WorkoutSetCopy.display(
            resources,
            set = set(
                status = SessionSetStatus.SKIPPED,
                actualReps = null,
                actualLoad = null,
                plannedMin = 8,
                plannedMax = 8,
                plannedLoad = PlannedLoadKind.BODYWEIGHT_ONLY
            ),
            exercise = exercise()
        )
        assertEquals(SessionSetStatus.SKIPPED, display.status)
        assertEquals("8 reps · bodyweight", display.planned)
        assertEquals("", display.performed)
    }

    @Test
    fun extraSetIsLabeled() {
        val display = WorkoutSetCopy.display(
            resources,
            set = set(addedDuringWorkout = true),
            exercise = exercise()
        )
        assertTrue(display.addedDuringWorkout)
    }

    private fun exercise(
        interpretation: WeightInterpretation = WeightInterpretation.NOT_APPLICABLE
    ): SessionExercise {
        return SessionExercise(
            id = 1L,
            sessionId = 1L,
            exerciseId = 1L,
            position = 0,
            name = "Húzódzkodás",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.VERTICAL_PULL,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = interpretation,
            primaryMuscle = MuscleGroup.LATS,
            secondaryMuscles = emptyList(),
            notes = null
        )
    }

    private fun set(
        status: SessionSetStatus = SessionSetStatus.COMPLETED,
        actualReps: Int? = 8,
        actualLoad: PlannedLoadKind? = PlannedLoadKind.BODYWEIGHT_ONLY,
        actualWeight: Double? = null,
        actualDuration: Int? = null,
        actualDistance: Double? = null,
        plannedMin: Int? = actualReps,
        plannedMax: Int? = actualReps,
        plannedLoad: PlannedLoadKind = actualLoad ?: PlannedLoadKind.NONE,
        addedDuringWorkout: Boolean = false
    ): SessionSet {
        return SessionSet(
            id = 1L,
            sessionExerciseId = 1L,
            position = 0,
            plannedMinReps = plannedMin,
            plannedMaxReps = plannedMax,
            plannedLoadKind = plannedLoad,
            plannedWeightKg = null,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = actualReps,
            actualLoadKind = actualLoad,
            actualWeightKg = actualWeight,
            actualDurationSeconds = actualDuration,
            actualDistanceMeters = actualDistance,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) 1L else null,
            addedDuringWorkout = addedDuringWorkout
        )
    }
}
