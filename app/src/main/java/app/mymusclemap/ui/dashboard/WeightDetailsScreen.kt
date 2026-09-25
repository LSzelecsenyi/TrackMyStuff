package app.mymusclemap.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.DashboardSnapshot
import app.mymusclemap.domain.model.ChartPoint
import app.mymusclemap.domain.model.ChartRange
import app.mymusclemap.ui.components.DeleteMeasurementDialog
import app.mymusclemap.ui.components.HeroSurface
import app.mymusclemap.ui.components.MeasurementEditorSheet
import app.mymusclemap.ui.components.SectionHeader
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.components.UserMessageEffect
import app.mymusclemap.ui.components.WeightChart
import app.mymusclemap.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightDetailsScreen(
    state: WeightDetailsUiState,
    onBack: () -> Unit,
    onAddToday: () -> Unit,
    onChartRangeSelected: (ChartRange) -> Unit,
    onEditorDateChange: (java.time.LocalDate) -> Unit,
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
            TopAppBar(
                title = { Text(stringResource(R.string.weight_details_title)) },
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 8.dp, bottom = AppDimens.scrollEndPadding)
        ) {
            WeightHeroSection(
                snapshot = state.snapshot,
                onAddToday = onAddToday
            )
            Spacer(Modifier.height(AppDimens.sectionGap))
            SectionHeader(title = stringResource(R.string.chart_title))
            val ranges = ChartRange.entries
            SegmentedControl(
                options = ranges.map { stringResource(it.labelRes()) },
                selectedIndex = ranges.indexOf(state.chartRange).coerceAtLeast(0),
                onSelected = { onChartRangeSelected(ranges[it]) }
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            WeightChartBlock(
                snapshot = state.snapshot,
                selectedPoint = selectedPoint,
                onPointSelected = { selectedPoint = it }
            )
        }
    }
    state.editor?.let { editor ->
        MeasurementEditorSheet(
            state = editor,
            today = state.today,
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
internal fun WeightHeroSection(
    snapshot: DashboardSnapshot,
    onAddToday: () -> Unit
) {
    HeroSurface {
        if (snapshot.latest == null) {
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.empty_body_short),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.latest_weight_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = UiFormatters.weightKg(snapshot.latest.weightKg),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = UiFormatters.longDate(snapshot.latest.date),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            snapshot.currentWeek?.let { week ->
                Text(
                    text = stringResource(
                        R.string.hero_weekly_average,
                        UiFormatters.weightKg(week.averageKg)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Text(
                text = snapshot.previousWeekChangeKg?.let {
                    stringResource(R.string.change_from_previous_week_value, UiFormatters.signedWeightKg(it))
                } ?: stringResource(R.string.no_previous_week_average),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
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

@Composable
internal fun WeightChartBlock(
    snapshot: DashboardSnapshot,
    selectedPoint: ChartPoint?,
    onPointSelected: (ChartPoint?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (snapshot.chartPoints.isEmpty()) {
            Text(
                text = stringResource(R.string.chart_empty_range),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = AppDimens.itemGap)
            )
        } else {
            snapshot.chartRangeAverageKg?.let { average ->
                Text(
                    text = stringResource(R.string.chart_range_average, UiFormatters.weightKg(average)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            WeightChart(
                points = snapshot.chartPoints,
                contentDescription = stringResource(
                    R.string.chart_content_description,
                    snapshot.chartPoints.size,
                    UiFormatters.weightKg(snapshot.chartPoints.minOf { it.weightKg }),
                    UiFormatters.weightKg(snapshot.chartPoints.maxOf { it.weightKg })
                ),
                onPointSelected = onPointSelected
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
    }
}

internal fun ChartRange.labelRes(): Int {
    return when (this) {
        ChartRange.Days30 -> R.string.range_30
        ChartRange.Days90 -> R.string.range_90
        ChartRange.All -> R.string.range_all
    }
}
