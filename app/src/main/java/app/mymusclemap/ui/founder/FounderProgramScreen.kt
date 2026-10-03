package app.mymusclemap.ui.founder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramStatus
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
internal const val FOUNDER_REPORT = "founder-report"
internal const val FOUNDER_STATUS = "founder-status"
internal const val FOUNDER_INVITATION = "founder-invitation"
internal const val FOUNDER_INVITATION_NOT_NOW = "founder-invitation-not-now"

@Composable
fun FounderProgramScreen(
    state: FounderProgramUiState,
    onBack: () -> Unit,
    onEnroll: () -> Unit,
    onFeedbackChange: (String) -> Unit,
    onSaveFeedback: () -> Unit,
    onShareFeedback: () -> Unit,
    onSubmitReport: () -> Unit,
    onAcknowledgeMilestone: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
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
                    .padding(horizontal = AppDimens.screenPadding)
            ) {
                if (state.journey.phase == FounderJourneyPhase.Welcome) {
                    FounderWelcomeContent(rules = state.journey.rules, onEnroll = onEnroll)
                } else {
                    EnrolledSection(state = state)
                    if (state.journey.showFeedback) {
                        FeedbackSection(
                            state = state,
                            onFeedbackChange = onFeedbackChange,
                            onSaveFeedback = onSaveFeedback,
                            onShareFeedback = onShareFeedback
                        )
                    }
                    if (state.journey.showReport) {
                        ReportSection(state = state, onSubmitReport = onSubmitReport)
                    }
                    FounderDebugReview(
                        status = state.status,
                        onApprove = onApprove,
                        onReject = onReject
                    )
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
    actionsEnabled: Boolean = true
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
        Spacer(Modifier.height(AppDimens.sectionGap))
        Button(
            onClick = onEnroll,
            enabled = actionsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FOUNDER_ENROLL)
        ) {
            Text(stringResource(R.string.founder_enroll))
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
                    FounderMilestone.TemporaryProUnlocked -> {
                        BodyText(stringResource(R.string.founder_milestone_pro_body))
                        Spacer(Modifier.height(AppDimens.sectionGap))
                        ChecklistSection(
                            title = stringResource(R.string.founder_milestone_next),
                            rows = state.journey.presentedChecklist()
                        )
                    }
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

@Composable
private fun milestoneTitle(milestone: FounderMilestone): String {
    return stringResource(
        when (milestone) {
            FounderMilestone.TemporaryProUnlocked -> R.string.founder_milestone_pro_title
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
private fun FeedbackSection(
    state: FounderProgramUiState,
    onFeedbackChange: (String) -> Unit,
    onSaveFeedback: () -> Unit,
    onShareFeedback: () -> Unit
) {
    val journey = state.journey
    if (!journey.acceptsContribution && !journey.feedbackSaved) {
        return
    }
    Spacer(Modifier.height(AppDimens.sectionGap))
    CompactEditorSection(
        title = stringResource(R.string.founder_feedback_title),
        modifier = Modifier.testTag(FOUNDER_FEEDBACK)
    ) {
        if (journey.acceptsContribution && !journey.feedbackSaved) {
            BodyText(stringResource(R.string.founder_feedback_why))
            Spacer(Modifier.height(AppDimens.itemGap))
            OutlinedTextField(
                value = state.feedbackDraft,
                onValueChange = onFeedbackChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.founder_feedback_hint)) },
                isError = state.feedbackBlank,
                supportingText = if (state.feedbackBlank) {
                    { Text(stringResource(R.string.founder_feedback_blank)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                minLines = 3
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(onClick = onSaveFeedback, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.founder_feedback_save))
            }
        } else {
            Text(
                text = stringResource(R.string.founder_feedback_saved),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            BodyText(stringResource(R.string.founder_feedback_share_optional))
            TextButton(onClick = onShareFeedback) {
                Text(stringResource(R.string.founder_feedback_share))
            }
        }
    }
}

@Composable
private fun ReportSection(
    state: FounderProgramUiState,
    onSubmitReport: () -> Unit
) {
    val journey = state.journey
    if (!journey.acceptsContribution && !journey.reportShared) {
        return
    }
    Spacer(Modifier.height(AppDimens.sectionGap))
    CompactEditorSection(
        title = stringResource(R.string.founder_report_title),
        modifier = Modifier.testTag(FOUNDER_REPORT)
    ) {
        BodyText(stringResource(R.string.founder_report_body))
        if (state.reportShareFailed) {
            Spacer(Modifier.height(AppDimens.itemGap))
            Text(
                text = stringResource(R.string.founder_report_share_failed),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(AppDimens.itemGap))
        if (journey.acceptsContribution && !journey.reportShared) {
            BodyText(stringResource(R.string.founder_report_submit_hint))
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(onClick = onSubmitReport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.founder_report_submit))
            }
        } else {
            Text(
                text = stringResource(R.string.founder_report_submitted),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onSubmitReport) {
                Text(stringResource(R.string.founder_report_share_again))
            }
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
    reportText: () -> String,
    onBack: () -> Unit,
    onEnroll: () -> Unit,
    onFeedbackChange: (String) -> Unit,
    onSaveFeedback: () -> Unit,
    onReportShareResult: suspend (Boolean) -> Unit,
    onAcknowledgeMilestone: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feedbackSubject = stringResource(R.string.founder_feedback_share_subject)
    val reportSubject = stringResource(R.string.founder_report_share_subject)
    FounderProgramScreen(
        state = state,
        onBack = onBack,
        onEnroll = onEnroll,
        onFeedbackChange = onFeedbackChange,
        onSaveFeedback = onSaveFeedback,
        onShareFeedback = {
            FounderShare.text(context, feedbackSubject, state.storedFeedback)
        },
        onSubmitReport = {
            val shared = FounderShare.text(context, reportSubject, reportText())
            if (!state.reportSubmitted) {
                scope.launch { onReportShareResult(shared) }
            }
        },
        onAcknowledgeMilestone = onAcknowledgeMilestone,
        onApprove = onApprove,
        onReject = onReject
    )
}
