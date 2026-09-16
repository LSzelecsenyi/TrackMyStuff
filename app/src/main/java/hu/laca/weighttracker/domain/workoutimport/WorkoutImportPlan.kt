package hu.laca.weighttracker.domain.workoutimport

import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.MuscleRole
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.BodyWeightProposal
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class WorkoutImportMapping(
    val incomingNormalizedName: String,
    val catalogExerciseId: Long
)

data class WorkoutImportMuscleSnapshot(
    val muscleGroup: MuscleGroup,
    val role: MuscleRole
)

data class WorkoutImportExerciseSnapshot(
    val exerciseId: Long,
    val catalogName: String,
    val incomingName: String,
    val category: ExerciseCategory,
    val movementPattern: MovementPattern,
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val weightInterpretation: WeightInterpretation,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val muscles: List<WorkoutImportMuscleSnapshot>,
    val notes: String?,
    val archived: Boolean
)

data class WorkoutImportResolvedSet(
    val sourceRowNumber: Int,
    val setIndex: Int,
    val status: SessionSetStatus,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val loadKind: PlannedLoadKind?,
    val weightKg: BigDecimal?
)

data class WorkoutImportResolvedExercise(
    val sourceRowNumber: Int,
    val exerciseIndex: Int,
    val incomingName: String,
    val normalizedIncomingName: String,
    val snapshot: WorkoutImportExerciseSnapshot?,
    val mappedManually: Boolean,
    val sets: List<WorkoutImportResolvedSet>,
    val errors: List<WorkoutImportError>,
    val warnings: List<WorkoutImportWarning>
)

data class WorkoutImportResolvedWorkout(
    val sourceRowNumber: Int,
    val workoutId: String,
    val name: String,
    val normalizedName: String,
    val workoutDate: LocalDate,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime,
    val durationMillis: Long,
    val notes: String?,
    val bodyWeight: BodyWeightProposal,
    val exercises: List<WorkoutImportResolvedExercise>,
    val errors: List<WorkoutImportError>,
    val warnings: List<WorkoutImportWarning>
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

data class WorkoutImportPlan(
    val formatVersion: Int,
    val workouts: List<WorkoutImportResolvedWorkout>,
    val unresolvedNames: List<String>,
    val errors: List<WorkoutImportError>,
    val warnings: List<WorkoutImportWarning>
) {
    val workoutCount: Int get() = workouts.size
    val dateRange: ClosedRange<LocalDate>?
        get() {
            val dates = workouts.map { it.workoutDate }
            if (dates.isEmpty()) {
                return null
            }
            return dates.min()..dates.max()
        }
    val distinctIncomingExerciseCount: Int
        get() = workouts.flatMap { workout ->
            workout.exercises.map { it.normalizedIncomingName }
        }.distinct().size
    val resolvedExerciseCount: Int
        get() = workouts.flatMap { it.exercises }.count { it.snapshot != null }
    val completedSetCount: Int get() = workouts.sumOf { it.completedSetCount }
    val skippedSetCount: Int get() = workouts.sumOf { it.skippedSetCount }
    val errorCount: Int get() = errors.size
    val warningCount: Int get() = warnings.size
    val canConfirm: Boolean get() = errors.isEmpty() && unresolvedNames.isEmpty()
}
