package app.mymusclemap.domain.workout

data class ActiveWorkoutNotificationModel(
    val sessionId: Long,
    val workoutName: String,
    val body: ActiveWorkoutNotificationBody
)

sealed interface ActiveWorkoutNotificationBody {
    data class CurrentSet(
        val setId: Long,
        val exerciseName: String,
        val setNumber: Int,
        val setCount: Int,
        val reps: Int?,
        val loadKind: PlannedLoadKind?,
        val weightKg: Double?,
        val durationSeconds: Int?,
        val distanceMeters: Double?,
        val completable: Boolean,
        /**
         * True when the notification must not offer completion and should tell the
         * user to open STRICT. Covers an unparsable draft and an entitlement-locked
         * exercise. Timed work and an active work phase stay visible without this
         * prompt and without a completion control.
         */
        val needsInAppInput: Boolean = false
    ) : ActiveWorkoutNotificationBody

    data object ReadyToFinish : ActiveWorkoutNotificationBody
}

object ActiveWorkoutNotificationLogic {
    /**
     * Projects the single in-progress aggregate into notification content.
     * Returns null when there is nothing to show.
     *
     * [lockedExerciseIds] matches the ids [SessionFocusLogic.currentPendingSet] skips
     * inside notification completion. A set the repository will reject is not marked completable.
     */
    fun project(
        aggregate: WorkoutSessionAggregate?,
        lockedExerciseIds: Set<Long> = emptySet()
    ): ActiveWorkoutNotificationModel? {
        if (aggregate == null || aggregate.session.status != SessionStatus.IN_PROGRESS) {
            return null
        }
        val current = SessionFocusLogic.currentPendingSet(aggregate, lockedExerciseIds)
        if (current == null) {
            val blocked = SessionFocusLogic.currentPendingSet(aggregate)
            if (blocked != null && lockedExerciseIds.isNotEmpty()) {
                return currentSetModel(aggregate, blocked, locked = true)
            }
            return ActiveWorkoutNotificationModel(
                sessionId = aggregate.session.id,
                workoutName = aggregate.session.templateName,
                body = ActiveWorkoutNotificationBody.ReadyToFinish
            )
        }
        return currentSetModel(aggregate, current, locked = false)
    }

    fun completes(
        aggregate: WorkoutSessionAggregate?,
        setId: Long,
        lockedExerciseIds: Set<Long> = emptySet()
    ): Boolean {
        val body = project(aggregate, lockedExerciseIds)?.body as? ActiveWorkoutNotificationBody.CurrentSet
            ?: return false
        return body.completable && body.setId == setId
    }

    private fun currentSetModel(
        aggregate: WorkoutSessionAggregate,
        current: SessionSet,
        locked: Boolean
    ): ActiveWorkoutNotificationModel? {
        val item = aggregate.exercises.firstOrNull { exercise ->
            exercise.sets.any { it.id == current.id }
        } ?: return null
        val parsed = ActualSetLogic.parse(
            ActualSetLogic.draftFromSet(current),
            item.exercise.measurementType,
            item.exercise.resistanceBasis,
            timedWork = current.timedWork
        ).first
        val timerBlocksCompletion = current.timedWork || aggregate.session.workPhase != null
        val needsInAppInput = locked || parsed == null
        return ActiveWorkoutNotificationModel(
            sessionId = aggregate.session.id,
            workoutName = aggregate.session.templateName,
            body = ActiveWorkoutNotificationBody.CurrentSet(
                setId = current.id,
                exerciseName = item.exercise.name,
                setNumber = current.position + 1,
                setCount = item.sets.size,
                reps = parsed?.reps,
                loadKind = parsed?.loadKind,
                weightKg = parsed?.weightKg,
                durationSeconds = parsed?.durationSeconds,
                distanceMeters = parsed?.distanceMeters,
                completable = parsed != null && !needsInAppInput && !timerBlocksCompletion,
                needsInAppInput = needsInAppInput
            )
        )
    }
}

internal fun notificationLoadLabel(
    kind: PlannedLoadKind,
    weightKg: Double?,
    bodyweightLabel: String,
    system: app.mymusclemap.domain.units.UnitSystem = app.mymusclemap.domain.units.UnitSystem.METRIC
): String {
    val load = PlannedTargetDisplay.load(kind, weightKg, system)
    return when (load) {
        "BW" -> bodyweightLabel
        "+", "−" -> ""
        else -> load
    }
}

internal fun notificationValueParts(
    repsLabel: String?,
    loadLabel: String?,
    durationLabel: String?,
    distanceLabel: String?
): String {
    return listOfNotNull(repsLabel, loadLabel, durationLabel, distanceLabel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}
