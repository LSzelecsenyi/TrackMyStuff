package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.WeightParseError
import hu.laca.weighttracker.domain.workout.ActiveSessionSummary
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.ElapsedTime
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import hu.laca.weighttracker.ui.components.SettingsAction
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppShapeTokens
import hu.laca.weighttracker.ui.theme.AppTypeTokens
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val HUB_ROOT = "workout-hub-root"
internal const val HUB_SETTINGS = "workout-hub-settings"
internal const val HUB_ACTIVE = "workout-hub-active"
internal const val HUB_RESUME = "workout-hub-resume"
internal const val HUB_START_SECTION = "workout-hub-start-section"
internal const val HUB_RECENT = "workout-hub-recent"
internal const val HUB_MANAGE_TEMPLATES = "workout-hub-manage-templates"
internal const val HUB_MANAGE_EXERCISES = "workout-hub-manage-exercises"
internal const val HUB_EMPTY_CREATE_TEMPLATE = "workout-hub-create-template"
internal const val HUB_START_SHEET = "workout-hub-start-sheet"
internal const val HUB_START_CONFIRM = "workout-hub-start-confirm"
internal const val HUB_START_CANCEL = "workout-hub-start-cancel"
internal const val HUB_START_WEIGHT = "workout-hub-start-weight"

internal fun hubStartRowTag(id: Long): String = "workout-hub-start-row-$id"
internal fun hubStartPlayTag(id: Long): String = "workout-hub-start-play-$id"

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
    val resources = androidx.compose.ui.platform.LocalResources.current
    var openedKeys by remember { mutableStateOf(setOf<String>()) }
    var startRequested by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                openedKeys = emptySet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.startDraft) {
        if (state.startDraft == null) {
            startRequested = false
        }
    }
    val navigateOnce: (String, () -> Unit) -> Unit = { key, action ->
        if (key !in openedKeys) {
            openedKeys = openedKeys + key
            action()
        }
    }
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
        modifier = Modifier
            .fillMaxSize()
            .testTag(HUB_ROOT),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 8.dp, bottom = AppDimens.scrollEndPadding)
        ) {
            HubHeader(onOpenSettings = { navigateOnce("settings", onOpenSettings) })
            state.activeSession?.let { active ->
                HubSectionDivider()
                ActiveSessionRow(
                    summary = active,
                    onResume = {
                        navigateOnce("resume-${active.session.id}") {
                            onResume(active.session.id)
                        }
                    }
                )
            }
            HubSectionDivider()
            StartTemplatesSection(
                templates = state.templates,
                blocked = state.activeSession != null,
                starting = state.isStarting || startRequested,
                onStart = { item ->
                    if (state.activeSession != null || state.startDraft != null || state.isStarting || startRequested) {
                        return@StartTemplatesSection
                    }
                    startRequested = true
                    onStartTemplate(item)
                },
                onCreateTemplate = { navigateOnce("create-template", onOpenTemplates) }
            )
            HubSectionDivider()
            RecentWorkoutRow(
                summary = state.recentCompleted,
                loading = state.loading,
                onOpen = { id -> navigateOnce("recent-$id") { onOpenRecent(id) } }
            )
            HubSectionDivider()
            ManageSection(
                templateCount = state.activeTemplateCount,
                exerciseCount = state.activeCount,
                loading = state.loading,
                onOpenTemplates = { navigateOnce("templates", onOpenTemplates) },
                onOpenCatalog = { navigateOnce("catalog", onOpenCatalog) }
            )
        }
    }
    state.startDraft?.let { draft ->
        StartWorkoutSheet(
            draft = draft,
            starting = state.isStarting,
            onDismiss = onDismissStart,
            onWeightChange = onStartWeightChange,
            onConfirm = onConfirmStart
        )
    }
}

@Composable
private fun HubHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.nav_workout),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        SettingsAction(
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .size(AppDimens.minTouch)
                .testTag(HUB_SETTINGS)
        )
    }
}

@Composable
private fun HubSectionDivider() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(AppDimens.sectionDividerSpace))
        HorizontalDivider(
            thickness = AppDimens.strokeThin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(AppDimens.sectionDividerSpace))
    }
}

