package app.mymusclemap.domain.achievements

/**
 * Derived Badge Wall state. Counts, ranking, and sections are not persisted.
 */
data class AlmostThereEntry(
    val achievementId: AchievementId,
    val fraction: Double,
    val countProgress: CountProgress? = null,
    val remainingKg: Double? = null
)

data class BadgeWallSection(
    val category: AchievementCategory,
    val items: List<BadgeWallItem>
)

data class BadgeWallPresentation(
    val earnedCount: Int,
    val totalCount: Int,
    val overallFraction: Float,
    val almostThere: List<AlmostThereEntry>,
    val filters: List<AchievementCategory>,
    val selectedFilter: AchievementCategory?,
    val sections: List<BadgeWallSection>,
    val catalog: List<BadgeWallItem>
) {
    fun item(id: AchievementId): BadgeWallItem? = catalog.firstOrNull { it.id == id }
}

enum class BadgeVisualState {
    EARNED,
    LOCKED_PROGRESS,
    LOCKED
}

fun BadgeWallItem.visualState(): BadgeVisualState {
    return when {
        unlocked -> BadgeVisualState.EARNED
        countProgress != null && countProgress.current > 0 -> BadgeVisualState.LOCKED_PROGRESS
        else -> BadgeVisualState.LOCKED
    }
}

object BadgeWallPresenter {
    private val categoryOrder = listOf(
        AchievementCategory.CONSISTENCY,
        AchievementCategory.JOURNEY,
        AchievementCategory.GOALS,
        AchievementCategory.PERFORMANCE
    )

    fun present(
        board: AchievementBoard,
        selectedFilter: AchievementCategory? = null
    ): BadgeWallPresentation {
        val catalog = board.items
        val earned = catalog.count { it.unlocked }
        val total = catalog.size
        val filters = categoryOrder.filter { category -> catalog.any { it.category == category } }
        val active = selectedFilter?.takeIf { it in filters }
        val visible = if (active == null) catalog else catalog.filter { it.category == active }
        val sections = categoryOrder.mapNotNull { category ->
            val items = visible.filter { it.category == category }
            if (items.isEmpty()) null else BadgeWallSection(category, items)
        }
        return BadgeWallPresentation(
            earnedCount = earned,
            totalCount = total,
            overallFraction = if (total == 0) 0f else earned.toFloat() / total.toFloat(),
            almostThere = almostThere(board),
            filters = filters,
            selectedFilter = active,
            sections = sections,
            catalog = catalog
        )
    }

    /**
     * At most one next milestone per measurable family, ranked by completion.
     * Binary Journey awards are not candidates.
     */
    fun almostThere(board: AchievementBoard): List<AlmostThereEntry> {
        val candidates = listOfNotNull(
            nextCountMilestone(board.items) { it.workoutThreshold },
            nextCountMilestone(board.items) { it.streakWeeks },
            targetWeightMilestone(board)
        )
        return candidates.sortedWith(almostThereOrder()).take(MAX_ALMOST_THERE)
    }

    private fun nextCountMilestone(
        items: List<BadgeWallItem>,
        threshold: (AchievementId) -> Int?
    ): AlmostThereEntry? {
        val next = items
            .filter { threshold(it.id) != null }
            .sortedBy { threshold(it.id) }
            .firstOrNull { !it.unlocked }
            ?: return null
        val progress = next.countProgress ?: return null
        if (progress.current <= 0 || progress.threshold <= 0) return null
        return AlmostThereEntry(
            achievementId = next.id,
            fraction = (progress.current.toDouble() / progress.threshold.toDouble()).coerceIn(0.0, 1.0),
            countProgress = progress
        )
    }

    private fun targetWeightMilestone(board: AchievementBoard): AlmostThereEntry? {
        val item = board.items.firstOrNull { it.id == AchievementId.TARGET_WEIGHT_REACHED } ?: return null
        if (item.unlocked || item.id.journeyMilestone != null) return null
        val progress = board.targetWeightProgress ?: return null
        return AlmostThereEntry(
            achievementId = item.id,
            fraction = progress.progressFraction.coerceIn(0.0, 1.0),
            remainingKg = progress.remainingTowardTargetKg
        )
    }

    private fun almostThereOrder(): Comparator<AlmostThereEntry> {
        return compareByDescending<AlmostThereEntry> { it.fraction }
            .thenComparator { left, right ->
                val leftRemaining = left.countProgress?.let { (it.threshold - it.current).toDouble() }
                val rightRemaining = right.countProgress?.let { (it.threshold - it.current).toDouble() }
                if (leftRemaining != null && rightRemaining != null && sameCounter(left, right)) {
                    leftRemaining.compareTo(rightRemaining)
                } else {
                    0
                }
            }
            .thenBy { AchievementId.entries.indexOf(it.achievementId) }
    }

    private fun sameCounter(left: AlmostThereEntry, right: AlmostThereEntry): Boolean {
        val leftWorkouts = left.achievementId.workoutThreshold != null
        val rightWorkouts = right.achievementId.workoutThreshold != null
        val leftStreak = left.achievementId.streakWeeks != null
        val rightStreak = right.achievementId.streakWeeks != null
        return (leftWorkouts && rightWorkouts) || (leftStreak && rightStreak)
    }

    private const val MAX_ALMOST_THERE = 3
}
