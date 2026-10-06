package app.mymusclemap.domain.achievements

data class WorkoutCountProgress(
    val current: Int,
    val threshold: Int
)

data class BadgeWallItem(
    val id: AchievementId,
    val category: AchievementCategory,
    val badgeKey: String,
    val unlocked: Boolean,
    val unlockedAt: Long?,
    val workoutProgress: WorkoutCountProgress?
)

data class CelebrationAcknowledgement(
    val progressEventKey: String? = null,
    val achievementId: String? = null
)

sealed interface PendingCelebration {
    val acknowledgement: CelebrationAcknowledgement
    val triggerClientWorkoutId: String?

    data class HistoryRecognized(
        val badgeCount: Int,
        override val acknowledgement: CelebrationAcknowledgement
    ) : PendingCelebration {
        override val triggerClientWorkoutId: String? = null
    }

    data class WeeklyGoalCompleted(
        val completed: Int,
        val goal: Int,
        override val triggerClientWorkoutId: String?,
        override val acknowledgement: CelebrationAcknowledgement
    ) : PendingCelebration

    data class WorkoutCountUnlocked(
        val achievementId: AchievementId,
        val threshold: Int,
        override val triggerClientWorkoutId: String?,
        override val acknowledgement: CelebrationAcknowledgement
    ) : PendingCelebration

    data class TargetWeightMilestone(
        val milestone: WeightMilestone,
        val includesLifetimeUnlock: Boolean,
        override val acknowledgement: CelebrationAcknowledgement
    ) : PendingCelebration {
        override val triggerClientWorkoutId: String? = null
    }
}

data class UnlockSnapshot(
    val achievementId: AchievementId,
    val unlockedAt: Long,
    val celebratedAt: Long?,
    val triggerClientWorkoutId: String?
)

data class ProgressEventSnapshot(
    val dedupeKey: String,
    val kind: ProgressEventKind,
    val payload: String,
    val celebratedAt: Long?,
    val triggerClientWorkoutId: String?
)

data class AchievementBoard(
    val completedWorkoutCount: Int,
    val items: List<BadgeWallItem>,
    val next: NextWorkoutMilestone,
    val pending: List<PendingCelebration>
)

object AchievementBoardAssembler {
    fun assemble(
        completedWorkoutCount: Int,
        unlocks: List<UnlockSnapshot>,
        events: List<ProgressEventSnapshot>
    ): AchievementBoard {
        val unlockById = unlocks.associateBy { it.achievementId }
        val items = AchievementId.entries.map { id ->
            val unlock = unlockById[id]
            val threshold = id.workoutThreshold
            BadgeWallItem(
                id = id,
                category = id.category,
                badgeKey = id.badgeKey,
                unlocked = unlock != null,
                unlockedAt = unlock?.unlockedAt,
                workoutProgress = threshold?.let { goal ->
                    WorkoutCountProgress(
                        current = completedWorkoutCount.coerceAtLeast(0).coerceAtMost(goal),
                        threshold = goal
                    )
                }
            )
        }
        return AchievementBoard(
            completedWorkoutCount = completedWorkoutCount.coerceAtLeast(0),
            items = items,
            next = WorkoutCountEvaluator.next(completedWorkoutCount),
            pending = pending(unlocks, events)
        )
    }

    private fun pending(
        unlocks: List<UnlockSnapshot>,
        events: List<ProgressEventSnapshot>
    ): List<PendingCelebration> {
        val history = events.mapNotNull { event ->
            if (event.celebratedAt != null || event.kind != ProgressEventKind.HISTORY_RECOGNIZED) {
                return@mapNotNull null
            }
            val count = event.payload.toIntOrNull() ?: return@mapNotNull null
            if (count <= 0) return@mapNotNull null
            PendingCelebration.HistoryRecognized(
                badgeCount = count,
                acknowledgement = CelebrationAcknowledgement(progressEventKey = event.dedupeKey)
            )
        }
        val weeks = events.mapNotNull { event ->
            if (event.celebratedAt != null || event.kind != ProgressEventKind.WEEKLY_GOAL_COMPLETED) {
                return@mapNotNull null
            }
            val week = WeeklyGoalCompletionEvaluator.parsePayload(event.payload) ?: return@mapNotNull null
            PendingCelebration.WeeklyGoalCompleted(
                completed = week.completed,
                goal = week.goal,
                triggerClientWorkoutId = event.triggerClientWorkoutId,
                acknowledgement = CelebrationAcknowledgement(progressEventKey = event.dedupeKey)
            )
        }.sortedBy { it.acknowledgement.progressEventKey }
        val awards = unlocks
            .filter { it.celebratedAt == null && it.achievementId.workoutThreshold != null }
            .sortedBy { it.achievementId.workoutThreshold }
            .map { unlock ->
                PendingCelebration.WorkoutCountUnlocked(
                    achievementId = unlock.achievementId,
                    threshold = unlock.achievementId.workoutThreshold!!,
                    triggerClientWorkoutId = unlock.triggerClientWorkoutId,
                    acknowledgement = CelebrationAcknowledgement(achievementId = unlock.achievementId.name)
                )
            }
        val lifetimeUnlock = unlocks.firstOrNull { it.achievementId == AchievementId.TARGET_WEIGHT_REACHED }
        val weight = events.mapNotNull { event ->
            if (event.celebratedAt != null || event.kind != ProgressEventKind.WEIGHT_GOAL_MILESTONE) {
                return@mapNotNull null
            }
            val milestone = WeightMilestone.fromPayload(event.payload) ?: return@mapNotNull null
            val includesLifetime = milestone == WeightMilestone.REACHED &&
                lifetimeUnlock != null &&
                lifetimeUnlock.celebratedAt == null
            PendingCelebration.TargetWeightMilestone(
                milestone = milestone,
                includesLifetimeUnlock = includesLifetime,
                acknowledgement = CelebrationAcknowledgement(
                    progressEventKey = event.dedupeKey,
                    achievementId = if (includesLifetime) AchievementId.TARGET_WEIGHT_REACHED.name else null
                )
            )
        }.sortedByDescending { it.milestone.priority }
        return history + weeks + awards + weight
    }
}
