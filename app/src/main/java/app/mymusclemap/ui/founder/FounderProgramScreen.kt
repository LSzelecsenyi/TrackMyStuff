package app.mymusclemap.ui.founder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import kotlinx.coroutines.launch

internal const val FOUNDER_ROOT = "founder-program-root"
internal const val FOUNDER_ENROLL = "founder-program-enroll"
internal const val FOUNDER_BADGE = "founder-badge"
internal const val FOUNDER_WELCOME = "founder-welcome"
internal const val FOUNDER_MILESTONE = "founder-milestone"
internal const val FOUNDER_MILESTONE_CONTINUE = "founder-milestone-continue"
internal const val FOUNDER_NEXT = "founder-next"
internal const val FOUNDER_FEEDBACK = "founder-feedback"
internal const val FOUNDER_FEEDBACK_FIELD = "founder-feedback-field"
internal const val FOUNDER_SUBMIT_REPORT = "founder-submit-report"
internal const val FOUNDER_STATUS = "founder-status"
internal const val FOUNDER_INVITATION = "founder-invitation"
internal const val FOUNDER_INVITATION_NOT_NOW = "founder-invitation-not-now"
internal const val FOUNDER_JOIN_NOTICE = "founder-join-notice"

@Composable
fun FounderProgramScreen(
    state: FounderProgramUiState,
    onBack: () -> Unit,
    onEnroll: () -> Unit,
    onResumeSession: () -> Unit = {},
    onFeedbackChange: (String) -> Unit,
    onSubmitReport: () -> Unit,
    onAcknowledgeMilestone: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(FOUNDER_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
                Text(
                    text = stringResource(R.string.founder_program_title),
                    style = AppTypeTokens.sectionTitle,
                    modifier = Modifier.weight(1f)
                )
                if (state.founderBadge) {
                    FounderBadge()
                }
            }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(AppDimens.screenPadding))
                return@Column
            }
            if (state.journey.milestone != null) {
                FounderMilestoneDialog(
                    state = state,
                    onAcknowledge = onAcknowledgeMilestone
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = AppDimens.screenPadding)
            ) {
                if (state.journey.phase == FounderJourneyPhase.Welcome) {
                    FounderWelcomeContent(
                        rules = state.journey.rules,
                        onEnroll = onEnroll,
                        actionsEnabled = !state.joining,
                        joining = state.joining,
                        joinNotice = state.joinNotice
                    )
                } else {
                    if (state.sessionRequired) {
                        Button(
                            onClick = onResumeSession,
                            enabled = !state.joining,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppDimens.sectionGap)
                                .testTag("founder-resume-session")
                        ) {
                            Text(
                                stringResource(
                                    if (state.joining) {
                                        R.string.founder_enroll_working
                                    } else {
                                        R.string.founder_resume_sign_in
                                    }
                                )
                            )
                        }
                    }
                    EnrolledSection(state = state)
                    if (state.journey.showFeedback && !state.journey.reportShared) {
                        TesterReportSection(
                            state = state,
                            onFeedbackChange = onFeedbackChange,
                            onSubmitReport = onSubmitReport
                        )
                    }
                    Spacer(Modifier.height(AppDimens.sectionGap))
                }
            }
        }
    }
}

@Composable
fun FounderBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.founder_badge),
        style = AppTypeTokens.statCaption,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .testTag(FOUNDER_BADGE)
            .padding(horizontal = AppDimens.itemGap)
    )
}

