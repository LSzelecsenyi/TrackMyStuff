package hu.laca.weighttracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.DaySheetState
import hu.laca.weighttracker.domain.workout.ScheduledStatusLabel
import hu.laca.weighttracker.domain.workout.ScheduledWorkout
import hu.laca.weighttracker.domain.workout.ScheduledWorkoutUiLogic
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppTypeTokens
import hu.laca.weighttracker.ui.workout.CompactSheetHandle
import java.time.LocalDate

internal const val DAY_DETAILS_SHEET = "day-details-sheet"
internal const val DAY_SHEET_SCHEDULE_ACTION = "day-sheet-schedule-action"
internal const val DAY_SHEET_RECORD_WEIGHT = "day-sheet-record-weight"

internal fun daySheetScheduledRowTag(id: Long): String = "day-sheet-scheduled-row-$id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsSheet(
    state: DaySheetState,
    today: LocalDate,
    onRecordWeight: () -> Unit,
    onEditWeight: () -> Unit,
    onDeleteWeight: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onScheduleWorkout: () -> Unit,
    onStartScheduled: (Long) -> Unit,
    onContinueScheduled: (Long) -> Unit,
    onOpenScheduledJournal: (Long) -> Unit,
    onReschedule: (ScheduledWorkout) -> Unit,
    onUnschedule: (ScheduledWorkout) -> Unit,
    onDismiss: () -> Unit,
    busy: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var actionLocked by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (!busy) {
            actionLocked = false
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(DAY_DETAILS_SHEET)
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
                text = UiFormatters.longDateWithWeekday(state.date),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            ScheduledWorkoutsSection(
                items = state.scheduledWorkouts,
                today = today,
                busy = busy || actionLocked,
                onScheduleWorkout = {
                    if (actionLocked || busy) return@ScheduledWorkoutsSection
                    onScheduleWorkout()
                },
                onStartScheduled = { id ->
                    if (actionLocked || busy) return@ScheduledWorkoutsSection
                    actionLocked = true
                    onStartScheduled(id)
                },
                onContinueScheduled = onContinueScheduled,
                onOpenScheduledJournal = onOpenScheduledJournal,
                onReschedule = onReschedule,
                onUnschedule = onUnschedule
            )
            if (state.canRecordWeight || state.measurement != null) {
                Spacer(Modifier.height(AppDimens.sectionGap))
                WeightSection(
                    state = state,
                    onRecordWeight = onRecordWeight,
                    onEditWeight = onEditWeight,
                    onDeleteWeight = onDeleteWeight
                )
            }
            if (state.workouts.isNotEmpty()) {
                Spacer(Modifier.height(AppDimens.sectionGap))
                Text(
                    text = stringResource(R.string.day_sheet_workouts),
                    style = AppTypeTokens.sectionKicker,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                state.workouts.forEachIndexed { index, workout ->
                    DayWorkoutRow(summary = workout, onOpen = { onOpenWorkout(workout.session.id) })
                    if (index != state.workouts.lastIndex) {
                        HorizontalDivider(
                            thickness = AppDimens.strokeThin,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun ScheduledWorkoutsSection(
    items: List<ScheduledWorkout>,
    today: LocalDate,
    busy: Boolean,
    onScheduleWorkout: () -> Unit,
    onStartScheduled: (Long) -> Unit,
    onContinueScheduled: (Long) -> Unit,
    onOpenScheduledJournal: (Long) -> Unit,
    onReschedule: (ScheduledWorkout) -> Unit,
    onUnschedule: (ScheduledWorkout) -> Unit
) {
    Text(
        text = stringResource(R.string.day_sheet_scheduled_title),
        style = AppTypeTokens.sectionKicker,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(AppDimens.headerStackGap))
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.day_sheet_no_scheduled),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        items.forEachIndexed { index, item ->
            ScheduledWorkoutRow(
                item = item,
                today = today,
                enabled = !busy,
                onStart = { onStartScheduled(item.id) },
                onContinue = { onContinueScheduled(item.id) },
                onOpenJournal = { onOpenScheduledJournal(item.id) },
                onReschedule = { onReschedule(item) },
                onUnschedule = { onUnschedule(item) }
            )
            if (index != items.lastIndex) {
                HorizontalDivider(
                    thickness = AppDimens.strokeThin,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
    TextButton(
        onClick = onScheduleWorkout,
        enabled = !busy,
        modifier = Modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(DAY_SHEET_SCHEDULE_ACTION)
    ) {
        Text(stringResource(R.string.action_schedule_workout))
    }
}

@Composable
private fun ScheduledWorkoutRow(
    item: ScheduledWorkout,
    today: LocalDate,
    enabled: Boolean,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onOpenJournal: () -> Unit,
    onReschedule: () -> Unit,
    onUnschedule: () -> Unit
) {
    val actions = ScheduledWorkoutUiLogic.actions(item, today)
    var menuOpen by remember(item.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(daySheetScheduledRowTag(item.id))
            .padding(vertical = AppDimens.headerStackGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.templateName,
                    style = AppTypeTokens.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = stringResource(R.string.template_row_meta, item.exerciseCount, item.plannedSetCount),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!actions.canStart) {
                    Spacer(Modifier.height(AppDimens.statSecondaryGap))
                    Text(
                        text = stringResource(actions.status.labelRes()),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (actions.canReschedule || actions.canUnschedule) {
                IconButton(
                    onClick = { menuOpen = true },
                    enabled = enabled,
                    modifier = Modifier
                        .size(AppDimens.minTouch)
                        .testTag("day-sheet-scheduled-overflow-${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more_schedule),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    if (actions.canReschedule) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_reschedule)) },
                            onClick = {
                                menuOpen = false
                                onReschedule()
                            }
                        )
                    }
                    if (actions.canUnschedule) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_unschedule)) },
                            onClick = {
                                menuOpen = false
                                onUnschedule()
                            }
                        )
                    }
                }
            }
        }
        when {
            actions.canStart -> {
                TextButton(
                    onClick = onStart,
                    enabled = enabled,
                    modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                ) {
                    Text(stringResource(R.string.action_start_workout))
                }
            }
            actions.canContinue -> {
                TextButton(
                    onClick = onContinue,
                    enabled = enabled,
                    modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                ) {
                    Text(stringResource(R.string.action_resume_workout))
                }
            }
            actions.canOpenJournal -> {
                TextButton(
                    onClick = onOpenJournal,
                    enabled = enabled,
                    modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                ) {
                    Text(stringResource(R.string.action_view_in_journal))
                }
            }
        }
    }
}

@Composable
private fun WeightSection(
    state: DaySheetState,
    onRecordWeight: () -> Unit,
    onEditWeight: () -> Unit,
    onDeleteWeight: () -> Unit
) {
    if (state.measurement != null) {
        Text(
            text = UiFormatters.weightKg(state.measurement.weightKg),
            style = AppTypeTokens.statValue,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = state.differenceFromPreviousKg?.let {
                stringResource(R.string.change_from_previous_value, UiFormatters.signedWeightKg(it))
            } ?: stringResource(R.string.no_previous_measurement),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppDimens.statSecondaryGap)
        )
        TextButton(
            onClick = onEditWeight,
            modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
        ) {
            Text(stringResource(R.string.action_edit_weight))
        }
        TextButton(
            onClick = onDeleteWeight,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
        ) {
            Text(stringResource(R.string.action_delete))
        }
    } else if (state.canRecordWeight) {
        Text(
            text = stringResource(R.string.day_sheet_no_weight),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = onRecordWeight,
            modifier = Modifier
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag(DAY_SHEET_RECORD_WEIGHT)
        ) {
            Text(stringResource(R.string.action_record_weight))
        }
    }
}

@Composable
private fun DayWorkoutRow(
    summary: WorkoutSessionSummary,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .padding(vertical = AppDimens.headerStackGap)
    ) {
        Text(summary.session.templateName, style = AppTypeTokens.sectionTitle)
        Text(
            text = stringResource(R.string.journal_duration, summary.durationLabel),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.journal_workout_summary,
                summary.exerciseCount,
                summary.progress.completed,
                summary.progress.total
            ),
            style = AppTypeTokens.statCaption
        )
        TextButton(
            onClick = onOpen,
            modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
        ) {
            Text(stringResource(R.string.action_open_details))
        }
    }
}

private fun ScheduledStatusLabel.labelRes(): Int {
    return when (this) {
        ScheduledStatusLabel.PLANNED -> R.string.schedule_status_planned
        ScheduledStatusLabel.MISSED -> R.string.schedule_status_missed
        ScheduledStatusLabel.IN_PROGRESS -> R.string.schedule_status_in_progress
        ScheduledStatusLabel.COMPLETED -> R.string.schedule_status_completed
        ScheduledStatusLabel.ARCHIVED -> R.string.schedule_status_archived
    }
}
