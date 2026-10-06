package app.mymusclemap.domain.achievements

/**
 * Decides which weight-goal events to insert, drop, or silence.
 * A single evaluation leaves only the strongest newly visible milestone pending.
 */
object TargetWeightMilestonePlanner {
    fun plan(
        goal: TargetWeightGoalFacts?,
        currentKg: Double?,
        historicalKg: List<Double> = emptyList(),
        events: List<StoredProgressEvent>,
        targetWeightUnlocked: Boolean,
        nowMillis: Long
    ): WeightMilestonePlan {
        val weightEvents = events.filter { it.kind == ProgressEventKind.WEIGHT_GOAL_MILESTONE }
        val active = goal?.takeIf { it.active && it.baselineKg != null && it.direction != null }
        val progress = active?.let { TargetWeightProgressEvaluator.progress(it, currentKg) }
        val crossed = if (active == null) {
            emptyList()
        } else {
            TargetWeightProgressEvaluator.crossedForJourney(
                goal = active,
                currentKg = currentKg,
                historicalKg = historicalKg
            )
        }
        val crossedKeys = crossed.associateWith { milestone ->
            TargetWeightProgressEvaluator.dedupeKey(active!!.id, milestone)
        }
        val activePrefix = active?.let { "weight-goal:${it.id}:" }
        val unseenElsewhere = weightEvents.filter { event ->
            event.celebratedAt == null && (activePrefix == null || !event.dedupeKey.startsWith(activePrefix))
        }.map { it.dedupeKey }
        val thisGoal = if (activePrefix == null) {
            emptyList()
        } else {
            weightEvents.filter { it.dedupeKey.startsWith(activePrefix) }
        }
        val existingByKey = thisGoal.associateBy { it.dedupeKey }
        val noLongerTrue = thisGoal.filter { event ->
            event.celebratedAt == null && event.dedupeKey !in crossedKeys.values
        }.map { it.dedupeKey }
        val pending = crossed.filter { milestone ->
            val stored = existingByKey[crossedKeys.getValue(milestone)]
            stored == null || stored.celebratedAt == null
        }
        val top = pending.maxByOrNull { it.priority }
        val topKey = top?.let { crossedKeys.getValue(it) }
        val inserts = crossed.mapNotNull { milestone ->
            val key = crossedKeys.getValue(milestone)
            if (key in existingByKey) return@mapNotNull null
            ProgressEventInsert(
                dedupeKey = key,
                kind = ProgressEventKind.WEIGHT_GOAL_MILESTONE,
                payload = milestone.name,
                occurredAt = nowMillis,
                celebratedAt = if (key == topKey) null else nowMillis,
                triggerClientWorkoutId = null
            )
        }
        val silence = thisGoal.filter { event ->
            event.celebratedAt == null &&
                event.dedupeKey != topKey &&
                event.dedupeKey !in noLongerTrue
        }.map { it.dedupeKey }
        val unlock = if (progress?.reached == true && !targetWeightUnlocked) {
            UnlockInsert(
                achievementId = AchievementId.TARGET_WEIGHT_REACHED,
                unlockedAt = nowMillis,
                celebratedAt = if (top == WeightMilestone.REACHED) null else nowMillis,
                triggerClientWorkoutId = null
            )
        } else {
            null
        }
        return WeightMilestonePlan(
            insertEvents = inserts,
            deleteEventKeys = (unseenElsewhere + noLongerTrue).toSet(),
            silenceEventKeys = silence.toSet(),
            insertUnlock = unlock
        )
    }
}

data class WeightMilestonePlan(
    val insertEvents: List<ProgressEventInsert>,
    val deleteEventKeys: Set<String>,
    val silenceEventKeys: Set<String>,
    val insertUnlock: UnlockInsert?
) {
    val changesNothing: Boolean
        get() = insertEvents.isEmpty() &&
            deleteEventKeys.isEmpty() &&
            silenceEventKeys.isEmpty() &&
            insertUnlock == null
}