@Composable
internal fun FounderWelcomeContent(
    rules: FounderProgramRules,
    onEnroll: () -> Unit,
    onDecline: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
    joining: Boolean = false,
    joinNotice: FounderJoinNotice = FounderJoinNotice.None
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(AppDimens.itemGap))
        FounderProgramMark(prominent = true)
        Spacer(Modifier.height(AppDimens.sectionGap))
        Text(
            text = stringResource(R.string.founder_welcome_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FOUNDER_WELCOME)
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        BodyText(stringResource(R.string.founder_welcome_lead))
        Spacer(Modifier.height(AppDimens.sectionGap))
        CompactEditorSection(title = stringResource(R.string.founder_challenge_title)) {
            Bullet(pluralStringResource(R.plurals.founder_challenge_workouts, rules.founderWorkoutCount, rules.founderWorkoutCount))
            Bullet(pluralStringResource(R.plurals.founder_challenge_days, rules.requiredDistinctWorkoutDays, rules.requiredDistinctWorkoutDays))
            if (rules.feedbackRequired) {
                Bullet(stringResource(R.string.founder_challenge_feedback))
            }
            if (rules.testerAnalyticsReportRequired) {
                Bullet(stringResource(R.string.founder_challenge_report))
            }
            Bullet(pluralStringResource(R.plurals.founder_challenge_window, rules.qualificationWindowDays, rules.qualificationWindowDays))
        }
        Spacer(Modifier.height(AppDimens.sectionGap))
        CompactEditorSection(title = stringResource(R.string.founder_unlock_early_title)) {
            BodyText(
                pluralStringResource(
                    R.plurals.founder_temporary_pro_offer,
                    rules.temporaryProWorkoutCount,
                    rules.temporaryProWorkoutCount
                )
            )
        }
        Spacer(Modifier.height(AppDimens.itemGap))
        SecondaryText(stringResource(R.string.founder_welcome_review))
        Spacer(Modifier.height(AppDimens.itemGap))
        SecondaryText(stringResource(R.string.founder_welcome_native))
        Spacer(Modifier.height(AppDimens.itemGap))
        SecondaryText(stringResource(R.string.founder_welcome_upload))
        Spacer(Modifier.height(AppDimens.itemGap))
        SecondaryText(stringResource(R.string.founder_welcome_account))
        Spacer(Modifier.height(AppDimens.sectionGap))
        Button(
            onClick = onEnroll,
            enabled = actionsEnabled && !joining,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FOUNDER_ENROLL)
        ) {
            if (joining) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                stringResource(
                    if (joining) R.string.founder_enroll_working else R.string.founder_enroll
                )
            )
        }
        val notice = joinNoticeText(joinNotice)
        if (notice != null) {
            Spacer(Modifier.height(AppDimens.itemGap))
            Text(
                text = notice,
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FOUNDER_JOIN_NOTICE)
            )
        }
        if (onDecline != null) {
            Spacer(Modifier.height(AppDimens.itemGap))
            TextButton(
                onClick = onDecline,
                enabled = actionsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FOUNDER_INVITATION_NOT_NOW)
            ) {
                Text(stringResource(R.string.founder_invitation_not_now))
            }
        }
        Spacer(Modifier.height(AppDimens.sectionGap))
    }
}

@Composable
private fun joinNoticeText(notice: FounderJoinNotice): String? {
    val res = when (notice) {
        FounderJoinNotice.None -> return null
        FounderJoinNotice.GoogleFailed -> R.string.founder_join_google_failed
        FounderJoinNotice.Unavailable -> R.string.founder_join_unavailable
        FounderJoinNotice.Rejected -> R.string.founder_join_rejected
        FounderJoinNotice.NotConfigured -> R.string.founder_join_not_configured
    }
    return stringResource(res)
}

@Composable
private fun EnrolledSection(state: FounderProgramUiState) {
    val journey = state.journey
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(AppDimens.itemGap))
        FounderProgramMark(prominent = journey.phase == FounderJourneyPhase.FoundingMember)
        Spacer(Modifier.height(AppDimens.sectionGap))
        when (journey.phase) {
            FounderJourneyPhase.ActiveFree -> ActiveFreeCopy(journey)
            FounderJourneyPhase.ActivePro -> ActiveProCopy(journey)
            FounderJourneyPhase.PendingReview -> PendingCopy()
            FounderJourneyPhase.FoundingMember -> MemberCopy()
            FounderJourneyPhase.Expired -> ExpiredCopy(journey)
            FounderJourneyPhase.Rejected -> RejectedCopy(state.rejectionReason)
            FounderJourneyPhase.Welcome -> Unit
        }
    }
    if (journey.phase == FounderJourneyPhase.ActiveFree || journey.phase == FounderJourneyPhase.ActivePro) {
        if (journey.nextAction == FounderNextAction.Feedback) {
            Spacer(Modifier.height(AppDimens.sectionGap))
            Headline(stringResource(R.string.founder_training_complete_title), tag = FOUNDER_STATUS)
            Spacer(Modifier.height(AppDimens.itemGap))
            BodyText(stringResource(R.string.founder_training_complete_body))
        } else if (journey.nextAction == FounderNextAction.TesterReport) {
            Spacer(Modifier.height(AppDimens.sectionGap))
            Headline(stringResource(R.string.founder_feedback_complete_title), tag = FOUNDER_STATUS)
            Spacer(Modifier.height(AppDimens.itemGap))
            Text(
                text = stringResource(R.string.founder_report_final),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(AppDimens.sectionGap))
        ChecklistSection(
            title = stringResource(R.string.founder_progress_title),
            rows = journey.presentedChecklist()
        )
        if (journey.showDeadline) {
            state.deadline?.let { deadline ->
                Spacer(Modifier.height(AppDimens.itemGap))
                BodyText(stringResource(R.string.founder_deadline, UiFormatters.longDate(deadline)))
            }
        }
    }
}

