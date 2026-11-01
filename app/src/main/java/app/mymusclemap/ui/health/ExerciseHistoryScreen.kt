package app.mymusclemap.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.health.ExerciseHistoryEntry
import app.mymusclemap.domain.health.ExerciseHistoryFilter
import app.mymusclemap.domain.health.ExerciseHistoryCountScope
import app.mymusclemap.domain.health.ExerciseHistoryPresentation
import app.mymusclemap.domain.health.ExerciseHistorySnapshot
import app.mymusclemap.domain.health.HealthExerciseKind
import app.mymusclemap.domain.health.HealthHistoryAccess
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val EXERCISE_HISTORY = "exercise-history"
internal const val EXERCISE_HISTORY_BACK = "exercise-history-back"
internal const val EXERCISE_HISTORY_SUMMARY = "exercise-history-summary"
internal const val EXERCISE_HISTORY_FILTER_ALL = "exercise-history-filter-all"
internal const val EXERCISE_HISTORY_FILTER_RUNNING = "exercise-history-filter-running"
internal const val EXERCISE_HISTORY_FILTER_CYCLING = "exercise-history-filter-cycling"
internal const val EXERCISE_HISTORY_FILTER_STRENGTH = "exercise-history-filter-strength"
internal const val EXERCISE_HISTORY_MORE = "exercise-history-more"
internal const val EXERCISE_HISTORY_RETRY = "exercise-history-retry"
internal const val EXERCISE_HISTORY_ALLOW = "exercise-history-allow"
internal const val EXERCISE_HISTORY_ROW = "exercise-history-row-"

@Composable
fun ExerciseHistoryScreen(
    history: ExerciseHistorySnapshot,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryInitial: () -> Unit,
    onRetryPage: () -> Unit,
    onRequestHistory: () -> Unit,
    modifier: Modifier = Modifier,
    sourceName: ((String) -> String?)? = null
) {
    val context = LocalContext.current
    val resolveSource = sourceName ?: { packageName -> healthSourceLabel(context, packageName) }
    var filterName by rememberSaveable { mutableStateOf(ExerciseHistoryFilter.All.name) }
    val filter = ExerciseHistoryFilter.valueOf(filterName)
    val listState = rememberLazyListState()
    var requestedAtSize by remember(filter) { mutableIntStateOf(-1) }
    val needsOlder = ExerciseHistoryPresentation.needsOlderPage(
        entries = history.entries,
        filter = filter,
        canLoadMore = history.canLoadMore,
        failed = history.failed,
        loading = history.loading
    )
    val olderSearchStalled = needsOlder && requestedAtSize == history.entries.size
    LaunchedEffect(needsOlder, history.entries.size) {
        if (!needsOlder || requestedAtSize == history.entries.size) return@LaunchedEffect
        requestedAtSize = history.entries.size
        onLoadMore()
    }
    ScrolledPageEffect(listState = listState, history = history, onLoadMore = onLoadMore)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(EXERCISE_HISTORY),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            HistoryHeader(onBack = onBack)
            when {
                !history.visible -> {
                    HistoryMessage(stringResource(R.string.health_connect_overview_unavailable))
                }
                history.loading && history.entries.isEmpty() -> {
                    HistoryMessage(stringResource(R.string.health_connect_history_loading))
                }
                history.entries.isEmpty() && history.failed -> {
                    HistoryProblem(onRetryInitial)
                }
                else -> {
                    HistoryList(
                        history = history,
                        filter = filter,
                        olderSearchStalled = olderSearchStalled,
                        onFilter = { filterName = it.name },
                        listState = listState,
                        resolveSource = resolveSource,
                        onLoadMore = onLoadMore,
                        onRetryPage = onRetryPage,
                        onRequestHistory = onRequestHistory
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrolledPageEffect(
    listState: LazyListState,
    history: ExerciseHistorySnapshot,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(listState, history.canLoadMore, history.loading, history.failed) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val scrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            scrolled && last >= layout.totalItemsCount - 1 && layout.totalItemsCount > 0
        }.distinctUntilChanged().collect { atEnd ->
            if (atEnd && history.canLoadMore && !history.loading && !history.failed) {
                onLoadMore()
            }
        }
    }
}

@Composable
private fun HistoryHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenPadding)
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(AppDimens.minTouch)
                .testTag(EXERCISE_HISTORY_BACK)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Text(
            text = stringResource(R.string.health_connect_history_title),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HistoryList(
    history: ExerciseHistorySnapshot,
    filter: ExerciseHistoryFilter,
    olderSearchStalled: Boolean,
    onFilter: (ExerciseHistoryFilter) -> Unit,
    listState: LazyListState,
    resolveSource: (String) -> String?,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit,
    onRequestHistory: () -> Unit
) {
    val visible = ExerciseHistoryPresentation.visibleEntries(history.entries, filter)
    val months = ExerciseHistoryPresentation.months(visible)
    val searching = !olderSearchStalled && (
        ExerciseHistoryPresentation.needsOlderPage(
            entries = history.entries,
            filter = filter,
            canLoadMore = history.canLoadMore,
            failed = false,
            loading = history.loading
        ) || (history.loading && visible.isEmpty() && history.canLoadMore)
        )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = AppDimens.screenPadding,
            end = AppDimens.screenPadding,
            bottom = AppDimens.sectionGap
        )
    ) {
        item(key = "summary") {
            HistorySummary(history)
            FilterRow(selected = filter, onFilter = onFilter)
            HistoryNotes(history = history, onRequestHistory = onRequestHistory)
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                FilterEmpty(
                    history = history,
                    filter = filter,
                    searching = searching,
                    onLoadMore = onLoadMore,
                    onRetryPage = onRetryPage
                )
            }
        } else {
            months.forEach { month ->
                item(key = "month-${month.month}") {
                    Text(
                        text = UiFormatters.monthTitle(month.month),
                        style = AppTypeTokens.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.itemGap, bottom = AppDimens.statSecondaryGap)
                    )
                }
                items(items = month.entries, key = { it.id }) { entry ->
                    ExerciseHistoryRow(entry = entry, resolveSource = resolveSource)
                }
            }
            item(key = "footer") {
                HistoryFooter(
                    history = history,
                    onLoadMore = onLoadMore,
                    onRetryPage = onRetryPage
                )
            }
        }
    }
}

