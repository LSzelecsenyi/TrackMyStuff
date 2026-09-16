package hu.laca.weighttracker.domain.musclemap

import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateMuscleMapAssemblerTest {
    @Test
    fun primaryEmphasis() {
        val state = TemplateMuscleMapAssembler.assemble(listOf(exercise(MuscleGroup.CHEST)))
        assertEquals(TemplateMuscleEmphasis.PRIMARY, state.emphasis[MuscleGroup.CHEST])
    }

    @Test
    fun secondaryEmphasis() {
        val state = TemplateMuscleMapAssembler.assemble(
            listOf(exercise(MuscleGroup.LATS, listOf(MuscleGroup.BICEPS)))
        )
        assertEquals(TemplateMuscleEmphasis.SECONDARY, state.emphasis[MuscleGroup.BICEPS])
        assertEquals(TemplateMuscleEmphasis.PRIMARY, state.emphasis[MuscleGroup.LATS])
    }

    @Test
    fun primaryWinsOverSecondary() {
        val state = TemplateMuscleMapAssembler.assemble(
            listOf(
                exercise(MuscleGroup.BICEPS, listOf(MuscleGroup.FOREARMS)),
                exercise(MuscleGroup.LATS, listOf(MuscleGroup.BICEPS))
            )
        )
        assertEquals(TemplateMuscleEmphasis.PRIMARY, state.emphasis[MuscleGroup.BICEPS])
    }

    @Test
    fun duplicateExercisesCollapse() {
        val state = TemplateMuscleMapAssembler.assemble(
            listOf(
                exercise(MuscleGroup.QUADRICEPS, listOf(MuscleGroup.GLUTES)),
                exercise(MuscleGroup.QUADRICEPS, listOf(MuscleGroup.GLUTES))
            )
        )
        assertEquals(TemplateMuscleEmphasis.PRIMARY, state.emphasis[MuscleGroup.QUADRICEPS])
        assertEquals(TemplateMuscleEmphasis.SECONDARY, state.emphasis[MuscleGroup.GLUTES])
        assertEquals(2, state.emphasis.size)
    }

    @Test
    fun fullBodyAndCardiovascularAreTextual() {
        val state = TemplateMuscleMapAssembler.assemble(
            listOf(
                exercise(MuscleGroup.FULL_BODY),
                exercise(MuscleGroup.CARDIOVASCULAR)
            )
        )
        assertTrue(state.hasFullBody)
        assertTrue(state.hasCardiovascular)
        assertTrue(state.emphasis.isEmpty())
        assertFalse(MuscleGroup.FULL_BODY in state.emphasis)
        assertFalse(MuscleGroup.CARDIOVASCULAR in state.emphasis)
    }

    @Test
    fun eachDeltoidGroupCanBePrimaryOrSecondaryIndependently() {
        val state = TemplateMuscleMapAssembler.assemble(
            listOf(
                exercise(MuscleGroup.FRONT_DELTOID, listOf(MuscleGroup.SIDE_DELTOID)),
                exercise(MuscleGroup.REAR_DELTOID)
            )
        )
        assertEquals(TemplateMuscleEmphasis.PRIMARY, state.emphasis[MuscleGroup.FRONT_DELTOID])
        assertEquals(TemplateMuscleEmphasis.SECONDARY, state.emphasis[MuscleGroup.SIDE_DELTOID])
        assertEquals(TemplateMuscleEmphasis.PRIMARY, state.emphasis[MuscleGroup.REAR_DELTOID])
        assertFalse(MuscleGroup.CHEST in state.emphasis)
    }

    private fun exercise(
        primary: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList()
    ): Exercise {
        return Exercise(
            id = primary.hashCode().toLong() + secondary.hashCode(),
            name = primary.name,
            normalizedName = primary.name.lowercase(),
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.OTHER,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = primary,
            secondaryMuscles = secondary,
            notes = null,
            archived = false,
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
