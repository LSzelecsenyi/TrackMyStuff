package hu.laca.weighttracker.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.ArchiveFilter
import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.ui.components.SegmentedControl
import hu.laca.weighttracker.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(
    state: ExerciseListUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onCategoryFilter: (ExerciseCategory?) -> Unit,
    onMuscleFilter: (MuscleGroup?) -> Unit,
    onArchiveFilter: (ArchiveFilter) -> Unit,
    onClearFilters: () -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onRequestDelete: (Exercise) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onMessageConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(message.labelRes()))
        onMessageConsumed()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_add_exercise)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.exercise_search)) }
            )
            Spacer(Modifier.height(12.dp))
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.exercise_filter_active),
                    stringResource(R.string.exercise_filter_archived)
                ),
                selectedIndex = if (state.archiveFilter == ArchiveFilter.ARCHIVED) 1 else 0,
                onSelected = { index ->
                    onArchiveFilter(if (index == 1) ArchiveFilter.ARCHIVED else ArchiveFilter.ACTIVE)
                }
            )
            Spacer(Modifier.height(8.dp))
            CatalogDropdown(
                label = stringResource(R.string.exercise_filter_category),
                selected = state.category,
                options = ExerciseCategory.entries,
                optionLabel = { stringResource(it.labelRes()) },
                noneLabel = stringResource(R.string.exercise_filter_all_categories),
                onSelected = onCategoryFilter
            )
            Spacer(Modifier.height(8.dp))
            CatalogDropdown(
                label = stringResource(R.string.exercise_filter_muscle),
                selected = state.muscle,
                options = MuscleGroup.entries,
                optionLabel = { stringResource(it.labelRes()) },
                noneLabel = stringResource(R.string.exercise_filter_all_muscles),
                onSelected = onMuscleFilter
            )
            if (state.filtersActive) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.action_clear_filters))
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            when {
                state.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.visibleExercises.isEmpty() -> EmptyCatalog(state.emptyKind)
                else -> ExerciseList(
                    exercises = state.visibleExercises,
                    onEdit = onEdit,
                    onArchive = onArchive,
                    onRestore = onRestore,
                    onRequestDelete = onRequestDelete
                )
            }
        }
    }
    state.pendingDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.exercise_delete_title)) },
            text = {
                Text(stringResource(R.string.exercise_delete_message, exercise.name))
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(
                        text = stringResource(R.string.action_delete_permanently),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyCatalog(kind: CatalogEmptyKind?) {
    val title = when (kind) {
        CatalogEmptyKind.Archived -> stringResource(R.string.exercise_empty_archived_title)
        CatalogEmptyKind.Search -> stringResource(R.string.exercise_empty_search_title)
        else -> stringResource(R.string.exercise_empty_active_title)
    }
    val body = when (kind) {
        CatalogEmptyKind.Archived -> stringResource(R.string.exercise_empty_archived_body)
        CatalogEmptyKind.Search -> stringResource(R.string.exercise_empty_search_body)
        else -> stringResource(R.string.exercise_empty_active_body)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExerciseList(
    exercises: List<Exercise>,
    onEdit: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onRequestDelete: (Exercise) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppDimens.scrollEndPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(exercises, key = { it.id }) { exercise ->
            ExerciseRow(
                exercise = exercise,
                onEdit = { onEdit(exercise.id) },
                onArchive = { onArchive(exercise.id) },
                onRestore = { onRestore(exercise.id) },
                onDelete = { onRequestDelete(exercise) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = exercise.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.exercise_row_meta,
                    stringResource(exercise.category.labelRes()),
                    stringResource(exercise.primaryMuscle.labelRes())
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.exercise_row_summary,
                    stringResource(exercise.movementPattern.labelRes()),
                    stringResource(exercise.measurementType.labelRes())
                ),
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
        TextButton(onClick = onEdit) {
            Text(stringResource(R.string.action_edit))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.exercise_more_actions, exercise.name)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (exercise.archived) {
                                stringResource(R.string.action_restore_exercise)
                            } else {
                                stringResource(R.string.action_archive_exercise)
                            }
                        )
                    },
                    onClick = {
                        menuOpen = false
                        if (exercise.archived) onRestore() else onArchive()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_delete_permanently),
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
