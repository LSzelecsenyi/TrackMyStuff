package app.mymusclemap.domain.achievements

/**
 * Code-defined awards. Room stores unlocks by [AchievementId.name], never a drawable id.
 * [badgeKey] is the stable artwork identity and can outlive any particular icon file.
 */
enum class AchievementCategory {
    CONSISTENCY,
    PERFORMANCE,
    JOURNEY,
    GOALS
}

/** Awards that are the same badge at different lengths. Later tiers can be added here. */
enum class BadgeFamily {
    WEEKLY_GOAL_STREAK
}

enum class BadgeTier {
    BRONZE,
    SILVER,
    GOLD
}

/** Lifetime product milestones. These are not numeric progress awards. */
enum class JourneyMilestone {
    FIRST_WORKOUT,
    FIRST_PLAN,
    FIRST_MONTHLY_REPORT
}

enum class AchievementId(
    val category: AchievementCategory,
    /**
     * Set only for awards that mean "the current completed-workout count is at least this".
     * Those awards are revoked when history no longer qualifies. Other awards are sticky.
     */
    val workoutThreshold: Int? = null,
    /** Consecutive successful weekly-goal weeks required by a streak tier. */
    val streakWeeks: Int? = null,
    val badgeFamily: BadgeFamily? = null,
    val badgeTier: BadgeTier? = null,
    val journeyMilestone: JourneyMilestone? = null
) {
    WORKOUTS_5(AchievementCategory.CONSISTENCY, workoutThreshold = 5),
    WORKOUTS_10(AchievementCategory.CONSISTENCY, workoutThreshold = 10),
    WORKOUTS_30(AchievementCategory.CONSISTENCY, workoutThreshold = 30),
    WORKOUTS_50(AchievementCategory.CONSISTENCY, workoutThreshold = 50),
    WORKOUTS_100(AchievementCategory.CONSISTENCY, workoutThreshold = 100),
    WORKOUTS_200(AchievementCategory.CONSISTENCY, workoutThreshold = 200),
    TARGET_WEIGHT_REACHED(AchievementCategory.GOALS),
    WEEKLY_GOAL_STREAK_4(
        AchievementCategory.GOALS,
        streakWeeks = 4,
        badgeFamily = BadgeFamily.WEEKLY_GOAL_STREAK,
        badgeTier = BadgeTier.BRONZE
    ),
    WEEKLY_GOAL_STREAK_8(
        AchievementCategory.GOALS,
        streakWeeks = 8,
        badgeFamily = BadgeFamily.WEEKLY_GOAL_STREAK,
        badgeTier = BadgeTier.SILVER
    ),
    WEEKLY_GOAL_STREAK_12(
        AchievementCategory.GOALS,
        streakWeeks = 12,
        badgeFamily = BadgeFamily.WEEKLY_GOAL_STREAK,
        badgeTier = BadgeTier.GOLD
    ),
    FIRST_WORKOUT(
        AchievementCategory.JOURNEY,
        journeyMilestone = JourneyMilestone.FIRST_WORKOUT
    ),
    FIRST_CUSTOM_WORKOUT_PLAN(
        AchievementCategory.JOURNEY,
        journeyMilestone = JourneyMilestone.FIRST_PLAN
    ),
    FIRST_MONTHLY_REPORT(
        AchievementCategory.JOURNEY,
        journeyMilestone = JourneyMilestone.FIRST_MONTHLY_REPORT
    );

    val badgeKey: String = name

    /** True only for the current completed-workout count. Sticky awards never return true. */
    val revokesWhenWorkoutCountDrops: Boolean = workoutThreshold != null

    init {
        if (streakWeeks != null) {
            require(streakWeeks > 0)
            require(badgeFamily == BadgeFamily.WEEKLY_GOAL_STREAK)
            require(badgeTier != null)
            require(workoutThreshold == null)
            require(journeyMilestone == null)
        }
        if (journeyMilestone != null) {
            require(category == AchievementCategory.JOURNEY)
            require(workoutThreshold == null)
            require(streakWeeks == null)
            require(badgeFamily == null)
        }
    }

    companion object {
        fun fromStorage(raw: String): AchievementId? = entries.firstOrNull { it.name == raw }
    }
}

object AchievementCatalog {
    val workoutCounts: List<AchievementId> = AchievementId.entries.filter { it.workoutThreshold != null }

    val weeklyStreaks: List<AchievementId> = AchievementId.entries
        .filter { it.badgeFamily == BadgeFamily.WEEKLY_GOAL_STREAK }
        .sortedBy { it.streakWeeks }

    val journey: List<AchievementId> = AchievementId.entries.filter { it.journeyMilestone != null }

    fun byCategory(category: AchievementCategory): List<AchievementId> {
        return AchievementId.entries.filter { it.category == category }
    }
}
