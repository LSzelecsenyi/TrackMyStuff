package app.mymusclemap.ui.dashboard

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.founder.FounderChecklistRow
import app.mymusclemap.ui.founder.FounderProUnlockedDialog
import app.mymusclemap.ui.membership.MembershipBadge
import app.mymusclemap.ui.membership.MembershipDetail
import app.mymusclemap.ui.membership.MembershipPresentation
import app.mymusclemap.ui.pro.ProInfoSheet
import app.mymusclemap.domain.DashboardSnapshot
import app.mymusclemap.domain.WeeklyOverview
import app.mymusclemap.domain.WeeklyOverviewLogic
import app.mymusclemap.domain.achievements.NextWorkoutMilestone
import app.mymusclemap.domain.calendar.MonthGridCalculator
import app.mymusclemap.domain.model.ChartPoint
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.ui.components.DayDetailsSheet
import app.mymusclemap.ui.components.DeleteMeasurementDialog
import app.mymusclemap.ui.components.MeasurementEditorSheet
import app.mymusclemap.ui.components.OverviewCalendar
import app.mymusclemap.ui.components.WeeklyGoalEditorSheet
import app.mymusclemap.ui.components.RescheduleDateSheet
import app.mymusclemap.ui.components.ScheduleWorkoutPickerSheet
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.components.UnscheduleWorkoutDialog
import app.mymusclemap.ui.components.UserMessageEffect
import app.mymusclemap.ui.components.stringRes
import app.mymusclemap.ui.components.WeightChart
import app.mymusclemap.domain.health.HealthCardState
import app.mymusclemap.domain.health.HealthQuietStatus
import app.mymusclemap.ui.components.musclemap.MuscleHeatmapCard
import app.mymusclemap.ui.health.HealthConnectOverviewCard
import app.mymusclemap.ui.onboarding.OnboardingReminderCard
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import app.mymusclemap.ui.theme.WeightTrackerTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

