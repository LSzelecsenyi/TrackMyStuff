package app.mymusclemap.domain.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeWallPresenterTest {
    @Test
    fun summaryCountsOnlyTheCurrentCatalog() {
        val empty = BadgeWallPresenter.present(board(completed = 0))
        assertEquals(0, empty.earnedCount)
        assertEquals(AchievementId.entries.size, empty.totalCount)
        assertEquals(0f, empty.overallFraction)
        val earned = BadgeWallPresenter.present(
            board(completed = 12, unlocks = listOf(AchievementId.WORKOUTS_5, AchievementId.FIRST_WORKOUT))
        )
        assertEquals(2, earned.earnedCount)
        assertEquals(AchievementId.entries.size, earned.totalCount)
        assertEquals(2f / AchievementId.entries.size, earned.overallFraction)
    }

    @Test
    fun emptyCategoriesAreNotFiltersOrAchievements() {
        val presentation = BadgeWallPresenter.present(board(completed = 0))
        assertFalse(presentation.filters.contains(AchievementCategory.PERFORMANCE))
        assertTrue(presentation.sections.none { it.category == AchievementCategory.PERFORMANCE })
        assertEquals(
            listOf(
                AchievementCategory.CONSISTENCY,
                AchievementCategory.JOURNEY,
                AchievementCategory.GOALS
            ),
            presentation.filters
        )
        assertEquals(AchievementId.entries.map { it }, presentation.catalog.map { it.id })
        assertFalse(presentation.catalog.any { it.id.name == "FIRST_PROGRESS_PHOTO" })
        assertFalse(AchievementId.entries.any { it.name.contains("VOLUME") || it.name.contains("PR") })
    }

    @Test
    fun filterChangesTheCollectionWithoutChangingTheSummary() {
        val source = board(completed = 8, unlocks = listOf(AchievementId.WORKOUTS_5))
        val all = BadgeWallPresenter.present(source, null)
        val journey = BadgeWallPresenter.present(source, AchievementCategory.JOURNEY)
        assertEquals(all.earnedCount, journey.earnedCount)
        assertEquals(all.totalCount, journey.totalCount)
        assertEquals(listOf(AchievementCategory.JOURNEY), journey.sections.map { it.category })
        assertTrue(journey.sections.single().items.all { it.category == AchievementCategory.JOURNEY })
        assertEquals(AchievementCategory.JOURNEY, journey.selectedFilter)
        val unknown = BadgeWallPresenter.present(source, AchievementCategory.PERFORMANCE)
        assertNull(unknown.selectedFilter)
        assertEquals(all.sections.map { it.category }, unknown.sections.map { it.category })
    }

    @Test
    fun twentyEightWorkoutsShowsOnlyTheNextWorkoutMilestone() {
        val presentation = BadgeWallPresenter.present(
            board(
                completed = 28,
                unlocks = listOf(AchievementId.WORKOUTS_5, AchievementId.WORKOUTS_10)
            )
        )
        val workouts = presentation.almostThere.filter { it.achievementId.workoutThreshold != null }
        assertEquals(listOf(AchievementId.WORKOUTS_30), workouts.map { it.achievementId })
        assertEquals(28, workouts.single().countProgress!!.current)
        assertEquals(30, workouts.single().countProgress!!.threshold)
        assertFalse(
            presentation.almostThere.any {
                it.achievementId == AchievementId.WORKOUTS_50 ||
                    it.achievementId == AchievementId.WORKOUTS_100 ||
                    it.achievementId == AchievementId.WORKOUTS_200
            }
        )
    }

    @Test
    fun streakOfSixShowsTheEightWeekTierOnly() {
        val presentation = BadgeWallPresenter.present(
            board(
                completed = 6,
                streak = 6,
                unlocks = listOf(AchievementId.WEEKLY_GOAL_STREAK_4)
            )
        )
        val streaks = presentation.almostThere.filter { it.achievementId.streakWeeks != null }
        assertEquals(listOf(AchievementId.WEEKLY_GOAL_STREAK_8), streaks.map { it.achievementId })
        assertEquals(6, streaks.single().countProgress!!.current)
        assertEquals(8, streaks.single().countProgress!!.threshold)
        assertFalse(presentation.almostThere.any { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_12 })
    }

    @Test
    fun lockedOnTargetWithAnActiveGoalIsACandidate() {
        val presentation = BadgeWallPresenter.present(
            board(completed = 0, weight = weightProgress(0.4, remaining = 2.0))
        )
        val target = presentation.almostThere.single { it.achievementId == AchievementId.TARGET_WEIGHT_REACHED }
        assertEquals(0.4, target.fraction, 0.0001)
        assertEquals(2.0, target.remainingKg!!, 0.0001)
    }

    @Test
    fun earnedOnTargetIsNotAlmostThere() {
        val presentation = BadgeWallPresenter.present(
            board(
                completed = 0,
                unlocks = listOf(AchievementId.TARGET_WEIGHT_REACHED),
                weight = weightProgress(0.9, remaining = 1.0)
            )
        )
        assertTrue(presentation.almostThere.none { it.achievementId == AchievementId.TARGET_WEIGHT_REACHED })
    }

    @Test
    fun binaryJourneyAwardsAreExcluded() {
        val presentation = BadgeWallPresenter.present(board(completed = 4, streak = 3))
        assertTrue(
            presentation.almostThere.none {
                it.achievementId == AchievementId.FIRST_WORKOUT ||
                    it.achievementId == AchievementId.FIRST_CUSTOM_WORKOUT_PLAN ||
                    it.achievementId == AchievementId.FIRST_MONTHLY_REPORT
            }
        )
    }

    @Test
    fun candidatesAreRankedByNormalizedCompletion() {
        val presentation = BadgeWallPresenter.present(
            board(
                completed = 28,
                streak = 7,
                unlocks = listOf(
                    AchievementId.WORKOUTS_5,
                    AchievementId.WORKOUTS_10,
                    AchievementId.WEEKLY_GOAL_STREAK_4
                ),
                weight = weightProgress(0.78, remaining = 2.2)
            )
        )
        assertEquals(
            listOf(
                AchievementId.WORKOUTS_30,
                AchievementId.WEEKLY_GOAL_STREAK_8,
                AchievementId.TARGET_WEIGHT_REACHED
            ),
            presentation.almostThere.map { it.achievementId }
        )
        assertTrue(presentation.almostThere.size <= 3)
    }

    @Test
    fun aCompletedCatalogHasNoAlmostThereCandidate() {
        val earned = AchievementId.entries
        val presentation = BadgeWallPresenter.present(
            board(completed = 200, streak = 12, unlocks = earned, weight = weightProgress(1.0, remaining = 0.0))
        )
        assertEquals(earned.size, presentation.earnedCount)
        assertTrue(presentation.almostThere.isEmpty())
    }

    @Test
    fun noMeasurableProgressHidesAlmostThere() {
        val presentation = BadgeWallPresenter.present(board(completed = 0, streak = 0))
        assertTrue(presentation.almostThere.isEmpty())
    }

    @Test
    fun theSameStateAlwaysRanksTheSameWay() {
        val source = board(
            completed = 28,
            streak = 7,
            unlocks = listOf(AchievementId.WORKOUTS_5, AchievementId.WORKOUTS_10, AchievementId.WEEKLY_GOAL_STREAK_4),
            weight = weightProgress(0.78, remaining = 2.2)
        )
        assertEquals(
            BadgeWallPresenter.almostThere(source).map { it.achievementId },
            BadgeWallPresenter.almostThere(source).map { it.achievementId }
        )
    }

    @Test
    fun catalogOrderInsideASectionStaysAscending() {
        val goals = BadgeWallPresenter.present(board(completed = 0))
            .sections
            .single { it.category == AchievementCategory.GOALS }
            .items
            .map { it.id }
        assertEquals(
            listOf(
                AchievementId.TARGET_WEIGHT_REACHED,
                AchievementId.WEEKLY_GOAL_STREAK_4,
                AchievementId.WEEKLY_GOAL_STREAK_8,
                AchievementId.WEEKLY_GOAL_STREAK_12
            ),
            goals
        )
        val consistency = BadgeWallPresenter.present(board(completed = 40))
            .sections
            .single { it.category == AchievementCategory.CONSISTENCY }
            .items
            .map { it.id }
        assertEquals(AchievementCatalog.workoutCounts, consistency)
    }

    private fun board(
        completed: Int,
        streak: Int = 0,
        unlocks: List<AchievementId> = emptyList(),
        weight: TargetWeightProgress? = null
    ): AchievementBoard {
        return AchievementBoardAssembler.assemble(
            completedWorkoutCount = completed,
            unlocks = unlocks.map {
                UnlockSnapshot(it, unlockedAt = 10L, celebratedAt = 10L, triggerClientWorkoutId = null)
            },
            events = emptyList(),
            currentStreak = streak
        ).copy(targetWeightProgress = weight)
    }

    private fun weightProgress(fraction: Double, remaining: Double): TargetWeightProgress {
        return TargetWeightProgress(
            goalId = 1L,
            baselineKg = 80.0,
            currentKg = 78.0,
            targetKg = 70.0,
            direction = TargetWeightDirection.LOSS,
            totalDistanceKg = 10.0,
            progressedKg = 10.0 * fraction,
            remainingKg = remaining,
            progressFraction = fraction,
            reached = false
        )
    }
}
