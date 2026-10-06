package app.mymusclemap.domain.achievements

/**
 * Qualified workout-count awards for a completed-session total.
 *
 * The count is every [app.mymusclemap.domain.workout.SessionStatus.COMPLETED] session,
 * including CSV imports. It is not the Founder native-only count and not the free
 * 30-day Statistics window. Health Connect activity is absent because it is not a session.
 */
object WorkoutCountEvaluator {
    fun qualified(completedWorkoutCount: Int): Set<AchievementId> {
        if (completedWorkoutCount <= 0) return emptySet()
        return AchievementCatalog.workoutCounts
            .filter { completedWorkoutCount >= it.workoutThreshold!! }
            .toSet()
    }

    fun next(completedWorkoutCount: Int): NextWorkoutMilestone {
        val upcoming = AchievementCatalog.workoutCounts
            .filter { completedWorkoutCount < it.workoutThreshold!! }
            .minByOrNull { it.workoutThreshold!! }
        return NextWorkoutMilestone(
            completed = completedWorkoutCount.coerceAtLeast(0),
            next = upcoming
        )
    }
}

data class NextWorkoutMilestone(
    val completed: Int,
    val next: AchievementId?
) {
    val allCurrentMilestonesComplete: Boolean = next == null
}
