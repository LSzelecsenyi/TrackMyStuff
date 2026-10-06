package app.mymusclemap.ui.achievements

import app.mymusclemap.R
import app.mymusclemap.testQuantity
import app.mymusclemap.testString
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.AchievementCategory
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.achievements.UnlockSnapshot
import app.mymusclemap.domain.achievements.WorkoutCountEvaluator
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.dashboard.NextAchievementLine
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
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
                AchievementsScreen(items = board.items, onBack = {})
            }
        }
        AchievementId.entries.forEach { id ->
            composeRule.onNodeWithTag("achievement-${id.name}").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("achievement-badge-unlocked-WORKOUTS_50").assertIsDisplayed()
        composeRule.onNodeWithTag("achievement-badge-locked-WORKOUTS_100").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("achievement-detail-WORKOUTS_100").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_progress_count, 73, 100)).assertIsDisplayed()
        val earned = Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        composeRule.onAllNodesWithText(
            testString(R.string.achievements_unlocked_on, UiFormatters.compactDate(earned))
        ).assertCountEquals(4)
        listOf(
            AchievementCategory.PERFORMANCE,
            AchievementCategory.JOURNEY
        ).forEach { category ->
            composeRule.onNodeWithTag("achievements-empty-${category.name}")
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag("achievement-badge-locked-TARGET_WEIGHT_REACHED")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_on_target_name)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_on_target_requirement)).assertIsDisplayed()
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
                AchievementsScreen(items = board.items, onBack = {})
            }
        }
        composeRule.onNodeWithTag("achievement-badge-unlocked-TARGET_WEIGHT_REACHED")
            .performScrollTo()
            .assertIsDisplayed()
        val earned = Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        composeRule.onNodeWithText(
            testString(R.string.achievements_unlocked_on, UiFormatters.compactDate(earned))
        ).assertIsDisplayed()
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