internal const val OVERVIEW_OVERFLOW_ANCHOR = "overview-overflow-anchor"
internal const val OVERVIEW_OVERFLOW_BUTTON = "overview-overflow-button"
internal const val OVERVIEW_OVERFLOW_MENU = "overview-overflow-menu"
internal const val OVERVIEW_OVERFLOW_TEMPLATES = "overview-overflow-templates"
internal const val OVERVIEW_OVERFLOW_EXERCISES = "overview-overflow-exercises"
internal const val OVERVIEW_OVERFLOW_SETTINGS = "overview-overflow-settings"
internal const val OVERVIEW_OVERFLOW_ACHIEVEMENTS = "overview-overflow-achievements"
internal const val OVERVIEW_NEXT_ACHIEVEMENT = "overview-next-achievement"
internal const val OVERVIEW_MEMBERSHIP_BADGE = "overview-membership-badge"
internal const val OVERVIEW_MEMBERSHIP_INFO = "overview-membership-info"
private val HeatmapCoachCalloutSpace = 176.dp

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDisplayedMonthChange: (YearMonth) -> Unit = {},
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
    onCreateTemplateFromSchedule: () -> Unit = {},
    onContinueOnboarding: () -> Unit = {},
    onDismissOnboardingReminder: () -> Unit = {},
    heatmapRevealRequested: Boolean = false,
    calendarRevealRequested: Boolean = false,
    chartRevealRequested: Boolean = false,
    onHeatmapBounds: (Rect) -> Unit = {},
    onCalendarBounds: (Rect) -> Unit = {},
    onTodayBounds: (Rect) -> Unit = {},
    onChartBounds: (Rect) -> Unit = {},
    onDashboardTargetRevealed: () -> Unit = {},
    onDismissLocked: () -> Unit = {},
    health: HealthCardState = HealthCardState.Quiet(HealthQuietStatus.NotConnected),
    onOpenHealthSettings: () -> Unit = {},
    onOpenHealthDetails: () -> Unit = {},
    onSetWeeklyGoal: (Int) -> Unit = {},
    onDisableWeeklyGoal: () -> Unit = {},
    membership: MembershipPresentation = MembershipPresentation.None,
    showTemporaryProMilestone: Boolean = false,
    temporaryProChecklist: List<FounderChecklistRow> = emptyList(),
    onAcknowledgeTemporaryPro: () -> Unit = {},
    showTrainingCompleteMilestone: Boolean = false,
    onAcknowledgeTrainingComplete: () -> Unit = {},
    nextAchievement: NextWorkoutMilestone? = null,
    onOpenAchievements: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var weeklyGoalEditorOpen by remember { mutableStateOf(false) }
    var membershipInfoOpen by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var heatmapSize by remember { mutableStateOf(IntSize.Zero) }
    var heatmapYInContent by remember { mutableStateOf(0) }
    var calendarSize by remember { mutableStateOf(IntSize.Zero) }
    var calendarYInContent by remember { mutableStateOf(0) }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var chartYInContent by remember { mutableStateOf(0) }
    val revealTarget by rememberUpdatedState(onDashboardTargetRevealed)
    val reportHeatmapBounds by rememberUpdatedState(onHeatmapBounds)
    val reportCalendarBounds by rememberUpdatedState(onCalendarBounds)
    val reportTodayBounds by rememberUpdatedState(onTodayBounds)
    val reportChartBounds by rememberUpdatedState(onChartBounds)
    val revealRequest = when {
        heatmapRevealRequested -> "heatmap"
        calendarRevealRequested -> "calendar"
        chartRevealRequested -> "chart"
        else -> null
    }
    var scrolledRevealKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revealRequest) {
        val key = revealRequest
        if (key == null) {
            scrolledRevealKey = null
            return@LaunchedEffect
        }
        snapshotFlow {
            when (key) {
                "heatmap" -> heatmapSize
                "calendar" -> calendarSize
                else -> chartSize
            }
        }.first { it != IntSize.Zero }
        if (scrolledRevealKey != key) {
            scrolledRevealKey = key
            val extra = with(density) { HeatmapCoachCalloutSpace.roundToPx() }
            val y = when (key) {
                "heatmap" -> heatmapYInContent
                "calendar" -> calendarYInContent
                else -> chartYInContent
            }
            val target = (y - extra).coerceAtLeast(0)
            if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
                scrollState.scrollTo(target)
            } else {
                scrollState.animateScrollTo(target)
            }
        }
        revealTarget()
    }
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
                .verticalScroll(scrollState)
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 4.dp, bottom = 24.dp)
        ) {
            OverviewHeader(
                overview = state.weeklyOverview,
                today = state.today,
                membership = membership,
                onMembershipClick = { membershipInfoOpen = true },
                onOpenSettings = onOpenSettings,
                onOpenTemplates = onOpenTemplates,
                onOpenCatalog = onOpenCatalog,
                onOpenAchievements = onOpenAchievements
            )
            if (state.onboarding.reminderVisible) {
                OverviewSectionDivider()
                OnboardingReminderCard(
                    checklist = state.onboarding.checklist,
                    onContinue = onContinueOnboarding,
                    onDismiss = onDismissOnboardingReminder
                )
            }
            OverviewSectionDivider()
            MuscleHeatmapCard(
                state = state.heatmap,
                modifier = Modifier
                    .testTag("dashboard_heatmap")
                    .onGloballyPositioned { coordinates ->
                        heatmapSize = coordinates.size
                        heatmapYInContent = coordinates.positionInParent().y.roundToInt()
                        reportHeatmapBounds(coordinates.boundsInRoot())
                    }
            )
            OverviewSectionDivider()
            OverviewCalendar(
                monthGrid = state.monthGrid,
                displayedMonth = state.displayedMonth,
                today = state.today,
                selectedDate = state.daySheet?.date,
                onDisplayedMonthChange = onDisplayedMonthChange,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDayClick = onDaySelected,
                onTodayBounds = reportTodayBounds,
                weeklyGoal = state.weeklyGoal,
                onConfigureGoal = { weeklyGoalEditorOpen = true },
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    calendarSize = coordinates.size
                    calendarYInContent = coordinates.positionInParent().y.roundToInt()
                    reportCalendarBounds(coordinates.boundsInRoot())
                }
            )
            if (nextAchievement != null) {
                NextAchievementLine(
                    milestone = nextAchievement,
                    onClick = onOpenAchievements
                )
            }
            OverviewSectionDivider()
            HealthConnectOverviewCard(
                state = health,
                onOpenSettings = onOpenHealthSettings,
                onOpenDetails = onOpenHealthDetails
            )
            OverviewSectionDivider()
            CompactWeightChartSection(
                snapshot = state.snapshot,
                onOpenDetails = onOpenWeightDetails,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    chartSize = coordinates.size
                    chartYInContent = coordinates.positionInParent().y.roundToInt()
                    reportChartBounds(coordinates.boundsInRoot())
                }
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
    if (weeklyGoalEditorOpen) {
        WeeklyGoalEditorSheet(
            status = state.weeklyGoal,
            onSave = onSetWeeklyGoal,
            onDisable = onDisableWeeklyGoal,
            onDismiss = { weeklyGoalEditorOpen = false }
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
    state.lockedFeature?.let { feature ->
        ProInfoSheet(
            feature = feature,
            onDismiss = onDismissLocked
        )
    }
    if (showTemporaryProMilestone) {
        FounderProUnlockedDialog(
            checklist = temporaryProChecklist,
            onAcknowledge = onAcknowledgeTemporaryPro
        )
    } else if (showTrainingCompleteMilestone) {
        app.mymusclemap.ui.founder.FounderTrainingCompleteDialog(
            onAcknowledge = onAcknowledgeTrainingComplete
        )
    }
    val membershipDetail = membership.detail
    if (membershipInfoOpen && membershipDetail != null) {
        MembershipInfoDialog(
            detail = membershipDetail,
            onDismiss = { membershipInfoOpen = false }
        )
    }
}

@Composable
internal fun NextAchievementLine(
    milestone: NextWorkoutMilestone,
    onClick: () -> Unit
) {
    val detail = if (milestone.next == null) {
        stringResource(R.string.achievements_all_workout_milestones)
    } else {
        stringResource(R.string.achievements_workouts_name, milestone.next.workoutThreshold!!)
    }
    val progress = milestone.next?.let { next ->
        val threshold = next.workoutThreshold ?: return@let null
        stringResource(
            R.string.achievements_progress_count,
            milestone.completed.coerceAtMost(threshold),
            threshold
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(OVERVIEW_NEXT_ACHIEVEMENT)
    ) {
        Text(
            text = stringResource(R.string.achievements_next),
            style = AppTypeTokens.columnHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyLarge
        )
        if (progress != null) {
            Text(
                text = progress,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("overview-next-achievement-progress")
            )
        }
    }
}

@Composable
private fun OverviewSectionDivider() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.strokeThin)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CompactWeightChartSection(
    snapshot: DashboardSnapshot,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
    membership: MembershipPresentation,
    onMembershipClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenAchievements: () -> Unit
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
        pluralStringResource(
            R.plurals.weekly_overview_workouts,
            overview.workoutCount,
            overview.workoutCount
        ),
        pluralStringResource(
            R.plurals.weekly_overview_sets,
            overview.completedSetCount,
            overview.completedSetCount
        ),
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
            verticalAlignment = Alignment.Bottom
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
                Text(
                    text = dateRange,
                    style = AppTypeTokens.sectionSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.testTag("dashboard_weekly_range")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MembershipBadgeChip(
                    presentation = membership,
                    onClick = onMembershipClick
                )
                OverviewOverflowMenu(
                    onOpenTemplates = onOpenTemplates,
                    onOpenCatalog = onOpenCatalog,
                    onOpenSettings = onOpenSettings,
                    onOpenAchievements = onOpenAchievements
                )
            }
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

/**
 * Compact membership mark in the Overview header.
 *
 * TODO: Replace the Founder label with the final Strict Founder artwork during the planned branding pass.
 * This chip is not a final logo and does not grant Pro.
 */
@Composable
private fun MembershipBadgeChip(
    presentation: MembershipPresentation,
    onClick: () -> Unit
) {
    val badge = presentation.badge
    if (badge == MembershipBadge.None || presentation.detail == null) return
    val label = when (badge) {
        MembershipBadge.Pro -> stringResource(R.string.membership_badge_pro)
        MembershipBadge.Founder -> stringResource(R.string.membership_badge_founder)
        MembershipBadge.None -> return
    }
    val description = when (badge) {
        MembershipBadge.Pro -> stringResource(R.string.membership_badge_pro_description)
        MembershipBadge.Founder -> stringResource(R.string.founder_badge)
        MembershipBadge.None -> label
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .padding(end = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(OVERVIEW_MEMBERSHIP_BADGE)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun MembershipInfoDialog(
    detail: MembershipDetail,
    onDismiss: () -> Unit
) {
    val title = when (detail) {
        MembershipDetail.FoundingMember -> stringResource(R.string.founder_badge)
        MembershipDetail.TemporaryFounderPro,
        MembershipDetail.PendingFounderReview,
        MembershipDetail.Pro -> stringResource(R.string.membership_pro_title)
    }
    val body = when (detail) {
        MembershipDetail.TemporaryFounderPro -> stringResource(R.string.membership_temporary_pro_body)
        MembershipDetail.PendingFounderReview -> stringResource(R.string.membership_pending_pro_body)
        MembershipDetail.FoundingMember -> stringResource(R.string.founder_pro_active)
        MembershipDetail.Pro -> stringResource(R.string.membership_generic_pro_body)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                modifier = Modifier.testTag(OVERVIEW_MEMBERSHIP_INFO)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        }
    )
}

@Composable
private fun OverviewOverflowMenu(
    onOpenTemplates: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAchievements: () -> Unit
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
                text = { Text(stringResource(R.string.achievements_title)) },
                onClick = {
                    menuOpen = false
                    onOpenAchievements()
                },
                modifier = Modifier.testTag(OVERVIEW_OVERFLOW_ACHIEVEMENTS)
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
            color = StrictBrand.result(),
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
                    chartRangeAverageKg = 82.1
                ),
                today = date,
                weeklyOverview = WeeklyOverview(workoutCount = 3, completedSetCount = 18, weightChangeKg = 0.4),
                displayedMonth = month,
                monthGrid = MonthGridCalculator.grid(month, date)
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
                monthGrid = MonthGridCalculator.grid(month, date)
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
