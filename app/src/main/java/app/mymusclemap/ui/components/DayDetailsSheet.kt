package app.mymusclemap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.DaySheetState
import app.mymusclemap.domain.WeightParseError
import app.mymusclemap.domain.WeightParser
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.ScheduledWorkoutUiLogic
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import app.mymusclemap.ui.theme.sheetContainerColor
import app.mymusclemap.ui.workout.CompactSheetHandle
import java.time.LocalDate

internal const val DAY_DETAILS_SHEET = "day-details-sheet"
internal const val DAY_SHEET_CLOSE = "day-sheet-close"
internal const val DAY_SHEET_SCHEDULE_ACTION = "day-sheet-schedule-action"
internal const val DAY_SHEET_WEIGHT_INPUT = "day-sheet-weight-input"
internal const val DAY_SHEET_WEIGHT_SAVE = "day-sheet-weight-save"
internal const val DAY_SHEET_WEIGHT_EDIT = "day-sheet-weight-edit"

internal fun daySheetScheduledRowTag(id: Long): String = "day-sheet-scheduled-row-$id"

internal fun daySheetWorkoutRowTag(id: Long): String = "day-sheet-workout-row-$id"

internal fun daySheetCalendarIconTag(id: Long): String = "day-sheet-calendar-icon-$id"

internal fun daySheetCompletedIconTag(id: Long): String = "day-sheet-completed-icon-$id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsSheet(
    state: DaySheetState,
    today: LocalDate,
    weightInput: String,
    weightError: WeightParseError?,
    weightEditing: Boolean,
    weightSavedTick: Int,
    onWeightChange: (String) -> Unit,
    onSaveWeight: () -> Unit,
    onBeginWeightEdit: () -> Unit,
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
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var seenSaveTick by remember { mutableIntStateOf(weightSavedTick) }
    LaunchedEffect(busy) {
        if (!busy) {
            actionLocked = false
        }
    }
    LaunchedEffect(weightSavedTick) {
        if (weightSavedTick != seenSaveTick) {
            seenSaveTick = weightSavedTick
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor(),
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(DAY_DETAILS_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(bottom = AppDimens.itemGap)
            ) {
                DaySheetHeader(date = state.date, today = today, onDismiss = onDismiss)
                if (state.canRecordWeight || state.measurement != null) {
                    Spacer(Modifier.height(AppDimens.itemGap))
                    DayWeightCard(
                        state = state,
                        input = weightInput,
                        error = weightError,
                        editing = weightEditing,
                        onWeightChange = onWeightChange,
                        onSaveWeight = onSaveWeight,
                        onBeginWeightEdit = onBeginWeightEdit
                    )
                }
                if (state.workouts.isNotEmpty()) {
                    Spacer(Modifier.height(AppDimens.itemGap))
                    DaySheetSectionHeading(
                        title = stringResource(R.string.day_sheet_completed_heading),
                        count = state.workouts.size
                    )
                    state.workouts.forEachIndexed { index, workout ->
                        CompletedWorkoutRow(
                            summary = workout,
                            onOpen = { onOpenWorkout(workout.session.id) }
                        )
                        if (index != state.workouts.lastIndex) {
                            DaySheetDivider()
                        }
                    }
                }
                if (state.scheduledWorkouts.isNotEmpty()) {
                    Spacer(Modifier.height(AppDimens.itemGap))
                    DaySheetSectionHeading(
                        title = stringResource(R.string.day_sheet_scheduled_heading),
                        count = state.scheduledWorkouts.size
                    )
                    state.scheduledWorkouts.forEachIndexed { index, item ->
                        ScheduledOverviewRow(
                            item = item,
                            today = today,
                            enabled = !busy && !actionLocked,
                            onStart = {
                                if (actionLocked || busy) return@ScheduledOverviewRow
                                actionLocked = true
                                onStartScheduled(item.id)
                            },
                            onContinue = { onContinueScheduled(item.id) },
                            onOpenJournal = { onOpenScheduledJournal(item.id) },
                            onReschedule = { onReschedule(item) },
                            onUnschedule = { onUnschedule(item) }
                        )
                        if (index != state.scheduledWorkouts.lastIndex) {
                            DaySheetDivider()
                        }
                    }
                }
                Spacer(Modifier.height(AppDimens.itemGap))
                OutlinedButton(
                    onClick = onScheduleWorkout,
                    enabled = !busy,
                    shape = AppShapeTokens.button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(DAY_SHEET_SCHEDULE_ACTION)
                ) {
                    Text(stringResource(R.string.action_schedule_workout))
                }
            }
        }
    }
}

