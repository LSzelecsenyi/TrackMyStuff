package app.mymusclemap.domain.workout

enum class PlannedLoadKind {
    BODYWEIGHT_ONLY,
    ADDED_WEIGHT,
    ASSISTANCE,
    EXTERNAL_WEIGHT,
    NONE
}

enum class DistanceUnit {
    METERS,
    KILOMETERS
}

enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
}

enum class SessionSetStatus {
    PENDING,
    COMPLETED,
    SKIPPED
}

enum class BodyWeightSource {
    MEASURED_SAME_DAY,
    NEAREST_PREVIOUS_MEASUREMENT,
    MANUAL,
    UNKNOWN
}

enum class TemplateFieldError {
    NameBlank,
    NameTooLong,
    NotesTooLong,
    RepsRequired,
    RepsMalformed,
    RepsNotPositive,
    RepsMaxLessThanMin,
    RepsTooLarge,
    LoadIncompatible,
    WeightRequired,
    WeightMalformed,
    WeightNotPositive,
    WeightTooLarge,
    DurationRequired,
    DurationMalformed,
    DurationNotPositive,
    DistanceRequired,
    DistanceMalformed,
    DistanceNotPositive,
    CompletionSingleSet
}
