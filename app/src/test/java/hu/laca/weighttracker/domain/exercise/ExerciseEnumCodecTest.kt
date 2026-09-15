package hu.laca.weighttracker.domain.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseEnumCodecTest {
    @Test
    fun unknownPersistedCodesFallBackToSafeDefaults() {
        assertEquals(ExerciseCategory.STRENGTH, ExerciseEnumCodec.category("not-a-category"))
        assertEquals(MovementPattern.OTHER, ExerciseEnumCodec.movement("mystery"))
        assertEquals(MeasurementType.COMPLETION_ONLY, ExerciseEnumCodec.measurement(""))
        assertEquals(ResistanceBasis.NONE, ExerciseEnumCodec.resistance("BAND"))
        assertEquals(WeightInterpretation.NOT_APPLICABLE, ExerciseEnumCodec.weight("BODY"))
        assertNull(ExerciseEnumCodec.muscle("NECK"))
        assertEquals(MuscleGroup.FULL_BODY, ExerciseEnumCodec.muscleOrFallback("NECK"))
        assertEquals(MuscleRole.SECONDARY, ExerciseEnumCodec.role("ASSIST"))
    }

    @Test
    fun knownCodesRoundTrip() {
        assertEquals(ExerciseCategory.MOBILITY, ExerciseEnumCodec.category("MOBILITY"))
        assertEquals(MuscleGroup.SIDE_DELTOID, ExerciseEnumCodec.muscle("SIDE_DELTOID"))
        assertEquals(WeightInterpretation.PER_SIDE, ExerciseEnumCodec.weight("PER_SIDE"))
    }
}
