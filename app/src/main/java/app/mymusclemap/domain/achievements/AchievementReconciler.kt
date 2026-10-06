package app.mymusclemap.domain.achievements

enum class ProgressEventKind {
    WEEKLY_GOAL_COMPLETED,
    HISTORY_RECOGNIZED,
    WEIGHT_GOAL_MILESTONE;

    companion object {
        fun fromStorage(raw: String): ProgressEventKind? = entries.firstOrNull { it.name == raw }
    }
}

data class StoredUnlock(
    val achievementId: AchievementId,
    val celebratedAt: Long?
)

data class StoredProgressEvent(
    val dedupeKey: String,
    val kind: ProgressEventKind,
    val celebratedAt: Long?
)

data class ReconcileRequest(
    val initialized: Boolean,
    val completedWorkoutCount: Int,
    val achievedWeeks: List<AchievedWeek>,
    val nowMillis: Long,
    val triggerClientWorkoutId: String?
)

data class UnlockInsert(
    val achievementId: AchievementId,
    val unlockedAt: Long,
    val celebratedAt: Long?,
    val triggerClientWorkoutId: String?
)

data class ProgressEventInsert(
    val dedupeKey: String,
    val kind: ProgressEventKind,
    val payload: String,
    val occurredAt: Long,
    val celebratedAt: Long?,
    val triggerClientWorkoutId: String?
)

data class ReconcilePlan(
    val insertUnlocks: List<UnlockInsert>,
    val revoke: Set<AchievementId>,
    val insertEvents: List<ProgressEventInsert>,
    val deleteEventKeys: Set<String>,
    val markInitialized: Boolean
) {
    val changesNothing: Boolean
        get() = insertUnlocks.isEmpty() &&
            revoke.isEmpty() &&
            insertEvents.isEmpty() &&
            deleteEventKeys.isEmpty() &&
            !markInitialized
}

/**
 * Pure comparison of current history with stored awards.
 *
 * The first run records already-earned workout awards without individual celebrations
 * and, when at least one award is discovered, queues a single history summary.
 * Later runs celebrate a newly crossed threshold, and a single newly achieved week.
 * Two or more newly achieved weeks in one pass are recorded silently so a first goal
 * or a bulk import does not open one dialog per past week.
 */
object AchievementReconciler {
    fun plan(
        request: ReconcileRequest,
        unlocks: List<StoredUnlock>,
        events: List<StoredProgressEvent>
    ): ReconcilePlan {
        val qualified = WorkoutCountEvaluator.qualified(request.completedWorkoutCount)
        val storedIds = unlocks.map { it.achievementId }.toSet()
        val revoke = storedIds.filter { stored ->
            stored.revokesWhenWorkoutCountDrops && stored !in qualified
        }.toSet()
        val missing = AchievementCatalog.workoutCounts.filter { it in qualified && it !in storedIds }
        val silentAwards = !request.initialized
        val insertUnlocks = missing.map { id ->
            UnlockInsert(
                achievementId = id,
                unlockedAt = request.nowMillis,
                celebratedAt = if (silentAwards) request.nowMillis else null,
                triggerClientWorkoutId = if (silentAwards) null else request.triggerClientWorkoutId
            )
        }

        val achievedByKey = request.achievedWeeks.associateBy {
            WeeklyGoalCompletionEvaluator.dedupeKey(it.weekStart)
        }
        val weeklyEvents = events.filter { it.kind == ProgressEventKind.WEEKLY_GOAL_COMPLETED }
        val existingWeekKeys = weeklyEvents.map { it.dedupeKey }.toSet()
        val newWeeks = request.achievedWeeks.filter {
            WeeklyGoalCompletionEvaluator.dedupeKey(it.weekStart) !in existingWeekKeys
        }
        val silenceWeeks = silentAwards || newWeeks.size > 1
        val weekInserts = newWeeks.map { week ->
            ProgressEventInsert(
                dedupeKey = WeeklyGoalCompletionEvaluator.dedupeKey(week.weekStart),
                kind = ProgressEventKind.WEEKLY_GOAL_COMPLETED,
                payload = WeeklyGoalCompletionEvaluator.payload(week),
                occurredAt = request.nowMillis,
                celebratedAt = if (silenceWeeks) request.nowMillis else null,
                triggerClientWorkoutId = if (silenceWeeks) null else request.triggerClientWorkoutId
            )
        }
        val deleteEventKeys = weeklyEvents
            .filter { it.celebratedAt == null && it.dedupeKey !in achievedByKey }
            .map { it.dedupeKey }
            .toSet()

        val historyKey = WeeklyGoalCompletionEvaluator.HISTORY_RECOGNIZED_KEY
        val historyExists = events.any { it.dedupeKey == historyKey }
        val historyInsert = if (silentAwards && missing.isNotEmpty() && !historyExists) {
            ProgressEventInsert(
                dedupeKey = historyKey,
                kind = ProgressEventKind.HISTORY_RECOGNIZED,
                payload = missing.size.toString(),
                occurredAt = request.nowMillis,
                celebratedAt = null,
                triggerClientWorkoutId = null
            )
        } else {
            null
        }

        return ReconcilePlan(
            insertUnlocks = insertUnlocks,
            revoke = revoke,
            insertEvents = weekInserts + listOfNotNull(historyInsert),
            deleteEventKeys = deleteEventKeys,
            markInitialized = !request.initialized
        )
    }
}
