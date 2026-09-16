package hu.laca.weighttracker.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.DashboardSnapshot
import hu.laca.weighttracker.domain.Greeting
import hu.laca.weighttracker.domain.calendar.MonthGridCalculator
import hu.laca.weighttracker.domain.model.ChartPoint
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.ui.components.DayDetailsSheet
import hu.laca.weighttracker.ui.components.DeleteMeasurementDialog
import hu.laca.weighttracker.ui.components.HeroSurface
import hu.laca.weighttracker.ui.components.MeasurementEditorSheet
import hu.laca.weighttracker.ui.components.MonthCalendar
import hu.laca.weighttracker.ui.components.SectionHeader
import hu.laca.weighttracker.ui.components.SettingsAction
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.components.musclemap.MuscleHeatmapCard
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DashboardScreen(
    state: DashboardUiState,
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
    onOpenSettings: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenWeightDetails: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
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
            MuscleHeatmapCard(state = state.heatmap)
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
            CompactWeightChartCard(
                snapshot = state.snapshot,
                onOpenDetails = onOpenWeightDetails
            )
        }
    }
    state.daySheet?.let { sheet ->
        DayDetailsSheet(
            state = sheet,
            onRecordWeight = onRecordSelectedDay,
            onEditWeight = onRecordSelectedDay,
            onDeleteWeight = onRequestDayDelete,
            onOpenWorkout = onOpenWorkout,
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
private fun CompactWeightChartCard(
    snapshot: DashboardSnapshot,
    onOpenDetails: () -> Unit
) {
    HeroSurface(
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpenDetails)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.chart_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenDetails) {
                Text(stringResource(R.string.action_open_details))
            }
        }
        snapshot.latest?.let { latest ->
            Text(
                text = stringResource(
                    R.string.weight_chart_latest_summary,
                    UiFormatters.weightKg(latest.weightKg)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        WeightChartBlock(
            snapshot = snapshot,
            selectedPoint = null,
            onPointSelected = {}
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

private fun Greeting.stringRes(): Int {
    return when (this) {
        Greeting.Morning -> R.string.greeting_morning
        Greeting.Day -> R.string.greeting_day
        Greeting.Evening -> R.string.greeting_evening
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
                    currentWeek = null,
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
            onOpenSettings = {},
            onOpenWorkout = {},
            onOpenWeightDetails = {}
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
            onOpenSettings = {},
            onOpenWorkout = {},
            onOpenWeightDetails = {}
        )
    }
}
