package app.mymusclemap.domain.achievements

/**
 * Code-defined awards. Room stores unlocks by [AchievementId.name], never a drawable id.
 * [badgeKey] is the stable artwork identity and can outlive any particular icon file.
 */
enum class AchievementCategory {
    CONSISTENCY,
    PERFORMANCE,
    JOURNEY,
    GOALS,
    /** Section bucket for [AchievementAccess.SPECIAL]. Access, not this category, is the filter. */
    SPECIAL
}

/** Awards that are the same badge at different lengths. Later tiers can be added here. */
enum class BadgeFamily {
    WEEKLY_GOAL_STREAK,
    PR_HUNTER,
    EXERCISE_MASTERY
}

enum class BadgeTier {
    BRONZE,
    SILVER,
    GOLD
}

/**
 * Static catalog access. This is not a stored subscription flag.
 * [PRO] requirements are still evaluated for Free users; only the unlock requires Pro.
 */
enum class AchievementAccess {
    FREE,
    PRO,
    SPECIAL
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
    val journeyMilestone: JourneyMilestone? = null,
    val access: AchievementAccess = AchievementAccess.FREE,
    /**
     * Sticky completed-workout target. Unlike [workoutThreshold], earning it is not revoked
     * when later history no longer reaches the count.
     */
    val lifetimeWorkoutTarget: Int? = null,
    /** Lifetime eligible kilogram volume required to satisfy the requirement. */
    val volumeThresholdKg: Double? = null,
    /** Personal-record events required by a PR Hunter tier. */
    val prHunterTarget: Int? = null,
    /** Completed sets of one exercise required by an Exercise Mastery tier. */
    val masterySetTarget: Int? = null
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
    WEEKLY_GOAL_STREAK_26(
        AchievementCategory.GOALS,
        streakWeeks = 26,
        badgeFamily = BadgeFamily.WEEKLY_GOAL_STREAK,
        badgeTier = BadgeTier.GOLD,
        access = AchievementAccess.PRO
    ),
    WEEKLY_GOAL_STREAK_52(
        AchievementCategory.GOALS,
        streakWeeks = 52,
        badgeFamily = BadgeFamily.WEEKLY_GOAL_STREAK,
        badgeTier = BadgeTier.GOLD,
        access = AchievementAccess.PRO
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
    ),
    FIRST_PR(AchievementCategory.PERFORMANCE),
    WEIGHT_PR(AchievementCategory.PERFORMANCE),
    REP_RECORD(AchievementCategory.PERFORMANCE),
    VOLUME_RECORD(AchievementCategory.PERFORMANCE),
    IRON_DISCIPLINE(
        AchievementCategory.CONSISTENCY,
        access = AchievementAccess.PRO,
        lifetimeWorkoutTarget = 250
    ),
    VOLUME_MASTER(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        volumeThresholdKg = 100_000.0
    ),
    PR_HUNTER_10(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        badgeFamily = BadgeFamily.PR_HUNTER,
        badgeTier = BadgeTier.BRONZE,
        prHunterTarget = 10
    ),
    PR_HUNTER_25(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        badgeFamily = BadgeFamily.PR_HUNTER,
        badgeTier = BadgeTier.SILVER,
        prHunterTarget = 25
    ),
    PR_HUNTER_50(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        badgeFamily = BadgeFamily.PR_HUNTER,
        badgeTier = BadgeTier.GOLD,
        prHunterTarget = 50
    ),
    PR_HUNTER_100(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        badgeFamily = BadgeFamily.PR_HUNTER,
        badgeTier = BadgeTier.GOLD,
        prHunterTarget = 100
    ),
    EXERCISE_MASTERY_100(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        badgeFamily = BadgeFamily.EXERCISE_MASTERY,
        badgeTier = BadgeTier.BRONZE,
        masterySetTarget = 100
    ),
    EXERCISE_MASTERY_250(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        badgeFamily = BadgeFamily.EXERCISE_MASTERY,
        badgeTier = BadgeTier.SILVER,
        masterySetTarget = 250
    ),
    EXERCISE_MASTERY_500(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        badgeFamily = BadgeFamily.EXERCISE_MASTERY,
        badgeTier = BadgeTier.GOLD,
        masterySetTarget = 500
    ),
    EXERCISE_MASTERY_1000(
        AchievementCategory.PERFORMANCE,
        access = AchievementAccess.PRO,
        badgeFamily = BadgeFamily.EXERCISE_MASTERY,
        badgeTier = BadgeTier.GOLD,
        masterySetTarget = 1000
    ),
    FOUNDER(
        AchievementCategory.SPECIAL,
        access = AchievementAccess.SPECIAL
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
        if (category == AchievementCategory.PERFORMANCE) {
            require(workoutThreshold == null)
            require(lifetimeWorkoutTarget == null)
            require(streakWeeks == null)
            require(journeyMilestone == null)
            require(
                badgeFamily == null ||
                    badgeFamily == BadgeFamily.PR_HUNTER ||
                    badgeFamily == BadgeFamily.EXERCISE_MASTERY
            )
        }
        if (badgeFamily == BadgeFamily.PR_HUNTER || prHunterTarget != null) {
            require(badgeFamily == BadgeFamily.PR_HUNTER)
            require(prHunterTarget != null && prHunterTarget > 0)
            require(access == AchievementAccess.PRO)
            require(badgeTier != null)
            require(category == AchievementCategory.PERFORMANCE)
        }
        if (badgeFamily == BadgeFamily.EXERCISE_MASTERY || masterySetTarget != null) {
            require(badgeFamily == BadgeFamily.EXERCISE_MASTERY)
            require(masterySetTarget != null && masterySetTarget > 0)
            require(access == AchievementAccess.PRO)
            require(badgeTier != null)
            require(category == AchievementCategory.PERFORMANCE)
        }
        if (lifetimeWorkoutTarget != null) {
            require(lifetimeWorkoutTarget > 0)
            require(workoutThreshold == null)
            require(access == AchievementAccess.PRO)
        }
        if (volumeThresholdKg != null) {
            require(volumeThresholdKg > 0.0)
            require(access == AchievementAccess.PRO)
            require(workoutThreshold == null)
            require(lifetimeWorkoutTarget == null)
        }
        if (category == AchievementCategory.SPECIAL || access == AchievementAccess.SPECIAL) {
            require(category == AchievementCategory.SPECIAL)
            require(access == AchievementAccess.SPECIAL)
            require(workoutThreshold == null)
            require(lifetimeWorkoutTarget == null)
            require(volumeThresholdKg == null)
            require(streakWeeks == null)
            require(journeyMilestone == null)
            require(badgeFamily == null)
        }
    }

