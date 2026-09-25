package app.mymusclemap.domain.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterCatalogTest {
    @Test
    fun draftsAreASmallValidCatalogWithUniqueNames() {
        val drafts = StarterCatalog.drafts
        assertTrue("size=${drafts.size}", drafts.size in 15..25)
        drafts.forEach { draft ->
            assertTrue("${draft.name} ${ExerciseDraftLogic.validate(draft)}", ExerciseDraftLogic.validate(draft).isEmpty())
            assertFalse(draft.primaryMuscle in draft.secondaryMuscles)
            assertEquals(ExerciseNaming.displayName(draft.name), draft.name)
        }
        val names = drafts.map { ExerciseNaming.normalize(it.name) }
        assertEquals(names.distinct(), names)
    }

    @Test
    fun coversCommonStrengthPatternsWithCorrectMeasurementMetadata() {
        val byName = StarterCatalog.drafts.associateBy { it.name }
        val required = listOf(
            "Squat",
            "Deadlift",
            "Bench Press",
            "Overhead Press",
            "Pull-Up",
            "Chin-Up",
            "Barbell Row",
            "Dumbbell Row",
            "Dip",
            "Push-Up",
            "Lateral Raise",
            "Biceps Curl",
            "Triceps Extension",
            "Lunge",
            "Leg Press",
            "Leg Curl",
            "Calf Raise",
            "Plank"
        )
        required.forEach { name ->
            assertTrue(name, byName.containsKey(name))
        }

        val squat = byName.getValue("Squat")
        assertEquals(MeasurementType.REPETITIONS_AND_WEIGHT, squat.measurementType)
        assertEquals(ResistanceBasis.EXTERNAL, squat.resistanceBasis)
        assertEquals(WeightInterpretation.TOTAL, squat.weightInterpretation)
        assertEquals(MovementPattern.SQUAT, squat.movementPattern)

        val pullUp = byName.getValue("Pull-Up")
        assertEquals(MeasurementType.REPETITIONS, pullUp.measurementType)
        assertEquals(ResistanceBasis.BODYWEIGHT, pullUp.resistanceBasis)
        assertEquals(WeightInterpretation.NOT_APPLICABLE, pullUp.weightInterpretation)
        assertEquals(MovementPattern.VERTICAL_PULL, pullUp.movementPattern)

        val dumbbellRow = byName.getValue("Dumbbell Row")
        assertEquals(WeightInterpretation.PER_SIDE, dumbbellRow.weightInterpretation)

        val plank = byName.getValue("Plank")
        assertEquals(ExerciseCategory.STATIC_HOLD, plank.category)
        assertEquals(MeasurementType.DURATION, plank.measurementType)
        assertEquals(ResistanceBasis.BODYWEIGHT, plank.resistanceBasis)
        assertEquals(WeightInterpretation.NOT_APPLICABLE, plank.weightInterpretation)
        assertEquals(MovementPattern.CORE, plank.movementPattern)
    }

    @Test
    fun compoundExercisesHaveExpectedPrimaryAndSecondaryMuscles() {
        val expected = mapOf(
            "Squat" to mapping(
                MuscleGroup.QUADRICEPS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.ADDUCTORS,
                MuscleGroup.LOWER_BACK
            ),
            "Leg Press" to mapping(
                MuscleGroup.QUADRICEPS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.ADDUCTORS
            ),
            "Lunge" to mapping(
                MuscleGroup.QUADRICEPS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.ADDUCTORS
            ),
            "Deadlift" to mapping(
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.GLUTES,
                MuscleGroup.LOWER_BACK,
                MuscleGroup.QUADRICEPS,
                MuscleGroup.UPPER_BACK,
                MuscleGroup.FOREARMS
            ),
            "Romanian Deadlift" to mapping(
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.GLUTES,
                MuscleGroup.LOWER_BACK,
                MuscleGroup.UPPER_BACK,
                MuscleGroup.FOREARMS
            ),
            "Hip Thrust" to mapping(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            "Bench Press" to mapping(
                MuscleGroup.CHEST,
                MuscleGroup.TRICEPS,
                MuscleGroup.FRONT_DELTOID
            ),
            "Push-Up" to mapping(
                MuscleGroup.CHEST,
                MuscleGroup.TRICEPS,
                MuscleGroup.FRONT_DELTOID
            ),
            "Overhead Press" to mapping(
                MuscleGroup.FRONT_DELTOID,
                MuscleGroup.TRICEPS,
                MuscleGroup.SIDE_DELTOID,
                MuscleGroup.UPPER_BACK
            ),
            "Dip" to mapping(
                MuscleGroup.TRICEPS,
                MuscleGroup.CHEST,
                MuscleGroup.FRONT_DELTOID
            ),
            "Pull-Up" to mapping(
                MuscleGroup.LATS,
                MuscleGroup.BICEPS,
                MuscleGroup.FOREARMS,
                MuscleGroup.UPPER_BACK
            ),
            "Chin-Up" to mapping(
                MuscleGroup.LATS,
                MuscleGroup.BICEPS,
                MuscleGroup.FOREARMS,
                MuscleGroup.UPPER_BACK
            ),
            "Lat Pulldown" to mapping(
                MuscleGroup.LATS,
                MuscleGroup.BICEPS,
                MuscleGroup.UPPER_BACK
            ),
            "Barbell Row" to mapping(
                MuscleGroup.UPPER_BACK,
                MuscleGroup.LATS,
                MuscleGroup.BICEPS,
                MuscleGroup.REAR_DELTOID,
                MuscleGroup.FOREARMS,
                MuscleGroup.LOWER_BACK
            ),
            "Dumbbell Row" to mapping(
                MuscleGroup.LATS,
                MuscleGroup.UPPER_BACK,
                MuscleGroup.BICEPS,
                MuscleGroup.REAR_DELTOID,
                MuscleGroup.FOREARMS
            ),
            "Plank" to mapping(MuscleGroup.ABS, MuscleGroup.OBLIQUES)
        )
        val byName = StarterCatalog.drafts.associateBy { it.name }
        expected.forEach { (name, muscles) ->
            val draft = byName.getValue(name)
            assertEquals(name, muscles.primary, draft.primaryMuscle)
            assertEquals(name, muscles.secondary, draft.secondaryMuscles)
            assertTrue(name, draft.secondaryMuscles.isNotEmpty())
            assertFalse(name, draft.primaryMuscle in draft.secondaryMuscles)
        }
    }

    @Test
    fun isolationExercisesKeepASinglePrimaryMuscle() {
        val expected = mapOf(
            "Leg Curl" to MuscleGroup.HAMSTRINGS,
            "Calf Raise" to MuscleGroup.CALVES,
            "Lateral Raise" to MuscleGroup.SIDE_DELTOID,
            "Triceps Extension" to MuscleGroup.TRICEPS
        )
        val byName = StarterCatalog.drafts.associateBy { it.name }
        expected.forEach { (name, primary) ->
            val draft = byName.getValue(name)
            assertEquals(name, primary, draft.primaryMuscle)
            assertTrue(name, draft.secondaryMuscles.isEmpty())
        }
        val curl = byName.getValue("Biceps Curl")
        assertEquals(MuscleGroup.BICEPS, curl.primaryMuscle)
        assertEquals(listOf(MuscleGroup.FOREARMS), curl.secondaryMuscles)
        val facePull = byName.getValue("Face Pull")
        assertEquals(MuscleGroup.REAR_DELTOID, facePull.primaryMuscle)
        assertEquals(listOf(MuscleGroup.UPPER_BACK), facePull.secondaryMuscles)
    }

    private data class MuscleMapping(
        val primary: MuscleGroup,
        val secondary: List<MuscleGroup>
    )

    private fun mapping(primary: MuscleGroup, vararg secondary: MuscleGroup): MuscleMapping {
        return MuscleMapping(primary, secondary.toList())
    }
}
