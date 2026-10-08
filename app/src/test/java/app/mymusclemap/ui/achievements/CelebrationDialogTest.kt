package app.mymusclemap.ui.achievements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CelebrationDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun plannerUnlockShowsArtworkNameAndRequirement() {
        var dismissed = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                CelebrationDialog(
                    celebrations = listOf(planner()),
                    onDismiss = { dismissed += 1 }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.celebration_unlocked_heading)).assertIsDisplayed()
        composeRule.onNodeWithTag("celebration-badge-${AchievementId.FIRST_CUSTOM_WORKOUT_PLAN.name}").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_planner_name)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_planner_requirement)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.celebration_awesome)).assertIsDisplayed()
        assertEquals(0, dismissed)
        composeRule.onNodeWithTag(CELEBRATION_CONFIRM).performClick()
        assertEquals(1, dismissed)
        composeRule.onNodeWithTag(CELEBRATION_CONFIRM).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun removingTheDialogWithoutAUserActionDoesNotAcknowledge() {
        var show by mutableStateOf(true)
        var dismissed = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                if (show) {
                    CelebrationDialog(
                        celebrations = listOf(planner()),
                        onDismiss = { dismissed += 1 }
                    )
                }
            }
        }
        composeRule.onNodeWithTag(CELEBRATION_DIALOG).assertIsDisplayed()
        show = false
        composeRule.waitForIdle()
        assertEquals(0, dismissed)
    }

    @Test
    fun multiplePendingAchievementsStayInOneDialog() {
        var dismissed = 0
        val onTarget = PendingCelebration.TargetWeightMilestone(
            milestone = app.mymusclemap.domain.achievements.WeightMilestone.REACHED,
            includesLifetimeUnlock = true,
            acknowledgement = CelebrationAcknowledgement(
                progressEventKey = "weight-goal:1:reached",
                achievementId = AchievementId.TARGET_WEIGHT_REACHED.name
            )
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                CelebrationDialog(
                    celebrations = listOf(planner(), onTarget),
                    onDismiss = { dismissed += 1 }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.achievement_planner_name)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_on_target_name)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.celebration_weight_reached_body)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CELEBRATION_CONFIRM).performClick()
        assertEquals(1, dismissed)
    }

    private fun planner(): PendingCelebration.JourneyUnlocked {
        return PendingCelebration.JourneyUnlocked(
            achievementId = AchievementId.FIRST_CUSTOM_WORKOUT_PLAN,
            triggerClientWorkoutId = null,
            acknowledgement = CelebrationAcknowledgement(
                achievementId = AchievementId.FIRST_CUSTOM_WORKOUT_PLAN.name
            )
        )
    }
}
