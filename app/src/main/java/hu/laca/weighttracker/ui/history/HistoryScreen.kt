package hu.laca.weighttracker.ui.history

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.journal.JournalEmptyKind
import hu.laca.weighttracker.domain.journal.JournalEntry
import hu.laca.weighttracker.domain.journal.JournalFilter
import hu.laca.weighttracker.domain.journal.WeightJournalEntry
import hu.laca.weighttracker.domain.journal.WorkoutJournalEntry
import hu.laca.weighttracker.domain.model.MeasurementListItem
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.SessionProgress
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.WorkoutSession
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import hu.laca.weighttracker.ui.components.MeasurementEditorSheet
import hu.laca.weighttracker.ui.components.MeasurementRow
import hu.laca.weighttracker.ui.components.SegmentedControl
import hu.laca.weighttracker.ui.components.SettingsAction
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.exercises.labelRes
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onFilterSelected: (JournalFilter) -> Unit,
    onIncludeAbandoned: (Boolean) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onRequestDeleteWorkout: (Long) -> Unit,
    onDismissDeleteWorkout: () -> Unit,
    onConfirmDeleteWorkout: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    IconButton(
                        onClick = onOpenImport,
                        modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = stringResource(R.string.workout_import_action)
                        )
                    }
                    SettingsAction(onOpenSettings)
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_add_measurement)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val filters = JournalFilter.entries
            SegmentedControl(
                options = filters.map { stringResource(it.labelRes()) },
                selectedIndex = filters.indexOf(state.filter).coerceAtLeast(0),
                onSelected = { onFilterSelected(filters[it]) },
                modifier = Modifier.padding(horizontal = AppDimens.screenPadding, vertical = 8.dp)
            )
            if (state.filter == JournalFilter.WORKOUT || state.includeAbandoned) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.screenPadding)
                        .defaultMinSize(minHeight = AppDimens.minTouch),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.journal_show_abandoned),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.includeAbandoned,
                        onCheckedChange = onIncludeAbandoned
                    )
                }
            }
            val emptyKind = state.emptyKind
            if (emptyKind != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(
                            if (emptyKind == JournalEmptyKind.FilterEmpty) {
                                R.string.journal_filter_empty
                            } else {
                                R.string.journal_empty
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = AppDimens.screenPadding,
                        vertical = 8.dp
                    )
                ) {
                    state.timeline.groups.forEach { group ->
                        item(key = "header-${group.date}") {
                            Text(
                                text = UiFormatters.longDate(group.date),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(group.entries, key = { it.key }) { entry ->
                            JournalEntryRow(
                                entry = entry,
                                deleting = state.deletingWorkout,
                                onEdit = onEdit,
                                onDelete = onDelete,
                                onOpenWorkout = onOpenWorkout,
                                onRequestDeleteWorkout = onRequestDeleteWorkout
                            )
                        }
                    }
                    item { Spacer(Modifier.height(AppDimens.scrollEndPadding)) }
                }
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
private fun JournalEntryRow(
    entry: JournalEntry,
    deleting: Boolean,
    onEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onRequestDeleteWorkout: (Long) -> Unit
) {
    when (entry) {
        is WeightJournalEntry -> MeasurementRow(
            item = entry.item,
            onEdit = { onEdit(entry.item.measurement.date) },
            onDelete = { onDelete(entry.item.measurement.date) }
        )
        is WorkoutJournalEntry -> WorkoutJournalCard(
            entry = entry,
            deleting = deleting,
            onOpen = { onOpenWorkout(entry.summary.session.id) },
            onRequestDelete = { onRequestDeleteWorkout(entry.summary.session.id) }
        )
    }
}

@Composable
private fun WorkoutJournalCard(
    entry: WorkoutJournalEntry,
    deleting: Boolean,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val summary = entry.summary
    val started = Instant.ofEpochMilli(summary.session.startedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("H:mm"))
    val description = buildString {
        append(summary.session.templateName)
        append(", ")
        append(if (entry.abandoned) "elvetve" else "befejezve")
        append(", ")
        append(summary.durationLabel)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = if (entry.abandoned) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.session.templateName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuOpen = true },
                        enabled = !deleting,
                        modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(
                                R.string.workout_card_more_actions,
                                summary.session.templateName
                            )
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            Text(
                text = stringResource(
                    if (entry.abandoned) R.string.workout_status_abandoned else R.string.workout_status_completed
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.abandoned) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
            Text(
                text = stringResource(R.string.journal_started, started),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.journal_duration, summary.durationLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.journal_workout_summary,
                    summary.exerciseCount,
                    summary.progress.completed,
                    summary.progress.total
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (summary.progress.skipped > 0) {
                Text(
                    text = stringResource(R.string.journal_workout_skipped, summary.progress.skipped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary.primaryMuscles.isNotEmpty()) {
                Text(
                    text = summary.primaryMuscles.map { stringResource(it.labelRes()) }.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
            ) {
                Text(stringResource(R.string.action_open_details))
            }
        }
    }
}

private fun JournalFilter.labelRes(): Int {
    return when (this) {
        JournalFilter.ALL -> R.string.journal_filter_all
        JournalFilter.WEIGHT -> R.string.journal_filter_weight
        JournalFilter.WORKOUT -> R.string.journal_filter_workout
    }
}

@Preview(showBackground = true, name = "History light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "History dark")
@Composable
private fun HistoryPreview() {
    val date = LocalDate.of(2026, 3, 11)
    WeightTrackerTheme {
        HistoryScreen(
            state = HistoryUiState(
                loading = false,
                timeline = hu.laca.weighttracker.domain.journal.JournalTimeline(
                    groups = listOf(
                        hu.laca.weighttracker.domain.journal.JournalDateGroup(
                            date = date,
                            entries = listOf(
                                WeightJournalEntry(
                                    MeasurementListItem(
                                        measurement = WeightMeasurement(1, date, 82.4, 0, 0),
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
            onOpenSettings = {},
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