@Composable
private fun DaySheetHeader(
    date: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = AppDimens.headerStackGap)
        ) {
            if (date == today) {
                Text(
                    text = stringResource(R.string.day_sheet_today).uppercase(AppLocale.UI),
                    style = AppTypeTokens.sectionKicker,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
            }
            Text(
                text = UiFormatters.daySheetTitle(date, today),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(AppDimens.minTouch)
                .testTag(DAY_SHEET_CLOSE)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.day_sheet_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayWeightCard(
    state: DaySheetState,
    input: String,
    error: WeightParseError?,
    editing: Boolean,
    onWeightChange: (String) -> Unit,
    onSaveWeight: () -> Unit,
    onBeginWeightEdit: () -> Unit
) {
    val measurement = state.measurement
    val showEditor = state.canRecordWeight && (measurement == null || editing)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapeTokens.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AppDimens.itemGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.field_weight),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.Scale,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(AppDimens.headerStackGap))
            if (showEditor) {
                DayWeightEditor(
                    input = input,
                    error = error,
                    requestFocus = editing,
                    onWeightChange = onWeightChange,
                    onSaveWeight = onSaveWeight
                )
            } else if (measurement != null) {
                DayWeightValue(
                    kilograms = measurement.weightKg,
                    canEdit = state.canRecordWeight,
                    onEdit = onBeginWeightEdit
                )
            }
        }
    }
}

@Composable
private fun DayWeightEditor(
    input: String,
    error: WeightParseError?,
    requestFocus: Boolean,
    onWeightChange: (String) -> Unit,
    onSaveWeight: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val weightLabel = stringResource(R.string.field_weight)
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = AppDimens.minTouch),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { onWeightChange(WeightParser.filterUserInput(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag(DAY_SHEET_WEIGHT_INPUT)
                            .semantics {
                                contentDescription = weightLabel
                            },
                        textStyle = AppTypeTokens.statBand.copy(color = MaterialTheme.colorScheme.onBackground),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSaveWeight() })
                    )
                }
                Spacer(Modifier.width(AppDimens.headerStackGap))
                Text(
                    text = stringResource(R.string.unit_kg),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppDimens.statSecondaryGap)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.strokeThin)
                    .background(
                        if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
            )
            if (error != null) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = stringResource(weightErrorMessage(error)),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.width(AppDimens.itemGap))
        Button(
            onClick = onSaveWeight,
            shape = AppShapeTokens.button,
            colors = StrictBrand.actionButtonColors(),
            modifier = Modifier
                .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
                .testTag(DAY_SHEET_WEIGHT_SAVE)
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun DayWeightValue(
    kilograms: Double,
    canEdit: Boolean,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = UiFormatters.weightValue(kilograms),
            style = AppTypeTokens.statBand,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(AppDimens.headerStackGap))
        Text(
            text = stringResource(R.string.unit_kg),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (canEdit) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(AppDimens.minTouch)
                    .testTag(DAY_SHEET_WEIGHT_EDIT)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.action_edit_weight),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DaySheetSectionHeading(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppDimens.headerStackGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(AppDimens.headerStackGap))
        Text(
            text = count.toString(),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(AppShapeTokens.compact)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = AppDimens.headerStackGap, vertical = AppDimens.statSecondaryGap)
        )
    }
}

@Composable
private fun DaySheetDivider() {
    HorizontalDivider(
        thickness = AppDimens.strokeThin,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun CompletedWorkoutRow(
    summary: WorkoutSessionSummary,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button }
            .testTag(daySheetWorkoutRowTag(summary.session.id))
            .padding(vertical = AppDimens.headerStackGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(R.string.day_sheet_completed_workout),
            tint = StrictBrand.actionContainer(),
            modifier = Modifier
                .size(20.dp)
                .testTag(daySheetCompletedIconTag(summary.session.id))
        )
        Spacer(Modifier.width(AppDimens.itemGap))
        Text(
            text = summary.session.templateName,
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ScheduledOverviewRow(
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
    val rowOpensReschedule = !actions.canStart &&
        !actions.canContinue &&
        !actions.canOpenJournal &&
        actions.canReschedule
    val rowOpensUnschedule = !actions.canStart &&
        !actions.canContinue &&
        !actions.canOpenJournal &&
        !actions.canReschedule &&
        actions.canUnschedule
    val showReschedule = actions.canReschedule && !rowOpensReschedule
    val showUnschedule = actions.canUnschedule && !rowOpensUnschedule
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(daySheetScheduledRowTag(item.id)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .clickable(enabled = enabled) {
                    if (!enabled) return@clickable
                    when {
                        actions.canStart -> onStart()
                        actions.canContinue -> onContinue()
                        actions.canOpenJournal -> onOpenJournal()
                        actions.canReschedule -> onReschedule()
                        actions.canUnschedule -> onUnschedule()
                    }
                }
                .semantics { role = Role.Button }
                .padding(vertical = AppDimens.headerStackGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = stringResource(R.string.day_sheet_scheduled_workout),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .testTag(daySheetCalendarIconTag(item.id))
            )
            Spacer(Modifier.width(AppDimens.itemGap))
            Text(
                text = item.templateName,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (showReschedule || showUnschedule) {
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
                if (showReschedule) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_reschedule)) },
                        onClick = {
                            menuOpen = false
                            onReschedule()
                        }
                    )
                }
                if (showUnschedule) {
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
}
