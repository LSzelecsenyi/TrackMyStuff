package app.mymusclemap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.calendar.CalendarCell
import app.mymusclemap.domain.calendar.CalendarDayCopy
import app.mymusclemap.domain.calendar.CalendarWorkoutMark
import app.mymusclemap.domain.calendar.MonthGrid
import app.mymusclemap.domain.calendar.WeekCalendar
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.workout.WeekProgress
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun MonthCalendar(
    grid: MonthGrid,
    selectedDate: LocalDate?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    isDayEnabled: (CalendarCell) -> Boolean = { true },
    showLegend: Boolean = true,
    onCollapse: (() -> Unit)? = null,
    onTodayBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    progressForWeek: (LocalDate) -> WeekProgress? = { null }
) {
    val locale = AppLocale.UI
    val currentWeekStart = grid.cells.firstOrNull { it.isToday }?.let { WeekCalendar.start(it.date) }
    fun treatmentFor(weekStart: LocalDate): WeekTrophyTreatment {
        val progress = progressForWeek(weekStart) ?: return WeekTrophyTreatment.Hidden
        return weekTrophyTreatment(progress, weekStart == currentWeekStart)
    }
    val showTrophyLane = grid.weeks.any { week ->
        treatmentFor(week.first().date) != WeekTrophyTreatment.Hidden
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(AppDimens.minTouch)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.calendar_previous_month)
                )
            }
            Text(
                text = UiFormatters.monthTitle(grid.month),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(AppDimens.minTouch)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.calendar_next_month)
                )
            }
            if (onCollapse != null) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(AppDimens.minTouch)
                        .testTag("overview-calendar-collapse")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExpandLess,
                        contentDescription = stringResource(R.string.calendar_show_week)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calendar-month-grid")
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (showTrophyLane) {
                    Spacer(
                        Modifier
                            .width(WeekTrophyLane)
                            .testTag("calendar-trophy-lane")
                    )
                }
                weekdayOrder().forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, locale),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calendar-weekday"),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            grid.weeks.forEach { week ->
                val weekStart = week.first().date
                val progress = progressForWeek(weekStart)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showTrophyLane) {
                        WeekStreakMark(
                            progress = progress,
                            isCurrentWeek = weekStart == currentWeekStart
                        )
                    }
                    week.forEach { cell ->
                        CalendarDayCell(
                            cell = cell,
                            selected = selectedDate == cell.date,
                            enabled = isDayEnabled(cell),
                            onClick = { onDayClick(cell.date) },
                            onTodayBounds = onTodayBounds,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        if (showLegend) {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            CalendarLegend()
        }
    }
}

private val WeekTrophyLane = 28.dp
internal val WeeklyStreakIconSize = 22.dp
internal val PlannedOutlineWidth = 3.dp

@Composable
internal fun WeekStreakBadge(
    streak: Int,
    pending: Boolean,
    showCount: Boolean,
    modifier: Modifier = Modifier,
    stacked: Boolean = false
) {
    val description = if (pending) {
        stringResource(R.string.weekly_goal_streak_pending)
    } else {
        pluralStringResource(R.plurals.weekly_goal_streak, streak, streak)
    }
    val icon = if (pending) Icons.Outlined.EmojiEvents else Icons.Filled.EmojiEvents
    val iconTag = if (pending) "calendar-trophy-pending" else "calendar-trophy-achieved"
    if (stacked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TrophyIcon(icon, description, iconTag)
            if (showCount) {
                TrophyCount(streak, stacked = true)
            }
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TrophyIcon(icon, description, iconTag)
            if (showCount) {
                TrophyCount(streak, stacked = false)
            }
        }
    }
}

@Composable
private fun TrophyIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    iconTag: String
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(WeeklyStreakIconSize)
            .testTag(iconTag)
    )
}

@Composable
private fun TrophyCount(streak: Int, stacked: Boolean) {
    Text(
        text = streak.toString(),
        style = if (stacked) AppTypeTokens.statCaption else AppTypeTokens.sectionTitle,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.testTag("calendar-trophy-count")
    )
}

