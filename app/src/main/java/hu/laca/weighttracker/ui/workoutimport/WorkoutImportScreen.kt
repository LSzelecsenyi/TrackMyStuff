package hu.laca.weighttracker.ui.workoutimport

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportPlan
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportPreviewCopy
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme

private val csvMimeTypes = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/csv",
    "*/*"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutImportScreen(
    state: WorkoutImportUiState,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onFilePicked: (Uri?) -> Unit,
    onOpenMappingPicker: (String) -> Unit,
    onDismissMappingPicker: () -> Unit,
    onMappingQueryChange: (String) -> Unit,
    onMapExercise: (String, Long) -> Unit,
    onToggleWorkout: (String) -> Unit,
    onRequestConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmImport: () -> Unit,
    onViewJournal: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onFilePicked(uri)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workout_import_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (state.canOpenConfirm && state.phase != WorkoutImportPhase.Success) {
                Surface(tonalElevation = 2.dp) {
                    Button(
                        onClick = onRequestConfirm,
                        enabled = !state.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.screenPadding)
                            .defaultMinSize(minHeight = AppDimens.minTouch)
                            .testTag("workout_import_confirm")
                    ) {
                        Text(
                            stringResource(
                                R.string.workout_import_confirm_action,
                                state.plan?.workoutCount ?: 0
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (state.phase) {
            WorkoutImportPhase.Success -> SuccessContent(
                state = state,
                onViewJournal = onViewJournal,
                modifier = Modifier.padding(innerPadding)
            )
            WorkoutImportPhase.Parsing, WorkoutImportPhase.Importing -> BusyContent(
                importing = state.phase == WorkoutImportPhase.Importing,
                modifier = Modifier.padding(innerPadding)
            )
            else -> ImportBody(
                state = state,
                onPickFile = {
                    onPickFile()
                    picker.launch(csvMimeTypes)
                },
                onOpenMappingPicker = onOpenMappingPicker,
                onToggleWorkout = onToggleWorkout,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
    if (state.mappingPickerIncoming != null) {
        WorkoutImportMappingPicker(
            state = state,
            onQueryChange = onMappingQueryChange,
            onSelect = onMapExercise,
            onDismiss = onDismissMappingPicker
        )
    }
    if (state.confirmVisible && state.plan != null) {
        ConfirmDialog(
            plan = state.plan,
            onConfirm = onConfirmImport,
            onDismiss = onDismissConfirm
        )
    }
}

@Composable
private fun ImportBody(
    state: WorkoutImportUiState,
    onPickFile: () -> Unit,
    onOpenMappingPicker: (String) -> Unit,
    onToggleWorkout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = AppDimens.screenPadding)
            .padding(bottom = AppDimens.scrollEndPadding)
            .testTag("workout_import_scroll")
    ) {
        Spacer(Modifier.height(12.dp))
        if (state.phase == WorkoutImportPhase.Idle) {
            Text(
                text = stringResource(R.string.workout_import_idle_body),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
        }
        if (state.fileName != null) {
            Text(
                text = stringResource(R.string.workout_import_file_name, state.fileName),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("workout_import_file_name")
            )
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onPickFile,
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag("workout_import_pick_file")
        ) {
            Text(
                stringResource(
                    if (state.fileName == null) {
                        R.string.workout_import_pick_file
                    } else {
                        R.string.workout_import_pick_other
                    }
                )
            )
        }
        if (state.unresolved.isNotEmpty()) {
            Spacer(Modifier.height(AppDimens.sectionGap))
            WorkoutImportMappingSection(
                unresolved = state.unresolved,
                onSelect = onOpenMappingPicker
            )
        }
        val errors = state.displayedErrors
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(AppDimens.sectionGap))
            IssueSection(
                title = stringResource(R.string.workout_import_errors_title),
                items = errors.map { WorkoutImportMessages.error(resources, it) },
                blocking = true,
                tag = "workout_import_errors"
            )
        }
        if (state.duplicateWorkoutNames.isNotEmpty()) {
            Spacer(Modifier.height(AppDimens.itemGap))
            IssueSection(
                title = stringResource(R.string.workout_import_errors_title),
                items = listOf(
                    if (state.duplicateWorkoutNames.size == 1) {
                        stringResource(R.string.workout_import_failure_duplicate)
                    } else {
                        stringResource(
                            R.string.workout_import_failure_duplicates,
                            state.duplicateWorkoutNames.joinToString()
                        )
                    }
                ),
                blocking = true,
                tag = "workout_import_duplicates"
            )
        }
        val warnings = state.displayedWarnings
        if (warnings.isNotEmpty() && state.phase != WorkoutImportPhase.Failure) {
            Spacer(Modifier.height(AppDimens.sectionGap))
            IssueSection(
                title = stringResource(R.string.workout_import_warnings_title),
                items = warnings.map { WorkoutImportMessages.warning(resources, it) },
                blocking = false,
                tag = "workout_import_warnings"
            )
        }
        if (state.phase == WorkoutImportPhase.Failure && state.failureKind != null && errors.isEmpty()) {
            Spacer(Modifier.height(AppDimens.sectionGap))
            Text(
                text = failureMessage(state.failureKind),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("workout_import_failure")
            )
        }
        val plan = state.plan
        if (plan != null && state.phase != WorkoutImportPhase.Failure) {
            Spacer(Modifier.height(AppDimens.sectionGap))
            WorkoutImportPreviewSection(
                fileName = state.fileName,
                plan = plan,
                warningCount = state.displayedWarnings.size,
                expandedWorkoutIds = state.expandedWorkoutIds,
                onToggleWorkout = onToggleWorkout
            )
        }
    }
}

@Composable
private fun BusyContent(importing: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (importing) R.string.workout_import_importing else R.string.workout_import_parsing
            ),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SuccessContent(
    state: WorkoutImportUiState,
    onViewJournal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imported = state.imported ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimens.screenPadding)
            .testTag("workout_import_success"),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(
                R.string.workout_import_success,
                imported.workoutCount,
                imported.completedSetCount
            ),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(12.dp))
        state.plan?.dateRange?.let { range ->
            Text(
                text = stringResource(
                    R.string.workout_import_success_range,
                    WorkoutImportPreviewCopy.dateRange(range)
                ),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.workout_import_success_counts,
                imported.exerciseCount,
                imported.completedSetCount,
                imported.skippedSetCount
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onViewJournal,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag("workout_import_view_journal")
        ) {
            Text(stringResource(R.string.workout_import_view_journal))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onViewJournal,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
        ) {
            Text(stringResource(R.string.workout_import_done))
        }
    }
}

@Composable
private fun ConfirmDialog(
    plan: WorkoutImportPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_import_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.workout_import_confirm_body,
                    plan.workoutCount,
                    plan.completedSetCount,
                    WorkoutImportPreviewCopy.dateRange(plan.dateRange)
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("workout_import_confirm_dialog")
            ) {
                Text(stringResource(R.string.workout_import_confirm_action, plan.workoutCount))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workout_import_cancel))
            }
        }
    )
}

