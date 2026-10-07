package app.mymusclemap.domain.achievements

data class CountProgress(
    val current: Int,
    val threshold: Int
)

data class BadgeWallItem(
    val id: AchievementId,
    val category: AchievementCategory,
    val badgeKey: String,
    val badgeFamily: BadgeFamily?,
    val badgeTier: BadgeTier?,
    val unlocked: Boolean,
    val unlockedAt: Long?,
    val countProgress: CountProgress?
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

    data class WeeklyStreakUnlocked(
        val achievementId: AchievementId,
        val weeks: Int,
        override val triggerClientWorkoutId: String?,
        override val acknowledgement: CelebrationAcknowledgement
    ) : PendingCelebration

    data class JourneyUnlocked(
        val achievementId: AchievementId,
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
    val pending: List<PendingCelebration>,
    /** Active target journey, when a baseline exists. Not stored as achievement progress. */
    val targetWeightProgress: TargetWeightProgress? = null
)

object AchievementBoardAssembler {
    fun assemble(
        completedWorkoutCount: Int,
        unlocks: List<UnlockSnapshot>,
        events: List<ProgressEventSnapshot>,
        currentStreak: Int = 0
    ): AchievementBoard {
        val unlockById = unlocks.associateBy { it.achievementId }
        val streak = currentStreak.coerceAtLeast(0)
        val items = AchievementId.entries.map { id ->
            val unlock = unlockById[id]
            val workoutThreshold = id.workoutThreshold
            val streakWeeks = id.streakWeeks
            BadgeWallItem(
                id = id,
                category = id.category,
                badgeKey = id.badgeKey,
                badgeFamily = id.badgeFamily,
                badgeTier = id.badgeTier,
                unlocked = unlock != null,
                unlockedAt = unlock?.unlockedAt,
                countProgress = when {
                    workoutThreshold != null -> CountProgress(
                        current = completedWorkoutCount.coerceAtLeast(0).coerceAtMost(workoutThreshold),
                        threshold = workoutThreshold
                    )
                    streakWeeks != null -> CountProgress(
                        current = streak.coerceAtMost(streakWeeks),
                        threshold = streakWeeks
                    )
                    else -> null
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
        val streaks = unlocks
            .filter { it.celebratedAt == null && it.achievementId.streakWeeks != null }
            .sortedByDescending { it.achievementId.streakWeeks }
            .map { unlock ->
                PendingCelebration.WeeklyStreakUnlocked(
                    achievementId = unlock.achievementId,
                    weeks = unlock.achievementId.streakWeeks!!,
                    triggerClientWorkoutId = unlock.triggerClientWorkoutId,
                    acknowledgement = CelebrationAcknowledgement(achievementId = unlock.achievementId.name)
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
        val journeys = unlocks
            .filter { it.celebratedAt == null && it.achievementId.journeyMilestone != null }
            .sortedByDescending { it.unlockedAt }
            .map { unlock ->
                PendingCelebration.JourneyUnlocked(
                    achievementId = unlock.achievementId,
                    triggerClientWorkoutId = unlock.triggerClientWorkoutId,
                    acknowledgement = CelebrationAcknowledgement(achievementId = unlock.achievementId.name)
                )
            }
        return history + journeys + streaks + weeks + awards + weight
    }
}
