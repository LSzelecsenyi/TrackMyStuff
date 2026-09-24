package hu.laca.weighttracker.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.DashboardSnapshot
import hu.laca.weighttracker.domain.WeeklyOverview
import hu.laca.weighttracker.domain.WeeklyOverviewLogic
import hu.laca.weighttracker.domain.calendar.MonthGridCalculator
import hu.laca.weighttracker.domain.model.ChartPoint
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.ScheduledWorkout
import hu.laca.weighttracker.ui.components.DayDetailsSheet
import hu.laca.weighttracker.ui.components.DeleteMeasurementDialog
import hu.laca.weighttracker.ui.components.MeasurementEditorSheet
import hu.laca.weighttracker.ui.components.MonthCalendar
import hu.laca.weighttracker.ui.components.RescheduleDateSheet
import hu.laca.weighttracker.ui.components.ScheduleWorkoutPickerSheet
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.components.UnscheduleWorkoutDialog
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.components.stringRes
import hu.laca.weighttracker.ui.components.WeightChart
import hu.laca.weighttracker.ui.components.musclemap.MuscleHeatmapCard
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppTypeTokens
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme
import java.time.LocalDate
import java.time.YearMonth

internal const val OVERVIEW_OVERFLOW_ANCHOR = "overview-overflow-anchor"
internal const val OVERVIEW_OVERFLOW_BUTTON = "overview-overflow-button"
internal const val OVERVIEW_OVERFLOW_MENU = "overview-overflow-menu"
internal const val OVERVIEW_OVERFLOW_TEMPLATES = "overview-overflow-templates"
internal const val OVERVIEW_OVERFLOW_EXERCISES = "overview-overflow-exercises"
internal const val OVERVIEW_OVERFLOW_SETTINGS = "overview-overflow-settings"

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
    onOpenTemplates: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenWeightDetails: () -> Unit,
    onOpenSchedulePicker: () -> Unit = {},
    onDismissSchedulePicker: () -> Unit = {},
    onScheduleTemplate: (Long) -> Unit = {},
    onOpenReschedule: (ScheduledWorkout) -> Unit = {},
    onDismissReschedule: () -> Unit = {},
    onConfirmReschedule: (LocalDate) -> Unit = {},
    onOpenRemove: (ScheduledWorkout) -> Unit = {},
    onDismissRemove: () -> Unit = {},
    onConfirmRemove: () -> Unit = {},
    onStartScheduled: (Long) -> Unit = {},
    onContinueScheduled: (Long) -> Unit = {},
    onOpenScheduledJournal: (Long) -> Unit = {},
    onCreateTemplateFromSchedule: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
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
                overview = state.weeklyOverview,
                today = state.today,
                onOpenSettings = onOpenSettings,
                onOpenTemplates = onOpenTemplates,
                onOpenCatalog = onOpenCatalog
            )
            OverviewSectionDivider()
            MuscleHeatmapCard(
                state = state.heatmap,
                modifier = Modifier.testTag("dashboard_heatmap")
            )
            OverviewSectionDivider()
            Text(
                text = stringResource(R.string.calendar_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("dashboard_calendar_title")
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            MonthCalendar(
                grid = state.monthGrid,
                selectedDate = state.daySheet?.date,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDayClick = onDaySelected,
                modifier = Modifier.testTag("dashboard_calendar")
            )
            OverviewSectionDivider()
            CompactWeightChartSection(
                snapshot = state.snapshot,
                onOpenDetails = onOpenWeightDetails
            )
        }
    }
    state.daySheet?.let { sheet ->
        DayDetailsSheet(
            state = sheet,
            today = state.today,
            onRecordWeight = onRecordSelectedDay,
            onEditWeight = onRecordSelectedDay,
            onDeleteWeight = onRequestDayDelete,
            onOpenWorkout = onOpenWorkout,
            onScheduleWorkout = onOpenSchedulePicker,
            onStartScheduled = onStartScheduled,
            onContinueScheduled = onContinueScheduled,
            onOpenScheduledJournal = onOpenScheduledJournal,
            onReschedule = onOpenReschedule,
            onUnschedule = onOpenRemove,
            onDismiss = onDismissDaySheet,
            busy = state.scheduleBusy
        )
    }
    if (state.schedulePickerVisible) {
        state.daySheet?.let { sheet ->
            ScheduleWorkoutPickerSheet(
                date = sheet.date,
                templates = state.availableTemplates,
                busy = state.scheduleBusy,
                errorText = state.scheduleActionError?.let { resources.getString(it.stringRes()) },
                onDismiss = onDismissSchedulePicker,
                onSelectTemplate = { onScheduleTemplate(it.template.id) },
                onCreateTemplate = onCreateTemplateFromSchedule
            )
        }
    }
    state.rescheduleTarget?.let { target ->
        RescheduleDateSheet(
            target = target,
            today = state.today,
            errorText = state.scheduleActionError?.let { resources.getString(it.stringRes()) },
            onDismiss = onDismissReschedule,
            onSelectDate = onConfirmReschedule
        )
    }
    state.removeTarget?.let { target ->
        UnscheduleWorkoutDialog(
            item = target,
            onConfirm = onConfirmRemove,
            onDismiss = onDismissRemove
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
private fun OverviewSectionDivider() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(AppDimens.sectionDividerSpace))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.strokeThin)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.height(AppDimens.sectionDividerSpace))
    }
}