@Composable
internal fun IssueSection(
    title: String,
    items: List<String>,
    blocking: Boolean,
    tag: String
) {
    val color = if (blocking) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(modifier = Modifier.testTag(tag)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        items.distinct().forEach { item ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = item,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

@Composable
private fun failureMessage(kind: WorkoutImportFailureKind): String {
    return when (kind) {
        is WorkoutImportFailureKind.Structural -> stringResource(R.string.workout_import_failure_structural)
        WorkoutImportFailureKind.FileTooLarge -> stringResource(R.string.workout_import_failure_too_large)
        WorkoutImportFailureKind.Unreadable -> stringResource(R.string.workout_import_failure_read)
        WorkoutImportFailureKind.PermissionDenied -> stringResource(R.string.workout_import_failure_permission)
        is WorkoutImportFailureKind.Duplicate -> {
            if (kind.workoutNames.size <= 1) {
                stringResource(R.string.workout_import_failure_duplicate)
            } else {
                stringResource(
                    R.string.workout_import_failure_duplicates,
                    kind.workoutNames.joinToString()
                )
            }
        }
        WorkoutImportFailureKind.StaleCatalog -> stringResource(R.string.workout_import_failure_stale)
        WorkoutImportFailureKind.Database -> stringResource(R.string.workout_import_failure_database)
        WorkoutImportFailureKind.PlanNotConfirmable -> stringResource(R.string.workout_import_failure_not_confirmable)
    }
}

@Preview(showBackground = true, name = "Import idle")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Import idle dark")
@Composable
private fun WorkoutImportIdlePreview() {
    WeightTrackerTheme {
        WorkoutImportScreen(
            state = WorkoutImportUiState(),
            onBack = {},
            onPickFile = {},
            onFilePicked = {},
            onOpenMappingPicker = {},
            onDismissMappingPicker = {},
            onMappingQueryChange = {},
            onMapExercise = { _, _ -> },
            onToggleWorkout = {},
            onRequestConfirm = {},
            onDismissConfirm = {},
            onConfirmImport = {},
            onViewJournal = {}
        )
    }
}
