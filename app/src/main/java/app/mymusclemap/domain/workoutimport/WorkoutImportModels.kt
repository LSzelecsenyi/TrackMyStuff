package app.mymusclemap.domain.workoutimport

import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class WorkoutImportErrorCode {
    EmptyFile,
    HeaderOnly,
    InvalidUtf8,
    FileTooLarge,
    TooManyRows,
    TooManyWorkouts,
    TooManyExercises,
    TooManySets,
    FieldTooLong,
    MalformedQuote,
    MissingHeader,
    InvalidHeader,
    DuplicateHeader,
    ExtraColumns,
    MissingColumns,
    UnsupportedVersion,
    MissingRequired,
    InvalidInteger,
    InvalidDecimal,
    InvalidPrecision,
    OutOfRange,
    InvalidDate,
    FutureDate,
    InvalidTimestamp,
    TimestampDateMismatch,
    FinishBeforeStart,
    InvalidStatus,
    InvalidLoadKind,
    InvalidDistanceUnit,
    DistanceUnitWithoutDistance,
    MissingDistanceUnit,
    WeightIncompatibleWithLoad,
    SkippedHasActuals,
    CompletedMissingLoadKind,
    InconsistentWorkoutField,
    ConflictingExerciseName,
    NonContiguousExerciseIndex,
    DuplicateSetIndex,
    NonContiguousSetIndex,
    EmptyWorkout,
    NoCompletedSets,
    DuplicateRecord,
    UnresolvedExercise,
    AmbiguousCatalogMatch,
    StaleManualMapping,
    ConflictingManualMapping,
    IncompatibleMeasurement,
    MissingRequiredActual,
    ForbiddenActual,
    IncompatibleLoadKind,
    MissingLoadWeight,
    ForbiddenLoadWeight,
    InvalidCatalogMuscle
}

enum class WorkoutImportWarningCode {
    ArchivedExercise,
    ManualAliasMapping,
    MultipleIncomingNamesMapped,
    BodyWeightMismatch,
    PreviousMeasurementFallback,
    UnknownBodyWeight,
    SameDateAndName
}

data class WorkoutImportError(
    val rowNumber: Int?,
    val field: String?,
    val code: WorkoutImportErrorCode,
    val detail: String? = null,
    val workoutId: String? = null,
    val incomingExerciseName: String? = null,
    val exerciseIndex: Int? = null,
    val setIndex: Int? = null
)

data class WorkoutImportWarning(
    val rowNumber: Int?,
    val field: String?,
    val code: WorkoutImportWarningCode,
    val detail: String? = null,
    val workoutId: String? = null,
    val incomingExerciseName: String? = null,
    val exerciseIndex: Int? = null,
    val setIndex: Int? = null
)

data class WorkoutImportRow(
    val sourceRowNumber: Int,
    val workoutId: String,
    val workoutName: String,
    val normalizedWorkoutName: String,
    val workoutDate: LocalDate,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime,
    val notes: String?,
    val bodyWeightKg: BigDecimal?,
    val exerciseIndex: Int,
    val exerciseName: String,
    val normalizedExerciseName: String,
    val setIndex: Int,
    val status: SessionSetStatus,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val loadKind: PlannedLoadKind?,
    val weightKg: BigDecimal?,
    val rawFields: List<String>
)

data class WorkoutImportSet(
    val sourceRowNumber: Int,
    val setIndex: Int,
    val status: SessionSetStatus,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val loadKind: PlannedLoadKind?,
    val weightKg: BigDecimal?
)

data class WorkoutImportExercise(
    val sourceRowNumber: Int,
    val exerciseIndex: Int,
    val name: String,
    val normalizedName: String,
    val sets: List<WorkoutImportSet>
)

data class WorkoutImportWorkout(
    val sourceRowNumber: Int,
    val workoutId: String,
    val name: String,
    val normalizedName: String,
    val workoutDate: LocalDate,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime,
    val notes: String?,
    val bodyWeightKg: BigDecimal?,
    val exercises: List<WorkoutImportExercise>
) {
    val completedSetCount: Int
        get() = exercises.sumOf { exercise ->
            exercise.sets.count { it.status == SessionSetStatus.COMPLETED }
        }
    val skippedSetCount: Int
        get() = exercises.sumOf { exercise ->
            exercise.sets.count { it.status == SessionSetStatus.SKIPPED }
        }
}

data class WorkoutImportDocument(
    val workouts: List<WorkoutImportWorkout>
) {
    val workoutCount: Int get() = workouts.size
    val exerciseCount: Int get() = workouts.sumOf { it.exercises.size }
    val completedSetCount: Int get() = workouts.sumOf { it.completedSetCount }
    val skippedSetCount: Int get() = workouts.sumOf { it.skippedSetCount }
    val distinctIncomingExerciseNames: List<String>
        get() = workouts.flatMap { workout -> workout.exercises.map { it.normalizedName } }.distinct()
}

sealed class WorkoutImportParseResult {
    data class Success(
        val document: WorkoutImportDocument,
        val warnings: List<WorkoutImportWarning> = emptyList()
    ) : WorkoutImportParseResult()

    data class Failure(
        val errors: List<WorkoutImportError>
    ) : WorkoutImportParseResult()
}
