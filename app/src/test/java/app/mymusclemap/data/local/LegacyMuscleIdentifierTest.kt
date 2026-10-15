package app.mymusclemap.data.local

import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.MuscleRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacyMuscleIdentifierTest {
    @Test
    fun storedTrapsRowsReadAsUpperBackWithoutDroppingTheExercise() {
        val exercise = ExerciseEntity(
            id = 7,
            name = "Shrug",
            normalizedName = "shrug",
            category = "STRENGTH",
            movementPattern = "ISOLATION",
            measurementType = "REPETITIONS_AND_WEIGHT",
            resistanceBasis = "EXTERNAL",
            weightInterpretation = "TOTAL",
            notes = null,
            archived = false,
            createdAt = 1,
            updatedAt = 1
        ).toModel(
            listOf(
                ExerciseMuscleEntity(7, "TRAPS", MuscleRole.PRIMARY.name),
                ExerciseMuscleEntity(7, "BICEPS", MuscleRole.SECONDARY.name)
            )
        )
        assertEquals("Shrug", exercise.name)
        assertEquals(MuscleGroup.UPPER_BACK, exercise.primaryMuscle)
        assertEquals(listOf(MuscleGroup.BICEPS), exercise.secondaryMuscles)
        assertFalse(MuscleGroup.SERRATUS in exercise.secondaryMuscles)

        val session = WorkoutSessionExerciseEntity(
            id = 3,
            sessionId = 1,
            exerciseId = 7,
            position = 0,
            name = "Shrug",
            category = "STRENGTH",
            movementPattern = "ISOLATION",
            measurementType = "REPETITIONS_AND_WEIGHT",
            resistanceBasis = "EXTERNAL",
            weightInterpretation = "TOTAL",
            primaryMuscle = "TRAPS",
            notes = null
        ).toModel(listOf(WorkoutSessionExerciseMuscleEntity(3, "TRAPS", MuscleRole.SECONDARY.name)))
        assertEquals(MuscleGroup.UPPER_BACK, session.primaryMuscle)
        assertEquals(listOf(MuscleGroup.UPPER_BACK), session.secondaryMuscles)
        assertEquals(7, session.exerciseId)
    }
}