@Composable
private fun ActiveSessionRow(
    summary: ActiveSessionSummary,
    onResume: () -> Unit
) {
    val elapsed = remember(summary.session.startedAt) {
        ElapsedTime.format(summary.session.startedAt, System.currentTimeMillis())
    }
    val resumeLabel = stringResource(R.string.action_resume_named, summary.session.templateName)
    val fraction = if (summary.totalSets == 0) {
        0f
    } else {
        (summary.completedSets + summary.skippedSets).toFloat() / summary.totalSets.toFloat()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onResume)
            .testTag(HUB_ACTIVE)
            .semantics {
                role = Role.Button
                contentDescription = resumeLabel
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .width(3.dp)
                .height(56.dp)
                .clip(AppShapeTokens.compact)
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hub_in_progress_kicker),
                style = AppTypeTokens.sectionKicker,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = summary.session.templateName,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = "$elapsed · ${stringResource(R.string.hub_active_progress, summary.completedSets, summary.totalSets)}",
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
        TextButton(
            onClick = onResume,
            modifier = Modifier
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag(HUB_RESUME)
        ) {
            Text(
                text = stringResource(R.string.action_resume_workout),
                style = AppTypeTokens.statCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StartTemplatesSection(
    templates: List<TemplateListItem>,
    blocked: Boolean,
    starting: Boolean,
    onStart: (TemplateListItem) -> Unit,
    onCreateTemplate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HUB_START_SECTION)
    ) {
        Text(
            text = stringResource(R.string.hub_start_section),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        when {
            blocked -> {
                Text(
                    text = stringResource(R.string.hub_start_blocked),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            templates.isEmpty() -> {
                Text(
                    text = stringResource(R.string.workout_templates_empty),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                TextButton(
                    onClick = onCreateTemplate,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(HUB_EMPTY_CREATE_TEMPLATE)
                ) {
                    Text(stringResource(R.string.action_create_template))
                }
            }
            else -> {
                templates.forEachIndexed { index, item ->
                    StartTemplateRow(
                        item = item,
                        starting = starting,
                        onStart = { onStart(item) }
                    )
                    if (index != templates.lastIndex) {
                        HorizontalDivider(
                            thickness = AppDimens.strokeThin,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartTemplateRow(
    item: TemplateListItem,
    starting: Boolean,
    onStart: () -> Unit
) {
    val startLabel = stringResource(R.string.action_start_template, item.template.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(hubStartRowTag(item.template.id)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = item.template.name,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = stringResource(
                    R.string.template_row_meta,
                    item.exerciseCount,
                    item.setCount
                ),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onStart,
            enabled = !starting,
            modifier = Modifier
                .size(AppDimens.minTouch)
                .testTag(hubStartPlayTag(item.template.id))
                .semantics { contentDescription = startLabel }
        ) {
            if (starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RecentWorkoutRow(
    summary: WorkoutSessionSummary?,
    loading: Boolean,
    onOpen: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.hub_recent_title),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        if (summary == null) {
            if (!loading) {
                Text(
                    text = stringResource(R.string.hub_recent_empty),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }
        val openLabel = stringResource(R.string.hub_recent_open, summary.session.templateName)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .clickable { onOpen(summary.session.id) }
                .testTag(HUB_RECENT)
                .semantics {
                    role = Role.Button
                    contentDescription = openLabel
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = summary.session.templateName,
                    style = AppTypeTokens.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = listOf(
                        UiFormatters.longDate(summary.session.workoutDate),
                        summary.durationLabel,
                        stringResource(
                            R.string.journal_workout_summary,
                            summary.exerciseCount,
                            summary.progress.completed,
                            summary.progress.total
                        )
                    ).joinToString(" · "),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary.session.status == SessionStatus.COMPLETED) {
                    Spacer(Modifier.height(AppDimens.statSecondaryGap))
                    Text(
                        text = stringResource(R.string.workout_status_completed),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManageSection(
    templateCount: Int,
    exerciseCount: Int,
    loading: Boolean,
    onOpenTemplates: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.hub_manage_section),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        ManageRow(
            title = stringResource(R.string.templates_title),
            subtitle = if (loading) {
                null
            } else {
                stringResource(R.string.workout_templates_active_count, templateCount)
            },
            onClick = onOpenTemplates,
            testTag = HUB_MANAGE_TEMPLATES
        )
        HorizontalDivider(
            thickness = AppDimens.strokeThin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        ManageRow(
            title = stringResource(R.string.exercises_title),
            subtitle = if (loading) {
                null
            } else {
                stringResource(R.string.workout_catalog_active_count, exerciseCount)
            },
            onClick = onOpenCatalog,
            testTag = HUB_MANAGE_EXERCISES
        )
    }
}

@Composable
private fun ManageRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics {
                role = Role.Button
                contentDescription = title
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = subtitle,
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartWorkoutSheet(
    draft: StartWorkoutDraft,
    starting: Boolean,
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
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    ModalBottomSheet(
        onDismissRequest = { if (!starting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(HUB_START_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = AppDimens.itemGap)
        ) {
            Text(
                text = stringResource(R.string.start_workout_title),
                style = AppTypeTokens.sectionKicker,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = draft.template.template.name,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = stringResource(
                    R.string.start_workout_body,
                    draft.template.exerciseCount,
                    draft.template.setCount
                ),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Text(
                text = sourceText,
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            CompactStartWeightField(
                value = draft.weightText,
                onValueChange = onWeightChange,
                isError = draft.weightError != null,
                supportingText = draft.weightError?.let { stringResource(it.labelRes()) }
                    ?: stringResource(R.string.start_weight_optional),
                enabled = !starting,
                onDone = { focusManager.clearFocus(force = true) }
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(
                onClick = onConfirm,
                enabled = !starting && draft.weightError == null,
                shape = AppShapeTokens.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(HUB_START_CONFIRM)
            ) {
                if (starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.action_start_workout))
                }
            }
            TextButton(
                onClick = { if (!starting) onDismiss() },
                enabled = !starting,
                modifier = Modifier
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(HUB_START_CANCEL)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun CompactSheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .size(width = 32.dp, height = 3.dp)
            .clip(AppShapeTokens.compact)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun CompactStartWeightField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supportingText: String,
    enabled: Boolean,
    onDone: () -> Unit
) {
    val label = stringResource(R.string.field_weight)
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != field.text) {
            val cursor = field.selection.start.coerceIn(0, value.length)
            field = TextFieldValue(text = value, selection = TextRange(cursor))
        }
    }
    val lineColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = field,
                onValueChange = { incoming ->
                    field = incoming
                    onValueChange(incoming.text)
                },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label }
                    .testTag(HUB_START_WEIGHT),
                textStyle = AppTypeTokens.statHero.copy(color = MaterialTheme.colorScheme.onBackground),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() })
            )
            Text(
                text = stringResource(R.string.unit_kg),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.strokeThin)
                .background(lineColor)
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        Text(
            text = supportingText,
            style = AppTypeTokens.statCaption,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
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
