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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.calendar.CalendarCell
import app.mymusclemap.domain.calendar.CalendarDayCopy
import app.mymusclemap.domain.calendar.CalendarWorkoutMark
import app.mymusclemap.domain.calendar.MonthGrid
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.WorkoutColors
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
    onTodayBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {}
) {
    val locale = AppLocale.UI
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
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayOrder().forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        grid.weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
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
        if (showLegend) {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            CalendarLegend()
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
            modifier = Modifier.testTag("calendar-legend-weight")
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .border(AppDimens.strokeThin, MaterialTheme.colorScheme.tertiary, CircleShape)
            )
            Text(
                text = stringResource(R.string.calendar_legend_weight),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.testTag("calendar-legend-completed")
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(workoutAccent(), AppShapeTokens.compact)
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
                    .border(1.5.dp, workoutAccent(), AppShapeTokens.compact)
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
        hasMeasurement = cell.hasMeasurement,
        completedWorkoutCount = cell.completedWorkoutCount,
        isToday = cell.isToday,
        isFuture = cell.isFuture,
        plannedWorkoutCount = cell.plannedWorkoutCount
    )
    val luminance = MaterialTheme.colorScheme.background.luminance()
    val workout = WorkoutColors.accent(luminance)
    val onWorkout = WorkoutColors.onAccent(luminance)
    val shape = AppShapeTokens.compact
    val textColor = when {
        mark == CalendarWorkoutMark.Completed -> onWorkout
        !cell.inDisplayedMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.primary
        cell.isFuture && mark == CalendarWorkoutMark.None ->
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when (mark) {
        CalendarWorkoutMark.Planned -> workout
        CalendarWorkoutMark.None -> if (selected) MaterialTheme.colorScheme.primary else null
        CalendarWorkoutMark.Completed -> null
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
                if (borderColor != null) {
                    Modifier.border(1.5.dp, borderColor, shape)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(if (mark == CalendarWorkoutMark.Completed) workout else Color.Transparent)
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
        if (selected && mark == CalendarWorkoutMark.Completed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .border(1.dp, onWorkout, shape)
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
                    .padding(bottom = 3.dp)
                    .width(12.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(if (mark == CalendarWorkoutMark.Completed) onWorkout else MaterialTheme.colorScheme.onSurface)
                    .testTag("calendar-today-marker")
            )
        }
        if (cell.hasMeasurement) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(4.dp)
                    .testTag("calendar-weight-dot")
                    .border(
                        width = AppDimens.strokeThin,
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
internal fun workoutAccent(): Color {
    return WorkoutColors.accent(MaterialTheme.colorScheme.background.luminance())
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
