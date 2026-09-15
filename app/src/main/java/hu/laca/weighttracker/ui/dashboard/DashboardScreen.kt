package hu.laca.weighttracker.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import hu.laca.weighttracker.domain.Greeting
import hu.laca.weighttracker.domain.calendar.MonthGridCalculator
import hu.laca.weighttracker.domain.model.ChartPoint
import hu.laca.weighttracker.domain.model.ChartRange
import hu.laca.weighttracker.domain.model.WeeklyAverage
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.ui.components.DayDetailsSheet
import hu.laca.weighttracker.ui.components.DeleteMeasurementDialog
import hu.laca.weighttracker.ui.components.HeroSurface
import hu.laca.weighttracker.ui.components.MeasurementEditorSheet
import hu.laca.weighttracker.ui.components.MonthCalendar
import hu.laca.weighttracker.ui.components.SectionHeader
import hu.laca.weighttracker.ui.components.SegmentedControl
import hu.laca.weighttracker.ui.components.SettingsAction
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.components.WeightChart
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAddToday: () -> Unit,
    onChartRangeSelected: (ChartRange) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onDismissDaySheet: () -> Unit,
    onRecordSelectedDay: () -> Unit,
    onRequestDayDelete: () -> Unit,
    onDismissDayDelete: () -> Unit,
    onConfirmDayDelete: () -> Unit,
    onEditorDateChange: (LocalDate) -> Unit,
    onEditorWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissEditor: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onMessageConsumed: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            OverviewHeader(
                greeting = state.greeting,
                today = state.today,
                onOpenSettings = onOpenSettings
            )
            Spacer(Modifier.height(AppDimens.sectionGap))
            CurrentWeightHero(
                snapshot = state.snapshot,
                onAddToday = onAddToday
            )
            Spacer(Modifier.height(AppDimens.sectionGap))
            SectionHeader(title = stringResource(R.string.calendar_title))
            MonthCalendar(
                grid = state.monthGrid,
                selectedDate = state.daySheet?.date,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDayClick = onDaySelected
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
            if (state.snapshot.chartPoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.chart_empty_range),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AppDimens.itemGap)
                )
            } else {
                state.snapshot.chartRangeAverageKg?.let { average ->
                    Text(
                        text = stringResource(R.string.chart_range_average, UiFormatters.weightKg(average)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
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
        }
    }
    state.daySheet?.let { sheet ->
        DayDetailsSheet(
            state = sheet,
            onRecordWeight = onRecordSelectedDay,
            onEditWeight = onRecordSelectedDay,
            onDeleteWeight = onRequestDayDelete,
            onDismiss = onDismissDaySheet
        )
    }
    if (state.showDayDeleteConfirm) {
        state.daySheet?.measurement?.let { measurement ->
            DeleteMeasurementDialog(
                measurement = measurement,
                onConfirm = onConfirmDayDelete,
                onDismiss = onDismissDayDelete
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
private fun OverviewHeader(
    greeting: Greeting,
    today: LocalDate,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(greeting.stringRes()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = UiFormatters.longDate(today),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsAction(onOpenSettings = onOpenSettings)
    }
}

@Composable
private fun CurrentWeightHero(
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

private fun Greeting.stringRes(): Int {
    return when (this) {
        Greeting.Morning -> R.string.greeting_morning
        Greeting.Day -> R.string.greeting_day
        Greeting.Evening -> R.string.greeting_evening
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
    val month = YearMonth.from(date)
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
                    recentItems = emptyList(),
                    todayHasMeasurement = true,
                    measurementDates = setOf(date),
                    chartRangeAverageKg = 82.1
                ),
                today = date,
                greeting = Greeting.Morning,
                displayedMonth = month,
                monthGrid = MonthGridCalculator.grid(month, date, setOf(date))
            ),
            onAddToday = {},
            onChartRangeSelected = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onDaySelected = {},
            onDismissDaySheet = {},
            onRecordSelectedDay = {},
            onRequestDayDelete = {},
            onDismissDayDelete = {},
            onConfirmDayDelete = {},
            onEditorDateChange = {},
            onEditorWeightChange = {},
            onSave = {},
            onDismissEditor = {},
            onDeleteRequest = {},
            onDeleteDismiss = {},
            onDeleteConfirm = {},
            onMessageConsumed = {},
            onOpenSettings = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty light")
@Composable
private fun EmptyDashboardPreview() {
    val date = LocalDate.of(2026, 3, 11)
    val month = YearMonth.from(date)
    WeightTrackerTheme {
        DashboardScreen(
            state = DashboardUiState(
                today = date,
                greeting = Greeting.Day,
                displayedMonth = month,
                monthGrid = MonthGridCalculator.grid(month, date, emptySet())
            ),
            onAddToday = {},
            onChartRangeSelected = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onDaySelected = {},
            onDismissDaySheet = {},
            onRecordSelectedDay = {},
            onRequestDayDelete = {},
            onDismissDayDelete = {},
            onConfirmDayDelete = {},
            onEditorDateChange = {},
            onEditorWeightChange = {},
            onSave = {},
            onDismissEditor = {},
            onDeleteRequest = {},
            onDeleteDismiss = {},
            onDeleteConfirm = {},
            onMessageConsumed = {},
            onOpenSettings = {}
        )
    }
}
