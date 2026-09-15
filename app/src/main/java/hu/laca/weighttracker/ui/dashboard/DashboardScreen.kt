package hu.laca.weighttracker.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.DashboardSnapshot
import hu.laca.weighttracker.domain.model.ChartPoint
import hu.laca.weighttracker.domain.model.ChartRange
import hu.laca.weighttracker.domain.model.MeasurementListItem
import hu.laca.weighttracker.domain.model.WeeklyAverage
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.ui.components.MeasurementEditorSheet
import hu.laca.weighttracker.ui.components.MeasurementRow
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.components.WeeklyAveragesRow
import hu.laca.weighttracker.ui.components.WeightChart
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    today: LocalDate,
    onAddToday: () -> Unit,
    onChartRangeSelected: (ChartRange) -> Unit,
    onOpenHistory: () -> Unit,
    onEditMeasurement: (LocalDate) -> Unit,
    onDeleteMeasurement: (LocalDate) -> Unit,
    onEditorDateChange: (LocalDate) -> Unit,
    onEditorWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissEditor: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onMessageConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (state.snapshot.isEmpty) {
            EmptyDashboard(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                onAddToday = onAddToday
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                LatestWeightCard(
                    snapshot = state.snapshot,
                    onAddToday = onAddToday
                )
                Spacer(Modifier.height(16.dp))
                WeekSummaryRow(snapshot = state.snapshot)
                if (state.snapshot.recentWeeks.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.weekly_averages_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    WeeklyAveragesRow(weeks = state.snapshot.recentWeeks)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.chart_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.chartRange == range,
                            onClick = { onChartRangeSelected(range) },
                            label = { Text(stringResource(range.labelRes())) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.snapshot.chartPoints.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chart_empty_range),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    WeightChart(
                        points = state.snapshot.chartPoints,
                        contentDescription = stringResource(
                            R.string.chart_content_description,
                            state.snapshot.chartPoints.size,
                            UiFormatters.weightKg(state.snapshot.chartPoints.minOf { it.weightKg }),
                            UiFormatters.weightKg(state.snapshot.chartPoints.maxOf { it.weightKg })
                        ),
                        onPointSelected = { selectedPoint = it }
                    )
                    selectedPoint?.let { point ->
                        Text(
                            text = stringResource(
                                R.string.chart_selected_point,
                                UiFormatters.longDate(point.date),
                                UiFormatters.weightKg(point.weightKg)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recent_measurements_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenHistory) {
                        Text(stringResource(R.string.action_open_history))
                    }
                }
                state.snapshot.recentItems.forEach { item ->
                    MeasurementRow(
                        item = item,
                        showActions = true,
                        onEdit = { onEditMeasurement(item.measurement.date) },
                        onDelete = { onDeleteMeasurement(item.measurement.date) }
                    )
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
}

@Composable
private fun EmptyDashboard(
    modifier: Modifier,
    onAddToday: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddToday) {
            Text(stringResource(R.string.action_add_today))
        }
    }
}

@Composable
private fun LatestWeightCard(
    snapshot: DashboardSnapshot,
    onAddToday: () -> Unit
) {
    val latest = snapshot.latest ?: return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.latest_weight_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = UiFormatters.weightKg(latest.weightKg),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = UiFormatters.longDate(latest.date),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = snapshot.changeFromPreviousKg?.let {
                    stringResource(R.string.change_from_previous_value, UiFormatters.signedWeightKg(it))
                } ?: stringResource(R.string.no_previous_measurement),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAddToday,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (snapshot.todayHasMeasurement) {
                            R.string.action_edit_today
                        } else {
                            R.string.action_add_today
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun WeekSummaryRow(snapshot: DashboardSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.weekly_average_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val week = snapshot.currentWeek
                Text(
                    text = week?.let { UiFormatters.weightKg(it.averageKg) }
                        ?: stringResource(R.string.no_current_week_average),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (week != null) {
                    Text(
                        text = stringResource(
                            R.string.week_interval,
                            UiFormatters.compactDate(week.weekStart),
                            UiFormatters.compactDate(week.coveredEnd)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.change_from_previous_week),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = snapshot.previousWeekChangeKg?.let { UiFormatters.signedWeightKg(it) }
                        ?: stringResource(R.string.no_previous_week_average),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun ChartRange.labelRes(): Int {
    return when (this) {
        ChartRange.Days30 -> R.string.range_30
        ChartRange.Days90 -> R.string.range_90
        ChartRange.All -> R.string.range_all
    }
}

@Preview(showBackground = true, name = "Dashboard light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dashboard dark")
@Composable
private fun DashboardPreview() {
    val date = LocalDate.of(2026, 3, 11)
    val measurement = WeightMeasurement(1, date, 82.4, 0, 0)
    WeightTrackerTheme {
        DashboardScreen(
            state = DashboardUiState(
                snapshot = DashboardSnapshot(
                    isEmpty = false,
                    latest = measurement,
                    changeFromPreviousKg = 0.4,
                    currentWeek = WeeklyAverage(
                        weekBasedYear = 2026,
                        weekOfYear = 11,
                        weekStart = LocalDate.of(2026, 3, 9),
                        weekEnd = LocalDate.of(2026, 3, 15),
                        coveredEnd = date,
                        averageKg = 82.2,
                        sampleCount = 3,
                        isCurrentWeek = true
                    ),
                    previousWeekChangeKg = -0.3,
                    recentWeeks = emptyList(),
                    chartPoints = listOf(
                        ChartPoint(date.minusDays(2), 81.8),
                        ChartPoint(date, 82.4)
                    ),
                    recentItems = listOf(
                        MeasurementListItem(measurement, 0.4)
                    ),
                    todayHasMeasurement = true
                )
            ),
            today = date,
            onAddToday = {},
            onChartRangeSelected = {},
            onOpenHistory = {},
            onEditMeasurement = {},
            onDeleteMeasurement = {},
            onEditorDateChange = {},
            onEditorWeightChange = {},
            onSave = {},
            onDismissEditor = {},
            onDeleteRequest = {},
            onDeleteDismiss = {},
            onDeleteConfirm = {},
            onMessageConsumed = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Empty dark")
@Composable
private fun EmptyDashboardPreview() {
    WeightTrackerTheme {
        DashboardScreen(
            state = DashboardUiState(),
            today = LocalDate.of(2026, 3, 11),
            onAddToday = {},
            onChartRangeSelected = {},
            onOpenHistory = {},
            onEditMeasurement = {},
            onDeleteMeasurement = {},
            onEditorDateChange = {},
            onEditorWeightChange = {},
            onSave = {},
            onDismissEditor = {},
            onDeleteRequest = {},
            onDeleteDismiss = {},
            onDeleteConfirm = {},
            onMessageConsumed = {}
        )
    }
}
