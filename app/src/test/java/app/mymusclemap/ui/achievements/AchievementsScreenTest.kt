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
import app.mymusclemap.domain.achievements.AchievementCategory
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
        composeRule.onNodeWithText(testString(R.string.badge_wall_section_progress, 4, total)).assertIsDisplayed()
        composeRule.onAllNodesWithTag("badge-filter-PERFORMANCE").assertCountEquals(0)
        composeRule.onAllNodesWithTag("badge-section-PERFORMANCE").assertCountEquals(0)
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
    fun selectingGoalsShowsOnlyThatCollection() {
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 12,
            unlocks = emptyList(),
            events = emptyList()
        )
        composeRule.setContent {
            var filter by remember { mutableStateOf<AchievementCategory?>(null) }
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                AchievementsScreen(
                    presentation = BadgeWallPresenter.present(board, filter),
                    onBack = {},
                    onFilterSelected = { filter = it }
                )
            }
        }
        val total = AchievementId.entries.size
        composeRule.onNodeWithTag("badge-section-CONSISTENCY").assertIsDisplayed()
        composeRule.onNodeWithTag("badge-filter-GOALS").performClick()
        composeRule.onNodeWithTag("badge-section-GOALS").assertIsDisplayed()
        composeRule.onAllNodesWithTag("badge-section-CONSISTENCY").assertCountEquals(0)
        composeRule.onAllNodesWithTag("badge-section-JOURNEY").assertCountEquals(0)
        composeRule.onNodeWithText(testString(R.string.badge_wall_section_progress, 0, total)).assertIsDisplayed()
        composeRule.onNodeWithTag("achievement-TARGET_WEIGHT_REACHED").assertIsDisplayed()
        composeRule.onAllNodesWithTag("achievement-WORKOUTS_5").assertCountEquals(0)
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
