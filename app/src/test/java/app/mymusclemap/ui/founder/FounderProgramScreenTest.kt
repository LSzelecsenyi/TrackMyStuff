package app.mymusclemap.ui.founder

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.FounderDebugReviewAccess
import app.mymusclemap.R
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.FounderQualification
import app.mymusclemap.testString
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class FounderProgramScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fastRules = FounderProgramRules(
        temporaryProWorkoutCount = 1,
        founderWorkoutCount = 2,
        requiredDistinctWorkoutDays = 1,
        qualificationWindowDays = 45,
        feedbackRequired = true,
        testerAnalyticsReportRequired = true
    )

    @Test
    fun welcomeUsesTheActiveRulesAndJoinsWithoutAPurchaseLabel() {
        var enrolled = false
        render(ui(FounderProgramRules.Production), onEnroll = { enrolled = true })
        composeRule.onNodeWithTag(FOUNDER_MARK).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.founder_mark_content_description)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_native)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_review)).assertIsDisplayed()
        show(bullet(R.plurals.founder_challenge_workouts, FounderProgramRules.Production.founderWorkoutCount))
        show(bullet(R.plurals.founder_challenge_days, FounderProgramRules.Production.requiredDistinctWorkoutDays))
        show(bullet(R.plurals.founder_challenge_window, FounderProgramRules.Production.qualificationWindowDays))
        show(offer(FounderProgramRules.Production.temporaryProWorkoutCount))
        composeRule.onNodeWithText(bullet(R.plurals.founder_challenge_workouts, fastRules.founderWorkoutCount)).assertDoesNotExist()
        composeRule.onNodeWithText(offer(fastRules.temporaryProWorkoutCount)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_enroll)).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(enrolled)
        composeRule.onNodeWithText(testString(R.string.founder_invitation_not_now)).assertDoesNotExist()
        composeRule.onNodeWithText("Approved").assertDoesNotExist()
    }

    @Test
    fun welcomeCanShowTheFastRulesWithoutHardcodingProductionCounts() {
        render(ui(fastRules))
        show(bullet(R.plurals.founder_challenge_workouts, fastRules.founderWorkoutCount))
        show(bullet(R.plurals.founder_challenge_days, fastRules.requiredDistinctWorkoutDays))
        show(offer(fastRules.temporaryProWorkoutCount))
        composeRule.onNodeWithText(bullet(R.plurals.founder_challenge_workouts, FounderProgramRules.Production.founderWorkoutCount)).assertDoesNotExist()
        composeRule.onNodeWithText(offer(FounderProgramRules.Production.temporaryProWorkoutCount)).assertDoesNotExist()
    }

    @Test
    fun activeFreeExplainsTheTemporaryProStep() {
        val deadline = LocalDate.of(2026, 11, 17)
        render(ui(fastRules, status = FounderProgramStatus.ActiveFree, deadline = deadline))
        composeRule.onNodeWithText(testString(R.string.founder_in_title)).assertIsDisplayed()
        composeRule.onNodeWithText(unlock(fastRules.temporaryProWorkoutCount)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_deadline, UiFormatters.longDate(deadline))).performScrollTo().assertIsDisplayed()
        show(testString(R.string.founder_feedback_why))
        show(testString(R.string.founder_report_body))
        show(testString(R.string.founder_report_submit))
        composeRule.onNodeWithText(testString(R.string.founder_feedback_saved)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_report_submitted)).assertDoesNotExist()
    }

    @Test
    fun temporaryProMilestoneIsDismissedWithoutLeavingTheProgress() {
        var state by mutableStateOf(
            ui(fastRules, status = FounderProgramStatus.ActivePro, workouts = 1, days = 1)
        )
        render(state = { state }, onAcknowledge = {
            state = ui(
                fastRules,
                status = FounderProgramStatus.ActivePro,
                workouts = 1,
                days = 1,
                acknowledgements = FounderMilestoneAcknowledgements(temporaryProUnlocked = true)
            )
        })
        composeRule.onNodeWithTag(FOUNDER_MILESTONE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_pro_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_pro_body)).assertIsDisplayed()
        composeRule.onNodeWithText(unlock(1)).assertDoesNotExist()
        composeRule.onNodeWithTag(FOUNDER_MILESTONE_CONTINUE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_pro_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_temporary_pro_active)).assertIsDisplayed()
        composeRule.onNodeWithText(nextWorkouts(1)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_check_temporary_pro_done)).assertIsDisplayed()
    }

    @Test
    fun activeProNamesTheRemainingTrainingDays() {
        render(
            ui(
                FounderProgramRules.Production,
                status = FounderProgramStatus.ActivePro,
                workouts = 10,
                days = 4,
                acknowledgements = FounderMilestoneAcknowledgements(temporaryProUnlocked = true)
            )
        )
        composeRule.onNodeWithText(nextDays(2)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_pro_title)).assertDoesNotExist()
    }

    @Test
    fun savedFeedbackIsCompleteAndTheReportIsTheNextStep() {
        render(
            ui(
                fastRules,
                status = FounderProgramStatus.ActivePro,
                workouts = 2,
                days = 1,
                feedback = true,
                acknowledgements = FounderMilestoneAcknowledgements(temporaryProUnlocked = true)
            )
        )
        composeRule.onNodeWithText(testString(R.string.founder_next_report)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_feedback_saved)).performScrollTo().assertIsDisplayed()
        show(testString(R.string.founder_feedback_share_optional))
        composeRule.onNodeWithText(testString(R.string.founder_feedback_why)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_report_submitted)).assertDoesNotExist()
    }

    @Test
    fun pendingReviewExplainsThatRequirementsAreFinished() {
        var state by mutableStateOf(
            ui(
                fastRules,
                status = FounderProgramStatus.PendingApproval,
                workouts = 2,
                days = 1,
                feedback = true,
                report = true,
                deadline = LocalDate.of(2026, 11, 17)
            )
        )
        render(state = { state }, onAcknowledge = {
            state = ui(
                fastRules,
                status = FounderProgramStatus.PendingApproval,
                workouts = 2,
                days = 1,
                feedback = true,
                report = true,
                deadline = LocalDate.of(2026, 11, 17),
                acknowledgements = FounderMilestoneAcknowledgements(qualificationComplete = true)
            )
        })
        composeRule.onNodeWithText(testString(R.string.founder_milestone_qualified_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_qualified_pro)).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_MILESTONE_CONTINUE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_qualified_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_pending_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_pending_review)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_pending_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_deadline, UiFormatters.longDate(LocalDate.of(2026, 11, 17)))).assertDoesNotExist()
        val approve = testString(R.string.founder_debug_approve)
        if (FounderDebugReviewAccess.available) {
            composeRule.onNodeWithText(approve).performScrollTo().assertIsDisplayed()
        } else {
            composeRule.onNodeWithText(approve).assertDoesNotExist()
        }
    }

    @Test
    fun approvedScreenPresentsFoundingMemberAndLifetimePro() {
        var state by mutableStateOf(
            ui(
                fastRules,
                status = FounderProgramStatus.Approved,
                workouts = 2,
                days = 1,
                feedback = true,
                report = true
            )
        )
        render(state = { state }, onAcknowledge = {
            state = ui(
                fastRules,
                status = FounderProgramStatus.Approved,
                workouts = 2,
                days = 1,
                feedback = true,
                report = true,
                acknowledgements = FounderMilestoneAcknowledgements(founderApproved = true)
            )
        })
        composeRule.onNodeWithText(testString(R.string.founder_milestone_member_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_member_pro)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_member_thanks)).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_MILESTONE_CONTINUE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_member_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_lifetime_pro)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.founder_badge)).assertCountEquals(2)
        composeRule.onNodeWithText("Approved").assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_debug_approve)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(testString(R.string.founder_mark_content_description)).assertDoesNotExist()
    }

    @Test
    fun expiredExplainsThatTheWindowEnded() {
        render(ui(FounderProgramRules.Production, status = FounderProgramStatus.Expired, workouts = 1, days = 1))
        composeRule.onNodeWithText(testString(R.string.founder_expired_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_expired_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_expired_open)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_pro_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_pending_review)).assertDoesNotExist()
    }

    @Test
    fun rejectedExplainsTheDecisionAndShowsAReviewNote() {
        val reason = "The report was incomplete"
        render(
            ui(
                fastRules,
                status = FounderProgramStatus.Rejected,
                workouts = 2,
                days = 1,
                feedback = true,
                report = true,
                rejectionReason = reason
            )
        )
        composeRule.onNodeWithText(testString(R.string.founder_rejected_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_rejected_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_rejected_reason, reason)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_milestone_member_title)).assertDoesNotExist()
    }

    private fun render(
        state: FounderProgramUiState = ui(fastRules),
        onEnroll: () -> Unit = {},
        onAcknowledge: () -> Unit = {}
    ) {
        render(state = { state }, onEnroll = onEnroll, onAcknowledge = onAcknowledge)
    }

    private fun render(
        state: () -> FounderProgramUiState,
        onEnroll: () -> Unit = {},
        onAcknowledge: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .height(2000.dp)
                            .fillMaxSize()
                    ) {
                        FounderProgramScreen(
                            state = state(),
                            onBack = {},
                            onEnroll = onEnroll,
                            onFeedbackChange = {},
                            onSaveFeedback = {},
                            onShareFeedback = {},
                            onSubmitReport = {},
                            onAcknowledgeMilestone = onAcknowledge,
                            onApprove = {},
                            onReject = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun show(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun bullet(@PluralsRes id: Int, count: Int): String {
        return testString(R.string.founder_bullet, quantity(id, count))
    }

    private fun offer(count: Int): String = quantity(R.plurals.founder_temporary_pro_offer, count)

    private fun unlock(count: Int): String = quantity(R.plurals.founder_unlock_temporary_pro, count)

    private fun nextWorkouts(count: Int): String = quantity(R.plurals.founder_next_workouts, count)

    private fun nextDays(count: Int): String = quantity(R.plurals.founder_next_days, count)

    private fun quantity(@PluralsRes id: Int, count: Int): String {
        return ApplicationProvider.getApplicationContext<Context>()
            .resources
            .getQuantityString(id, count, count)
    }

    private fun ui(
        rules: FounderProgramRules,
        status: FounderProgramStatus = FounderProgramStatus.NotEnrolled,
        workouts: Int = 0,
        days: Int = 0,
        feedback: Boolean = false,
        report: Boolean = false,
        acknowledgements: FounderMilestoneAcknowledgements = FounderMilestoneAcknowledgements(),
        deadline: LocalDate? = null,
        rejectionReason: String? = null
    ): FounderProgramUiState {
        val qualification = FounderQualification(
            nativeCompletedWorkouts = workouts,
            distinctNativeWorkoutDays = days,
            feedbackRecorded = feedback,
            testerAnalyticsReportSubmitted = report,
            rules = rules
        )
        return FounderProgramUiState(
            loading = false,
            status = status,
            rules = rules,
            nativeWorkouts = workouts,
            distinctDays = days,
            temporaryProActive = status.grantsTemporaryPro(),
            feedbackRecorded = feedback,
            reportSubmitted = report,
            requirementsComplete = qualification.testerRequirementsComplete,
            deadline = deadline,
            rejectionReason = rejectionReason,
            storedFeedback = if (feedback) "Keep the rest timer visible." else "",
            founderBadge = status == FounderProgramStatus.Approved,
            journey = founderJourney(status, qualification, acknowledgements)
        )
    }
}
