package hu.laca.weighttracker.ui.templates

import android.os.SystemClock
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.ArchiveFilter
import hu.laca.weighttracker.domain.locale.LocalizedLabelOrder
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.ui.components.CompactSearchBar
import hu.laca.weighttracker.ui.components.SegmentedControl
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppShapeTokens
import hu.laca.weighttracker.ui.theme.AppTypeTokens

internal const val TEMPLATE_ROOT = "template-list-root"
internal const val TEMPLATE_ADD = "template-list-add"
internal const val TEMPLATE_SEARCH = "template-list-search"
internal const val TEMPLATE_SEARCH_CLEAR = "template-list-search-clear"
internal const val TEMPLATE_EMPTY_CREATE = "template-list-empty-create"
internal const val TEMPLATE_EMPTY_CLEAR_SEARCH = "template-list-empty-clear-search"
internal const val TEMPLATE_LIST = "template-list"
internal const val TEMPLATE_FILTER_ACTIVE = "template-list-filter-active"
internal const val TEMPLATE_FILTER_ARCHIVED = "template-list-filter-archived"
internal const val TEMPLATE_FILTER_ALL = "template-list-filter-all"

internal fun templateRowTag(id: Long): String = "template-list-row-$id"
internal fun templateOverflowAnchorTag(id: Long): String = "template-list-overflow-anchor-$id"
internal fun templateOverflowButtonTag(id: Long): String = "template-list-overflow-button-$id"
internal fun templateOverflowMenuTag(id: Long): String = "template-list-overflow-menu-$id"

@Composable
fun TemplateListScreen(
    state: TemplateListUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onArchiveFilter: (ArchiveFilter) -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onRequestDelete: (TemplateListItem) -> Unit,
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
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TEMPLATE_ROOT),
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
                    text = stringResource(R.string.templates_title),
                    style = AppTypeTokens.sectionTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { navigateOnce(onAdd) },
                    modifier = Modifier
                        .size(AppDimens.minTouch)
                        .testTag(TEMPLATE_ADD)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_create_template_header)
                    )
                }
            }
            CompactSearchBar(
                value = state.query,
                onValueChange = onQueryChange,
                onClear = { onQueryChange("") },
                label = stringResource(R.string.template_search),
                clearLabel = stringResource(R.string.action_clear_search),
                testTag = TEMPLATE_SEARCH,
                clearTestTag = TEMPLATE_SEARCH_CLEAR
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
                    TEMPLATE_FILTER_ACTIVE,
                    TEMPLATE_FILTER_ARCHIVED,
                    TEMPLATE_FILTER_ALL
                )
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            when {
                state.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.visibleItems.isEmpty() -> EmptyTemplates(
                    kind = state.emptyKind,
                    query = state.query,
                    onCreate = { navigateOnce(onAdd) },
                    onClearSearch = { onQueryChange("") }
                )
                else -> TemplateList(
                    items = state.visibleItems,
                    listState = listState,
                    onEdit = { id -> navigateOnce { onEdit(id) } },
                    onArchive = onArchive,
                    onRestore = onRestore,
                    onRequestDelete = onRequestDelete
                )
            }
        }
    }
    state.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.template_delete_message, item.template.name)) },
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
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.template_delete_blocked_body)) },
            confirmButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun EmptyTemplates(
    kind: TemplateEmptyKind?,
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
            TemplateEmptyKind.Archived -> {
                Text(
                    text = stringResource(R.string.template_empty_archived_title),
                    style = AppTypeTokens.sectionTitle
                )
            }
            TemplateEmptyKind.Search -> {
                Text(
                    text = if (query.isNotBlank()) {
                        stringResource(R.string.template_empty_search_query, query.trim())
                    } else {
                        stringResource(R.string.template_empty_search_title)
                    },
                    style = AppTypeTokens.sectionTitle
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                TextButton(
                    onClick = onClearSearch,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(TEMPLATE_EMPTY_CLEAR_SEARCH)
                ) {
                    Text(stringResource(R.string.action_clear_search))
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.template_empty_active_title),
                    style = AppTypeTokens.sectionTitle
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                Button(
                    onClick = onCreate,
                    shape = AppShapeTokens.button,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(TEMPLATE_EMPTY_CREATE)
                ) {
                    Text(stringResource(R.string.template_editor_add))
                }
            }
        }
    }
}

@Composable
private fun TemplateList(
    items: List<TemplateListItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEdit: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onRequestDelete: (TemplateListItem) -> Unit
) {
    val ordered = remember(items) {
        LocalizedLabelOrder.sorted(
            items = items,
            label = { it.template.name },
            key = { it.template.id.toString().padStart(20, '0') }
        )
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TEMPLATE_LIST),
        state = listState,
        contentPadding = PaddingValues(bottom = AppDimens.scrollEndPadding)
    ) {
        items(ordered, key = { it.template.id }) { item ->
            TemplateRow(
                item = item,
                onEdit = { onEdit(item.template.id) },
                onArchive = { onArchive(item.template.id) },
                onRestore = { onRestore(item.template.id) },
                onDelete = { onRequestDelete(item) }
            )
            HorizontalDivider(
                thickness = AppDimens.strokeThin,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun TemplateRow(
    item: TemplateListItem,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onEdit)
            .padding(vertical = AppDimens.headerStackGap)
            .testTag(templateRowTag(item.template.id))
            .semantics {
                role = Role.Button
                contentDescription = item.template.name
            },
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.template.name,
                style = AppTypeTokens.sectionTitle,
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
            if (item.exerciseNames.isNotEmpty()) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = item.exerciseNames.joinToString(" · "),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.template.archived) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = stringResource(R.string.template_archived_badge),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopEnd)
                .testTag(templateOverflowAnchorTag(item.template.id)),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(AppDimens.minTouch)
                    .testTag(templateOverflowButtonTag(item.template.id))
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(
                        R.string.template_more_actions,
                        item.template.name
                    )
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.testTag(templateOverflowMenuTag(item.template.id))
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
                            if (item.template.archived) {
                                stringResource(R.string.action_restore_template)
                            } else {
                                stringResource(R.string.action_archive_template)
                            }
                        )
                    },
                    onClick = {
                        menuOpen = false
                        if (item.template.archived) onRestore() else onArchive()
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
