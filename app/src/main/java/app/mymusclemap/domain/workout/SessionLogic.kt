package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.model.WeightMeasurement
import java.time.LocalDate

object BodyWeightSnapshotLogic {
    fun propose(
        workoutDate: LocalDate,
        sameDay: WeightMeasurement?,
        previous: WeightMeasurement?
    ): BodyWeightProposal {
        if (sameDay != null && sameDay.date == workoutDate) {
            return BodyWeightProposal(
                kilograms = sameDay.weightKg,
                source = BodyWeightSource.MEASURED_SAME_DAY,
                sourceDate = sameDay.date
            )
        }
        if (previous != null && previous.date.isBefore(workoutDate)) {
            return BodyWeightProposal(
                kilograms = previous.weightKg,
                source = BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT,
                sourceDate = previous.date
            )
        }
        return BodyWeightProposal(
            kilograms = null,
            source = BodyWeightSource.UNKNOWN,
            sourceDate = null
        )
    }

    fun afterManualEdit(kilograms: Double?): BodyWeightProposal {
        return if (kilograms == null) {
            BodyWeightProposal(null, BodyWeightSource.UNKNOWN, null)
        } else {
            BodyWeightProposal(kilograms, BodyWeightSource.MANUAL, null)
        }
    }
}

object SessionProgressLogic {
    fun from(sets: List<SessionSet>): SessionProgress {
        return SessionProgress(
            completed = sets.count { it.status == SessionSetStatus.COMPLETED },
            skipped = sets.count { it.status == SessionSetStatus.SKIPPED },
            pending = sets.count { it.status == SessionSetStatus.PENDING },
            total = sets.size
        )
    }

    fun fromAggregate(aggregate: WorkoutSessionAggregate): SessionProgress {
        return from(aggregate.exercises.flatMap { it.sets })
    }

    fun currentExercise(aggregate: WorkoutSessionAggregate): SessionExerciseItem? {
        return aggregate.exercises.firstOrNull { item ->
            item.sets.any { it.status == SessionSetStatus.PENDING }
        } ?: aggregate.exercises.firstOrNull()
    }
}

sealed interface WorkoutFocusTarget {
    data class Set(val setId: Long, val exerciseId: Long) : WorkoutFocusTarget
    data object Finish : WorkoutFocusTarget
}

object SessionFocusLogic {
    fun currentPendingExercise(aggregate: WorkoutSessionAggregate): SessionExerciseItem? {
        return aggregate.exercises.firstOrNull { item ->
            item.sets.any { it.status == SessionSetStatus.PENDING }
        }
    }

    fun currentPendingSet(aggregate: WorkoutSessionAggregate): SessionSet? {
        return currentPendingExercise(aggregate)?.sets?.firstOrNull { set ->
            set.status == SessionSetStatus.PENDING
        }
    }

    fun focusAfterResolving(
        exercises: List<SessionExerciseItem>,
        resolvedSetId: Long
    ): WorkoutFocusTarget {
        val currentIndex = exercises.indexOfFirst { item ->
            item.sets.any { it.id == resolvedSetId }
        }
        if (currentIndex < 0) {
            return nextPending(exercises) ?: WorkoutFocusTarget.Finish
        }
        val remainingHere = exercises[currentIndex].sets.firstOrNull { set ->
            set.id != resolvedSetId && set.status == SessionSetStatus.PENDING
        }
        if (remainingHere != null) {
            return WorkoutFocusTarget.Set(remainingHere.id, exercises[currentIndex].exercise.id)
        }
        val later = exercises.drop(currentIndex + 1).firstOrNull { item ->
            item.sets.any { it.status == SessionSetStatus.PENDING }
        }
        if (later != null) {
            val pending = later.sets.first { it.status == SessionSetStatus.PENDING }
            return WorkoutFocusTarget.Set(pending.id, later.exercise.id)
        }
        val earlier = exercises.take(currentIndex).firstOrNull { item ->
            item.sets.any { it.status == SessionSetStatus.PENDING }
        }
        if (earlier != null) {
            val pending = earlier.sets.first { it.status == SessionSetStatus.PENDING }
            return WorkoutFocusTarget.Set(pending.id, earlier.exercise.id)
        }
        return WorkoutFocusTarget.Finish
    }

