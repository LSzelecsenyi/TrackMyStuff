package app.mymusclemap.domain.achievements

/**
 * Derived Badge Wall state. Counts, ranking, and sections are not persisted.
 */
data class AlmostThereEntry(
    val achievementId: AchievementId,
    val fraction: Double,
    val countProgress: CountProgress? = null,
    val remainingKg: Double? = null,
    val volumeProgress: VolumeProgress? = null
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
    val filters: List<AchievementAccess>,
    val selectedFilter: AchievementAccess?,
    val sections: List<BadgeWallSection>,
    val catalog: List<BadgeWallItem>
) {
    fun item(id: AchievementId): BadgeWallItem? = catalog.firstOrNull { it.id == id }
}

enum class BadgeVisualState {
    EARNED,
    REQUIREMENT_MET_PRO_LOCKED,
    LOCKED_PROGRESS,
    LOCKED
}

fun BadgeWallItem.visualState(): BadgeVisualState {
    return when {
        unlocked -> BadgeVisualState.EARNED
        access == AchievementAccess.PRO && requirementMet -> BadgeVisualState.REQUIREMENT_MET_PRO_LOCKED
        progressStarted() -> BadgeVisualState.LOCKED_PROGRESS
        else -> BadgeVisualState.LOCKED
    }
}

private fun BadgeWallItem.progressStarted(): Boolean {
    val count = countProgress
    if (count != null && count.current > 0) return true
    val volume = volumeProgress
    return volume != null && volume.currentKg > 0.0
}

object BadgeWallPresenter {
    private val categoryOrder = listOf(
        AchievementCategory.CONSISTENCY,
        AchievementCategory.JOURNEY,
        AchievementCategory.GOALS,
        AchievementCategory.PERFORMANCE,
        AchievementCategory.SPECIAL
    )

    fun present(
        board: AchievementBoard,
        selectedFilter: AchievementAccess? = null
    ): BadgeWallPresentation {
        val catalog = board.items
        val filters = listOf(AchievementAccess.FREE, AchievementAccess.PRO, AchievementAccess.SPECIAL)
            .filter { access -> catalog.any { it.access == access } }
        val active = selectedFilter?.takeIf { it in filters }
        val visible = if (active == null) catalog else catalog.filter { it.access == active }
        val earned = visible.count { it.unlocked }
        val total = visible.size
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
            nextCountMilestone(board.items) { it.workoutCountTarget },
            nextCountMilestone(board.items) { it.streakWeeks },
            nextCountMilestone(board.items) { it.prHunterTarget },
            nextCountMilestone(board.items) { it.masterySetTarget },
            nextVolumeMilestone(board.items),
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
            .firstOrNull { !it.unlocked && !it.requirementMet }
            ?: return null
        val progress = next.countProgress ?: return null
        if (progress.current <= 0 || progress.threshold <= 0) return null
        if (progress.current >= progress.threshold) return null
        return AlmostThereEntry(
            achievementId = next.id,
            fraction = (progress.current.toDouble() / progress.threshold.toDouble()).coerceIn(0.0, 1.0),
            countProgress = progress
        )
    }

    private fun nextVolumeMilestone(items: List<BadgeWallItem>): AlmostThereEntry? {
        val next = items.firstOrNull { item ->
            item.id.volumeThresholdKg != null && !item.unlocked && !item.requirementMet
        } ?: return null
        val progress = next.volumeProgress ?: return null
        if (progress.currentKg <= 0.0 || progress.thresholdKg <= 0.0 || progress.met) return null
        return AlmostThereEntry(
            achievementId = next.id,
            fraction = (progress.currentKg / progress.thresholdKg).coerceIn(0.0, 1.0),
            volumeProgress = progress
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
        val leftWorkouts = left.achievementId.workoutCountTarget != null
        val rightWorkouts = right.achievementId.workoutCountTarget != null
        val leftStreak = left.achievementId.streakWeeks != null
        val rightStreak = right.achievementId.streakWeeks != null
        val leftHunter = left.achievementId.prHunterTarget != null
        val rightHunter = right.achievementId.prHunterTarget != null
        val leftMastery = left.achievementId.masterySetTarget != null
        val rightMastery = right.achievementId.masterySetTarget != null
        return (leftWorkouts && rightWorkouts) ||
            (leftStreak && rightStreak) ||
            (leftHunter && rightHunter) ||
            (leftMastery && rightMastery)
    }

    private const val MAX_ALMOST_THERE = 3
}