@Composable
private fun ActiveFreeCopy(journey: FounderJourney) {
    Headline(stringResource(R.string.founder_in_title), tag = FOUNDER_STATUS)
    Spacer(Modifier.height(AppDimens.itemGap))
    nextActionText(journey)?.let { next ->
        Text(
            text = next,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FOUNDER_NEXT)
        )
    }
    val temporaryPro = journey.checklist.firstOrNull { it.kind == FounderChecklistKind.TemporaryPro }
    if (temporaryPro != null && temporaryPro.current == 0) {
        Spacer(Modifier.height(AppDimens.itemGap))
        BodyText(stringResource(R.string.founder_next_first_workout))
    }
}

@Composable
private fun ActiveProCopy(journey: FounderJourney) {
    BodyText(stringResource(R.string.founder_temporary_pro_active))
    nextActionText(journey)?.let { next ->
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = next,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FOUNDER_NEXT)
        )
    }
}

@Composable
private fun PendingCopy() {
    Headline(stringResource(R.string.founder_pending_title), tag = FOUNDER_STATUS)
    Spacer(Modifier.height(AppDimens.headerStackGap))
    Text(
        text = stringResource(R.string.founder_pending_review),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(AppDimens.itemGap))
    BodyText(stringResource(R.string.founder_pending_body))
}

@Composable
private fun MemberCopy() {
    FounderBadge()
    Spacer(Modifier.height(AppDimens.itemGap))
    Headline(stringResource(R.string.founder_lifetime_pro), tag = FOUNDER_STATUS)
    Spacer(Modifier.height(AppDimens.itemGap))
    BodyText(stringResource(R.string.founder_milestone_member_helped))
}

@Composable
private fun ExpiredCopy(journey: FounderJourney) {
    Headline(stringResource(R.string.founder_expired_title), tag = FOUNDER_STATUS)
    Spacer(Modifier.height(AppDimens.itemGap))
    BodyText(stringResource(R.string.founder_expired_body))
    val open = journey.checklist.filterNot { it.done }
    if (open.isNotEmpty()) {
        Spacer(Modifier.height(AppDimens.sectionGap))
        ChecklistSection(title = stringResource(R.string.founder_expired_open), rows = open)
    }
}

@Composable
private fun RejectedCopy(rejectionReason: String?) {
    Headline(stringResource(R.string.founder_rejected_title), tag = FOUNDER_STATUS)
    Spacer(Modifier.height(AppDimens.itemGap))
    BodyText(stringResource(R.string.founder_rejected_body))
    if (!rejectionReason.isNullOrBlank()) {
        Spacer(Modifier.height(AppDimens.itemGap))
        BodyText(stringResource(R.string.founder_rejected_reason, rejectionReason))
    }
}

