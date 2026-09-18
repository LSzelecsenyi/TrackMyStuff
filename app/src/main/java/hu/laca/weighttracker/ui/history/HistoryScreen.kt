package hu.laca.weighttracker.ui.history

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.journal.JournalDateGroup
import hu.laca.weighttracker.domain.journal.JournalEntry
import hu.laca.weighttracker.domain.journal.JournalFilter
import hu.laca.weighttracker.domain.journal.JournalTimeline
import hu.laca.weighttracker.domain.journal.WeightJournalEntry
import hu.laca.weighttracker.domain.journal.WorkoutJournalEntry
import hu.laca.weighttracker.domain.locale.LocalizedLabelOrder
import hu.laca.weighttracker.domain.model.MeasurementListItem
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.ui.components.MeasurementEditorSheet
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.exercises.labelRes
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppTypeTokens
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val JOURNAL_ROOT = "journal-root"
internal const val JOURNAL_ADD = "journal-add"
internal const val JOURNAL_OVERFLOW_ANCHOR = "journal-overflow-anchor"
internal const val JOURNAL_OVERFLOW_BUTTON = "journal-overflow-button"
internal const val JOURNAL_OVERFLOW_MENU = "journal-overflow-menu"
internal const val JOURNAL_OVERFLOW_IMPORT = "journal-overflow-import"
internal const val JOURNAL_FILTER_ALL = "journal-filter-all"
internal const val JOURNAL_FILTER_WEIGHT = "journal-filter-weight"
internal const val JOURNAL_FILTER_WORKOUT = "journal-filter-workout"
internal val JOURNAL_FILTER_TAB_ORDER = listOf(
    JournalFilter.WORKOUT,
    JournalFilter.WEIGHT,
    JournalFilter.ALL
)
internal const val JOURNAL_LIST = "journal-list"
internal const val JOURNAL_EMPTY_RECORD = "journal-empty-record"

internal fun journalGroupTag(date: LocalDate): String = "journal-group-$date"

internal fun journalWeightRowTag(id: Long): String = "journal-weight-$id"

internal fun journalWorkoutRowTag(id: Long): String = "journal-workout-$id"

internal fun journalWorkoutOverflowAnchorTag(id: Long): String = "journal-workout-overflow-anchor-$id"

internal fun journalWorkoutOverflowButtonTag(id: Long): String = "journal-workout-overflow-button-$id"

internal fun journalWorkoutOverflowMenuTag(id: Long): String = "journal-workout-overflow-menu-$id"

internal fun journalWeightOverflowButtonTag(id: Long): String = "journal-weight-overflow-button-$id"

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onEditorDateChange: (LocalDate) -> Unit,
    onEditorWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissEditor: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onMessageConsumed: () -> Unit,
    onOpenImport: () -> Unit,
    onFilterSelected: (JournalFilter) -> Unit,
    onIncludeAbandoned: (Boolean) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onRequestDeleteWorkout: (Long) -> Unit,
    onDismissDeleteWorkout: () -> Unit,
    onConfirmDeleteWorkout: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val lastNavAt = remember { mutableMapOf<String, Long>() }
    val navigateOnce: (String, () -> Unit) -> Unit = { key, action ->
        val now = SystemClock.elapsedRealtime()
        val previous = lastNavAt[key] ?: (Long.MIN_VALUE / 2)
        if (now - previous >= 700L) {
            lastNavAt[key] = now
            action()
        }
    }
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(JOURNAL_ROOT),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding)
        ) {
            JournalHeader(
                onAdd = { navigateOnce("add", onAdd) },
                onOpenImport = { navigateOnce("import", onOpenImport) }
            )
            JournalFilterTabs(
                selected = state.filter,
                onSelected = onFilterSelected
            )
            if (state.filter == JournalFilter.WORKOUT || state.includeAbandoned) {
                AbandonedToggle(
                    checked = state.includeAbandoned,
                    onCheckedChange = onIncludeAbandoned
                )
            }
            val emptyKind = state.emptyKind
            if (emptyKind != null) {
                JournalEmptyState(
                    filter = state.filter,
                    onRecordWeight = { navigateOnce("add", onAdd) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                JournalTimelineList(
                    groups = state.timeline.groups,
                    listState = listState,
                    deletingWorkout = state.deletingWorkout,
                    onEdit = { date -> navigateOnce("edit") { onEdit(date) } },
                    onDelete = { date -> navigateOnce("deleteWeight") { onDelete(date) } },
                    onOpenWorkout = { id -> navigateOnce("open") { onOpenWorkout(id) } },
                    onRequestDeleteWorkout = { id ->
                        navigateOnce("deleteWorkout") { onRequestDeleteWorkout(id) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    state.editor?.let { editor ->
        MeasurementEditorSheet(
            state = editor,
            today = today,
            onDateChange = onEditorDateChange,
            onWeightChange = onEditorWeightChange,
            onSave = onSave,
            onDismiss = onDismissEditor,
            onDeleteRequest = onDeleteRequest,
            onDeleteDismiss = onDeleteDismiss,
            onDeleteConfirm = onDeleteConfirm
        )
    }
    state.pendingWorkoutDelete?.let { pending ->
        DeleteWorkoutDialog(
            name = pending.session.templateName,
            dateLabel = UiFormatters.longDate(pending.session.workoutDate),
            deleting = state.deletingWorkout,
            onDismiss = onDismissDeleteWorkout,
            onConfirm = onConfirmDeleteWorkout
        )
    }
}

@Composable
private fun JournalHeader(
    onAdd: () -> Unit,
    onOpenImport: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onAdd,
            modifier = Modifier
                .size(AppDimens.minTouch)
                .testTag(JOURNAL_ADD)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.action_record_weight)
            )
        }
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopEnd)
                .testTag(JOURNAL_OVERFLOW_ANCHOR),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(AppDimens.minTouch)
                    .testTag(JOURNAL_OVERFLOW_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more_overview)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.testTag(JOURNAL_OVERFLOW_MENU)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workout_import_action)) },
                    onClick = {
                        menuOpen = false
                        onOpenImport()
                    },
                    modifier = Modifier.testTag(JOURNAL_OVERFLOW_IMPORT)
                )
            }
        }
    }
}