@Composable
private fun WeekStreakMark(progress: WeekProgress?, isCurrentWeek: Boolean) {
    val treatment = progress?.let { weekTrophyTreatment(it, isCurrentWeek) } ?: WeekTrophyTreatment.Hidden
    Box(
        modifier = Modifier
            .width(WeekTrophyLane)
            .height(AppDimens.calendarCell)
            .testTag("calendar-week-streak"),
        contentAlignment = Alignment.Center
    ) {
        if (progress != null && treatment != WeekTrophyTreatment.Hidden) {
            WeekStreakBadge(
                streak = progress.streak,
                pending = treatment == WeekTrophyTreatment.Pending,
                showCount = treatment == WeekTrophyTreatment.Achieved,
                stacked = true
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend() {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar-legend"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.testTag("calendar-legend-completed")
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(StrictBrand.lime, AppShapeTokens.compact)
            )
            Text(
                text = stringResource(R.string.calendar_legend_workout),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.testTag("calendar-legend-planned")
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .border(2.dp, StrictBrand.lime, AppShapeTokens.compact)
            )
            Text(
                text = stringResource(R.string.calendar_legend_planned),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CalendarDayCell(
    cell: CalendarCell,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onTodayBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier,
    cellHeight: Dp = AppDimens.calendarCell
) {
    val mark = cell.workoutMark()
    val label = CalendarDayCopy.description(
        resources = LocalResources.current,
        date = cell.date,
        completedWorkoutCount = cell.completedWorkoutCount,
        isToday = cell.isToday,
        isFuture = cell.isFuture,
        plannedWorkoutCount = cell.plannedWorkoutCount
    )
    val onCompleted = StrictBrand.dark
    val chrome = calendarDayChrome(
        mark = mark,
        selected = selected,
        selectionColor = MaterialTheme.colorScheme.primary,
        selectionRingColor = MaterialTheme.colorScheme.onSurface
    )
    val shape = AppShapeTokens.compact
    val textColor = when {
        mark == CalendarWorkoutMark.Completed -> onCompleted
        !cell.inDisplayedMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.primary
        cell.isFuture && mark == CalendarWorkoutMark.None ->
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val workoutTag = when (mark) {
        CalendarWorkoutMark.Completed -> "calendar-completed-fill"
        CalendarWorkoutMark.Planned -> "calendar-planned-outline"
        CalendarWorkoutMark.None -> null
    }
    Box(
        modifier = modifier
            .height(cellHeight)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .then(
                if (chrome.outline != null) {
                    Modifier.border(chrome.outlineWidth, chrome.outline, shape)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(chrome.fill ?: Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .then(
                if (cell.isToday) {
                    Modifier.onGloballyPositioned { coordinates ->
                        onTodayBounds(coordinates.boundsInRoot())
                    }
                } else {
                    Modifier
                }
            )
            .then(if (workoutTag != null) Modifier.testTag(workoutTag) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (chrome.selectionRing != null) {
            val ringInset = if (mark == CalendarWorkoutMark.Planned) PlannedOutlineWidth + 1.dp else 3.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ringInset)
                    .border(1.dp, chrome.selectionRing, shape)
                    .testTag("calendar-selection-ring")
            )
        }
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (cell.isToday || selected || mark == CalendarWorkoutMark.Completed) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            },
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        if (cell.isToday) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (mark == CalendarWorkoutMark.Planned) PlannedOutlineWidth + 2.dp else 3.dp)
                    .width(12.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(if (mark == CalendarWorkoutMark.Completed) onCompleted else MaterialTheme.colorScheme.onSurface)
                    .testTag("calendar-today-marker")
            )
        }
    }
}

internal enum class WeekTrophyTreatment {
    Hidden,
    Pending,
    Achieved
}

internal fun weekTrophyTreatment(progress: WeekProgress, isCurrentWeek: Boolean): WeekTrophyTreatment {
    if (progress.goal == null) return WeekTrophyTreatment.Hidden
    if (isCurrentWeek && !progress.achieved) return WeekTrophyTreatment.Pending
    if (isCurrentWeek && progress.achieved) return WeekTrophyTreatment.Achieved
    if (progress.streak > 0) return WeekTrophyTreatment.Achieved
    return WeekTrophyTreatment.Hidden
}

internal data class CalendarDayChrome(
    val fill: Color?,
    val outline: Color?,
    val outlineWidth: Dp,
    val selectionRing: Color?
)

internal fun calendarDayChrome(
    mark: CalendarWorkoutMark,
    selected: Boolean,
    selectionColor: Color,
    selectionRingColor: Color
): CalendarDayChrome {
    return when (mark) {
        CalendarWorkoutMark.Completed -> CalendarDayChrome(
            fill = StrictBrand.lime,
            outline = null,
            outlineWidth = 0.dp,
            selectionRing = if (selected) StrictBrand.dark else null
        )
        CalendarWorkoutMark.Planned -> CalendarDayChrome(
            fill = null,
            outline = StrictBrand.lime,
            outlineWidth = PlannedOutlineWidth,
            selectionRing = if (selected) selectionRingColor else null
        )
        CalendarWorkoutMark.None -> CalendarDayChrome(
            fill = null,
            outline = if (selected) selectionColor else null,
            outlineWidth = if (selected) 1.5.dp else 0.dp,
            selectionRing = null
        )
    }
}

internal fun weekdayOrder(): List<DayOfWeek> {
    return listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )
}