@Composable
private fun FounderMilestoneDialog(
    state: FounderProgramUiState,
    onAcknowledge: () -> Unit
) {
    val milestone = state.journey.milestone ?: return
    if (milestone == FounderMilestone.TemporaryProUnlocked) {
        FounderProUnlockedDialog(
            checklist = state.journey.presentedChecklist(),
            onAcknowledge = onAcknowledge
        )
        return
    }
    if (milestone == FounderMilestone.TrainingComplete) {
        FounderTrainingCompleteDialog(onAcknowledge = onAcknowledge)
        return
    }
    val prominent = milestone == FounderMilestone.FounderApproved
    AlertDialog(
        onDismissRequest = onAcknowledge,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FounderProgramMark(prominent = prominent)
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = milestoneTitle(milestone),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FOUNDER_MILESTONE)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (milestone) {
                    FounderMilestone.TemporaryProUnlocked,
                    FounderMilestone.TrainingComplete -> Unit
                    FounderMilestone.QualificationComplete -> {
                        BodyText(stringResource(R.string.founder_milestone_qualified_body))
                        Spacer(Modifier.height(AppDimens.itemGap))
                        BodyText(stringResource(R.string.founder_milestone_qualified_pro))
                    }
                    FounderMilestone.FounderApproved -> {
                        BodyText(stringResource(R.string.founder_milestone_member_helped))
                        Spacer(Modifier.height(AppDimens.itemGap))
                        BodyText(stringResource(R.string.founder_milestone_member_pro))
                        Spacer(Modifier.height(AppDimens.itemGap))
                        BodyText(stringResource(R.string.founder_milestone_member_thanks))
                        Spacer(Modifier.height(AppDimens.itemGap))
                        FounderBadge(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.testTag(FOUNDER_MILESTONE_CONTINUE)
            ) {
                Text(stringResource(R.string.founder_milestone_continue))
            }
        }
    )
}

/**
 * The one Temporary Pro milestone. Overview and the Founder screen both present this dialog.
 */
@Composable
internal fun FounderProUnlockedDialog(
    checklist: List<FounderChecklistRow>,
    onAcknowledge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAcknowledge,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FounderProgramMark()
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = stringResource(R.string.founder_milestone_pro_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FOUNDER_MILESTONE)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                BodyText(stringResource(R.string.founder_milestone_pro_body))
                Spacer(Modifier.height(AppDimens.sectionGap))
                ChecklistSection(
                    title = stringResource(R.string.founder_milestone_next),
                    rows = checklist
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.testTag(FOUNDER_MILESTONE_CONTINUE)
            ) {
                Text(stringResource(R.string.founder_milestone_continue))
            }
        }
    )
}

/**
 * Training requirement completed. This dialog does not submit feedback or a report.
 */
@Composable
internal fun FounderTrainingCompleteDialog(
    onAcknowledge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAcknowledge,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FounderProgramMark()
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = stringResource(R.string.founder_milestone_training_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FOUNDER_MILESTONE)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BodyText(stringResource(R.string.founder_milestone_training_body))
                Spacer(Modifier.height(AppDimens.itemGap))
                BodyText(stringResource(R.string.founder_milestone_training_next))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.testTag(FOUNDER_MILESTONE_CONTINUE)
            ) {
                Text(stringResource(R.string.founder_milestone_continue))
            }
        }
    )
}

@Composable
private fun milestoneTitle(milestone: FounderMilestone): String {
    return stringResource(
        when (milestone) {
            FounderMilestone.TemporaryProUnlocked -> R.string.founder_milestone_pro_title
            FounderMilestone.TrainingComplete -> R.string.founder_milestone_training_title
            FounderMilestone.QualificationComplete -> R.string.founder_milestone_qualified_title
            FounderMilestone.FounderApproved -> R.string.founder_milestone_member_title
        }
    )
}

@Composable
private fun nextActionText(journey: FounderJourney): String? {
    val action = journey.nextAction ?: return null
    val remaining = journey.nextRemaining
    return when (action) {
        FounderNextAction.UnlockTemporaryPro -> pluralStringResource(
            R.plurals.founder_unlock_temporary_pro,
            remaining,
            remaining
        )
        FounderNextAction.QualifyingWorkout -> pluralStringResource(
            R.plurals.founder_next_workouts,
            remaining,
            remaining
        )
        FounderNextAction.TrainingDay -> pluralStringResource(
            R.plurals.founder_next_days,
            remaining,
            remaining
        )
        FounderNextAction.Feedback -> stringResource(R.string.founder_next_feedback)
        FounderNextAction.TesterReport -> stringResource(R.string.founder_next_report)
    }
}

