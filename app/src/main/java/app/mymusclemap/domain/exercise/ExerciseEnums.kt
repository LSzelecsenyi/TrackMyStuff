package app.mymusclemap.domain.exercise

enum class ExerciseCategory {
    STRENGTH,
    CARDIO,
    STATIC_HOLD,
    MOBILITY,
    SKILL
}

enum class MovementPattern {
    VERTICAL_PUSH,
    HORIZONTAL_PUSH,
    VERTICAL_PULL,
    HORIZONTAL_PULL,
    SQUAT,
    HIP_HINGE,
    LUNGE,
    CORE,
    CARRY,
    ISOLATION,
    CARDIO,
    MOBILITY,
    OTHER
}

enum class MeasurementType {
    REPETITIONS,
    REPETITIONS_AND_WEIGHT,
    DURATION,
    DURATION_AND_WEIGHT,
    DISTANCE_AND_DURATION,
    COMPLETION_ONLY
}

enum class ResistanceBasis {
    BODYWEIGHT,
    EXTERNAL,
    NONE
}

enum class WeightInterpretation {
    TOTAL,
    PER_SIDE,
    NOT_APPLICABLE
}

enum class MuscleGroup {
    CHEST,
    LATS,
    UPPER_BACK,
    LOWER_BACK,
    FRONT_DELTOID,
    SIDE_DELTOID,
    REAR_DELTOID,
    BICEPS,
    TRICEPS,
    FOREARMS,
    ABS,
    OBLIQUES,
    GLUTES,
    QUADRICEPS,
    HAMSTRINGS,
    ADDUCTORS,
    CALVES,
    NECK,
    FULL_BODY,
    CARDIOVASCULAR
}

enum class MuscleRole {
    PRIMARY,
    SECONDARY
}

enum class ArchiveFilter {
    ACTIVE,
    ARCHIVED,
    ALL
}