@Composable
private fun JournalFilterTabs(
    selected: JournalFilter,
    onSelected: (JournalFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true }
    ) {
        JOURNAL_FILTER_TAB_ORDER.forEachIndexed { index, filter ->
            FilterTab(
                label = stringResource(filter.labelRes()),
                selected = selected == filter,
                testTag = filter.testTag(),
                traversalIndex = index.toFloat(),
                onClick = { onSelected(filter) }
            )
        }
    }
}

@Composable
private fun RowScope.FilterTab(
    label: String,
    selected: Boolean,
    testTag: String,
    traversalIndex: Float,
    onClick: () -> Unit
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.Tab
                this.traversalIndex = traversalIndex
            }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AppTypeTokens.sectionTitle,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

@Composable
private fun AbandonedToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.journal_show_abandoned),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun JournalEmptyState(
    filter: JournalFilter,
    onRecordWeight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = when (filter) {
        JournalFilter.ALL -> stringResource(R.string.journal_empty)
        JournalFilter.WEIGHT -> stringResource(R.string.journal_empty_weight)
        JournalFilter.WORKOUT -> stringResource(R.string.journal_empty_workout)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = AppDimens.sectionGap),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = message,
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (filter == JournalFilter.WEIGHT) {
            Spacer(Modifier.height(AppDimens.itemGap))
            TextButton(
                onClick = onRecordWeight,
                modifier = Modifier
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(JOURNAL_EMPTY_RECORD)
            ) {
                Text(stringResource(R.string.action_record_weight))
            }
        }
    }
}

@Composable
private fun JournalTimelineList(
    groups: List<JournalDateGroup>,
    listState: LazyListState,
    deletingWorkout: Boolean,
    onEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onRequestDeleteWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(JOURNAL_LIST),
        state = listState,
        contentPadding = PaddingValues(bottom = AppDimens.scrollEndPadding)
    ) {
        groups.forEachIndexed { index, group ->
            if (index > 0) {
                item(key = "divider-${group.date}") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(AppDimens.sectionDividerSpace))
                        HorizontalDivider(
                            thickness = AppDimens.strokeThin,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(AppDimens.sectionDividerSpace))
                    }
                }
            }
            item(key = "header-${group.date}") {
                Text(
                    text = UiFormatters.longDate(group.date),
                    style = AppTypeTokens.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimens.headerStackGap)
                        .testTag(journalGroupTag(group.date))
                )
            }
            items(group.entries, key = { it.key }) { entry ->
                JournalEntryRow(
                    entry = entry,
                    deleting = deletingWorkout,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onOpenWorkout = onOpenWorkout,
                    onRequestDeleteWorkout = onRequestDeleteWorkout
                )
            }
        }
    }
}

@Composable
private fun JournalEntryRow(
    entry: JournalEntry,
    deleting: Boolean,
    onEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onRequestDeleteWorkout: (Long) -> Unit
) {
    when (entry) {
        is WeightJournalEntry -> WeightJournalRow(
            entry = entry,
            onEdit = { onEdit(entry.item.measurement.date) },
            onDelete = { onDelete(entry.item.measurement.date) }
        )
        is WorkoutJournalEntry -> WorkoutJournalRow(
            entry = entry,
            deleting = deleting,
            onOpen = { onOpenWorkout(entry.summary.session.id) },
            onRequestDelete = { onRequestDeleteWorkout(entry.summary.session.id) }
        )
    }
}

