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
        val completable: Boolean
    ) : ActiveWorkoutNotificationBody

    data object ReadyToFinish : ActiveWorkoutNotificationBody
}

object ActiveWorkoutNotificationLogic {
    /**
     * Projects the single in-progress aggregate into notification content.
     * Returns null when there is nothing to show.
     */
    fun project(aggregate: WorkoutSessionAggregate?): ActiveWorkoutNotificationModel? {
        if (aggregate == null || aggregate.session.status != SessionStatus.IN_PROGRESS) {
            return null
        }
        val current = SessionFocusLogic.currentPendingSet(aggregate)
        if (current == null) {
            return ActiveWorkoutNotificationModel(
                sessionId = aggregate.session.id,
                workoutName = aggregate.session.templateName,
                body = ActiveWorkoutNotificationBody.ReadyToFinish
            )
        }
        val item = aggregate.exercises.firstOrNull { exercise ->
            exercise.sets.any { it.id == current.id }
        } ?: return null
        val parsed = ActualSetLogic.parse(
            ActualSetLogic.draftFromSet(current),
            item.exercise.measurementType,
            item.exercise.resistanceBasis
        ).first
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
                completable = parsed != null
            )
        )
    }

    fun completes(aggregate: WorkoutSessionAggregate?, setId: Long): Boolean {
        val body = project(aggregate)?.body as? ActiveWorkoutNotificationBody.CurrentSet ?: return false
        return body.completable && body.setId == setId
    }
}

internal fun notificationLoadLabel(
    kind: PlannedLoadKind,
    weightKg: Double?,
    bodyweightLabel: String
): String {
    val load = PlannedTargetDisplay.load(kind, weightKg)
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