@Composable
private fun CompactWeightChartSection(
    snapshot: DashboardSnapshot,
    onOpenDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_weight_chart")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.chart_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.action_open_details),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .clickable(role = Role.Button, onClick = onOpenDetails)
                    .padding(horizontal = 4.dp, vertical = 12.dp)
                    .testTag("dashboard_weight_details")
            )
        }
        if (snapshot.latest != null || snapshot.chartRangeAverageKg != null) {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
            ) {
                snapshot.latest?.let { latest ->
                    CompactStat(
                        label = stringResource(R.string.weight_stat_latest_label),
                        value = UiFormatters.weightKg(latest.weightKg),
                        modifier = Modifier.weight(1f)
                    )
                }
                snapshot.chartRangeAverageKg?.let { average ->
                    CompactStat(
                        label = stringResource(R.string.weight_stat_average_label),
                        value = UiFormatters.weightKg(average),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(AppDimens.headerStackGap))
        if (snapshot.chartPoints.isEmpty()) {
            Text(
                text = stringResource(R.string.chart_empty_range),
                style = AppTypeTokens.sectionSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            WeightChart(
                points = snapshot.chartPoints,
                contentDescription = stringResource(
                    R.string.chart_content_description,
                    snapshot.chartPoints.size,
                    UiFormatters.weightKg(snapshot.chartPoints.minOf { it.weightKg }),
                    UiFormatters.weightKg(snapshot.chartPoints.maxOf { it.weightKg })
                ),
                onPointSelected = {},
                subdued = true,
                chartHeight = 200.dp
            )
        }
    }
}

@Composable
private fun CompactStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = AppTypeTokens.statValue,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OverviewHeader(
    overview: WeeklyOverview,
    today: LocalDate,
    onOpenSettings: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    val dateRange = UiFormatters.inclusiveDateRange(
        WeeklyOverviewLogic.windowStart(today),
        today
    )
    val weightValue = overview.weightChangeKg?.let(WeeklyOverviewLogic::weightChangeLabel)
        ?: stringResource(R.string.weekly_overview_missing_weight)
    val description = stringResource(
        R.string.weekly_overview_description,
        dateRange,
        overview.workoutCount,
        overview.completedSetCount,
        weightValue
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_weekly_overview")
            .semantics { contentDescription = description }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_weekly_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.weekly_overview_kicker),
                    style = AppTypeTokens.sectionKicker,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.testTag("dashboard_weekly_kicker")
                )
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = dateRange,
                    style = AppTypeTokens.sectionSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.testTag("dashboard_weekly_range")
                )
            }
            OverviewOverflowMenu(
                onOpenTemplates = onOpenTemplates,
                onOpenCatalog = onOpenCatalog,
                onOpenSettings = onOpenSettings
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .testTag("dashboard_weekly_stats"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeeklyStatColumn(
                label = stringResource(R.string.weekly_overview_column_workouts),
                value = overview.workoutCount.toString(),
                testTagPrefix = "dashboard_stat_workouts",
                modifier = Modifier.weight(1f)
            )
            WeeklyStatDivider()
            WeeklyStatColumn(
                label = stringResource(R.string.weekly_overview_column_sets),
                value = overview.completedSetCount.toString(),
                testTagPrefix = "dashboard_stat_sets",
                modifier = Modifier.weight(1f)
            )
            WeeklyStatDivider()
            WeeklyStatColumn(
                label = stringResource(R.string.weekly_overview_column_weight),
                value = weightValue,
                testTagPrefix = "dashboard_stat_weight",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OverviewOverflowMenu(
    onOpenTemplates: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .wrapContentSize(Alignment.TopEnd)
            .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
            .testTag(OVERVIEW_OVERFLOW_ANCHOR),
        contentAlignment = Alignment.TopEnd
    ) {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier
                .size(AppDimens.minTouch)
                .testTag(OVERVIEW_OVERFLOW_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more_overview),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.testTag(OVERVIEW_OVERFLOW_MENU)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.templates_title)) },
                onClick = {
                    menuOpen = false
                    onOpenTemplates()
                },
                modifier = Modifier.testTag(OVERVIEW_OVERFLOW_TEMPLATES)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exercises_title)) },
                onClick = {
                    menuOpen = false
                    onOpenCatalog()
                },
                modifier = Modifier.testTag(OVERVIEW_OVERFLOW_EXERCISES)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    menuOpen = false
                    onOpenSettings()
                },
                modifier = Modifier.testTag(OVERVIEW_OVERFLOW_SETTINGS)
            )
        }
    }
}

@Composable
private fun WeeklyStatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(AppDimens.strokeThin)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun WeeklyStatColumn(
    label: String,
    value: String,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag(testTagPrefix),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = AppTypeTokens.columnHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${testTagPrefix}_label")
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        Text(
            text = value,
            style = AppTypeTokens.statBand,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${testTagPrefix}_value")
        )
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
                weeklyOverview = WeeklyOverview(workoutCount = 3, completedSetCount = 18, weightChangeKg = 0.4),
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
            onOpenTemplates = {},
            onOpenCatalog = {},
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
                weeklyOverview = WeeklyOverview(),
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
            onOpenTemplates = {},
            onOpenCatalog = {},
            onOpenWorkout = {},
            onOpenWeightDetails = {}
        )
    }
}
