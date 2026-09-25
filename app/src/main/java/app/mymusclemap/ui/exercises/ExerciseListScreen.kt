package app.mymusclemap.ui.exercises

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraftLogic
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.locale.LocalizedLabelOrder
import app.mymusclemap.ui.components.CompactDropdown
import app.mymusclemap.ui.components.CompactSearchBar
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val CATALOG_ROOT = "exercise-catalog-root"
internal const val CATALOG_ADD = "exercise-catalog-add"
internal const val CATALOG_SEARCH = "exercise-catalog-search"
internal const val CATALOG_SEARCH_CLEAR = "catalog-search-clear"
internal const val CATALOG_EMPTY_CREATE = "exercise-catalog-empty-create"
internal const val CATALOG_EMPTY_CLEAR_SEARCH = "exercise-catalog-empty-clear-search"
internal const val CATALOG_LIST = "exercise-catalog-list"
internal const val CATALOG_FILTER_ACTIVE = "exercise-catalog-filter-active"
internal const val CATALOG_FILTER_ARCHIVED = "exercise-catalog-filter-archived"
internal const val CATALOG_FILTER_ALL = "exercise-catalog-filter-all"

internal fun catalogRowTag(id: Long): String = "exercise-catalog-row-$id"
internal fun catalogOverflowAnchorTag(id: Long): String = "exercise-catalog-overflow-anchor-$id"
internal fun catalogOverflowButtonTag(id: Long): String = "exercise-catalog-overflow-button-$id"
internal fun catalogOverflowMenuTag(id: Long): String = "exercise-catalog-overflow-menu-$id"

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
    val listState = rememberLazyListState()
    var lastNavAt by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    val navigateOnce: (() -> Unit) -> Unit = { action ->
        val now = SystemClock.elapsedRealtime()
        if (now - lastNavAt >= 700L) {
            lastNavAt = now
            action()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(message.labelRes()))
        onMessageConsumed()
    }
    val categories = rememberSortedOptions(ExerciseCategory.entries) { it.labelRes() }
    val muscles = rememberSortedOptions(MuscleGroup.entries) { it.labelRes() }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CATALOG_ROOT),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(AppDimens.minTouch)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
                Text(
                    text = stringResource(R.string.exercises_title),
                    style = AppTypeTokens.sectionTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { navigateOnce(onAdd) },
                    modifier = Modifier
                        .size(AppDimens.minTouch)
                        .testTag(CATALOG_ADD)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_create_exercise)
                    )
                }
            }
            CompactSearchBar(
                value = state.query,
                onValueChange = onQueryChange,
                onClear = { onQueryChange("") },
                label = stringResource(R.string.exercise_search),
                clearLabel = stringResource(R.string.action_clear_search),
                testTag = CATALOG_SEARCH,
                clearTestTag = CATALOG_SEARCH_CLEAR
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.exercise_filter_active),
                    stringResource(R.string.exercise_filter_archived),
                    stringResource(R.string.exercise_filter_all)
                ),
                selectedIndex = when (state.archiveFilter) {
                    ArchiveFilter.ACTIVE -> 0
                    ArchiveFilter.ARCHIVED -> 1
                    ArchiveFilter.ALL -> 2
                },
                onSelected = { index ->
                    onArchiveFilter(
                        when (index) {
                            1 -> ArchiveFilter.ARCHIVED
                            2 -> ArchiveFilter.ALL
                            else -> ArchiveFilter.ACTIVE
                        }
                    )
                },
                compact = true,
                modifier = Modifier.fillMaxWidth(),
                optionTestTags = listOf(
                    CATALOG_FILTER_ACTIVE,
                    CATALOG_FILTER_ARCHIVED,
                    CATALOG_FILTER_ALL
                )
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            CompactDropdown(
                label = stringResource(R.string.exercise_filter_category),
                selected = state.category,
                options = categories,
                optionLabel = { resources.getString(it.labelRes()) },
                noneLabel = stringResource(R.string.exercise_filter_all_categories),
                onSelected = { value -> onCategoryFilter(value) },
                onClear = { onCategoryFilter(null) },
                placeholder = stringResource(R.string.exercise_filter_all_categories),
                testTag = "exercise-filter-category"
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            CompactDropdown(
                label = stringResource(R.string.exercise_filter_muscle),
                selected = state.muscle,
                options = muscles,
                optionLabel = { resources.getString(it.labelRes()) },
                noneLabel = stringResource(R.string.exercise_filter_all_muscles),
                onSelected = { value -> onMuscleFilter(value) },
                onClear = { onMuscleFilter(null) },
                placeholder = stringResource(R.string.exercise_filter_all_muscles),
                testTag = "exercise-filter-muscle"
            )
            if (state.filtersActive) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.action_clear_filters))
                }
            } else {
                Spacer(Modifier.height(AppDimens.headerStackGap))
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
                state.visibleExercises.isEmpty() -> EmptyCatalog(
                    kind = state.emptyKind,
                    query = state.query,
                    onCreate = { navigateOnce(onAdd) },
                    onClearSearch = { onQueryChange("") }
                )
                else -> ExerciseList(
                    exercises = state.visibleExercises,
                    listState = listState,
                    onEdit = { id -> navigateOnce { onEdit(id) } },
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
    state.pendingBlocked?.let {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.exercise_delete_title)) },
            text = { Text(stringResource(R.string.exercise_delete_blocked_body)) },
            confirmButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun EmptyCatalog(
    kind: CatalogEmptyKind?,
    query: String,
    onCreate: () -> Unit,
    onClearSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = AppDimens.sectionGap),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        when (kind) {
            CatalogEmptyKind.Archived -> {
                Text(
                    text = stringResource(R.string.exercise_empty_archived_title),
                    style = AppTypeTokens.sectionTitle
                )
            }
            CatalogEmptyKind.Search -> {
                Text(
                    text = if (query.isNotBlank()) {
                        stringResource(R.string.exercise_empty_search_query, query.trim())
                    } else {
                        stringResource(R.string.exercise_empty_search_title)
                    },
                    style = AppTypeTokens.sectionTitle
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                TextButton(
                    onClick = onClearSearch,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(CATALOG_EMPTY_CLEAR_SEARCH)
                ) {
                    Text(stringResource(R.string.action_clear_search))
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.exercise_empty_active_title),
                    style = AppTypeTokens.sectionTitle
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                Text(
                    text = stringResource(R.string.exercise_empty_active_body),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                Button(
                    onClick = onCreate,
                    shape = AppShapeTokens.button,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(CATALOG_EMPTY_CREATE)
                ) {
                    Text(stringResource(R.string.exercise_editor_add))
                }
            }
        }
    }
}