@Composable
private fun HistorySummary(history: ExerciseHistorySnapshot) {
    val counts = ExerciseHistoryPresentation.counts(history.entries, history.canLoadMore)
    if (counts.total == 0) return
    Column(modifier = Modifier.testTag(EXERCISE_HISTORY_SUMMARY).padding(bottom = AppDimens.itemGap)) {
        if (counts.scope == ExerciseHistoryCountScope.Loaded) {
            Text(
                text = stringResource(R.string.health_connect_history_loaded_note),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = AppDimens.statSecondaryGap)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Count(
                value = counts.total,
                label = stringResource(
                    if (counts.scope == ExerciseHistoryCountScope.Loaded) {
                        R.string.health_connect_history_loaded_label
                    } else {
                        R.string.health_connect_history_total
                    }
                )
            )
            Count(value = counts.running, label = stringResource(R.string.health_connect_running))
            Count(value = counts.cycling, label = stringResource(R.string.health_connect_cycling))
            Count(value = counts.strength, label = stringResource(R.string.health_connect_strength))
        }
    }
}

@Composable
private fun Count(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value.toString(),
            style = AppTypeTokens.statValue,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilterRow(selected: ExerciseHistoryFilter, onFilter: (ExerciseHistoryFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = AppDimens.itemGap),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.statSecondaryGap)
    ) {
        HistoryFilter(ExerciseHistoryFilter.All, R.string.health_connect_history_filter_all, EXERCISE_HISTORY_FILTER_ALL, selected, onFilter)
        HistoryFilter(ExerciseHistoryFilter.Running, R.string.health_connect_running, EXERCISE_HISTORY_FILTER_RUNNING, selected, onFilter)
        HistoryFilter(ExerciseHistoryFilter.Cycling, R.string.health_connect_cycling, EXERCISE_HISTORY_FILTER_CYCLING, selected, onFilter)
        HistoryFilter(ExerciseHistoryFilter.Strength, R.string.health_connect_strength, EXERCISE_HISTORY_FILTER_STRENGTH, selected, onFilter)
    }
}

@Composable
private fun HistoryFilter(
    filter: ExerciseHistoryFilter,
    label: Int,
    tag: String,
    selected: ExerciseHistoryFilter,
    onFilter: (ExerciseHistoryFilter) -> Unit
) {
    val isSelected = filter == selected
    Surface(
        shape = AppShapeTokens.chip,
        color = if (isSelected) StrictBrand.actionContainer() else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(tag)
            .semantics { this.selected = isSelected }
            .clickable { onFilter(filter) }
    ) {
        Text(
            text = stringResource(label),
            style = AppTypeTokens.statSecondary,
            color = if (isSelected) StrictBrand.onAction() else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = AppDimens.itemGap, vertical = AppDimens.itemGap)
        )
    }
}

@Composable
private fun HistoryNotes(history: ExerciseHistorySnapshot, onRequestHistory: () -> Unit) {
    val note = when {
        history.limitedToRecentWindow -> stringResource(R.string.health_connect_history_limited)
        history.access == HealthHistoryAccess.Unsupported ->
            stringResource(R.string.health_connect_history_unsupported)
        history.access == HealthHistoryAccess.NotGranted ->
            stringResource(R.string.health_connect_history_not_granted)
        else -> null
    }
    if (note != null) {
        Text(
            text = note,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = AppDimens.statSecondaryGap)
        )
    }
    if (history.access == HealthHistoryAccess.NotGranted) {
        ActionText(
            text = stringResource(R.string.health_connect_history_allow),
            tag = EXERCISE_HISTORY_ALLOW,
            onClick = onRequestHistory
        )
    }
}