    /** Completed-workout target used for progress, including the sticky Pro milestone. */
    val workoutCountTarget: Int? get() = workoutThreshold ?: lifetimeWorkoutTarget

    companion object {
        fun fromStorage(raw: String): AchievementId? = entries.firstOrNull { it.name == raw }
    }
}

object AchievementCatalog {
    val workoutCounts: List<AchievementId> = AchievementId.entries.filter { it.workoutThreshold != null }

    val weeklyStreaks: List<AchievementId> = AchievementId.entries
        .filter { it.badgeFamily == BadgeFamily.WEEKLY_GOAL_STREAK }
        .sortedBy { it.streakWeeks }

    val prHunter: List<AchievementId> = AchievementId.entries
        .filter { it.badgeFamily == BadgeFamily.PR_HUNTER }
        .sortedBy { it.prHunterTarget }

    val exerciseMastery: List<AchievementId> = AchievementId.entries
        .filter { it.badgeFamily == BadgeFamily.EXERCISE_MASTERY }
        .sortedBy { it.masterySetTarget }

    val journey: List<AchievementId> = AchievementId.entries.filter { it.journeyMilestone != null }

    val performance: List<AchievementId> = AchievementId.entries
        .filter { it.category == AchievementCategory.PERFORMANCE }

    val pro: List<AchievementId> = AchievementId.entries.filter { it.access == AchievementAccess.PRO }

    val special: List<AchievementId> = AchievementId.entries.filter { it.access == AchievementAccess.SPECIAL }

    val free: List<AchievementId> = AchievementId.entries.filter { it.access == AchievementAccess.FREE }

    fun byCategory(category: AchievementCategory): List<AchievementId> {
        return AchievementId.entries.filter { it.category == category }
    }
}
