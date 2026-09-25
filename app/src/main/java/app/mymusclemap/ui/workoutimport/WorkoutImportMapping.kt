package app.mymusclemap.ui.workoutimport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.workoutimport.WorkoutImportPreviewCopy
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.AppDimens

@Composable
fun WorkoutImportMappingSection(
    unresolved: List<WorkoutImportUnresolvedName>,
    onSelect: (String) -> Unit
) {
    val resources = LocalResources.current
    Column(modifier = Modifier.testTag("workout_import_mapping")) {
        Text(
            text = stringResource(R.string.workout_import_mapping_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.workout_import_mapping_body),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.workout_import_mapping_missing_catalog),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        unresolved.forEach { item ->
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(item.incomingName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.workout_import_occurrences, item.occurrenceCount),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    item.appearances.forEach { appearance ->
                        Text(
                            text = stringResource(
                                R.string.workout_import_appearance,
                                appearance.workoutName,
                                WorkoutImportPreviewCopy.dateLabel(appearance.date)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.selectedExerciseName != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.workout_import_selected_target,
                                item.selectedExerciseName
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item.errors.forEach { error ->
                        Text(
                            text = WorkoutImportMessages.error(resources, error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    item.warnings.forEach { warning ->
                        Text(
                            text = WorkoutImportMessages.warning(resources, warning),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onSelect(item.normalizedName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Text(
                            stringResource(
                                if (item.selectedExerciseId == null) {
                                    R.string.workout_import_select_exercise
                                } else {
                                    R.string.workout_import_change_exercise
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutImportMappingPicker(
    state: WorkoutImportUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val incoming = state.mappingPickerIncoming ?: return
    val needle = ExerciseNaming.normalize(state.mappingQuery)
    val filtered = state.catalog.filter { exercise ->
        needle.isEmpty() ||
            exercise.normalizedName.contains(needle) ||
            ExerciseNaming.normalize(exercise.notes.orEmpty()).contains(needle)
    }
    val active = filtered.filter { !it.archived }.sortedBy { it.name.lowercase() }
    val archived = filtered.filter { it.archived }.sortedBy { it.name.lowercase() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .testTag("workout_import_mapping_picker"),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.workout_import_select_exercise)) },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = AppDimens.screenPadding)
            ) {
                OutlinedTextField(
                    value = state.mappingQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.workout_import_search_exercise)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                if (active.isEmpty() && archived.isEmpty()) {
                    Text(
                        text = stringResource(R.string.workout_import_no_catalog_match),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (active.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.workout_import_active_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(active, key = { it.id }) { exercise ->
                                MappingExerciseRow(exercise) { onSelect(incoming, exercise.id) }
                            }
                        }
                        if (archived.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.workout_import_archived_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(archived, key = { it.id }) { exercise ->
                                MappingExerciseRow(exercise) { onSelect(incoming, exercise.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MappingExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(exercise.name, style = MaterialTheme.typography.titleSmall)
        Text(
            text = listOf(
                stringResource(exercise.measurementType.labelRes()),
                stringResource(exercise.resistanceBasis.labelRes()),
                stringResource(exercise.weightInterpretation.labelRes()),
                stringResource(exercise.primaryMuscle.labelRes())
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (exercise.archived) {
            Text(
                text = stringResource(R.string.exercise_archived_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
    HorizontalDivider()
}
