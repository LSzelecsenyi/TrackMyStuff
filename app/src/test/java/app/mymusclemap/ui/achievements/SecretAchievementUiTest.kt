package app.mymusclemap.ui.achievements

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.AchievementAccess
import app.mymusclemap.domain.achievements.AchievementCatalog
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.BadgeWallPresenter
import app.mymusclemap.domain.achievements.UnlockSnapshot
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecretAchievementUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lockedSecretCopyHidesTheRealNameAndRequirement() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Column {
                    Text(achievementTitle(AchievementId.SILENT_NIGHT, revealed = false))
                    Text(achievementTitle(AchievementId.TRICK_OR_LIFT, revealed = false))
                    Text(achievementRequirement(AchievementId.TRICK_OR_LIFT, revealed = false))
                    Text(achievementRequirement(AchievementId.FRIDAY_THE_STRONGTEENTH, revealed = false))
                    Text(achievementTitle(AchievementId.TRIPLE_CROWN, revealed = true))
                    Text(achievementRequirement(AchievementId.ONE_MORE_THING, revealed = true))
                }
            }
        }
        composeRule.onAllNodesWithText("???").assertCountEquals(2)
        composeRule.onAllNodesWithText("A secret achievement discovered through training.").assertCountEquals(2)
        composeRule.onNodeWithText("Triple Crown").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Finish every planned set, then complete one more set on an exercise in that plan."
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Silent Night, Heavy Weights").assertCountEquals(0)
        composeRule.onAllNodesWithText("Friday the Strongteenth").assertCountEquals(0)
        composeRule.onAllNodesWithText("Complete a workout between December 24 and 26.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Trick or Lift").assertCountEquals(0)
        composeRule.onAllNodesWithText("Complete a workout on October 31.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Complete a workout on Friday the 13th.").assertCountEquals(0)
    }

    @Test
    fun secretsStayInTheFreeCatalogAndOutOfAlmostThere() {
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 4,
            unlocks = listOf(
                UnlockSnapshot(AchievementId.NEW_YEAR_SAME_ME, 20L, 20L, null)
            ),
            events = emptyList()
        )
        val presentation = BadgeWallPresenter.present(board)
        assertTrue(presentation.catalog.any { it.id == AchievementId.SILENT_NIGHT && !it.unlocked })
        assertTrue(presentation.catalog.single { it.id == AchievementId.NEW_YEAR_SAME_ME }.unlocked)
        assertTrue(presentation.almostThere.none { it.achievementId.secret })
        assertEquals(
            AchievementCatalog.free.size,
            BadgeWallPresenter.present(board, AchievementAccessFilter).totalCount
        )
        val lockedArt = AchievementCatalog.secrets.map {
            BadgeArtworkResolver.drawableForWall(it, unlocked = false)
        }
        assertEquals(listOf(R.drawable.hidden_gem_placeholder), lockedArt.toSet().toList())
        assertEquals(
            R.drawable.trick_or_lift_badge,
            BadgeArtworkResolver.drawableForWall(AchievementId.TRICK_OR_LIFT, unlocked = true)
        )
    }

    @Test
    fun anUnlockedSecretCelebrationShowsItsArtworkAndName() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                CelebrationDialog(
                    celebrations = listOf(
                        app.mymusclemap.domain.achievements.PendingCelebration.SecretUnlocked(
                            achievementId = AchievementId.TRICK_OR_LIFT,
                            triggerClientWorkoutId = "halloween",
                            acknowledgement = app.mymusclemap.domain.achievements.CelebrationAcknowledgement(
                                achievementId = AchievementId.TRICK_OR_LIFT.name
                            )
                        )
                    ),
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Trick or Lift").assertIsDisplayed()
        composeRule.onNodeWithText("Complete a workout on October 31.").assertIsDisplayed()
        composeRule.onNodeWithTag("celebration-badge-${AchievementId.TRICK_OR_LIFT.name}").assertIsDisplayed()
        composeRule.onAllNodesWithText("???").assertCountEquals(0)
    }

    @Test
    fun lockedHalloweenStaysHiddenOnTheWallAndUnlockedChristmasShowsItsArtwork() {
        val unlockedAt = 1_700_000_000_000L
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 1,
            unlocks = listOf(
                UnlockSnapshot(AchievementId.SILENT_NIGHT, unlockedAt, unlockedAt, null)
            ),
            events = emptyList()
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                AchievementsScreen(
                    presentation = BadgeWallPresenter.present(board, AchievementAccess.FREE),
                    onBack = {},
                    selectedBadgeId = AchievementId.TRICK_OR_LIFT
                )
            }
        }
        composeRule.onAllNodesWithText("Hidden Gems").assertCountEquals(2)
        composeRule.onNodeWithText("Silent Night, Heavy Weights").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Trick or Lift").assertCountEquals(0)
        composeRule.onAllNodesWithText("Complete a workout on October 31.").assertCountEquals(0)
        composeRule.onNodeWithText("A secret achievement discovered through training.").assertIsDisplayed()
        composeRule.onAllNodesWithTag(
            "achievement-badge-locked-${AchievementId.TRICK_OR_LIFT.name}",
            useUnmergedTree = true
        ).assertCountEquals(2)
        composeRule.onAllNodesWithTag(
            "achievement-badge-unlocked-${AchievementId.SILENT_NIGHT.name}",
            useUnmergedTree = true
        ).assertCountEquals(1)
    }

    private val AchievementAccessFilter = AchievementAccess.FREE
}