    private fun nextPending(exercises: List<SessionExerciseItem>): WorkoutFocusTarget.Set? {
        val item = exercises.firstOrNull { exercise ->
            exercise.sets.any { it.status == SessionSetStatus.PENDING }
        } ?: return null
        val pending = item.sets.first { it.status == SessionSetStatus.PENDING }
        return WorkoutFocusTarget.Set(pending.id, item.exercise.id)
    }
}

object ElapsedTime {
    fun millis(startedAt: Long, now: Long): Long {
        return (now - startedAt).coerceAtLeast(0L)
    }

    fun format(startedAt: Long, now: Long): String {
        return formatMillis(millis(startedAt, now))
    }

    fun formatMillis(durationMillis: Long): String {
        val totalSeconds = durationMillis.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }

    fun forSession(session: WorkoutSession): Long {
        val end = when (session.status) {
            SessionStatus.COMPLETED -> session.finishedAt
            SessionStatus.ABANDONED -> session.abandonedAt
            SessionStatus.IN_PROGRESS -> null
        }
        if (end == null) {
            return 0L
        }
        return millis(session.startedAt, end)
    }

    fun formatSession(session: WorkoutSession): String {
        return formatMillis(forSession(session))
    }
}

object WorkoutCompletionLogic {
    fun from(aggregate: WorkoutSessionAggregate): WorkoutCompletionSummary? {
        if (aggregate.session.status != SessionStatus.COMPLETED) {
            return null
        }
        val progress = SessionProgressLogic.fromAggregate(aggregate)
        return WorkoutCompletionSummary(
            exerciseCount = aggregate.exercises.size,
            completedSetCount = progress.completed,
            durationMillis = ElapsedTime.forSession(aggregate.session)
        )
    }
}

object ActualSetLogic {
    /**
     * Skipping keeps the planned snapshot and clears actual values so skipped
     * sets are never treated as completed performances.
     */
    fun clearActualsOnSkip(): Boolean = true

    fun draftFromSet(set: SessionSet): ActualSetDraft {
        val sourceKind = set.actualLoadKind ?: set.plannedLoadKind
        val sourceReps = set.actualReps ?: set.plannedMinReps
        val sourceWeight = set.actualWeightKg ?: set.plannedWeightKg
        val sourceDuration = set.actualDurationSeconds ?: set.plannedDurationSeconds
        val sourceDistance = set.actualDistanceMeters ?: set.plannedDistanceMeters
        val (minutes, seconds) = QuantityParser.fromSeconds(sourceDuration ?: 0)
        return ActualSetDraft(
            repsText = sourceReps?.toString().orEmpty(),
            loadKind = sourceKind,
            weightText = sourceWeight?.let { QuantityParser.formatDisplay(it) }.orEmpty(),
            minutesText = minutes.toString(),
            secondsText = seconds.toString(),
            distanceText = sourceDistance?.let { QuantityParser.formatDisplay(it) }.orEmpty(),
            distanceUnit = DistanceUnit.METERS
        )
    }

    fun parse(
        draft: ActualSetDraft,
        measurement: MeasurementType,
        resistance: ResistanceBasis
    ): Pair<ActualSetValues?, List<TemplateFieldError>> {
        val plannedDraft = PlannedSetDraft(
            localId = 0L,
            minRepsText = draft.repsText,
            maxRepsText = "",
            loadKind = draft.loadKind,
            weightText = draft.weightText,
            minutesText = draft.minutesText,
            secondsText = draft.secondsText,
            distanceText = draft.distanceText,
            distanceUnit = draft.distanceUnit
        )
        val snapshot = exerciseSnapshot(measurement, resistance)
        val (values, errors) = PlannedSetLogic.parseValues(plannedDraft, snapshot)
        if (values == null) {
            return null to errors
        }
        val reps = if (PlannedSetLogic.requiresReps(measurement)) values.minReps else null
        return ActualSetValues(
            reps = reps,
            loadKind = values.loadKind,
            weightKg = values.weightKg,
            durationSeconds = values.durationSeconds,
            distanceMeters = values.distanceMeters
        ) to emptyList()
    }

