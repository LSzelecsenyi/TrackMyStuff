package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import java.time.LocalDate

data class WorkoutSession(
    val id: Long,
    val templateId: Long?,
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
    val updatedAt: Long,
    val importFingerprint: String? = null,
    val scheduledWorkoutId: Long? = null,
    val clientWorkoutId: String = "",
    /**
     * True after this completed workout's approved plan update was committed.
     * A later visit must not apply that workout's progression again.
     */
    val planProgressionApplied: Boolean = false,
    val restProgressionMode: RestProgressionMode = RestProgressionMode.STRICT,
    val restPhase: RestPhase? = null,
    val restOriginSetId: Long? = null,
    val restNextSetId: Long? = null,
    val restAnchorElapsedRealtimeMillis: Long? = null,
    val restDeadlineElapsedRealtimeMillis: Long? = null,
    val restDeadlineWallClockMillis: Long? = null,
    val restDurationMillis: Long? = null,
    val restGeneration: Long = 0L,
    val workPhase: String? = null,
    val workSetId: Long? = null,
    val workTargetMillis: Long? = null,
    val workElapsedActiveMillis: Long? = null,
    val workAnchorElapsedRealtimeMillis: Long? = null,
    val workDeadlineElapsedRealtimeMillis: Long? = null,
    val workDeadlineWallClockMillis: Long? = null,
    val workGeneration: Long = 0L
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
    val notes: String?,
    /**
     * True when this occurrence was added during the session.
     * False means it was copied from the plan at start, imported, or stored before
     * extra exercises existed. Position and name are not origin.
     * [sourceTemplateExerciseId] is the plan-exercise row copied at start.
     * Null means this occurrence has no reliable plan source, including history
     * from before that id was stored. Do not infer one.
     */
    val addedDuringWorkout: Boolean = false,
    val sourceTemplateExerciseId: Long? = null,
    val restSeconds: Int? = null
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
    val addedDuringWorkout: Boolean,
    val draft: ActualSetDraft? = null,
    /** Plan-set row copied at start. Null when this set has no reliable plan source. */
    val sourceTemplateSetId: Long? = null,
    /** Null inherits the exercise rest. Zero disables rest. A positive value is seconds. */
    val restSeconds: Int? = null,
    /** True when this set's target is a work duration. Repetitions stay optional. */
    val timedWork: Boolean = false
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

data class WorkoutCompletionSummary(
    val exerciseCount: Int,
    val completedSetCount: Int,
    val durationMillis: Long,
    val clientWorkoutId: String? = null
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
    data object ScheduleNotFound : StartWorkoutResult()
    data object ScheduleTemplateMismatch : StartWorkoutResult()
    data object ScheduleAlreadyStarted : StartWorkoutResult()
    data object ScheduleNotOnToday : StartWorkoutResult()
    data class InvalidBodyWeight(val error: app.mymusclemap.domain.WeightParseError) : StartWorkoutResult()
    data object PlanLocked : StartWorkoutResult()
}

sealed class SessionMutationResult {
    data object Updated : SessionMutationResult()
    data object NotFound : SessionMutationResult()
    data object NotActive : SessionMutationResult()
    data object OriginalSetProtected : SessionMutationResult()
    data class Invalid(val errors: List<TemplateFieldError>) : SessionMutationResult()
}

/**
 * Result of completing the set a workout notification was rendered for.
 * Anything other than [Updated] leaves persisted workout state unchanged.
 */
sealed class NotificationSetCompletion {
    data object Updated : NotificationSetCompletion()
    data object Stale : NotificationSetCompletion()
    data object Invalid : NotificationSetCompletion()
    data object NotFound : NotificationSetCompletion()
}

sealed class FinishWorkoutResult {
    data object Finished : FinishWorkoutResult()
    data object AlreadyTerminal : FinishWorkoutResult()
    data object NotFound : FinishWorkoutResult()
    data class PendingRemaining(val count: Int) : FinishWorkoutResult()
}

sealed class AddExtraExerciseResult {
    data class Added(val sessionExerciseId: Long, val setId: Long) : AddExtraExerciseResult()
    data object SessionNotFound : AddExtraExerciseResult()
    data object NotActive : AddExtraExerciseResult()
    data object ExerciseNotFound : AddExtraExerciseResult()
    data object ExerciseArchived : AddExtraExerciseResult()
    data object ExerciseLocked : AddExtraExerciseResult()
}

sealed class AbandonWorkoutResult {
    data object Abandoned : AbandonWorkoutResult()
    data object AlreadyTerminal : AbandonWorkoutResult()
    data object NotFound : AbandonWorkoutResult()
    data object Failed : AbandonWorkoutResult()
}

sealed class DeleteWorkoutResult {
    data object Deleted : DeleteWorkoutResult()
    data object NotFound : DeleteWorkoutResult()
    data object ActiveSession : DeleteWorkoutResult()
    data object Failed : DeleteWorkoutResult()
}
