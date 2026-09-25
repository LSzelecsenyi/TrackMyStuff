package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateLogicTest {
    @Test
    fun nameTrimAndNormalization() {
        assertEquals("Push A", TemplateNaming.displayName("  Push   A  "))
        assertEquals("push a", TemplateNaming.normalize("  PUSH A "))
    }

    @Test
    fun moveUpAndDownHonorBoundaries() {
        val items = listOf("a", "b", "c")
        assertFalse(TemplateOrdering.canMoveUp(0))
        assertTrue(TemplateOrdering.canMoveUp(1))
        assertFalse(TemplateOrdering.canMoveDown(2, 3))
        assertEquals(listOf("a", "b", "c"), TemplateOrdering.moveUp(items, 0))
        assertEquals(listOf("b", "a", "c"), TemplateOrdering.moveUp(items, 1))
        assertEquals(listOf("a", "c", "b"), TemplateOrdering.moveDown(items, 1))
        assertEquals(listOf(0, 1, 2), TemplateOrdering.compactPositions(3))
    }

    @Test
    fun exactAndRangeRepetitionDisplay() {
        assertEquals("8", RepetitionTarget.display(8, 8))
        assertEquals("8–10", RepetitionTarget.display(8, 10))
        assertEquals(8 to 8, RepetitionTarget.fromExact(8))
    }

    @Test
    fun repetitionRangeValidation() {
        val set = PlannedSetDraft(
            localId = -1,
            minRepsText = "8",
            maxRepsText = "10",
            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
        )
        val (values, errors) = PlannedSetLogic.parseValues(set, pullUp())
        assertTrue(errors.isEmpty())
        assertEquals(8, values!!.minReps)
        assertEquals(10, values.maxReps)
    }

    @Test
    fun invalidRepetitionBoundsAreRejected() {
        val zero = PlannedSetDraft(-1, minRepsText = "0", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        assertTrue(PlannedSetLogic.parseValues(zero, pullUp()).second.contains(TemplateFieldError.RepsNotPositive))
        val inverted = PlannedSetDraft(
            -1,
            minRepsText = "10",
            maxRepsText = "8",
            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
        )
        assertTrue(
            PlannedSetLogic.parseValues(inverted, pullUp()).second.contains(TemplateFieldError.RepsMaxLessThanMin)
        )
    }

    @Test
    fun commaAndPeriodDecimalParsing() {
        val comma = QuantityParser.parseDecimal("8,75", 2, true, 500.0) as DecimalParseResult.Valid
        val period = QuantityParser.parseDecimal("8.75", 2, true, 500.0) as DecimalParseResult.Valid
        assertEquals(8.75, comma.value, 0.0)
        assertEquals(8.75, period.value, 0.0)
        assertTrue(
            QuantityParser.parseDecimal("abc", 2, true, 500.0) is DecimalParseResult.Invalid
        )
        assertTrue(
            QuantityParser.parseDecimal("0", 2, true, 500.0) is DecimalParseResult.Invalid
        )
    }

    @Test
    fun bodyweightLoadKinds() {
        val only = parseLoad(PlannedLoadKind.BODYWEIGHT_ONLY, "")
        val added = parseLoad(PlannedLoadKind.ADDED_WEIGHT, "10")
        val assist = parseLoad(PlannedLoadKind.ASSISTANCE, "5")
        assertEquals(PlannedLoadKind.BODYWEIGHT_ONLY, only.loadKind)
        assertEquals(null, only.weightKg)
        assertEquals(10.0, added.weightKg!!, 0.0)
        assertEquals(5.0, assist.weightKg!!, 0.0)
        assertTrue(PlannedLoadLogic.addedAndAssistanceAreExclusive())
        assertFalse(
            PlannedLoadKind.ADDED_WEIGHT in
                PlannedLoadLogic.compatibleKinds(ResistanceBasis.EXTERNAL, MeasurementType.REPETITIONS_AND_WEIGHT)
        )
    }

    @Test
    fun addedWeightAndAssistanceUseOneKind() {
        val set = PlannedSetDraft(
            localId = -1,
            minRepsText = "8",
            loadKind = PlannedLoadKind.ADDED_WEIGHT,
            weightText = "10"
        )
        val values = PlannedSetLogic.parseValues(set, pullUp()).first!!
        assertEquals(PlannedLoadKind.ADDED_WEIGHT, values.loadKind)
        assertEquals(10.0, values.weightKg!!, 0.0)
    }

    @Test
    fun incompatibleLoadKindIsRejected() {
        val set = PlannedSetDraft(
            localId = -1,
            minRepsText = "8",
            loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            weightText = "20"
        )
        assertTrue(
            PlannedSetLogic.parseValues(set, pullUp()).second.contains(TemplateFieldError.LoadIncompatible)
        )
    }

    @Test
    fun externalWeightCompatibility() {
        val set = PlannedSetDraft(
            localId = -1,
            minRepsText = "8",
            loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            weightText = "8,75"
        )
        val values = PlannedSetLogic.parseValues(set, lateralRaise()).first!!
        assertEquals(8.75, values.weightKg!!, 0.0)
    }

    @Test
    fun durationCanonicalSeconds() {
        assertEquals(90, QuantityParser.toSeconds(1, 30))
        assertEquals(1 to 30, QuantityParser.fromSeconds(90))
        val set = PlannedSetDraft(
            localId = -1,
            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            minutesText = "1",
            secondsText = "0"
        )
        val values = PlannedSetLogic.parseValues(set, plank()).first!!
        assertEquals(60, values.durationSeconds)
    }

    @Test
    fun distanceCanonicalMeters() {
        val meters = QuantityParser.toMeters(1.5, DistanceUnit.KILOMETERS)
        assertEquals(1500.0, meters, 0.0)
        assertEquals(1.5, QuantityParser.fromMeters(1500.0, DistanceUnit.KILOMETERS), 0.0)
        val set = PlannedSetDraft(
            localId = -1,
            loadKind = PlannedLoadKind.NONE,
            minutesText = "5",
            secondsText = "0",
            distanceText = "1,5",
            distanceUnit = DistanceUnit.KILOMETERS
        )
        val values = PlannedSetLogic.parseValues(set, cycling()).first!!
        assertEquals(1500.0, values.distanceMeters!!, 0.0)
        assertEquals(300, values.durationSeconds)
    }

    @Test
    fun applyTargetToRemainingDoesNotChangeEarlierSets() {
        val first = PlannedSetDraft(-1, minRepsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        val second = PlannedSetDraft(-2, minRepsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        val third = PlannedSetDraft(-3, minRepsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        val edited = listOf(
            first,
            second.copy(minRepsText = "6", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "5"),
            third
        )
        val applied = PlannedSetLogic.applyToRemaining(edited, 1)
        assertEquals("8", applied[0].minRepsText)
        assertEquals(PlannedLoadKind.BODYWEIGHT_ONLY, applied[0].loadKind)
        assertEquals("6", applied[1].minRepsText)
        assertEquals("6", applied[2].minRepsText)
        assertEquals(PlannedLoadKind.ADDED_WEIGHT, applied[2].loadKind)
        assertEquals("5", applied[2].weightText)
        assertEquals(-3L, applied[2].localId)
    }

    @Test
    fun editingOneSetDoesNotModifySiblings() {
        val sets = listOf(
            PlannedSetDraft(-1, minRepsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY),
            PlannedSetDraft(-2, minRepsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        val edited = sets.mapIndexed { index, set ->
            if (index == 1) set.copy(minRepsText = "6", weightText = "5", loadKind = PlannedLoadKind.ADDED_WEIGHT)
            else set
        }
        assertEquals("8", edited[0].minRepsText)
        assertEquals(PlannedLoadKind.BODYWEIGHT_ONLY, edited[0].loadKind)
        assertEquals("6", edited[1].minRepsText)
    }

    @Test
    fun archivedExerciseCannotBeNewlyAdded() {
        val result = TemplateDraftLogic.addExercise(
            draft = TemplateDraft(name = "Push A"),
            exercise = pullUp().copy(archived = true),
            nextLocalId = -1,
            allowDuplicate = true
        )
        assertEquals(AddExerciseResult.ArchivedRejected, result)
    }

    @Test
    fun duplicateAddAsksForConfirmation() {
        val added = TemplateDraftLogic.addExercise(
            TemplateDraft(name = "Push A"),
            pullUp(),
            -1,
            false
        ) as AddExerciseResult.Added
        val again = TemplateDraftLogic.addExercise(added.draft, pullUp(), added.nextLocalId, false)
        assertTrue(again is AddExerciseResult.NeedsConfirmation)
    }

    @Test
    fun muscleSummaryDistinguishesPrimaryAndSecondary() {
        val summary = TemplateDraftLogic.muscleSummaryFromExercises(listOf(pullUp(), lateralRaise(), pullUp()))
        assertEquals(2, summary.primary.first { it.muscle == MuscleGroup.LATS }.occurrenceCount)
        assertEquals(1, summary.primary.first { it.muscle == MuscleGroup.SIDE_DELTOID }.occurrenceCount)
        assertEquals(2, summary.secondary.first { it.muscle == MuscleGroup.BICEPS }.occurrenceCount)
        assertFalse(summary.primary.any { it.muscle == MuscleGroup.BICEPS })
    }

    @Test
    fun schemaModelHasNoPerformedOrRirFields() {
        val names = WorkoutTemplateSet::class.java.declaredFields.map { it.name }
        assertFalse(names.any { it.contains("rir", ignoreCase = true) })
        assertFalse(names.any { it.contains("rpe", ignoreCase = true) })
        assertFalse(names.any { it.contains("actual", ignoreCase = true) })
        assertFalse(names.any { it.contains("completed", ignoreCase = true) })
    }

    private fun parseLoad(kind: PlannedLoadKind, weight: String): PlannedSetValues {
        val set = PlannedSetDraft(
            localId = -1,
            minRepsText = "8",
            loadKind = kind,
            weightText = weight
        )
        val result = PlannedSetLogic.parseValues(set, pullUp())
        assertTrue(result.second.isEmpty())
        return result.first!!
    }

    private fun pullUp(): Exercise = exercise(
        name = "Húzódzkodás",
        measurement = MeasurementType.REPETITIONS,
        resistance = ResistanceBasis.BODYWEIGHT,
        primary = MuscleGroup.LATS,
        secondary = listOf(MuscleGroup.BICEPS)
    )

    private fun lateralRaise(): Exercise = exercise(
        name = "Oldalemelés",
        measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance = ResistanceBasis.EXTERNAL,
        primary = MuscleGroup.SIDE_DELTOID,
        weight = WeightInterpretation.PER_SIDE
    )

    private fun plank(): Exercise = exercise(
        name = "Plank",
        measurement = MeasurementType.DURATION,
        resistance = ResistanceBasis.BODYWEIGHT,
        primary = MuscleGroup.ABS
    )

    private fun cycling(): Exercise = exercise(
        name = "Kerékpározás",
        category = ExerciseCategory.CARDIO,
        movement = MovementPattern.CARDIO,
        measurement = MeasurementType.DISTANCE_AND_DURATION,
        resistance = ResistanceBasis.NONE,
        primary = MuscleGroup.CARDIOVASCULAR
    )

    private fun exercise(
        name: String,
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
        movement: MovementPattern = MovementPattern.VERTICAL_PULL,
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        primary: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList(),
        weight: WeightInterpretation = WeightInterpretation.NOT_APPLICABLE
    ): Exercise {
        return Exercise(
            id = name.hashCode().toLong(),
            name = name,
            normalizedName = name.lowercase(),
            category = category,
            movementPattern = movement,
            measurementType = measurement,
            resistanceBasis = resistance,
            weightInterpretation = weight,
            primaryMuscle = primary,
            secondaryMuscles = secondary,
            notes = null,
            archived = false,
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
