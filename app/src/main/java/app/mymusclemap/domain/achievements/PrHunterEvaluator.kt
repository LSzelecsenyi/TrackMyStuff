package app.mymusclemap.domain.achievements

/**
 * PR Hunter reads [PerformanceRecordEvaluator.events]. It does not score performances again.
 */
object PrHunterEvaluator {
    fun qualifications(events: List<PerformanceRecordEvent>): List<ProQualification> {
        return AchievementCatalog.prHunter.mapNotNull { id ->
            val target = id.prHunterTarget ?: return@mapNotNull null
            if (events.size < target) return@mapNotNull null
            val crossing = events[target - 1]
            ProQualification(
                achievementId = id,
                unlockedAt = crossing.unlockedAt,
                clientWorkoutId = crossing.clientWorkoutId
            )
        }
    }
}
