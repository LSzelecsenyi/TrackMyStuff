package app.mymusclemap.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.testString
import app.mymusclemap.ui.founder.FOUNDER_MILESTONE_CONTINUE
import app.mymusclemap.ui.membership.MembershipDetail
import app.mymusclemap.ui.membership.MembershipPresentation
import app.mymusclemap.ui.navigation.BOTTOM_WORKOUT_ACTION
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class OverviewMembershipUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freeShowsNoMembershipBadge() {
        show(MembershipPresentation.None)
        composeRule.onNodeWithTag(OVERVIEW_MEMBERSHIP_BADGE).assertDoesNotExist()
        composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).assertDoesNotExist()
    }

    @Test
    fun proBadgeSitsInTheHeaderAndExplainsTemporaryPro() {
        show(
            MembershipPresentation(
                app.mymusclemap.ui.membership.MembershipBadge.Pro,
                MembershipDetail.TemporaryFounderPro
            ),
            darkTheme = true
        )
        val header = composeRule.onNodeWithTag("dashboard_weekly_header").getBoundsInRoot()
        val badge = composeRule.onNodeWithTag(OVERVIEW_MEMBERSHIP_BADGE).getBoundsInRoot()
        val overflow = composeRule.onNodeWithTag(OVERVIEW_OVERFLOW_ANCHOR).getBoundsInRoot()
        composeRule.onAllNodesWithTag(OVERVIEW_MEMBERSHIP_BADGE).assertCountEquals(1)
        composeRule.onNodeWithText(testString(R.string.membership_badge_pro)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.membership_badge_founder)).assertCountEquals(0)
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).assertDoesNotExist()
        assertTrue(badge.top >= header.top - 1.dp)
        assertTrue(badge.bottom <= header.bottom + 1.dp)
        assertTrue(badge.right <= overflow.left + 1.dp)
        composeRule.onNodeWithTag(OVERVIEW_MEMBERSHIP_BADGE).performClick()
        composeRule.onNodeWithText(testString(R.string.membership_temporary_pro_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(OVERVIEW_MEMBERSHIP_INFO).assertIsDisplayed()
    }

    @Test
    fun founderBadgeIsTheOnlyMembershipMark() {
        show(
            MembershipPresentation(
                app.mymusclemap.ui.membership.MembershipBadge.Founder,
                MembershipDetail.FoundingMember
            )
        )
        composeRule.onAllNodesWithTag(OVERVIEW_MEMBERSHIP_BADGE).assertCountEquals(1)
        composeRule.onNodeWithText(testString(R.string.membership_badge_founder)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.membership_badge_pro)).assertCountEquals(0)
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).assertDoesNotExist()
        composeRule.onNodeWithTag(OVERVIEW_MEMBERSHIP_BADGE).performClick()
        composeRule.onNodeWithText(testString(R.string.founder_lifetime_pro)).assertIsDisplayed()
    }

    @Test
    fun pendingAndGenericProExplainTheCurrentAccess() {
        show(
            MembershipPresentation(
                app.mymusclemap.ui.membership.MembershipBadge.Pro,
                MembershipDetail.PendingFounderReview
            )
        )
        composeRule.onNodeWithTag(OVERVIEW_MEMBERSHIP_BADGE).performClick()
        composeRule.onNodeWithText(testString(R.string.membership_pending_pro_body)).assertIsDisplayed()
    }

    @Test
    fun genericProExplainsActiveAccess() {
        show(
            MembershipPresentation(
                app.mymusclemap.ui.membership.MembershipBadge.Pro,
                MembershipDetail.Pro
            )
        )
        composeRule.onNodeWithTag(OVERVIEW_MEMBERSHIP_BADGE).performClick()
        composeRule.onNodeWithText(testString(R.string.membership_generic_pro_body)).assertIsDisplayed()
    }

    @Test
    fun continueAcknowledgesAndRecompositionDoesNotReopen() {
        var showMilestone by mutableStateOf(true)
        var onOverview by mutableStateOf(true)
        var acknowledgements = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                if (onOverview) {
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
                        onOpenWeightDetails = {},
                        showTemporaryProMilestone = showMilestone,
                        onAcknowledgeTemporaryPro = {
                            acknowledgements += 1
                            showMilestone = false
                        }
                    )
                }
            }
        }
        composeRule.onNodeWithText(testString(R.string.founder_milestone_pro_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_MILESTONE_CONTINUE).performClick()
        composeRule.waitForIdle()
        assertEquals(1, acknowledgements)
        composeRule.onNodeWithText(testString(R.string.founder_milestone_pro_title)).assertDoesNotExist()
        onOverview = false
        composeRule.waitForIdle()
        onOverview = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_pro_title)).assertDoesNotExist()
    }

    private fun show(
        membership: MembershipPresentation,
        darkTheme: Boolean = false
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview(
                seeds = if (darkTheme) ThemeSeeds.DefaultDark else ThemeSeeds.DefaultLight,
                darkTheme = darkTheme
            ) {
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
                    onOpenWeightDetails = {},
                    membership = membership
                )
            }
        }
    }
}
