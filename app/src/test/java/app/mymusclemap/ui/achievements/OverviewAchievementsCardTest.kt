package app.mymusclemap.ui.achievements

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.BadgeWallPresenter
import app.mymusclemap.domain.achievements.OverviewAchievements
import app.mymusclemap.domain.achievements.OverviewLatestUnlock
import app.mymusclemap.domain.achievements.UnlockSnapshot
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.testString
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.dashboard.DashboardScreen
import app.mymusclemap.ui.dashboard.DashboardUiState
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h2000dp")
class OverviewAchievementsCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingDoesNotInviteTheUserToEarnAFirstBadge() {
        assertNull(homeOverview(null))
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                DashboardScreen(
                    state = DashboardUiState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDaySelected = {},
                    onDismissDaySheet = {},
                    onRecordSelectedDay = {},
                    onRequestDayDelete = {},
                    onDismissDayDelete = {},
                    onConfirmDayDelete = {},
                    onEditorDateChange = {},
                    onEditorWeightChange = {},
                    onSave = {},
                    onDismissEditor = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                    onMessageConsumed = {},
                    onOpenSettings = {},
                    onOpenTemplates = {},
                    onOpenCatalog = {},
                    onOpenWorkout = {},
                    onOpenWeightDetails = {}
                )
            }
        }
        composeRule.onAllNodesWithText(testString(R.string.achievements_earn_first)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(OVERVIEW_ACHIEVEMENTS).assertCountEquals(0)
    }

    @Test
    fun noAwardShowsAnInvitationAndTheNextActionWithoutAFakeDate() {
        val today = LocalDate.of(2026, 10, 20)
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                OverviewAchievementsCard(
                    achievements = BadgeWallPresenter.overview(board()),
                    today = today,
                    onViewAll = {},
                    onOpenAchievement = {}
                )
            }
        }
        composeRule.onNodeWithTag(OVERVIEW_ACHIEVEMENTS_INVITATION).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.achievements_earned_today)).assertCountEquals(0)
        composeRule.onNodeWithText(testString(R.string.achievement_on_target_name)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_on_target_requirement)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(OVERVIEW_ACHIEVEMENTS_PROGRESS).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.achievements_all_workout_milestones)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.achievement_secret_name)).assertCountEquals(0)
    }

    @Test
    fun countProgressShowsTheRealRatioAndAnActionDoesNot() {
        val today = LocalDate.of(2026, 10, 20)
        val counted = BadgeWallPresenter.overview(
            board(
                completed = 8,
                unlocks = listOf(unlock(AchievementId.FIRST_PR, earnedAt(today)))
            )
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                OverviewAchievementsCard(
                    achievements = counted,
                    today = today,
                    onViewAll = {},
                    onOpenAchievement = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.achievement_first_pr_name)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievement_first_pr_requirement)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_earned_today)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_workouts_name, 10)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_progress_count, 8, 10)).assertIsDisplayed()
        composeRule.onNodeWithTag(OVERVIEW_ACHIEVEMENTS_PROGRESS, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anOlderAwardUsesTheLocalizedDate() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.of(2026, 10, 20)
        val earned = today.minusDays(4)
        val overview = OverviewAchievements(
            latest = OverviewLatestUnlock(
                id = AchievementId.REP_RECORD,
                unlockedAtEpochMillis = earned.atStartOfDay(zone).toInstant().toEpochMilli()
            ),
            upcoming = null
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                OverviewAchievementsCard(
                    achievements = overview,
                    today = today,
                    onViewAll = {},
                    onOpenAchievement = {}
                )
            }
        }
        composeRule.onNodeWithText(
            testString(R.string.achievements_unlocked_on, UiFormatters.compactDate(earned))
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.achievements_earned_today)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(OVERVIEW_ACHIEVEMENTS_UPCOMING).assertCountEquals(0)
        composeRule.onAllNodesWithTag(OVERVIEW_ACHIEVEMENTS_DIVIDER).assertCountEquals(0)
    }

    @Test
    fun anEarnedHiddenGemShowsItsUnlockedName() {
        val today = LocalDate.of(2026, 10, 20)
        val overview = BadgeWallPresenter.overview(
            board(unlocks = listOf(unlock(AchievementId.SILENT_NIGHT, earnedAt(today))))
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                OverviewAchievementsCard(
                    achievements = overview,
                    today = today,
                    onViewAll = {},
                    onOpenAchievement = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.achievement_silent_night_name)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.achievement_secret_name)).assertCountEquals(0)
        composeRule.onNodeWithTag(
            "achievement-badge-unlocked-${AchievementId.SILENT_NIGHT.name}",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun headerAndAchievementAreasAreSeparateTargets() {
        var viewed = 0
        var opened: AchievementId? = null
        val today = LocalDate.of(2026, 10, 20)
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                OverviewAchievementsCard(
                    achievements = BadgeWallPresenter.overview(
                        board(
                            completed = 8,
                            unlocks = listOf(unlock(AchievementId.FIRST_PR, earnedAt(today)))
                        )
                    ),
                    today = today,
                    onViewAll = { viewed += 1 },
                    onOpenAchievement = { opened = it }
                )
            }
        }
        composeRule.onNodeWithTag(OVERVIEW_ACHIEVEMENTS_VIEW_ALL).performClick()
        assertEquals(1, viewed)
        assertNull(opened)
        composeRule.onNodeWithTag(OVERVIEW_ACHIEVEMENTS_LATEST).performClick()
        assertEquals(AchievementId.FIRST_PR, opened)
        composeRule.onNodeWithTag(OVERVIEW_ACHIEVEMENTS_UPCOMING).performClick()
        assertEquals(AchievementId.WORKOUTS_10, opened)
        assertEquals(1, viewed)
    }

    @Test
    fun aNewAwardReplacesTheInvitation() {
        val today = LocalDate.of(2026, 10, 20)
        var achievements by mutableStateOf(BadgeWallPresenter.overview(board()))
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                OverviewAchievementsCard(
                    achievements = achievements,
                    today = today,
                    onViewAll = {},
                    onOpenAchievement = {}
                )
            }
        }
        composeRule.onNodeWithTag(OVERVIEW_ACHIEVEMENTS_INVITATION).assertIsDisplayed()
        achievements = BadgeWallPresenter.overview(
            board(unlocks = listOf(unlock(AchievementId.FIRST_PR, earnedAt(today))))
        )
        composeRule.onNodeWithText(testString(R.string.achievement_first_pr_name)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(OVERVIEW_ACHIEVEMENTS_INVITATION).assertCountEquals(0)
    }

    @Test
    fun longNamesAndLargeTextStayBesideTheBadge() {
        val today = LocalDate.of(2026, 10, 20)
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultLight, darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                    OverviewAchievementsCard(
                        achievements = BadgeWallPresenter.overview(
                            board(unlocks = listOf(unlock(AchievementId.SILENT_NIGHT, earnedAt(today))))
                        ),
                        today = today,
                        onViewAll = {},
                        onOpenAchievement = {},
                        modifier = Modifier.width(320.dp)
                    )
                }
            }
        }
        val badge = composeRule.onNodeWithTag(
            "achievement-badge-unlocked-${AchievementId.SILENT_NIGHT.name}",
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        val name = composeRule.onNodeWithText(
            testString(R.string.achievement_silent_night_name),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        composeRule.onNodeWithText(
            testString(R.string.achievement_silent_night_name),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.achievements_view_all)).assertIsDisplayed()
        assertTrue(name.left >= badge.right)
        composeRule.onAllNodesWithText(testString(R.string.achievement_silent_night_name)).assertCountEquals(1)
    }

    private fun board(
        completed: Int = 0,
        unlocks: List<UnlockSnapshot> = emptyList()
    ) = AchievementBoardAssembler.assemble(
        completedWorkoutCount = completed,
        unlocks = unlocks,
        events = emptyList()
    )

    private fun unlock(id: AchievementId, at: Long) = UnlockSnapshot(id, at, at, null)

    private fun earnedAt(day: LocalDate): Long {
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
