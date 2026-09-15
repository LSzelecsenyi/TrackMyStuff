package hu.laca.weighttracker.domain.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseNamingAndDraftLogicTest {
    @Test
    fun trimsAndCollapsesWhitespaceInDisplayName() {
        assertEquals("Húzódzkodás", ExerciseNaming.displayName("  Húzódzkodás  "))
        assertEquals("Húzódzkodás", ExerciseNaming.displayName("Húzódzkodás\t"))
        assertEquals("Oldal emelés", ExerciseNaming.displayName("  Oldal   emelés  "))
    }

    @Test
    fun normalizeIsCaseInsensitiveAndTrimmed() {
        assertEquals("huzodzkodas", ExerciseNaming.normalize("  HUZODZKODAS  "))
        assertEquals(
            ExerciseNaming.normalize("húzódzkodás"),
            ExerciseNaming.normalize("  Húzódzkodás  ")
        )
        assertEquals("", ExerciseNaming.normalize("   "))
    }

    @Test
    fun blankNameIsRejected() {
        val errors = ExerciseDraftLogic.validate(ExerciseDraft(name = "   "))
        assertTrue(ExerciseFieldError.NameBlank in errors)
    }

    @Test
    fun primaryCannotAlsoBeSecondary() {
        val errors = ExerciseDraftLogic.validate(
            ExerciseDraft(
                name = "Fekvenyomás",
                primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS)
            )
        )
        assertTrue(ExerciseFieldError.PrimaryAlsoSecondary in errors)
    }

    @Test
    fun duplicateSecondaryGroupsAreNormalized() {
        val normalized = ExerciseDraftLogic.normalizeSecondary(
            MuscleGroup.CHEST,
            listOf(MuscleGroup.TRICEPS, MuscleGroup.CHEST, MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTOID)
        )
        assertEquals(listOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTOID), normalized)
    }

    @Test
    fun changingPrimaryRemovesItFromSecondary() {
        val draft = ExerciseDraft(
            name = "Evezés",
            primaryMuscle = MuscleGroup.LATS,
            secondaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.UPPER_BACK)
        )
        val updated = ExerciseDraftLogic.applyPrimaryMuscle(draft, MuscleGroup.BICEPS)
        assertEquals(MuscleGroup.BICEPS, updated.primaryMuscle)
        assertEquals(listOf(MuscleGroup.UPPER_BACK), updated.secondaryMuscles)
        assertFalse(MuscleGroup.BICEPS in updated.secondaryMuscles)
    }

    @Test
    fun bodyweightExerciseDoesNotNeedASeparateWeightedVariant() {
        val draft = ExerciseDraft(
            name = "Húzódzkodás",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.VERTICAL_PULL,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.LATS
        )
        assertTrue(ExerciseDraftLogic.validate(draft).isEmpty())
        assertTrue(ExerciseDraftLogic.bodyweightAllowsPerSetLoad(draft.resistanceBasis))
        assertFalse(
            ExerciseDraftLogic.isWeightInterpretationVisible(
                draft.measurementType,
                draft.resistanceBasis
            )
        )
        assertEquals(
            WeightInterpretation.NOT_APPLICABLE,
            ExerciseDraftLogic.resolvedWeightInterpretation(
                draft.measurementType,
                draft.resistanceBasis,
                WeightInterpretation.TOTAL
            )
        )
    }

    @Test
    fun weightInterpretationIsVisibleOnlyForExternalWeightedMeasurements() {
        assertTrue(
            ExerciseDraftLogic.isWeightInterpretationVisible(
                MeasurementType.REPETITIONS_AND_WEIGHT,
                ResistanceBasis.EXTERNAL
            )
        )
        assertTrue(
            ExerciseDraftLogic.isWeightInterpretationVisible(
                MeasurementType.DURATION_AND_WEIGHT,
                ResistanceBasis.EXTERNAL
            )
        )
        assertFalse(
            ExerciseDraftLogic.isWeightInterpretationVisible(
                MeasurementType.REPETITIONS,
                ResistanceBasis.EXTERNAL
            )
        )
        assertFalse(
            ExerciseDraftLogic.isWeightInterpretationVisible(
                MeasurementType.REPETITIONS_AND_WEIGHT,
                ResistanceBasis.BODYWEIGHT
            )
        )
        assertFalse(
            ExerciseDraftLogic.isWeightInterpretationVisible(
                MeasurementType.REPETITIONS_AND_WEIGHT,
                ResistanceBasis.NONE
            )
        )
    }

    @Test
    fun visibleWeightInterpretationCannotStayNotApplicable() {
        val errors = ExerciseDraftLogic.validate(
            ExerciseDraft(
                name = "Oldalemelés",
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.SIDE_DELTOID
            )
        )
        assertTrue(ExerciseFieldError.WeightInterpretationRequired in errors)
    }

    @Test
    fun applyingResistanceClearsWeightInterpretationWhenNotRelevant() {
        val draft = ExerciseDraftLogic.applyResistance(
            ExerciseDraft(
                name = "Plank",
                measurementType = MeasurementType.DURATION,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = WeightInterpretation.TOTAL
            ),
            ResistanceBasis.BODYWEIGHT
        )
        assertEquals(WeightInterpretation.NOT_APPLICABLE, draft.weightInterpretation)
    }
}