@Composable
private fun WeightJournalRow(
    entry: WeightJournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val item = entry.item
    val weightLabel = UiFormatters.weightKg(item.measurement.weightKg)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(journalWeightRowTag(item.measurement.id)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(vertical = AppDimens.headerStackGap)
                .semantics {
                    role = Role.Button
                    contentDescription = weightLabel
                }
        ) {
            Text(
                text = weightLabel,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            val difference = item.differenceFromPreviousKg
            Text(
                text = if (difference == null) {
                    stringResource(R.string.no_previous_measurement)
                } else {
                    stringResource(
                        R.string.change_from_previous_value,
                        UiFormatters.signedWeightKg(difference)
                    )
                },
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.wrapContentSize(Alignment.TopEnd),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(AppDimens.minTouch)
                    .testTag(journalWeightOverflowButtonTag(item.measurement.id))
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.weight_row_more_actions, weightLabel)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkoutJournalRow(
    entry: WorkoutJournalEntry,
    deleting: Boolean,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val summary = entry.summary
    val started = Instant.ofEpochMilli(summary.session.startedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(clockFormat)
    val muscleLabels = LocalizedLabelOrder.sorted(
        items = summary.primaryMuscles.map { muscle ->
            stringResource(muscle.labelRes()) to muscle.name
        },
        label = { it.first },
        key = { it.second }
    ).map { it.first }
    val description = buildString {
        append(summary.session.templateName)
        append(", ")
        append(if (entry.abandoned) "elvetve" else "befejezve")
        append(", ")
        append(started)
        append(" · ")
        append(summary.durationLabel)
    }
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(journalWorkoutRowTag(summary.session.id)),
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(vertical = AppDimens.headerStackGap)
                .semantics {
                    role = Role.Button
                    contentDescription = description
                },
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.session.templateName,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = stringResource(
                    if (entry.abandoned) {
                        R.string.workout_status_abandoned
                    } else {
                        R.string.workout_status_completed
                    }
                ),
                style = AppTypeTokens.statCaption,
                color = if (entry.abandoned) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = "$started · ${summary.durationLabel}",
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = stringResource(
                    R.string.journal_workout_summary,
                    summary.exerciseCount,
                    summary.progress.completed,
                    summary.progress.total
                ),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (summary.progress.skipped > 0) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = stringResource(R.string.journal_workout_skipped, summary.progress.skipped),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (muscleLabels.isNotEmpty()) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = muscleLabels.joinToString(),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopEnd)
                .testTag(journalWorkoutOverflowAnchorTag(summary.session.id)),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = { menuOpen = true },
                enabled = !deleting,
                modifier = Modifier
                    .size(AppDimens.minTouch)
                    .testTag(journalWorkoutOverflowButtonTag(summary.session.id))
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(
                        R.string.workout_card_more_actions,
                        summary.session.templateName
                    )
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.testTag(journalWorkoutOverflowMenuTag(summary.session.id))
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_delete_workout),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRequestDelete()
                    },
                    enabled = !deleting,
                    modifier = Modifier.testTag(WORKOUT_DELETE_ACTION)
                )
            }
        }
    }
}

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun JournalFilter.labelRes(): Int {
    return when (this) {
        JournalFilter.ALL -> R.string.journal_filter_all
        JournalFilter.WEIGHT -> R.string.journal_filter_weight
        JournalFilter.WORKOUT -> R.string.journal_filter_workout
    }
}

private fun JournalFilter.testTag(): String {
    return when (this) {
        JournalFilter.ALL -> JOURNAL_FILTER_ALL
        JournalFilter.WEIGHT -> JOURNAL_FILTER_WEIGHT
        JournalFilter.WORKOUT -> JOURNAL_FILTER_WORKOUT
    }
}

@Preview(showBackground = true, name = "History light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "History dark")
@Composable
private fun HistoryPreview() {
    val date = LocalDate.of(2026, 9, 17)
    WeightTrackerTheme {
        HistoryScreen(
            state = HistoryUiState(
                loading = false,
                timeline = JournalTimeline(
                    groups = listOf(
                        JournalDateGroup(
                            date = date,
                            entries = listOf(
                                WeightJournalEntry(
                                    MeasurementListItem(
                                        measurement = WeightMeasurement(1, date, 87.9, 0, 0),
                                        differenceFromPreviousKg = 0.3
                                    )
                                )
                            )
                        )
                    ),
                    filter = JournalFilter.ALL,
                    includeAbandoned = false,
                    emptyKind = null
                )
            ),
            today = date,
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onEditorDateChange = {},
            onEditorWeightChange = {},
            onSave = {},
            onDismissEditor = {},
            onDeleteRequest = {},
            onDeleteDismiss = {},
            onDeleteConfirm = {},
            onMessageConsumed = {},
            onOpenImport = {},
            onFilterSelected = {},
            onIncludeAbandoned = {},
            onOpenWorkout = {},
            onRequestDeleteWorkout = {},
            onDismissDeleteWorkout = {},
            onConfirmDeleteWorkout = {}
        )
    }
}
