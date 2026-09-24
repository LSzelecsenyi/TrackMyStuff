package hu.laca.weighttracker.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.calendar.CalendarCell
import hu.laca.weighttracker.domain.calendar.MonthGrid
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppShapeTokens
import hu.laca.weighttracker.ui.theme.AppTypeTokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthCalendar(
    grid: MonthGrid,
    selectedDate: LocalDate?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    isDayEnabled: (CalendarCell) -> Boolean = { true },
    showLegend: Boolean = true
) {
    val locale = Locale.forLanguageTag("hu-HU")
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
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
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
                    .size(6.dp)
                    .border(AppDimens.strokeThin, MaterialTheme.colorScheme.secondary, CircleShape)
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
private fun CalendarDayCell(
    cell: CalendarCell,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = hu.laca.weighttracker.domain.calendar.CalendarDayCopy.description(
        date = cell.date,
        hasMeasurement = cell.hasMeasurement,
        completedWorkoutCount = cell.completedWorkoutCount,
        isToday = cell.isToday,
        isFuture = cell.isFuture,
        plannedWorkoutCount = cell.plannedWorkoutCount
    )
    val textColor = when {
        !cell.inDisplayedMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        selected || cell.isToday -> MaterialTheme.colorScheme.primary
        cell.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val outline = when {
        selected -> MaterialTheme.colorScheme.primary
        cell.isToday -> MaterialTheme.colorScheme.outline
        else -> null
    }
    Box(
        modifier = modifier
            .height(AppDimens.calendarCell)
            .padding(1.dp)
            .then(
                if (outline != null) {
                    Modifier.border(AppDimens.strokeThin, outline, AppShapeTokens.compact)
                } else {
                    Modifier
                }
            )
            .clip(AppShapeTokens.compact)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cell.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (cell.isToday || selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
            }
            if (cell.hasMeasurement || cell.hasPlannedWorkout || cell.hasCompletedWorkout) {
                Row(
                    modifier = Modifier.padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cell.hasMeasurement) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .testTag("calendar-weight-dot")
                                .border(
                                    width = AppDimens.strokeThin,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    shape = CircleShape
                                )
                        )
                    }
                    if (cell.hasPlannedWorkout) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .testTag("calendar-planned-dot")
                                .border(
                                    width = AppDimens.strokeThin,
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = CircleShape
                                )
                        )
                    }
                    if (cell.hasCompletedWorkout) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .testTag("calendar-completed-dot")
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun weekdayOrder(): List<DayOfWeek> {
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
