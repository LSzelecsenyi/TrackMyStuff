package app.mymusclemap.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.journal.ExerciseHistoryCopy
import app.mymusclemap.domain.journal.HistoryColumn
import app.mymusclemap.domain.workout.CurrentSetHistoryLine
import app.mymusclemap.domain.workout.ExerciseHistorySelection
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import app.mymusclemap.ui.theme.sheetContainerColor

internal const val SET_HISTORY_SHEET = "set-history-sheet"
internal const val SET_HISTORY_TITLE = "set-history-title"
internal const val SET_HISTORY_CLOSE = "set-history-close"
internal const val SET_HISTORY_NOTE = "set-history-note"
internal const val SET_HISTORY_COMPARISON = "set-history-comparison"

internal fun historyRowTag(position: Int): String = "set-history-row-$position"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseHistorySheet(
    exerciseName: String,
    selection: ExerciseHistorySelection,
    comparisonPosition: Int,
    onDismiss: () -> Unit
) {
    val resources = LocalResources.current
    val table = ExerciseHistoryCopy.table(resources, selection, comparisonPosition)
    val hasComparison = table.rows.any { it.comparison }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val source = stringResource(
        R.string.active_set_history_source,
        UiFormatters.longDate(selection.workoutDate),
        selection.templateName
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor(),
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(SET_HISTORY_SHEET)
    ) {
        BackHandler(onBack = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = AppDimens.sectionGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exerciseName,
                    style = AppTypeTokens.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(SET_HISTORY_TITLE)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(SET_HISTORY_CLOSE)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.active_set_history_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = source,
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selection.fromOtherPlan) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = stringResource(R.string.active_set_history_other_plan),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(AppDimens.itemGap))
            Text(
                text = stringResource(
                    if (hasComparison) {
                        R.string.active_set_history_comparison_note
                    } else {
                        R.string.active_set_history_no_comparison
                    }
                ),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(SET_HISTORY_NOTE)
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            HistoryHeader(table.columns)
            table.rows.forEach { row ->
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                HistoryDataRow(
                    columns = table.columns,
                    cells = row.cells,
                    comparison = row.comparison,
                    modifier = Modifier.testTag(
                        if (row.comparison) SET_HISTORY_COMPARISON else historyRowTag(row.position)
                    )
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(columns: List<HistoryColumn>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.headerStackGap)
    ) {
        columns.forEach { column ->
            Text(
                text = stringResource(column.labelRes()),
                style = AppTypeTokens.columnHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(column.weight())
            )
        }
    }
}

@Composable
private fun HistoryDataRow(
    columns: List<HistoryColumn>,
    cells: List<String>,
    comparison: Boolean,
    modifier: Modifier = Modifier
) {
    val comparisonLabel = stringResource(R.string.active_set_history_comparison_row)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (comparison) StrictBrand.blue.copy(alpha = 0.14f) else Color.Transparent,
                shape = AppShapeTokens.compact
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics {
                if (comparison) {
                    stateDescription = comparisonLabel
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.headerStackGap),
            verticalAlignment = Alignment.Top
        ) {
            columns.forEachIndexed { index, column ->
                Text(
                    text = cells.getOrElse(index) { "" },
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(column.weight())
                )
            }
        }
        if (comparison) {
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = stringResource(R.string.active_set_history_comparison_label),
                style = AppTypeTokens.statCaption,
                color = StrictBrand.blue,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun HistoryColumn.labelRes(): Int {
    return when (this) {
        HistoryColumn.SET -> R.string.active_set_history_column_set
        HistoryColumn.REPS -> R.string.active_set_history_column_reps
        HistoryColumn.WEIGHT -> R.string.active_set_history_column_weight
        HistoryColumn.DURATION -> R.string.active_set_history_column_time
        HistoryColumn.DISTANCE -> R.string.active_set_history_column_distance
        HistoryColumn.RESULT -> R.string.set_status_completed
    }
}

private fun HistoryColumn.weight(): Float {
    return when (this) {
        HistoryColumn.SET -> 0.7f
        HistoryColumn.REPS -> 1f
        HistoryColumn.WEIGHT -> 1.5f
        HistoryColumn.DURATION -> 1.2f
        HistoryColumn.DISTANCE -> 1.2f
        HistoryColumn.RESULT -> 1.2f
    }
}

@Composable
internal fun LastTimeRow(
    line: CurrentSetHistoryLine,
    onOpen: () -> Unit
) {
    val resources = LocalResources.current
    when (line) {
        CurrentSetHistoryLine.Hidden -> Unit
        CurrentSetHistoryLine.NoPreviousData -> {
            Text(
                text = stringResource(R.string.active_set_last_time_none),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.statSecondaryGap)
                    .testTag(SET_LAST_TIME_NONE)
            )
        }
        is CurrentSetHistoryLine.NoMatchingSet -> {
            ClickableHistoryLine(
                label = stringResource(R.string.active_set_last_time_no_set),
                onOpen = onOpen
            )
        }
        is CurrentSetHistoryLine.Recorded -> {
            val summary = ExerciseHistoryCopy.inlineValue(resources, line.selection, line.set)
            ClickableHistoryLine(
                label = stringResource(R.string.active_set_last_time, summary),
                onOpen = onOpen
            )
        }
    }
}

@Composable
private fun ClickableHistoryLine(
    label: String,
    onOpen: () -> Unit
) {
    val description = stringResource(R.string.active_set_last_time_a11y, label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(SET_LAST_TIME)
            .clickable(onClick = onOpen)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.headerStackGap)
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = StrictBrand.blue,
            modifier = Modifier
                .size(20.dp)
                .testTag(SET_LAST_TIME_ICON)
        )
    }
}

internal const val SET_LAST_TIME = "set-last-time"
internal const val SET_LAST_TIME_NONE = "set-last-time-none"
internal const val SET_LAST_TIME_ICON = "set-last-time-icon"
internal const val SET_PLAN_LINE = "set-plan-line"
