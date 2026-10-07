package app.mymusclemap.ui.achievements

import app.mymusclemap.R
import app.mymusclemap.testQuantity
import app.mymusclemap.testString
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.AchievementAccess
import app.mymusclemap.domain.achievements.AchievementCatalog
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.BadgeWallPresenter
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.achievements.UnlockSnapshot
import app.mymusclemap.domain.achievements.WorkoutCountEvaluator
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.dashboard.NextAchievementLine
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class AchievementsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wallShowsEveryWorkoutMilestoneAndMutedProgressForTheNextOne() {
        val unlockedAt = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli()
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 73,
            unlocks = listOf(
                UnlockSnapshot(AchievementId.WORKOUTS_5, unlockedAt, celebratedAt = unlockedAt, triggerClientWorkoutId = null),
                UnlockSnapshot(AchievementId.WORKOUTS_10, unlockedAt, celebratedAt = unlockedAt, triggerClientWorkoutId = null),
                UnlockSnapshot(AchievementId.WORKOUTS_30, unlockedAt, celebratedAt = unlockedAt, triggerClientWorkoutId = null),
                UnlockSnapshot(AchievementId.WORKOUTS_50, unlockedAt, celebratedAt = unlockedAt, triggerClientWorkoutId = null)
            ),
            events = emptyList()
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                AchievementsScreen(presentation = BadgeWallPresenter.present(board), onBack = {})
            }
        }
        val total = AchievementId.entries.size
        composeRule.onNodeWithTag("badge-summary-count").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-ALL").assertIsSelected()
        composeRule.onNodeWithText(testString(R.string.badge_wall_section_progress, 4, total)).assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-FREE").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-PRO").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-SPECIAL").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-section-PERFORMANCE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_first_pr_name)).performScrollTo().assertIsDisplayed()
        AchievementId.entries.forEach { id ->
            composeRule.onNodeWithTag("achievement-${id.name}").performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithTag("achievement-badge-unlocked-WORKOUTS_50", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("achievement-badge-locked-WORKOUTS_100", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithTag("badge-almost-WORKOUTS_100").assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.achievements_progress_count, 73, 100)).assertCountEquals(2)
        composeRule.onAllNodesWithText(testString(R.string.achievements_progress_count, 73, 200)).assertCountEquals(0)
        composeRule.onAllNodesWithTag("achievement-badge-locked-TARGET_WEIGHT_REACHED", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onNodeWithText(testString(R.string.achievement_on_target_name)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_first_step_name))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.achievement_first_step_requirement)).assertCountEquals(0)
    }

    @Test
    fun earnedJourneyShowsTheDateWithoutWorkoutProgress() {
        val unlockedAt = Instant.parse("2026-09-02T08:00:00Z").toEpochMilli()
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 12,
            unlocks = listOf(
                UnlockSnapshot(
                    AchievementId.FIRST_WORKOUT,
                    unlockedAt,
                    celebratedAt = unlockedAt,
                    triggerClientWorkoutId = null
                )
            ),
            events = emptyList()
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                AchievementsScreen(
                    presentation = BadgeWallPresenter.present(board),
                    onBack = {},
                    selectedBadgeId = AchievementId.FIRST_WORKOUT
                )
            }
        }
        val earned = Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        composeRule.onNodeWithTag("badge-detail-earned-date").assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.achievements_unlocked_on, UiFormatters.compactDate(earned))
        ).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_first_step_requirement)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.achievements_progress_count, 1, 1)).assertCountEquals(0)
        composeRule.onNodeWithText(testString(R.string.badge_wall_earned)).assertIsDisplayed()
    }

    @Test
    fun onTargetShowsItsOwnCopyAndEarnedDate() {
        val unlockedAt = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli()
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = listOf(
                UnlockSnapshot(
                    AchievementId.TARGET_WEIGHT_REACHED,
                    unlockedAt,
                    celebratedAt = unlockedAt,
                    triggerClientWorkoutId = null
                )
            ),
            events = emptyList()
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                AchievementsScreen(
                    presentation = BadgeWallPresenter.present(board),
                    onBack = {},
                    selectedBadgeId = AchievementId.TARGET_WEIGHT_REACHED
                )
            }
        }
        composeRule.onAllNodesWithTag("achievement-badge-unlocked-TARGET_WEIGHT_REACHED", useUnmergedTree = true)
            .assertCountEquals(2)
        val earned = Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        composeRule.onNodeWithText(
            testString(R.string.achievements_unlocked_on, UiFormatters.compactDate(earned))
        ).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.badge_wall_earned)).assertIsDisplayed()
    }

    @Test
    fun reachingTargetAndTheLifetimeAwardShareOneDialog() {
        val reached = PendingCelebration.TargetWeightMilestone(
            milestone = app.mymusclemap.domain.achievements.WeightMilestone.REACHED,
            includesLifetimeUnlock = true,
            acknowledgement = CelebrationAcknowledgement(
                progressEventKey = "weight-goal:1:reached",
                achievementId = AchievementId.TARGET_WEIGHT_REACHED.name
            )
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                CelebrationDialog(celebrations = listOf(reached), onDismiss = {})
            }
        }
        composeRule.onNodeWithText(testString(R.string.achievement_on_target_name)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.celebration_weight_reached_body)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.celebration_weight_reached_title)).assertCountEquals(0)
    }

    @Test
    fun streakRowsShowCurrentProgressAndLeaveEarnedTiersDated() {
        val unlockedAt = Instant.parse("2026-10-05T00:00:00Z").toEpochMilli()
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = listOf(
                UnlockSnapshot(
                    AchievementId.WEEKLY_GOAL_STREAK_4,
                    unlockedAt,
                    celebratedAt = unlockedAt,
                    triggerClientWorkoutId = null
                )
            ),
            events = emptyList(),
            currentStreak = 6
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                AchievementsScreen(
                    presentation = BadgeWallPresenter.present(board),
                    onBack = {},
                    selectedBadgeId = AchievementId.WEEKLY_GOAL_STREAK_4
                )
            }
        }
        composeRule.onNodeWithTag("badge-detail-title").assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.achievement_streak_name, 4)).assertCountEquals(2)
        composeRule.onAllNodesWithText(testString(R.string.achievements_progress_count, 6, 8)).assertCountEquals(2)
        composeRule.onAllNodesWithText(testString(R.string.achievements_progress_count, 6, 12)).assertCountEquals(0)
        composeRule.onNodeWithText(testString(R.string.badge_wall_tier_bronze)).assertIsDisplayed()
        val earned = Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        composeRule.onNodeWithText(
            testString(R.string.achievements_unlocked_on, UiFormatters.compactDate(earned))
        ).assertIsDisplayed()
    }

    @Test
    fun selectingProShowsOnlyProAchievementsAndChangesTheSummary() {
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 12,
            unlocks = emptyList(),
            events = emptyList()
        )
        composeRule.setContent {
            var filter by remember { mutableStateOf<AchievementAccess?>(null) }
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                AchievementsScreen(
                    presentation = BadgeWallPresenter.present(board, filter),
                    onBack = {},
                    onFilterSelected = { filter = it }
                )
            }
        }
        val total = AchievementId.entries.size
        composeRule.onNodeWithTag("badge-filter-ALL").assertIsSelected()
        composeRule.onNodeWithTag("badge-filter-FREE").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-PRO").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-SPECIAL").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-section-CONSISTENCY").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-PRO").performClick()
        composeRule.onNodeWithTag("badge-filter-PRO").assertIsSelected()
        composeRule.onNodeWithTag("badge-section-CONSISTENCY").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-section-PERFORMANCE").assertIsDisplayed()
        composeRule.onAllNodesWithTag("badge-section-JOURNEY").assertCountEquals(0)
        composeRule.onAllNodesWithTag("badge-section-GOALS").assertCountEquals(0)
        composeRule.onNodeWithText(
            testString(R.string.badge_wall_section_progress, 0, AchievementCatalog.pro.size)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("achievement-IRON_DISCIPLINE").assertIsDisplayed()
        composeRule.onAllNodesWithTag("achievement-FOUNDER").assertCountEquals(0)
        composeRule.onNodeWithTag("badge-filter-SPECIAL").performClick()
        composeRule.onNodeWithTag("badge-filter-SPECIAL").assertIsSelected()
        composeRule.onNodeWithTag("achievement-FOUNDER").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-special-FOUNDER", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("badge-pro-FOUNDER", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("achievement-progress-FOUNDER").assertCountEquals(0)
        composeRule.onAllNodesWithTag("achievement-VOLUME_MASTER").assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.badge_wall_unlock_with_pro)).assertCountEquals(0)
        composeRule.onNodeWithTag("badge-summary-count").assertTextEquals(
            testString(R.string.badge_wall_section_progress, 0, AchievementCatalog.special.size)
        )
        composeRule.onNodeWithTag("badge-filter-PRO").performClick()
        composeRule.onNodeWithTag("achievement-IRON_DISCIPLINE").assertIsDisplayed()
        composeRule.onNodeWithTag("achievement-VOLUME_MASTER").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-pro-VOLUME_MASTER", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("achievement-WORKOUTS_5").assertCountEquals(0)
        composeRule.onNodeWithTag("badge-filter-FREE").performClick()
        composeRule.onAllNodesWithTag("achievement-VOLUME_MASTER").assertCountEquals(0)
        composeRule.onNodeWithTag("achievement-WORKOUTS_5").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-ALL").performClick()
        composeRule.onNodeWithTag("badge-filter-ALL").assertIsSelected()
        composeRule.onNodeWithText(testString(R.string.badge_wall_section_progress, 0, total)).assertIsDisplayed()
        composeRule.onNodeWithTag("achievement-VOLUME_MASTER").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun founderDetailIsBinaryAndDoesNotOfferPro() {
        var openedPro = false
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = emptyList(),
            events = emptyList()
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                AchievementsScreen(
                    presentation = BadgeWallPresenter.present(board),
                    onBack = {},
                    onOpenPro = { openedPro = true },
                    selectedBadgeId = AchievementId.FOUNDER
                )
            }
        }
        composeRule.onNodeWithTag("badge-detail-requirement").assertTextEquals(
            testString(R.string.achievement_founder_requirement)
        )
        composeRule.onNodeWithTag("badge-detail-state").assertTextEquals(testString(R.string.badge_wall_locked))
        composeRule.onAllNodesWithTag("badge-detail-pro-lock").assertCountEquals(0)
        composeRule.onAllNodesWithTag("badge-detail-progress").assertCountEquals(0)
        composeRule.onAllNodesWithTag("badge-detail-earned-date").assertCountEquals(0)
        composeRule.onAllNodesWithTag("badge-pro-teaser").assertCountEquals(0)
        composeRule.onAllNodesWithTag("badge-pro-FOUNDER", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("badge-special-FOUNDER", useUnmergedTree = true).assertCountEquals(2)
        assertFalse(openedPro)
    }

    @Test
    fun realisticProgressKeepsEarnedAndLockedArtworkDistinctInLight() {
        realisticProgress(dark = false)
    }

    @Test
    fun realisticProgressKeepsEarnedAndLockedArtworkDistinctInDark() {
        realisticProgress(dark = true)
    }

    private fun realisticProgress(dark: Boolean) {
        val unlockedAt = 1L
        fun unlock(id: AchievementId) = UnlockSnapshot(id, unlockedAt, unlockedAt, null)
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 28,
            unlocks = listOf(
                unlock(AchievementId.WORKOUTS_5),
                unlock(AchievementId.WORKOUTS_10),
                unlock(AchievementId.FIRST_WORKOUT),
                unlock(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN),
                unlock(AchievementId.WEEKLY_GOAL_STREAK_4)
            ),
            events = emptyList(),
            currentStreak = 4
        )
        val presentation = BadgeWallPresenter.present(board)
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = dark) {
                AchievementsScreen(presentation = presentation, onBack = {})
            }
        }
        composeRule.onNodeWithText(testString(R.string.badge_wall_section_progress, 5, AchievementId.entries.size))
            .assertIsDisplayed()
            listOf(
                AchievementId.WORKOUTS_5,
                AchievementId.WORKOUTS_10,
                AchievementId.FIRST_WORKOUT,
                AchievementId.FIRST_CUSTOM_WORKOUT_PLAN,
                AchievementId.WEEKLY_GOAL_STREAK_4
            ).forEach { id ->
                composeRule.onNodeWithTag("achievement-${id.name}").performScrollTo().assertIsDisplayed()
                composeRule.onAllNodesWithTag("achievement-badge-unlocked-${id.name}", useUnmergedTree = true)
                    .assertCountEquals(1)
            }
            listOf(
                AchievementId.WORKOUTS_50,
                AchievementId.WORKOUTS_100,
                AchievementId.WORKOUTS_200,
                AchievementId.FIRST_MONTHLY_REPORT,
                AchievementId.TARGET_WEIGHT_REACHED,
                AchievementId.WEEKLY_GOAL_STREAK_12
            ).forEach { id ->
                composeRule.onAllNodesWithTag("achievement-badge-unlocked-${id.name}", useUnmergedTree = true)
                    .assertCountEquals(0)
                composeRule.onAllNodesWithTag("achievement-badge-locked-${id.name}", useUnmergedTree = true)
                    .assertCountEquals(1)
            }
            composeRule.onNodeWithTag("badge-almost-WORKOUTS_30").assertIsDisplayed()
            composeRule.onNodeWithTag("badge-almost-WEEKLY_GOAL_STREAK_8").assertIsDisplayed()
            composeRule.onAllNodesWithTag("achievement-badge-locked-WORKOUTS_30", useUnmergedTree = true)
                .assertCountEquals(2)
            composeRule.onAllNodesWithText(testString(R.string.achievements_progress_count, 28, 30))
                .assertCountEquals(2)
            composeRule.onAllNodesWithText(testString(R.string.achievements_progress_count, 4, 8))
                .assertCountEquals(2)
    }

    @Test
    fun columnsFollowAvailableWidth() {
        assertEquals(3, badgeColumnCount(320.dp))
        assertEquals(3, badgeColumnCount(360.dp))
        assertEquals(4, badgeColumnCount(480.dp))
        assertTrue(badgeColumnCount(700.dp) >= 6)
    }

    @Test
    fun lockedWorkoutDetailShowsRequirementAndProgress() {
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 28,
            unlocks = listOf(
                UnlockSnapshot(AchievementId.WORKOUTS_5, 1L, 1L, null),
                UnlockSnapshot(AchievementId.WORKOUTS_10, 1L, 1L, null)
            ),
            events = emptyList()
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                AchievementsScreen(
                    presentation = BadgeWallPresenter.present(board),
                    onBack = {},
                    selectedBadgeId = AchievementId.WORKOUTS_30
                )
            }
        }
        composeRule.onNodeWithTag("badge-detail").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-detail-title").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-detail-requirement").assertTextEquals(
            testString(R.string.achievements_workouts_requirement, 30)
        )
        composeRule.onNodeWithText(testString(R.string.badge_wall_locked), substring = false).assertIsDisplayed()
        composeRule.onNodeWithTag("badge-detail-progress").assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.achievements_progress_count, 28, 30)).assertCountEquals(3)
    }

    @Test
    fun overviewNextLineShowsTheFollowingWorkoutMilestone() {
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                NextAchievementLine(
                    milestone = WorkoutCountEvaluator.next(73),
                    onClick = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.achievements_next)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_workouts_name, 100)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_progress_count, 73, 100)).assertIsDisplayed()
    }

    @Test
    fun historySummaryIsOneDialogRatherThanOnePopupPerBadge() {
        var dismissed = 0
        val summary = PendingCelebration.HistoryRecognized(
            badgeCount = 5,
            acknowledgement = CelebrationAcknowledgement(progressEventKey = "history-recognized")
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = false) {
                CelebrationDialog(celebrations = listOf(summary), onDismiss = { dismissed += 1 })
            }
        }
        composeRule.onNodeWithText(testString(R.string.celebration_history_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testQuantity(R.plurals.celebration_history_body, 5)).assertIsDisplayed()
        composeRule.onNodeWithTag(CELEBRATION_CONFIRM).performClick()
        assertEquals(1, dismissed)
    }
}