@Composable
private fun ExerciseList(
    exercises: List<Exercise>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEdit: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onRequestDelete: (Exercise) -> Unit
) {
    val ordered = remember(exercises) {
        LocalizedLabelOrder.sorted(
            items = exercises,
            label = { it.name },
            key = { it.id.toString().padStart(20, '0') }
        )
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CATALOG_LIST),
        state = listState,
        contentPadding = PaddingValues(bottom = AppDimens.scrollEndPadding)
    ) {
        items(ordered, key = { it.id }) { exercise ->
            ExerciseRow(
                exercise = exercise,
                onEdit = { onEdit(exercise.id) },
                onArchive = { onArchive(exercise.id) },
                onRestore = { onRestore(exercise.id) },
                onDelete = { onRequestDelete(exercise) }
            )
            HorizontalDivider(
                thickness = AppDimens.strokeThin,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val secondaryLabels = remember(exercise.secondaryMuscles, resources.configuration) {
        LocalizedLabelOrder.sorted(
            items = exercise.secondaryMuscles,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        ).map { resources.getString(it.labelRes()) }
    }
    val meta = buildList {
        add(resources.getString(exercise.measurementType.labelRes()))
        add(resources.getString(exercise.resistanceBasis.labelRes()))
        if (ExerciseDraftLogic.isWeightInterpretationVisible(
                exercise.measurementType,
                exercise.resistanceBasis
            )
        ) {
            add(resources.getString(exercise.weightInterpretation.labelRes()))
        }
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onEdit)
            .padding(vertical = AppDimens.headerStackGap)
            .testTag(catalogRowTag(exercise.id))
            .semantics {
                role = Role.Button
                contentDescription = exercise.name
            },
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = AppTypeTokens.sectionTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = meta,
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MuscleChip(
                    label = stringResource(exercise.primaryMuscle.labelRes()),
                    primary = true
                )
                if (exercise.archived) {
                    MuscleChip(
                        label = stringResource(R.string.exercise_archived_badge),
                        primary = false,
                        archived = true
                    )
                }
            }
            if (secondaryLabels.isNotEmpty()) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = secondaryLabels.joinToString(" · "),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopEnd)
                .testTag(catalogOverflowAnchorTag(exercise.id))
        ) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(AppDimens.minTouch)
                    .testTag(catalogOverflowButtonTag(exercise.id))
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.exercise_more_actions, exercise.name)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.testTag(catalogOverflowMenuTag(exercise.id))
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

@Composable
private fun MuscleChip(
    label: String,
    primary: Boolean,
    archived: Boolean = false
) {
    Text(
        text = label,
        style = AppTypeTokens.statCaption,
        color = when {
            archived -> MaterialTheme.colorScheme.tertiary
            primary -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(AppShapeTokens.chip)
            .background(
                when {
                    archived -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    primary -> MaterialTheme.colorScheme.surfaceContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun <T> rememberSortedOptions(
    options: List<T>,
    labelRes: (T) -> Int
): List<T> {
    val resources = LocalResources.current
    val configuration = resources.configuration
    return remember(options, configuration) {
        LocalizedLabelOrder.sorted(
            items = options,
            label = { resources.getString(labelRes(it)) },
            key = { (it as? Enum<*>)?.name ?: it.toString() }
        )
    }
}
