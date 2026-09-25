package app.mymusclemap.domain.exercise

object StarterCatalog {
    val drafts: List<ExerciseDraft> = listOf(
        strength(
            name = "Squat",
            movementPattern = MovementPattern.SQUAT,
            primary = MuscleGroup.QUADRICEPS,
            secondary = listOf(
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.ADDUCTORS,
                MuscleGroup.LOWER_BACK
            )
        ),
        strength(
            name = "Leg Press",
            movementPattern = MovementPattern.SQUAT,
            primary = MuscleGroup.QUADRICEPS,
            secondary = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.ADDUCTORS)
        ),
        strength(
            name = "Lunge",
            movementPattern = MovementPattern.LUNGE,
            primary = MuscleGroup.QUADRICEPS,
            secondary = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.ADDUCTORS)
        ),
        strength(
            name = "Deadlift",
            movementPattern = MovementPattern.HIP_HINGE,
            primary = MuscleGroup.HAMSTRINGS,
            secondary = listOf(
                MuscleGroup.GLUTES,
                MuscleGroup.LOWER_BACK,
                MuscleGroup.QUADRICEPS,
                MuscleGroup.UPPER_BACK,
                MuscleGroup.FOREARMS
            )
        ),
        strength(
            name = "Romanian Deadlift",
            movementPattern = MovementPattern.HIP_HINGE,
            primary = MuscleGroup.HAMSTRINGS,
            secondary = listOf(
                MuscleGroup.GLUTES,
                MuscleGroup.LOWER_BACK,
                MuscleGroup.UPPER_BACK,
                MuscleGroup.FOREARMS
            )
        ),
        strength(
            name = "Hip Thrust",
            movementPattern = MovementPattern.HIP_HINGE,
            primary = MuscleGroup.GLUTES,
            secondary = listOf(MuscleGroup.HAMSTRINGS)
        ),
        strength(
            name = "Leg Curl",
            movementPattern = MovementPattern.ISOLATION,
            primary = MuscleGroup.HAMSTRINGS
        ),
        strength(
            name = "Calf Raise",
            movementPattern = MovementPattern.ISOLATION,
            primary = MuscleGroup.CALVES
        ),
        strength(
            name = "Bench Press",
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            primary = MuscleGroup.CHEST,
            secondary = listOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTOID)
        ),
        bodyweight(
            name = "Push-Up",
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            primary = MuscleGroup.CHEST,
            secondary = listOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTOID)
        ),
        strength(
            name = "Overhead Press",
            movementPattern = MovementPattern.VERTICAL_PUSH,
            primary = MuscleGroup.FRONT_DELTOID,
            secondary = listOf(
                MuscleGroup.TRICEPS,
                MuscleGroup.SIDE_DELTOID,
                MuscleGroup.UPPER_BACK
            )
        ),
        bodyweight(
            name = "Dip",
            movementPattern = MovementPattern.VERTICAL_PUSH,
            primary = MuscleGroup.TRICEPS,
            secondary = listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTOID)
        ),
        bodyweight(
            name = "Pull-Up",
            movementPattern = MovementPattern.VERTICAL_PULL,
            primary = MuscleGroup.LATS,
            secondary = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.UPPER_BACK)
        ),
        bodyweight(
            name = "Chin-Up",
            movementPattern = MovementPattern.VERTICAL_PULL,
            primary = MuscleGroup.LATS,
            secondary = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.UPPER_BACK)
        ),
        strength(
            name = "Lat Pulldown",
            movementPattern = MovementPattern.VERTICAL_PULL,
            primary = MuscleGroup.LATS,
            secondary = listOf(MuscleGroup.BICEPS, MuscleGroup.UPPER_BACK)
        ),
        strength(
            name = "Barbell Row",
            movementPattern = MovementPattern.HORIZONTAL_PULL,
            primary = MuscleGroup.UPPER_BACK,
            secondary = listOf(
                MuscleGroup.LATS,
                MuscleGroup.BICEPS,
                MuscleGroup.REAR_DELTOID,
                MuscleGroup.FOREARMS,
                MuscleGroup.LOWER_BACK
            )
        ),
        strength(
            name = "Dumbbell Row",
            movementPattern = MovementPattern.HORIZONTAL_PULL,
            primary = MuscleGroup.LATS,
            secondary = listOf(
                MuscleGroup.UPPER_BACK,
                MuscleGroup.BICEPS,
                MuscleGroup.REAR_DELTOID,
                MuscleGroup.FOREARMS
            ),
            weightInterpretation = WeightInterpretation.PER_SIDE
        ),
        strength(
            name = "Face Pull",
            movementPattern = MovementPattern.ISOLATION,
            primary = MuscleGroup.REAR_DELTOID,
            secondary = listOf(MuscleGroup.UPPER_BACK)
        ),
        strength(
            name = "Lateral Raise",
            movementPattern = MovementPattern.ISOLATION,
            primary = MuscleGroup.SIDE_DELTOID,
            weightInterpretation = WeightInterpretation.PER_SIDE
        ),
        strength(
            name = "Biceps Curl",
            movementPattern = MovementPattern.ISOLATION,
            primary = MuscleGroup.BICEPS,
            secondary = listOf(MuscleGroup.FOREARMS),
            weightInterpretation = WeightInterpretation.PER_SIDE
        ),
        strength(
            name = "Triceps Extension",
            movementPattern = MovementPattern.ISOLATION,
            primary = MuscleGroup.TRICEPS
        ),
        plank()
    )

    private fun strength(
        name: String,
        movementPattern: MovementPattern,
        primary: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList(),
        weightInterpretation: WeightInterpretation = WeightInterpretation.TOTAL
    ): ExerciseDraft {
        return ExerciseDraft(
            name = name,
            category = ExerciseCategory.STRENGTH,
            movementPattern = movementPattern,
            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistanceBasis = ResistanceBasis.EXTERNAL,
            weightInterpretation = weightInterpretation,
            primaryMuscle = primary,
            secondaryMuscles = secondary
        )
    }

    private fun bodyweight(
        name: String,
        movementPattern: MovementPattern,
        primary: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList()
    ): ExerciseDraft {
        return ExerciseDraft(
            name = name,
            category = ExerciseCategory.STRENGTH,
            movementPattern = movementPattern,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = primary,
            secondaryMuscles = secondary
        )
    }

    private fun plank(): ExerciseDraft {
        return ExerciseDraft(
            name = "Plank",
            category = ExerciseCategory.STATIC_HOLD,
            movementPattern = MovementPattern.CORE,
            measurementType = MeasurementType.DURATION,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.ABS,
            secondaryMuscles = listOf(MuscleGroup.OBLIQUES)
        )
    }
}
