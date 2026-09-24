package hu.laca.weighttracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.calendar.MonthGridCalculator
import hu.laca.weighttracker.domain.workout.ScheduledWorkout
import hu.laca.weighttracker.domain.workout.ScheduledWorkoutUiLogic
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppTypeTokens
import hu.laca.weighttracker.ui.workout.CompactSheetHandle
import java.time.LocalDate
import java.time.YearMonth

internal const val RESCHEDULE_SHEET = "reschedule-sheet"
internal const val RESCHEDULE_ERROR = "reschedule-error"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDateSheet(
    target: ScheduledWorkout,
    today: LocalDate,
    errorText: String?,
    onDismiss: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var month by remember(target.id, target.scheduledDate) {
        mutableStateOf(YearMonth.from(target.scheduledDate))
    }
    val grid = MonthGridCalculator.grid(
        month = month,
        today = today,
        measuredDates = emptySet()
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(RESCHEDULE_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = AppDimens.itemGap)
        ) {
            Text(
                text = stringResource(R.string.reschedule_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = target.templateName,
                style = AppTypeTokens.sectionSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (errorText != null) {
                Spacer(Modifier.height(AppDimens.headerStackGap))
                Text(
                    text = errorText,
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(RESCHEDULE_ERROR)
                )
            }
            Spacer(Modifier.height(AppDimens.itemGap))
            MonthCalendar(
                grid = grid,
                selectedDate = target.scheduledDate,
                onPreviousMonth = { month = month.minusMonths(1) },
                onNextMonth = { month = month.plusMonths(1) },
                onDayClick = onSelectDate,
                isDayEnabled = { cell ->
                    ScheduledWorkoutUiLogic.canSelectRescheduleDate(cell.date, today)
                },
                showLegend = false
            )
        }
    }
}
