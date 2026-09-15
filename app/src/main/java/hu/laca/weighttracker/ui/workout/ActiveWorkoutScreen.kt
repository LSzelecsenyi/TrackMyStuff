package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.ActualSetDraft
import hu.laca.weighttracker.domain.workout.ActualSetLogic
import hu.laca.weighttracker.domain.workout.DistanceUnit
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedLoadLogic
import hu.laca.weighttracker.domain.workout.PlannedSetLogic
import hu.laca.weighttracker.domain.workout.PlannedTargetDisplay
import hu.laca.weighttracker.domain.workout.QuantityParser
import hu.laca.weighttracker.domain.workout.SessionExerciseItem
import hu.laca.weighttracker.domain.workout.SessionSet
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import hu.laca.weighttracker.domain.workout.TemplateFieldError
import hu.laca.weighttracker.ui.components.SegmentedControl
import hu.laca.weighttracker.ui.exercises.CatalogDropdown
import hu.laca.weighttracker.ui.templates.labelRes
import hu.laca.weighttracker.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReps: (Long, String) -> Unit,
    onLoadKind: (Long, PlannedLoadKind) -> Unit,
    onWeight: (Long, String) -> Unit,
    onMinutes: (Long, String) -> Unit,
    onSeconds: (Long, String) -> Unit,
    onDistance: (Long, String) -> Unit,
    onDistanceUnit: (Long, DistanceUnit) -> Unit,
    onComplete: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onUndoSkip: (Long) -> Unit,
    onAddExtra: () -> Unit,
    onRemoveExtra: (Long) -> Unit,
    onRequestFinish: () -> Unit,
    onDismissFinish: () -> Unit,
    onConfirmFinish: (Boolean) -> Unit,
    onRequestAbandon: () -> Unit,
    onDismissAbandon: () -> Unit,
    onConfirmAbandon: () -> Unit,
    onFinished: () -> Unit,
    onAbandoned: () -> Unit,
    onMessageConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }
    LaunchedEffect(state.abandoned) {
        if (state.abandoned) onAbandoned()
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(message.labelRes()))
        onMessageConsumed()
    }
    val aggregate = state.aggregate
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(aggregate?.session?.templateName ?: stringResource(R.string.active_workout_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more_workout)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.action_abandon_workout),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onRequestAbandon()
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = AppDimens.screenPadding, vertical = 12.dp)
            ) {
                Button(
                    onClick = onRequestFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                ) {
                    Text(stringResource(R.string.action_finish_workout))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (aggregate == null) {
            return@Scaffold
        }
        val exercise = aggregate.exercises.getOrNull(state.selectedIndex)
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = 16.dp)
        ) {
            Text(state.elapsedLabel, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress.fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.active_workout_progress_label,
                    state.progress.completed,
                    state.progress.skipped,
                    state.progress.pending
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (exercise != null) {
                ExerciseHeader(
                    item = exercise,
                    index = state.selectedIndex,
                    total = aggregate.exercises.size,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    canPrevious = state.selectedIndex > 0,
                    canNext = state.selectedIndex < aggregate.exercises.lastIndex
                )
                Spacer(Modifier.height(12.dp))
                exercise.sets.forEach { set ->
                    SetRow(
                        exerciseName = exercise.exercise.name,
                        item = exercise,
                        set = set,
                        draft = state.drafts[set.id] ?: ActualSetLogic.draftFromSet(set),
                        errors = state.setErrors[set.id].orEmpty(),
                        onReps = { onReps(set.id, it) },
                        onLoadKind = { onLoadKind(set.id, it) },
                        onWeight = { onWeight(set.id, it) },
                        onMinutes = { onMinutes(set.id, it) },
                        onSeconds = { onSeconds(set.id, it) },
                        onDistance = { onDistance(set.id, it) },
                        onDistanceUnit = { onDistanceUnit(set.id, it) },
                        onComplete = { onComplete(set.id) },
                        onSkip = { onSkip(set.id) },
                        onUndoSkip = { onUndoSkip(set.id) },
                        onRemoveExtra = { onRemoveExtra(set.id) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(
                    onClick = onAddExtra,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                ) {
                    Text(stringResource(R.string.action_add_extra_set))
                }
            }
        }
    }
    state.pendingFinishCount?.let { count ->
        if (count > 0) {
            AlertDialog(
                onDismissRequest = onDismissFinish,
                title = { Text(stringResource(R.string.finish_pending_title)) },
                text = { Text(stringResource(R.string.finish_pending_body, count)) },
                confirmButton = {
                    TextButton(onClick = { onConfirmFinish(true) }) {
                        Text(stringResource(R.string.action_finish_skip_remaining))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissFinish) {
                        Text(stringResource(R.string.action_return_to_workout))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismissFinish,
                title = { Text(stringResource(R.string.finish_confirm_title)) },
                text = { Text(stringResource(R.string.finish_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = { onConfirmFinish(false) }) {
                        Text(stringResource(R.string.action_finish_workout))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissFinish) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
    if (state.confirmAbandon) {
        AlertDialog(
            onDismissRequest = onDismissAbandon,
            title = { Text(stringResource(R.string.abandon_title)) },
            text = { Text(stringResource(R.string.abandon_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmAbandon) {
                    Text(
                        text = stringResource(R.string.action_abandon_workout),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAbandon) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ExerciseHeader(
    item: SessionExerciseItem,
    index: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canPrevious: Boolean,
    canNext: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = canPrevious) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.action_previous_exercise)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.exercise.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.active_workout_position, index + 1, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.action_next_exercise)
            )
        }
    }
}

@Composable
private fun SetRow(
    exerciseName: String,
    item: SessionExerciseItem,
    set: SessionSet,
    draft: ActualSetDraft,
    errors: List<TemplateFieldError>,
    onReps: (String) -> Unit,
    onLoadKind: (PlannedLoadKind) -> Unit,
    onWeight: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
    onDistance: (String) -> Unit,
    onDistanceUnit: (DistanceUnit) -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onUndoSkip: () -> Unit,
    onRemoveExtra: () -> Unit
) {
    val planned = buildPlannedLabel(set)
    val statusLabel = stringResource(set.status.labelRes())
    val actualLabel = draft.repsText.ifBlank { planned }
    val description = stringResource(
        R.string.set_row_description,
        exerciseName,
        set.position + 1,
        planned,
        actualLabel,
        statusLabel
    )
    val highlighted = set.status == SessionSetStatus.PENDING
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (highlighted) 2.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.field_set_label, set.position + 1),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (set.status) {
                        SessionSetStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        SessionSetStatus.SKIPPED -> MaterialTheme.colorScheme.tertiary
                        SessionSetStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = stringResource(R.string.active_set_planned, planned),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (set.status != SessionSetStatus.SKIPPED) {
                Spacer(Modifier.height(8.dp))
                ActualFields(
                    item = item,
                    draft = draft,
                    errors = errors,
                    onReps = onReps,
                    onLoadKind = onLoadKind,
                    onWeight = onWeight,
                    onMinutes = onMinutes,
                    onSeconds = onSeconds,
                    onDistance = onDistance,
                    onDistanceUnit = onDistanceUnit
                )
            }
            Spacer(Modifier.height(8.dp))
            when (set.status) {
                SessionSetStatus.PENDING -> {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text(stringResource(R.string.action_complete_set))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onSkip, modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)) {
                            Text(stringResource(R.string.action_skip_set))
                        }
                        if (ActualSetLogic.canRemove(set)) {
                            TextButton(
                                onClick = onRemoveExtra,
                                modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                            ) {
                                Text(
                                    stringResource(R.string.action_remove_extra_set),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                SessionSetStatus.COMPLETED -> {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Text(stringResource(R.string.action_save_set))
                    }
                }
                SessionSetStatus.SKIPPED -> {
                    OutlinedButton(
                        onClick = onUndoSkip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Text(stringResource(R.string.action_undo_skip))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActualFields(
    item: SessionExerciseItem,
    draft: ActualSetDraft,
    errors: List<TemplateFieldError>,
    onReps: (String) -> Unit,
    onLoadKind: (PlannedLoadKind) -> Unit,
    onWeight: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
    onDistance: (String) -> Unit,
    onDistanceUnit: (DistanceUnit) -> Unit
) {
    val measurement = item.exercise.measurementType
    val resistance = item.exercise.resistanceBasis
    if (PlannedSetLogic.requiresReps(measurement)) {
        OutlinedTextField(
            value = draft.repsText,
            onValueChange = onReps,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_actual_reps)) },
            isError = errors.any { it.name.startsWith("Reps") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
    }
    val kinds = PlannedLoadLogic.compatibleKinds(resistance, measurement).toList()
    if (kinds.size > 1) {
        CatalogDropdown(
            label = stringResource(R.string.field_load),
            selected = draft.loadKind,
            options = kinds,
            optionLabel = { stringResource(it.labelRes()) },
            onSelected = { selected -> selected?.let(onLoadKind) }
        )
        Spacer(Modifier.height(8.dp))
    }
    if (PlannedLoadLogic.requiresPositiveWeight(draft.loadKind)) {
        val hint = when (item.exercise.weightInterpretation) {
            WeightInterpretation.PER_SIDE -> stringResource(R.string.weight_interpretation_hint_per_side)
            WeightInterpretation.TOTAL -> stringResource(R.string.weight_interpretation_hint_total)
            WeightInterpretation.NOT_APPLICABLE -> null
        }
        OutlinedTextField(
            value = draft.weightText,
            onValueChange = onWeight,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_weight_kg)) },
            supportingText = hint?.let { { Text(it) } },
            isError = errors.any { it.name.startsWith("Weight") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
    }
    if (PlannedSetLogic.requiresDuration(measurement)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.minutesText,
                onValueChange = onMinutes,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.field_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = draft.secondsText,
                onValueChange = onSeconds,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.field_seconds)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    if (PlannedSetLogic.requiresDistance(measurement)) {
        OutlinedTextField(
            value = draft.distanceText,
            onValueChange = onDistance,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_distance)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        SegmentedControl(
            options = listOf(
                stringResource(R.string.distance_unit_m),
                stringResource(R.string.distance_unit_km)
            ),
            selectedIndex = if (draft.distanceUnit == DistanceUnit.KILOMETERS) 1 else 0,
            onSelected = { onDistanceUnit(if (it == 1) DistanceUnit.KILOMETERS else DistanceUnit.METERS) }
        )
    }
    errors.forEach { error ->
        Text(
            text = stringResource(error.labelRes()),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun buildPlannedLabel(set: SessionSet): String {
    val parts = mutableListOf<String>()
    PlannedTargetDisplay.reps(set.plannedMinReps, set.plannedMaxReps)?.let { parts += it }
    val load = PlannedTargetDisplay.load(set.plannedLoadKind, set.plannedWeightKg)
    if (load.isNotBlank() && load != "BW") {
        parts += load
    } else if (load == "BW") {
        parts += "testsúly"
    }
    set.plannedDurationSeconds?.let {
        val (minutes, seconds) = QuantityParser.fromSeconds(it)
        parts += "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
    set.plannedDistanceMeters?.let { parts += "${QuantityParser.formatDisplay(it)} m" }
    if (parts.isEmpty()) {
        parts += "—"
    }
    return parts.joinToString(" · ")
}

private fun SessionSetStatus.labelRes(): Int {
    return when (this) {
        SessionSetStatus.PENDING -> R.string.set_status_pending
        SessionSetStatus.COMPLETED -> R.string.set_status_completed
        SessionSetStatus.SKIPPED -> R.string.set_status_skipped
    }
}

private fun ActiveWorkoutMessage.labelRes(): Int {
    return when (this) {
        ActiveWorkoutMessage.SetUpdated -> R.string.message_template_updated
        ActiveWorkoutMessage.ExtraAdded -> R.string.message_extra_set_added
        ActiveWorkoutMessage.ExtraRemoved -> R.string.message_extra_set_removed
        ActiveWorkoutMessage.CannotRemoveOriginal -> R.string.message_cannot_remove_original
    }
}
