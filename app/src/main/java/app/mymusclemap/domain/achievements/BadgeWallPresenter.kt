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

data class OverviewLatestUnlock(
    val id: AchievementId,
    val unlockedAtEpochMillis: Long
)

/**
 * Progress the home card can show without inventing a percentage.
 * [Requirement] is an action the model does not measure as a ratio.
 */
sealed interface OverviewProgress {
    data class Count(val current: Int, val threshold: Int) : OverviewProgress
    data class Volume(val currentKg: Double, val thresholdKg: Double) : OverviewProgress
    data class TargetWeight(val remainingKg: Double, val fraction: Double) : OverviewProgress
    data object Requirement : OverviewProgress
}

data class OverviewUpcoming(
    val id: AchievementId,
    val progress: OverviewProgress
)

data class OverviewAchievements(
    val latest: OverviewLatestUnlock?,
    val upcoming: OverviewUpcoming?
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
        // A completed Pro requirement without active Pro uses the ordinary locked look.
        access == AchievementAccess.PRO && requirementMet -> BadgeVisualState.LOCKED
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
        AchievementCategory.HIDDEN_GEMS,
        AchievementCategory.SPECIAL
    )

    fun present(
        board: AchievementBoard,
        selectedFilter: AchievementAccess? = null
    ): BadgeWallPresentation {
        val catalog = board.items.filter { it.id.listed(it.unlocked) }
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
     * Home-screen card. Latest unlock is the stored earned instant.
     * The next milestone reuses [almostThere] when any eligible award has measurable progress,
     * then a single action award in catalog order. Locked secrets are never recommended.
     */
    fun overview(board: AchievementBoard): OverviewAchievements {
        return OverviewAchievements(
            latest = latestUnlock(board),
            upcoming = upcoming(board)
        )
    }

    /**
     * At most one next milestone per measurable family, ranked by completion.
     * Binary Journey awards are not candidates.
     */
    fun almostThere(board: AchievementBoard): List<AlmostThereEntry> {
        val visible = board.items.filter { it.id.listed(it.unlocked) && !it.id.secret }
        val candidates = listOfNotNull(
            nextCountMilestone(visible) { it.workoutCountTarget },
            nextCountMilestone(visible) { it.streakWeeks },
            nextCountMilestone(visible) { it.prHunterTarget },
            nextCountMilestone(visible) { it.masterySetTarget },
            nextVolumeMilestone(visible),
            targetWeightMilestone(board)
        )
        return candidates.sortedWith(almostThereOrder()).take(MAX_ALMOST_THERE)
    }

    private fun latestUnlock(board: AchievementBoard): OverviewLatestUnlock? {
        val earned = board.items.filter { it.unlocked && it.unlockedAt != null }
        val latest = earned.maxWithOrNull(
            compareBy<BadgeWallItem> { it.unlockedAt!! }
                .thenBy { AchievementId.entries.indexOf(it.id) }
        ) ?: return null
        return OverviewLatestUnlock(
            id = latest.id,
            unlockedAtEpochMillis = latest.unlockedAt!!
        )
    }

    private fun upcoming(board: AchievementBoard): OverviewUpcoming? {
        val measurable = almostThere(board).firstOrNull { entry ->
            val item = board.items.firstOrNull { it.id == entry.achievementId } ?: return@firstOrNull false
            canRecommend(item, board.grantsPro)
        }
        if (measurable != null) {
            return OverviewUpcoming(
                id = measurable.achievementId,
                progress = when {
                    measurable.countProgress != null -> OverviewProgress.Count(
                        current = measurable.countProgress.current,
                        threshold = measurable.countProgress.threshold
                    )
                    measurable.volumeProgress != null -> OverviewProgress.Volume(
                        currentKg = measurable.volumeProgress.currentKg,
                        thresholdKg = measurable.volumeProgress.thresholdKg
                    )
                    measurable.remainingKg != null -> OverviewProgress.TargetWeight(
                        remainingKg = measurable.remainingKg,
                        fraction = measurable.fraction
                    )
                    else -> OverviewProgress.Requirement
                }
            )
        }
        val action = board.items
            .filter { canRecommend(it, board.grantsPro) && isActionBased(it) }
            .minByOrNull { AchievementId.entries.indexOf(it.id) }
            ?: return null
        return OverviewUpcoming(id = action.id, progress = OverviewProgress.Requirement)
    }

    private fun canRecommend(item: BadgeWallItem, grantsPro: Boolean): Boolean {
        if (item.unlocked || item.requirementMet) return false
        if (item.id.secret) return false
        if (!item.id.listed(item.unlocked)) return false
        if (item.access == AchievementAccess.SPECIAL) return false
        if (item.access == AchievementAccess.PRO && !grantsPro) return false
        return true
    }

    /** No count or volume ratio. Target weight without an active goal is treated as an action. */
    private fun isActionBased(item: BadgeWallItem): Boolean {
        return item.countProgress == null && item.volumeProgress == null
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
