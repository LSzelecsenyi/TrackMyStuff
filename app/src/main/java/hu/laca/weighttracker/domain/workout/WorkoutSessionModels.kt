package hu.laca.weighttracker.domain.workout

import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import java.time.LocalDate

data class WorkoutSession(
    val id: Long,
    val templateId: Long,
    val templateName: String,
    val status: SessionStatus,
    val workoutDate: LocalDate,
    val startedAt: Long,
    val finishedAt: Long?,
    val abandonedAt: Long?,
    val notes: String?,
    val bodyWeightKg: Double?,
    val bodyWeightSource: BodyWeightSource,
    val bodyWeightSourceDate: LocalDate?,
    val createdAt: Long,
    val updatedAt: Long
)

data class SessionExercise(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val name: String,
    val category: ExerciseCategory,
    val movementPattern: MovementPattern,
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val weightInterpretation: WeightInterpretation,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val notes: String?
)

data class SessionSet(
    val id: Long,
    val sessionExerciseId: Long,
    val position: Int,
    val plannedMinReps: Int?,
    val plannedMaxReps: Int?,
    val plannedLoadKind: PlannedLoadKind,
    val plannedWeightKg: Double?,
    val plannedDurationSeconds: Int?,
    val plannedDistanceMeters: Double?,
    val actualReps: Int?,
    val actualLoadKind: PlannedLoadKind?,
    val actualWeightKg: Double?,
    val actualDurationSeconds: Int?,
    val actualDistanceMeters: Double?,
    val status: SessionSetStatus,
    val completedAt: Long?,
    val addedDuringWorkout: Boolean
)

data class SessionExerciseItem(
    val exercise: SessionExercise,
    val sets: List<SessionSet>
)

data class WorkoutSessionAggregate(
    val session: WorkoutSession,
    val exercises: List<SessionExerciseItem>
)

data class ActiveSessionSummary(
    val session: WorkoutSession,
    val completedSets: Int,
    val skippedSets: Int,
    val pendingSets: Int,
    val totalSets: Int,
    val currentExerciseName: String?,
    val currentExercisePosition: Int?,
    val exerciseCount: Int
)

data class WorkoutSessionSummary(
    val session: WorkoutSession,
    val progress: SessionProgress,
    val exerciseCount: Int,
    val primaryMuscles: List<MuscleGroup>,
    val durationMillis: Long
) {
    val durationLabel: String get() = ElapsedTime.formatMillis(durationMillis)
}

data class BodyWeightProposal(
    val kilograms: Double?,
    val source: BodyWeightSource,
    val sourceDate: LocalDate?
)

data class ActualSetDraft(
    val repsText: String = "",
    val loadKind: PlannedLoadKind,
    val weightText: String = "",
    val minutesText: String = "0",
    val secondsText: String = "0",
    val distanceText: String = "",
    val distanceUnit: DistanceUnit = DistanceUnit.METERS
)

data class ActualSetValues(
    val reps: Int? = null,
    val loadKind: PlannedLoadKind,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null
)

data class SessionProgress(
    val completed: Int,
    val skipped: Int,
    val pending: Int,
    val total: Int
) {
    val resolved: Int get() = completed + skipped
    val fraction: Float get() = if (total == 0) 0f else resolved.toFloat() / total.toFloat()
}

sealed class StartWorkoutResult {
    data class Started(val sessionId: Long) : StartWorkoutResult()
    data object TemplateNotFound : StartWorkoutResult()
    data object TemplateArchived : StartWorkoutResult()
    data object TemplateEmpty : StartWorkoutResult()
    data object AlreadyActive : StartWorkoutResult()
    data class InvalidBodyWeight(val error: hu.laca.weighttracker.domain.WeightParseError) : StartWorkoutResult()
}

sealed class SessionMutationResult {
    data object Updated : SessionMutationResult()
    data object NotFound : SessionMutationResult()
    data object NotActive : SessionMutationResult()
    data object OriginalSetProtected : SessionMutationResult()
    data class Invalid(val errors: List<TemplateFieldError>) : SessionMutationResult()
}

sealed class FinishWorkoutResult {
    data object Finished : FinishWorkoutResult()
    data object AlreadyTerminal : FinishWorkoutResult()
    data object NotFound : FinishWorkoutResult()
    data class PendingRemaining(val count: Int) : FinishWorkoutResult()
}

sealed class AbandonWorkoutResult {
    data object Abandoned : AbandonWorkoutResult()
    data object AlreadyTerminal : AbandonWorkoutResult()
    data object NotFound : AbandonWorkoutResult()
}