@Composable
private fun FilterEmpty(
    history: ExerciseHistorySnapshot,
    filter: ExerciseHistoryFilter,
    searching: Boolean,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit
) {
    when {
        searching || (history.loading && history.canLoadMore) -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(AppDimens.itemGap))
                Text(
                    text = stringResource(R.string.health_connect_history_filter_searching),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        history.failed && history.canLoadMore -> {
            Text(
                text = stringResource(R.string.health_connect_history_failed),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionText(stringResource(R.string.health_connect_history_retry), EXERCISE_HISTORY_RETRY, onRetryPage)
        }
        history.canLoadMore -> {
            Text(
                text = stringResource(R.string.health_connect_history_filter_older),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionText(stringResource(R.string.health_connect_history_more), EXERCISE_HISTORY_MORE, onLoadMore)
        }
        filter == ExerciseHistoryFilter.All -> {
            Text(
                text = stringResource(R.string.health_connect_history_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> {
            Text(
                text = stringResource(
                    when (filter) {
                        ExerciseHistoryFilter.Running -> R.string.health_connect_history_filter_none_running
                        ExerciseHistoryFilter.Cycling -> R.string.health_connect_history_filter_none_cycling
                        ExerciseHistoryFilter.Strength -> R.string.health_connect_history_filter_none_strength
                        ExerciseHistoryFilter.All -> R.string.health_connect_history_empty
                    }
                ),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryFooter(
    history: ExerciseHistorySnapshot,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit
) {
    if (history.loading) {
        Row(
            modifier = Modifier.padding(vertical = AppDimens.itemGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(AppDimens.itemGap))
            Text(
                text = stringResource(R.string.health_connect_history_loading),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (history.failed && history.entries.isNotEmpty()) {
        Text(
            text = stringResource(R.string.health_connect_history_failed),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ActionText(stringResource(R.string.health_connect_history_retry), EXERCISE_HISTORY_RETRY, onRetryPage)
    } else if (history.canLoadMore && !history.loading) {
        ActionText(stringResource(R.string.health_connect_history_more), EXERCISE_HISTORY_MORE, onLoadMore)
    }
}

@Composable
private fun ExerciseHistoryRow(
    entry: ExerciseHistoryEntry,
    resolveSource: (String) -> String?
) {
    val kind = stringResource(kindLabel(entry.kind))
    val time = entry.startTime.format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(AppLocale.UI)
    )
    val whenLabel = stringResource(
        R.string.health_connect_dated_value,
        UiFormatters.compactDate(entry.date),
        time
    )
    val duration = healthSessionDuration(entry.duration)
    val source = entry.sourcePackage?.let(resolveSource)
    Column(modifier = Modifier.testTag(EXERCISE_HISTORY_ROW + entry.id)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.itemGap)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = kindIcon(entry.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(AppDimens.itemGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kind,
                    style = AppTypeTokens.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = whenLabel,
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (source != null) {
                    Text(
                        text = source,
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = duration,
                style = AppTypeTokens.statValue,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HistoryProblem(onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = AppDimens.screenPadding)) {
        Text(
            text = stringResource(R.string.health_connect_history_failed),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ActionText(stringResource(R.string.health_connect_history_retry), EXERCISE_HISTORY_RETRY, onRetry)
    }
}

@Composable
private fun HistoryMessage(message: String) {
    Text(
        text = message,
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = AppDimens.screenPadding)
    )
}

@Composable
private fun ActionText(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = AppTypeTokens.sectionTitle,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(vertical = AppDimens.statSecondaryGap)
    )
}

private fun kindLabel(kind: HealthExerciseKind): Int {
    return when (kind) {
        HealthExerciseKind.STRENGTH -> R.string.health_connect_strength
        HealthExerciseKind.CYCLING -> R.string.health_connect_cycling
        HealthExerciseKind.RUNNING -> R.string.health_connect_running
        HealthExerciseKind.OTHER -> R.string.health_connect_exercise_sessions
    }
}

private fun kindIcon(kind: HealthExerciseKind): ImageVector {
    return when (kind) {
        HealthExerciseKind.RUNNING -> Icons.AutoMirrored.Outlined.DirectionsRun
        HealthExerciseKind.CYCLING -> Icons.AutoMirrored.Outlined.DirectionsBike
        HealthExerciseKind.STRENGTH -> Icons.Outlined.FitnessCenter
        HealthExerciseKind.OTHER -> Icons.Outlined.FitnessCenter
    }
}