    fun extraSetFromPrevious(previous: SessionSet): Pair<PlannedSetValues, ActualSetDraft> {
        val planned = PlannedSetValues(
            minReps = previous.plannedMinReps,
            maxReps = previous.plannedMaxReps,
            loadKind = previous.plannedLoadKind,
            weightKg = previous.plannedWeightKg,
            durationSeconds = previous.plannedDurationSeconds,
            distanceMeters = previous.plannedDistanceMeters
        )
        return planned to draftFromSet(previous)
    }

    fun extraSetDefaults(measurement: MeasurementType, resistance: ResistanceBasis): Pair<PlannedSetValues, ActualSetDraft> {
        val exercise = exerciseSnapshot(measurement, resistance)
        val draft = PlannedSetLogic.defaultSet(-1L, exercise)
        val values = PlannedSetLogic.parseValues(draft, exercise).first!!
        return values to ActualSetDraft(
            repsText = draft.minRepsText,
            loadKind = draft.loadKind,
            weightText = draft.weightText,
            minutesText = draft.minutesText,
            secondsText = draft.secondsText,
            distanceText = draft.distanceText,
            distanceUnit = draft.distanceUnit
        )
    }

    fun canRemove(set: SessionSet): Boolean {
        return set.addedDuringWorkout && set.status == SessionSetStatus.PENDING
    }

    fun adjustRepsText(
        current: String,
        delta: Int,
        min: Int = MIN_COMPLETED_REPS,
        max: Int = QuantityParser.MAX_REPS
    ): String {
        val trimmed = current.trim()
        if (trimmed.isEmpty()) {
            return if (delta > 0) min.toString() else current
        }
        val value = trimmed.toIntOrNull() ?: return current
        val next = value + delta
        return when {
            next < min -> if (value < min) current else min.toString()
            next > max -> if (value > max) current else max.toString()
            else -> next.toString()
        }
    }

    const val MIN_COMPLETED_REPS = 1

    fun nextExerciseIndex(
        exercises: List<SessionExerciseItem>,
        currentIndex: Int
    ): Int {
        if (exercises.isEmpty()) {
            return 0
        }
        val fromNext = exercises.drop(currentIndex + 1).indexOfFirst { item ->
            item.sets.any { it.status == SessionSetStatus.PENDING }
        }
        if (fromNext >= 0) {
            return currentIndex + 1 + fromNext
        }
        val earlier = exercises.take(currentIndex + 1).indexOfFirst { item ->
            item.sets.any { it.status == SessionSetStatus.PENDING }
        }
        return if (earlier >= 0) earlier else currentIndex.coerceIn(0, exercises.lastIndex)
    }

    private fun exerciseSnapshot(
        measurement: MeasurementType,
        resistance: ResistanceBasis
    ): app.mymusclemap.domain.exercise.Exercise {
        return app.mymusclemap.domain.exercise.Exercise(
            id = 0L,
            name = "",
            normalizedName = "",
            category = app.mymusclemap.domain.exercise.ExerciseCategory.STRENGTH,
            movementPattern = app.mymusclemap.domain.exercise.MovementPattern.OTHER,
            measurementType = measurement,
            resistanceBasis = resistance,
            weightInterpretation = app.mymusclemap.domain.exercise.WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = app.mymusclemap.domain.exercise.MuscleGroup.FULL_BODY,
            secondaryMuscles = emptyList(),
            notes = null,
            archived = false,
            createdAt = 0L,
            updatedAt = 0L
        )
    }
}

object PlannedTargetDisplay {
    fun reps(minReps: Int?, maxReps: Int?): String? {
        if (minReps == null || maxReps == null) {
            return null
        }
        return RepetitionTarget.display(minReps, maxReps)
    }

    fun load(kind: PlannedLoadKind, weightKg: Double?): String {
        val weight = weightKg?.let { QuantityParser.formatDisplay(it) }
        return when (kind) {
            PlannedLoadKind.BODYWEIGHT_ONLY -> "BW"
            PlannedLoadKind.ADDED_WEIGHT -> if (weight != null) "+$weight kg" else "+"
            PlannedLoadKind.ASSISTANCE -> if (weight != null) "−$weight kg" else "−"
            PlannedLoadKind.EXTERNAL_WEIGHT -> if (weight != null) "$weight kg" else ""
            PlannedLoadKind.NONE -> ""
        }
    }
}