@Composable
private fun ChecklistSection(
    title: String,
    rows: List<FounderChecklistRow>
) {
    CompactEditorSection(title = title) {
        rows.forEach { row ->
            ChecklistRow(row)
        }
    }
}

@Composable
private fun ChecklistRow(row: FounderChecklistRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.headerStackGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (row.done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (row.done) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = checklistLabel(row),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = AppDimens.itemGap)
        )
    }
}

@Composable
private fun checklistLabel(row: FounderChecklistRow): String {
    return when (row.kind) {
        FounderChecklistKind.TemporaryPro -> if (row.done) {
            stringResource(R.string.founder_check_temporary_pro_done)
        } else {
            pluralStringResource(
                R.plurals.founder_progress_temporary_pro,
                row.required,
                row.current,
                row.required
            )
        }
        FounderChecklistKind.QualifyingWorkouts -> pluralStringResource(
            R.plurals.founder_progress_workouts,
            row.required,
            row.current,
            row.required
        )
        FounderChecklistKind.TrainingDays -> pluralStringResource(
            R.plurals.founder_progress_days,
            row.required,
            row.current,
            row.required
        )
        FounderChecklistKind.Feedback -> stringResource(
            if (row.done) R.string.founder_feedback_done else R.string.founder_feedback_needed
        )
        FounderChecklistKind.TesterReport -> stringResource(
            if (row.done) R.string.founder_report_done else R.string.founder_report_needed
        )
    }
}

@Composable
private fun TesterReportSection(
    state: FounderProgramUiState,
    onFeedbackChange: (String) -> Unit,
    onSubmitReport: () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(AppDimens.sectionGap))
    CompactEditorSection(
        title = stringResource(R.string.founder_report_title),
        modifier = Modifier.testTag(FOUNDER_FEEDBACK)
    ) {
        BodyText(stringResource(R.string.founder_feedback_why))
        Spacer(Modifier.height(AppDimens.itemGap))
        OutlinedTextField(
            value = state.feedbackDraft,
            onValueChange = onFeedbackChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FOUNDER_FEEDBACK_FIELD)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                },
            label = { Text(stringResource(R.string.founder_feedback_hint)) },
            isError = state.feedbackBlank || state.feedbackTooLong,
            supportingText = when {
                state.feedbackBlank -> {
                    { Text(stringResource(R.string.founder_feedback_blank)) }
                }
                state.feedbackTooLong -> {
                    { Text(stringResource(R.string.founder_feedback_too_long)) }
                }
                else -> null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            minLines = 3
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        BodyText(stringResource(R.string.founder_report_submit_hint))
        if (state.reportSubmitFailed) {
            Spacer(Modifier.height(AppDimens.itemGap))
            Text(
                text = stringResource(R.string.founder_report_failed),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(AppDimens.itemGap))
        Button(
            onClick = onSubmitReport,
            enabled = !state.submittingReport,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FOUNDER_SUBMIT_REPORT)
        ) {
            Text(
                stringResource(
                    if (state.submittingReport) {
                        R.string.founder_report_submitting
                    } else {
                        R.string.founder_report_submit
                    }
                )
            )
        }
    }
}

@Composable
private fun Headline(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SecondaryText(text: String) {
    Text(
        text = text,
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Bullet(text: String) {
    Text(
        text = stringResource(R.string.founder_bullet, text),
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = AppDimens.statSecondaryGap)
    )
}

@Composable
fun FounderProgramRoute(
    state: FounderProgramUiState,
    onBack: () -> Unit,
    onEnroll: () -> Unit,
    onResumeSession: () -> Unit = {},
    onFeedbackChange: (String) -> Unit,
    onSubmitReport: () -> Unit,
    onAcknowledgeMilestone: () -> Unit
) {
    FounderProgramScreen(
        state = state,
        onBack = onBack,
        onEnroll = onEnroll,
        onResumeSession = onResumeSession,
        onFeedbackChange = onFeedbackChange,
        onSubmitReport = onSubmitReport,
        onAcknowledgeMilestone = onAcknowledgeMilestone
    )
}
