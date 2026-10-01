package app.mymusclemap.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.calendar.CalendarCell
import app.mymusclemap.domain.calendar.MonthGrid
import app.mymusclemap.domain.calendar.MonthGridCalculator
import app.mymusclemap.domain.calendar.WeekCalendar
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.WeightTrackerTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import kotlinx.coroutines.launch

internal object WeekPager {
    const val Center = 500
    const val Count = 1001
}

private val WeekCellHeight = 32.dp

@Composable
fun OverviewCalendar(
    monthGrid: MonthGrid,
    displayedMonth: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    onDisplayedMonthChange: (YearMonth) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onTodayBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    startExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var scrollExpandedIntoView by remember { mutableStateOf(false) }
    val todayWeekStart = WeekCalendar.start(today)
    val pagerState = rememberPagerState(initialPage = WeekPager.Center, pageCount = { WeekPager.Count })
    val weekOffset = pagerState.currentPage - WeekPager.Center
    val weekStart = todayWeekStart.plusWeeks(weekOffset.toLong())
    val scope = rememberCoroutineScope()
    LaunchedEffect(weekStart, expanded) {
        if (!expanded && !WeekCalendar.fitsInMonth(displayedMonth, weekStart)) {
            onDisplayedMonthChange(WeekCalendar.primaryMonth(weekStart))
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onGloballyPositioned {
                // Placement has the expanded month's size. The week bounds would already be on screen.
                if (!expanded || !scrollExpandedIntoView) return@onGloballyPositioned
                scrollExpandedIntoView = false
                scope.launch { bringIntoViewRequester.bringIntoView() }
            }
            .testTag("dashboard_calendar")
    ) {
        if (expanded) {
            MonthCalendar(
                grid = monthGrid,
                selectedDate = selectedDate,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDayClick = onDayClick,
                onCollapse = {
                    expanded = false
                },
                onTodayBounds = onTodayBounds
            )
        } else {
            WeekCalendarHeader(
                weekStart = weekStart,
                isCurrentWeek = weekOffset == 0,
                onExpand = {
                    if (!WeekCalendar.fitsInMonth(displayedMonth, weekStart)) {
                        onDisplayedMonthChange(WeekCalendar.primaryMonth(weekStart))
                    }
                    expanded = true
                    scrollExpandedIntoView = true
                },
                onReturnToToday = {
                    scope.launch { pagerState.animateScrollToPage(WeekPager.Center) }
                }
            )
            WeekdayLabels()
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WeekCellHeight)
                    .testTag("overview-calendar-pager")
            ) { page ->
                val pageStart = todayWeekStart.plusWeeks((page - WeekPager.Center).toLong())
                WeekDateRow(
                    cells = weekCells(monthGrid, pageStart, today),
                    selectedDate = selectedDate,
                    onDayClick = onDayClick,
                    onTodayBounds = onTodayBounds
                )
            }
        }
    }
}

@Composable
private fun WeekCalendarHeader(
    weekStart: LocalDate,
    isCurrentWeek: Boolean,
    onExpand: () -> Unit,
    onReturnToToday: () -> Unit
) {
    val range = UiFormatters.inclusiveDateRange(weekStart, WeekCalendar.end(weekStart))
    val heading = if (isCurrentWeek) {
        stringResource(R.string.calendar_week_heading, range)
    } else {
        range
    }
    val expandLabel = stringResource(R.string.calendar_show_month)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 32.dp)
            .clickable(onClick = onExpand)
            .semantics { contentDescription = expandLabel }
            .testTag("overview-calendar-expand"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = heading,
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag("overview-calendar-heading")
        )
        if (!isCurrentWeek) {
            Text(
                text = stringResource(R.string.calendar_return_today),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onReturnToToday)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("overview-calendar-today")
            )
        }
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun WeekdayLabels() {
    val locale = AppLocale.UI
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdayOrder().forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, locale),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WeekDateRow(
    cells: List<CalendarCell>,
    selectedDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    onTodayBounds: (androidx.compose.ui.geometry.Rect) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(WeekCellHeight)
    ) {
        cells.forEach { cell ->
            CalendarDayCell(
                cell = cell,
                selected = selectedDate == cell.date,
                enabled = true,
                onClick = { onDayClick(cell.date) },
                onTodayBounds = onTodayBounds,
                cellHeight = WeekCellHeight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(showBackground = true, name = "Week light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Week dark")
@Composable
private fun CurrentWeekPreview() {
    CalendarPreview(today = LocalDate.of(2026, 10, 1))
}

@Preview(showBackground = true, name = "Week planned")
@Composable
private fun PlannedWeekPreview() {
    CalendarPreview(
        today = LocalDate.of(2026, 10, 1),
        planned = mapOf(LocalDate.of(2026, 9, 30) to 1, LocalDate.of(2026, 10, 2) to 1)
    )
}

@Preview(showBackground = true, name = "Week completed")
@Composable
private fun CompletedWeekPreview() {
    CalendarPreview(
        today = LocalDate.of(2026, 10, 1),
        completed = mapOf(LocalDate.of(2026, 9, 29) to 1, LocalDate.of(2026, 10, 1) to 1),
        planned = mapOf(LocalDate.of(2026, 10, 1) to 1)
    )
}

@Preview(showBackground = true, name = "Month expanded light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Month expanded dark")
@Composable
private fun ExpandedMonthPreview() {
    CalendarPreview(
        today = LocalDate.of(2026, 10, 1),
        completed = mapOf(LocalDate.of(2026, 9, 29) to 1),
        planned = mapOf(LocalDate.of(2026, 10, 3) to 1),
        expanded = true
    )
}

@Composable
private fun CalendarPreview(
    today: LocalDate,
    completed: Map<LocalDate, Int> = emptyMap(),
    planned: Map<LocalDate, Int> = emptyMap(),
    expanded: Boolean = false
) {
    val month = YearMonth.from(today)
    WeightTrackerTheme {
        OverviewCalendar(
            monthGrid = MonthGridCalculator.grid(
                month = month,
                today = today,
                measuredDates = setOf(today),
                completedWorkoutCounts = completed,
                plannedWorkoutCounts = planned
            ),
            displayedMonth = month,
            today = today,
            selectedDate = null,
            onDisplayedMonthChange = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onDayClick = {},
            startExpanded = expanded
        )
    }
}

private fun weekCells(grid: MonthGrid, weekStart: LocalDate, today: LocalDate): List<CalendarCell> {
    val byDate = grid.cells.associateBy { it.date }
    return WeekCalendar.dates(weekStart).map { date ->
        val existing = byDate[date]
        if (existing != null) {
            existing.copy(inDisplayedMonth = true)
        } else {
            CalendarCell(
                date = date,
                inDisplayedMonth = true,
                isToday = date == today,
                hasMeasurement = false,
                completedWorkoutCount = 0,
                plannedWorkoutCount = 0,
                isFuture = date.isAfter(today)
            )
        }
    }
}
