package app.mymusclemap.domain.exercise

/**
 * Official built-in exercises. [SystemExerciseDefinition.catalogKey] is the permanent
 * identity of a catalog entry. It is not derived from the display name, a translation,
 * a database id, or the list order, and it is never reused for a different exercise.
 */
data class SystemExerciseDefinition(
    val catalogKey: String,
    val draft: ExerciseDraft
)

object StarterCatalog {
    val entries: List<SystemExerciseDefinition> = listOf(
        strength("SQUAT", "Squat", MovementPattern.SQUAT, MuscleGroup.QUADRICEPS, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.ADDUCTORS, MuscleGroup.LOWER_BACK)),
        strength("LEG_PRESS", "Leg Press", MovementPattern.SQUAT, MuscleGroup.QUADRICEPS, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.ADDUCTORS)),
        strength("LUNGE", "Lunge", MovementPattern.LUNGE, MuscleGroup.QUADRICEPS, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.ADDUCTORS)),
        strength("DEADLIFT", "Deadlift", MovementPattern.HIP_HINGE, MuscleGroup.HAMSTRINGS, listOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK, MuscleGroup.QUADRICEPS, MuscleGroup.UPPER_BACK, MuscleGroup.FOREARMS)),
        strength("ROMANIAN_DEADLIFT", "Romanian Deadlift", MovementPattern.HIP_HINGE, MuscleGroup.HAMSTRINGS, listOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK, MuscleGroup.UPPER_BACK, MuscleGroup.FOREARMS)),
        strength("HIP_THRUST", "Hip Thrust", MovementPattern.HIP_HINGE, MuscleGroup.GLUTES, listOf(MuscleGroup.HAMSTRINGS)),
        strength("LEG_CURL", "Leg Curl", MovementPattern.ISOLATION, MuscleGroup.HAMSTRINGS),
        strength("CALF_RAISE", "Calf Raise", MovementPattern.ISOLATION, MuscleGroup.CALVES),
        strength("BENCH_PRESS", "Bench Press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTOID)),
        bodyweight("PUSH_UP", "Push-Up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTOID)),
        strength("OVERHEAD_PRESS", "Overhead Press", MovementPattern.VERTICAL_PUSH, MuscleGroup.FRONT_DELTOID, listOf(MuscleGroup.TRICEPS, MuscleGroup.SIDE_DELTOID, MuscleGroup.UPPER_BACK)),
        bodyweight("DIP", "Dip", MovementPattern.VERTICAL_PUSH, MuscleGroup.TRICEPS, listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTOID)),
        bodyweight("PULL_UP", "Pull-Up", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.UPPER_BACK)),
        bodyweight("CHIN_UP", "Chin-Up", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.UPPER_BACK)),
        strength("LAT_PULLDOWN", "Lat Pulldown", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.UPPER_BACK)),
        strength("BARBELL_ROW", "Barbell Row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.UPPER_BACK, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS, MuscleGroup.REAR_DELTOID, MuscleGroup.FOREARMS, MuscleGroup.LOWER_BACK)),
        strength("DUMBBELL_ROW", "Dumbbell Row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.LATS, listOf(MuscleGroup.UPPER_BACK, MuscleGroup.BICEPS, MuscleGroup.REAR_DELTOID, MuscleGroup.FOREARMS), WeightInterpretation.PER_SIDE),
        strength("FACE_PULL", "Face Pull", MovementPattern.ISOLATION, MuscleGroup.REAR_DELTOID, listOf(MuscleGroup.UPPER_BACK)),
        strength("LATERAL_RAISE", "Lateral Raise", MovementPattern.ISOLATION, MuscleGroup.SIDE_DELTOID, emptyList(), WeightInterpretation.PER_SIDE),
        strength("BICEPS_CURL", "Biceps Curl", MovementPattern.ISOLATION, MuscleGroup.BICEPS, listOf(MuscleGroup.FOREARMS), WeightInterpretation.PER_SIDE),
        strength("TRICEPS_EXTENSION", "Triceps Extension", MovementPattern.ISOLATION, MuscleGroup.TRICEPS),
        plank()
    )

    val drafts: List<ExerciseDraft> = entries.map { it.draft }

    val keys: Set<String> = entries.map { it.catalogKey }.toSet()

    private val byKey: Map<String, SystemExerciseDefinition> = entries.associateBy { it.catalogKey }

    val normalizedNames: Set<String> by lazy {
        drafts.map { ExerciseNaming.normalize(it.name) }.toSet()
    }

    fun isOfficialKey(catalogKey: String): Boolean = catalogKey in keys

    fun definition(catalogKey: String): SystemExerciseDefinition? = byKey[catalogKey]

    private fun strength(
        catalogKey: String,
        name: String,
        movementPattern: MovementPattern,
        primary: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList(),
        weightInterpretation: WeightInterpretation = WeightInterpretation.TOTAL
    ): SystemExerciseDefinition {
        return SystemExerciseDefinition(
            catalogKey = catalogKey,
            draft = ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = movementPattern,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = weightInterpretation,
                primaryMuscle = primary,
                secondaryMuscles = secondary
            )
        )
    }

    private fun bodyweight(
        catalogKey: String,
        name: String,
        movementPattern: MovementPattern,
        primary: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList()
    ): SystemExerciseDefinition {
        return SystemExerciseDefinition(
            catalogKey = catalogKey,
            draft = ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = movementPattern,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = primary,
                secondaryMuscles = secondary
            )
        )
    }

    private fun plank(): SystemExerciseDefinition {
        return SystemExerciseDefinition(
            catalogKey = "PLANK",
            draft = ExerciseDraft(
                name = "Plank",
                category = ExerciseCategory.STATIC_HOLD,
                movementPattern = MovementPattern.CORE,
                measurementType = MeasurementType.DURATION,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.ABS,
                secondaryMuscles = listOf(MuscleGroup.OBLIQUES)
            )
        )
    }
}
