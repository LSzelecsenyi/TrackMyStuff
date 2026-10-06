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

enum class AchievementId(
    val category: AchievementCategory,
    /**
     * Set only for awards that mean "the current completed-workout count is at least this".
     * Those awards are revoked when history no longer qualifies. Other awards are sticky.
     */
    val workoutThreshold: Int? = null
) {
    WORKOUTS_5(AchievementCategory.CONSISTENCY, 5),
    WORKOUTS_10(AchievementCategory.CONSISTENCY, 10),
    WORKOUTS_30(AchievementCategory.CONSISTENCY, 30),
    WORKOUTS_50(AchievementCategory.CONSISTENCY, 50),
    WORKOUTS_100(AchievementCategory.CONSISTENCY, 100),
    WORKOUTS_200(AchievementCategory.CONSISTENCY, 200),
    TARGET_WEIGHT_REACHED(AchievementCategory.GOALS);

    val badgeKey: String = name

    /** True only for the current completed-workout count. Sticky awards never return true. */
    val revokesWhenWorkoutCountDrops: Boolean = workoutThreshold != null

    companion object {
        fun fromStorage(raw: String): AchievementId? = entries.firstOrNull { it.name == raw }
    }
}

object AchievementCatalog {
    val workoutCounts: List<AchievementId> = AchievementId.entries.filter { it.workoutThreshold != null }

    fun byCategory(category: AchievementCategory): List<AchievementId> {
        return AchievementId.entries.filter { it.category == category }
    }
}
