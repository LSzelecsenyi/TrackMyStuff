package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.WeightParseError
import hu.laca.weighttracker.domain.workout.ActiveSessionSummary
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import hu.laca.weighttracker.ui.components.SettingsAction
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.theme.AppDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHubScreen(
    state: WorkoutHubUiState,
    onOpenSettings: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenCatalog: () -> Unit,
    onStartTemplate: (TemplateListItem) -> Unit,
    onResume: (Long) -> Unit,
    onDismissStart: () -> Unit,
    onStartWeightChange: (String) -> Unit,
    onConfirmStart: () -> Unit,
    onStartedConsumed: () -> Unit,
    onOpenStarted: (Long) -> Unit,
    onMessageConsumed: () -> Unit,
    onOpenRecent: (Long) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(message.labelRes()))
        onMessageConsumed()
    }
    LaunchedEffect(state.startedSessionId) {
        val id = state.startedSessionId ?: return@LaunchedEffect
        onOpenStarted(id)
        onStartedConsumed()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_workout)) },
                actions = { SettingsAction(onOpenSettings) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 8.dp, bottom = AppDimens.scrollEndPadding)
        ) {
            Text(
                text = stringResource(R.string.workout_hub_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.sectionGap))
            val active = state.activeSession
            if (active != null) {
                ActiveSessionCard(summary = active, onResume = { onResume(active.session.id) })
            } else {
                StartTemplatesCard(
                    templates = state.templates,
                    onStart = onStartTemplate,
                    onOpenTemplates = onOpenTemplates
                )
            }
            state.recentCompleted?.let { recent ->
                Spacer(Modifier.height(AppDimens.sectionGap))
                RecentWorkoutCard(summary = recent, onOpen = { onOpenRecent(recent.session.id) })
            } ?: run {
                if (!state.loading) {
                    Spacer(Modifier.height(AppDimens.sectionGap))
                    Text(
                        text = stringResource(R.string.hub_recent_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(AppDimens.sectionGap))
            HubCard(
                title = stringResource(R.string.templates_title),
                subtitle = stringResource(R.string.workout_templates_subtitle),
                body = stringResource(R.string.workout_templates_body),
                countText = when {
                    state.loading -> null
                    state.activeTemplateCount == 0 && state.archivedTemplateCount == 0 ->
                        stringResource(R.string.workout_templates_empty)
                    state.archivedTemplateCount > 0 -> stringResource(
                        R.string.workout_templates_counts,
                        state.activeTemplateCount,
                        state.archivedTemplateCount
                    )
                    else -> stringResource(
                        R.string.workout_templates_active_count,
                        state.activeTemplateCount
                    )
                },
                action = stringResource(R.string.action_manage_templates),
                onAction = onOpenTemplates
            )
            Spacer(Modifier.height(AppDimens.sectionGap))
            HubCard(
                title = stringResource(R.string.exercises_title),
                subtitle = stringResource(R.string.workout_catalog_subtitle),
                body = stringResource(R.string.workout_catalog_body),
                countText = when {
                    state.loading -> null
                    state.activeCount == 0 && state.archivedCount == 0 ->
                        stringResource(R.string.workout_catalog_empty)
                    state.archivedCount > 0 -> stringResource(
                        R.string.workout_catalog_counts,
                        state.activeCount,
                        state.archivedCount
                    )
                    else -> stringResource(R.string.workout_catalog_active_count, state.activeCount)
                },
                action = stringResource(R.string.action_manage_exercises),
                onAction = onOpenCatalog
            )
        }
    }
    state.startDraft?.let { draft ->
        StartWorkoutDialog(
            draft = draft,
            onDismiss = onDismissStart,
            onWeightChange = onStartWeightChange,
            onConfirm = onConfirmStart
        )
    }
}

@Composable
private fun ActiveSessionCard(
    summary: ActiveSessionSummary,
    onResume: () -> Unit
) {
    val started = Instant.ofEpochMilli(summary.session.startedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("H:mm"))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(AppDimens.heroPadding)) {
            Text(
                text = summary.session.templateName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.active_session_started, started),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (summary.totalSets == 0) 0f
                    else (summary.completedSets + summary.skippedSets).toFloat() / summary.totalSets
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.active_session_progress,
                    summary.completedSets,
                    summary.totalSets - summary.skippedSets
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (summary.skippedSets > 0) {
                Text(
                    text = stringResource(R.string.active_session_skipped, summary.skippedSets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            summary.currentExerciseName?.let { name ->
                Text(
                    text = stringResource(R.string.active_session_current, name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_resume_workout))
            }
        }
    }
}

@Composable
private fun RecentWorkoutCard(
    summary: WorkoutSessionSummary,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(modifier = Modifier.padding(AppDimens.heroPadding)) {
            Text(
                text = stringResource(R.string.hub_recent_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = summary.session.templateName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = UiFormatters.longDate(summary.session.workoutDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = stringResource(R.string.journal_duration, summary.durationLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = stringResource(
                    R.string.journal_workout_summary,
                    summary.exerciseCount,
                    summary.progress.completed,
                    summary.progress.total
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_details))
            }
        }
    }
}

@Composable
private fun StartTemplatesCard(
    templates: List<TemplateListItem>,
    onStart: (TemplateListItem) -> Unit,
    onOpenTemplates: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(AppDimens.heroPadding)) {
            Text(
                text = stringResource(R.string.action_start_workout),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_active_templates_to_start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenTemplates, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_manage_templates))
                }
            } else {
                templates.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = item.template.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(
                                    R.string.template_row_meta,
                                    item.exerciseCount,
                                    item.setCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Button(onClick = { onStart(item) }) {
                            Text(stringResource(R.string.action_start_workout))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartWorkoutDialog(
    draft: StartWorkoutDraft,
    onDismiss: () -> Unit,
    onWeightChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val sourceText = when (draft.proposal.source) {
        BodyWeightSource.MEASURED_SAME_DAY -> stringResource(
            R.string.start_weight_same_day,
            draft.proposal.kilograms?.let(UiFormatters::weightKg).orEmpty()
        )
        BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT -> stringResource(
            R.string.start_weight_previous,
            draft.proposal.sourceDate?.let { formatMonthDay(it) }.orEmpty(),
            draft.proposal.kilograms?.let(UiFormatters::weightKg).orEmpty()
        )
        BodyWeightSource.MANUAL -> stringResource(R.string.start_weight_manual)
        BodyWeightSource.UNKNOWN -> stringResource(R.string.start_weight_none)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.start_workout_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            Text(draft.template.template.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.start_workout_body,
                    draft.template.exerciseCount,
                    draft.template.setCount
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(sourceText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.weightText,
                onValueChange = onWeightChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_weight)) },
                isError = draft.weightError != null,
                supportingText = {
                    Text(
                        draft.weightError?.let { stringResource(it.labelRes()) }
                            ?: stringResource(R.string.start_weight_optional)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onConfirm,
                enabled = draft.weightError == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
            ) {
                Text(stringResource(R.string.action_start_workout))
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    body: String,
    countText: String?,
    action: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(modifier = Modifier.padding(AppDimens.heroPadding)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (countText != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(action)
            }
        }
    }
}

private fun formatMonthDay(date: java.time.LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("MMMM d.", Locale.forLanguageTag("hu-HU")))
}

fun WorkoutHubMessage.labelRes(): Int {
    return when (this) {
        WorkoutHubMessage.AlreadyActive -> R.string.message_workout_already_active
        WorkoutHubMessage.TemplateArchived -> R.string.message_workout_template_archived
        WorkoutHubMessage.TemplateEmpty -> R.string.message_workout_template_empty
        WorkoutHubMessage.TemplateNotFound -> R.string.message_workout_template_empty
        WorkoutHubMessage.WorkoutFinished -> R.string.message_workout_finished
        WorkoutHubMessage.WorkoutAbandoned -> R.string.message_workout_abandoned
    }
}

fun WeightParseError.labelRes(): Int {
    return when (this) {
        WeightParseError.Empty -> R.string.error_weight_empty
        WeightParseError.Malformed, WeightParseError.NotFinite -> R.string.error_weight_malformed
        WeightParseError.TooManyDecimals -> R.string.error_weight_decimals
        WeightParseError.OutOfRange -> R.string.error_weight_range
    }
}
