package hu.laca.weighttracker.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.workout.DistanceUnit
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedLoadLogic
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workout.PlannedSetLogic
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateFieldError
import hu.laca.weighttracker.domain.workout.TemplateOrdering
import hu.laca.weighttracker.domain.workout.TemplateValidationIssue
import hu.laca.weighttracker.ui.components.SegmentedControl
import hu.laca.weighttracker.ui.components.musclemap.TemplateMuscleMapCard
import hu.laca.weighttracker.ui.exercises.CatalogDropdown
import hu.laca.weighttracker.ui.exercises.labelRes
import hu.laca.weighttracker.ui.theme.AppDimens

internal const val TEMPLATE_HEADER_KEY = "template-header"
internal const val TEMPLATE_ADD_KEY = "template-add"

internal fun templateExerciseKey(localId: Long): String = "template-exercise-$localId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    state: TemplateEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onPickerQuery: (String) -> Unit,
    onPickerCategory: (ExerciseCategory?) -> Unit,
    onPickerMuscle: (MuscleGroup?) -> Unit,
    onSelectExercise: (Exercise) -> Unit,
    onConfirmDuplicate: () -> Unit,
    onDismissDuplicate: () -> Unit,
    onRemoveExercise: (Long) -> Unit,
    onMoveExercise: (Long, Boolean) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    onSetCount: (Long, Int) -> Unit,
    onAddSet: (Long) -> Unit,
    onRemoveSet: (Long, Long) -> Unit,
    onMoveSet: (Long, Long, Boolean) -> Unit,
    onApplyRemaining: (Long, Long) -> Unit,
    onApplyAll: (Long, Long) -> Unit,
    onMinReps: (Long, Long, String) -> Unit,
    onMaxReps: (Long, Long, String) -> Unit,
    onLoadKind: (Long, Long, PlannedLoadKind) -> Unit,
    onWeight: (Long, Long, String) -> Unit,
    onMinutes: (Long, Long, String) -> Unit,
    onSeconds: (Long, Long, String) -> Unit,
    onDistance: (Long, Long, String) -> Unit,
    onDistanceUnit: (Long, Long, DistanceUnit) -> Unit,
    onSave: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onFinished: (Boolean, Boolean) -> Unit,
    onScrollConsumed: () -> Unit
) {
    BackHandler {
        if (state.pane == TemplateEditorPane.Picker) onClosePicker() else onBack()
    }
    LaunchedEffect(state.finished) {
        if (state.finished) {
            onFinished(state.saved, state.created)
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.pane == TemplateEditorPane.Picker) {
                                R.string.template_picker_title
                            } else if (state.isEditing) {
                                R.string.template_editor_edit
                            } else {
                                R.string.template_editor_add
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.pane == TemplateEditorPane.Picker) onClosePicker() else onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.pane == TemplateEditorPane.Picker) {
            PickerPane(
                state = state,
                modifier = Modifier.padding(innerPadding),
                onQuery = onPickerQuery,
                onCategory = onPickerCategory,
                onMuscle = onPickerMuscle,
                onSelect = onSelectExercise
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .imePadding()
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(state.scrollEvent?.generation, state.draft.exercises.map { it.localId }) {
                    val event = state.scrollEvent ?: return@LaunchedEffect
                    val index = state.draft.exercises.indexOfFirst { it.localId == event.exerciseLocalId }
                    if (index < 0) {
                        onScrollConsumed()
                        return@LaunchedEffect
                    }
                    listState.animateScrollToItem(index + 1)
                    onScrollConsumed()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppDimens.screenPadding)
                        .padding(top = 8.dp)
                ) {
                    item(key = TEMPLATE_HEADER_KEY) {
                        val nameError = state.issues.firstOrNull {
                            it.error == TemplateFieldError.NameBlank ||
                                it.error == TemplateFieldError.NameTooLong
                        }
                        Column {
                            OutlinedTextField(
                                value = state.draft.name,
                                onValueChange = onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.template_field_name)) },
                                isError = nameError != null || state.duplicateName,
                                supportingText = {
                                    val message = when {
                                        state.duplicateName -> stringResource(R.string.error_template_duplicate)
                                        nameError != null -> stringResource(nameError.error.labelRes())
                                        else -> null
                                    }
                                    if (message != null) Text(message)
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = state.draft.notes,
                                onValueChange = onNotesChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.template_field_notes)) },
                                minLines = 2
                            )
                            if (state.draft.exercises.isNotEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                TemplateMuscleMapCard(state = state.musclePreview)
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = stringResource(R.string.template_exercises_heading),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            if (state.draft.exercises.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.template_no_exercises),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        items = state.draft.exercises,
                        key = { _, item -> templateExerciseKey(item.localId) }
                    ) { index, item ->
                        val exercise = state.catalog[item.exerciseId]
                        Column {
                            Spacer(Modifier.height(12.dp))
                            if (exercise != null) {
                                ExerciseCard(
                                    modifier = Modifier.testTag(templateExerciseKey(item.localId)),
                                    index = index,
                                    total = state.draft.exercises.size,
                                    item = item,
                                    exercise = exercise,
                                    issues = state.issues.filter { it.exerciseIndex == index },
                                    onMove = { up -> onMoveExercise(item.localId, up) },
                                    onRemove = { onRemoveExercise(item.localId) },
                                    onToggle = { onToggleExpanded(item.localId) },
                                    onSetCount = { onSetCount(item.localId, it) },
                                    onAddSet = { onAddSet(item.localId) },
                                    onRemoveSet = { onRemoveSet(item.localId, it) },
                                    onMoveSet = { setId, up -> onMoveSet(item.localId, setId, up) },
                                    onApplyRemaining = { onApplyRemaining(item.localId, it) },
                                    onApplyAll = { onApplyAll(item.localId, it) },
                                    onMinReps = { setId, value -> onMinReps(item.localId, setId, value) },
                                    onMaxReps = { setId, value -> onMaxReps(item.localId, setId, value) },
                                    onLoadKind = { setId, kind -> onLoadKind(item.localId, setId, kind) },
                                    onWeight = { setId, value -> onWeight(item.localId, setId, value) },
                                    onMinutes = { setId, value -> onMinutes(item.localId, setId, value) },
                                    onSeconds = { setId, value -> onSeconds(item.localId, setId, value) },
                                    onDistance = { setId, value -> onDistance(item.localId, setId, value) },
                                    onDistanceUnit = { setId, unit -> onDistanceUnit(item.localId, setId, unit) }
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.exercise_archived_badge),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                    item(key = TEMPLATE_ADD_KEY) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onOpenPicker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppDimens.scrollEndPadding)
                        ) {
                            Text(stringResource(R.string.template_add_exercise))
                        }
                    }
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.screenPadding, vertical = 12.dp)
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag("template-save"),
                    enabled = !state.duplicateName
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
    state.pendingDuplicate?.let { exercise ->
        AlertDialog(
            onDismissRequest = onDismissDuplicate,
            title = { Text(stringResource(R.string.template_duplicate_title)) },
            text = { Text(stringResource(R.string.template_duplicate_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDuplicate) {
                    Text(stringResource(R.string.action_add_again))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDuplicate) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.template_discard_title)) },
            text = { Text(stringResource(R.string.template_discard_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard) {
                    Text(stringResource(R.string.action_discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PickerPane(
    state: TemplateEditorUiState,
    modifier: Modifier,
    onQuery: (String) -> Unit,
    onCategory: (ExerciseCategory?) -> Unit,
    onMuscle: (MuscleGroup?) -> Unit,
    onSelect: (Exercise) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.screenPadding)
            .verticalScroll(rememberScrollState())
    ) {
        if (state.catalog.values.none { !it.archived }) {
            Text(
                text = stringResource(R.string.template_picker_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp)
            )
            return
        }
        OutlinedTextField(
            value = state.pickerQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.exercise_search)) }
        )
        Spacer(Modifier.height(8.dp))
        CatalogDropdown(
            label = stringResource(R.string.exercise_filter_category),
            selected = state.pickerCategory,
            options = ExerciseCategory.entries,
            optionLabel = { stringResource(it.labelRes()) },
            noneLabel = stringResource(R.string.exercise_filter_all_categories),
            onSelected = onCategory
        )
        Spacer(Modifier.height(8.dp))
        CatalogDropdown(
            label = stringResource(R.string.exercise_filter_muscle),
            selected = state.pickerMuscle,
            options = MuscleGroup.entries,
            optionLabel = { stringResource(it.labelRes()) },
            noneLabel = stringResource(R.string.exercise_filter_all_muscles),
            onSelected = onMuscle
        )
        Spacer(Modifier.height(12.dp))
        state.pickerExercises.forEach { exercise ->
            TextButton(
                onClick = { onSelect(exercise) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(
                            R.string.exercise_row_meta,
                            stringResource(exercise.measurementType.labelRes()),
                            stringResource(exercise.primaryMuscle.labelRes())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(exercise.movementPattern.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(AppDimens.scrollEndPadding))
    }
}

@Composable
private fun ExerciseCard(
    modifier: Modifier = Modifier,
    index: Int,
    total: Int,
    item: TemplateExerciseDraft,
    exercise: Exercise,
    issues: List<TemplateValidationIssue>,
    onMove: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onToggle: () -> Unit,
    onSetCount: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Long) -> Unit,
    onMoveSet: (Long, Boolean) -> Unit,
    onApplyRemaining: (Long) -> Unit,
    onApplyAll: (Long) -> Unit,
    onMinReps: (Long, String) -> Unit,
    onMaxReps: (Long, String) -> Unit,
    onLoadKind: (Long, PlannedLoadKind) -> Unit,
    onWeight: (Long, String) -> Unit,
    onMinutes: (Long, String) -> Unit,
    onSeconds: (Long, String) -> Unit,
    onDistance: (Long, String) -> Unit,
    onDistanceUnit: (Long, DistanceUnit) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${index + 1}. ${exercise.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            append(stringResource(exercise.measurementType.labelRes()))
                            append(" · ")
                            append(stringResource(exercise.primaryMuscle.labelRes()))
                            if (exercise.archived) {
                                append(" · ")
                                append(stringResource(R.string.exercise_archived_badge))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onMove(true) },
                    enabled = TemplateOrdering.canMoveUp(index)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.action_move_exercise_up)
                    )
                }
                IconButton(
                    onClick = { onMove(false) },
                    enabled = TemplateOrdering.canMoveDown(index, total)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_move_exercise_down)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_remove_exercise)
                    )
                }
            }
            TextButton(onClick = onToggle) {
                Text(if (item.expanded) "Kevesebb" else "Sorozatok")
            }
            if (item.expanded) {
                if (exercise.measurementType != MeasurementType.COMPLETION_ONLY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.field_set_count), modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onSetCount(item.sets.size - 1) },
                            enabled = item.sets.size > 1
                        ) {
                            Text("−")
                        }
                        Text("${item.sets.size}")
                        IconButton(
                            onClick = { onSetCount(item.sets.size + 1) },
                            enabled = item.sets.size < PlannedSetLogic.maxSetCount(exercise.measurementType)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_set))
                        }
                    }
                }
                item.sets.forEachIndexed { setIndex, set ->
                    Spacer(Modifier.height(8.dp))
                    SetCard(
                        index = setIndex,
                        total = item.sets.size,
                        set = set,
                        exercise = exercise,
                        issues = issues.filter { it.setIndex == setIndex },
                        onRemove = { onRemoveSet(set.localId) },
                        onMove = { up -> onMoveSet(set.localId, up) },
                        onApplyRemaining = { onApplyRemaining(set.localId) },
                        onApplyAll = { onApplyAll(set.localId) },
                        onMinReps = { onMinReps(set.localId, it) },
                        onMaxReps = { onMaxReps(set.localId, it) },
                        onLoadKind = { onLoadKind(set.localId, it) },
                        onWeight = { onWeight(set.localId, it) },
                        onMinutes = { onMinutes(set.localId, it) },
                        onSeconds = { onSeconds(set.localId, it) },
                        onDistance = { onDistance(set.localId, it) },
                        onDistanceUnit = { onDistanceUnit(set.localId, it) }
                    )
                }
                if (exercise.measurementType != MeasurementType.COMPLETION_ONLY) {
                    TextButton(
                        onClick = onAddSet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Text(
                            text = stringResource(R.string.action_copy_previous_set),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetCard(
    index: Int,
    total: Int,
    set: PlannedSetDraft,
    exercise: Exercise,
    issues: List<TemplateValidationIssue>,
    onRemove: () -> Unit,
    onMove: (Boolean) -> Unit,
    onApplyRemaining: () -> Unit,
    onApplyAll: () -> Unit,
    onMinReps: (String) -> Unit,
    onMaxReps: (String) -> Unit,
    onLoadKind: (PlannedLoadKind) -> Unit,
    onWeight: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
    onDistance: (String) -> Unit,
    onDistanceUnit: (DistanceUnit) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.field_set_label, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onMove(true) }, enabled = TemplateOrdering.canMoveUp(index)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.action_move_set_up)
                    )
                }
                IconButton(
                    onClick = { onMove(false) },
                    enabled = TemplateOrdering.canMoveDown(index, total)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_move_set_down)
                    )
                }
                IconButton(onClick = onRemove, enabled = total > 1) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_remove_set)
                    )
                }
            }
            if (PlannedSetLogic.requiresReps(exercise.measurementType)) {
                OutlinedTextField(
                    value = set.minRepsText,
                    onValueChange = onMinReps,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_reps_min)) },
                    isError = issues.any {
                        it.error.name.startsWith("Reps")
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = set.maxRepsText,
                    onValueChange = onMaxReps,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_reps_max)) },
                    singleLine = true
                )
            }
            val kinds = PlannedLoadLogic.compatibleKinds(
                exercise.resistanceBasis,
                exercise.measurementType
            ).toList()
            if (kinds.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.field_load), style = MaterialTheme.typography.labelLarge)
                CatalogDropdown(
                    label = stringResource(R.string.field_load),
                    selected = set.loadKind,
                    options = kinds,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelected = { selected -> selected?.let(onLoadKind) }
                )
            }
            if (PlannedLoadLogic.requiresPositiveWeight(set.loadKind)) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = set.weightText,
                    onValueChange = onWeight,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_weight_kg)) },
                    isError = issues.any { it.error.name.startsWith("Weight") },
                    singleLine = true
                )
            }
            if (PlannedSetLogic.requiresDuration(exercise.measurementType)) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = set.minutesText,
                        onValueChange = onMinutes,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.field_minutes)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = set.secondsText,
                        onValueChange = onSeconds,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.field_seconds)) },
                        singleLine = true
                    )
                }
            }
            if (PlannedSetLogic.requiresDistance(exercise.measurementType)) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = set.distanceText,
                    onValueChange = onDistance,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_distance)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.distance_unit_m),
                        stringResource(R.string.distance_unit_km)
                    ),
                    selectedIndex = if (set.distanceUnit == DistanceUnit.KILOMETERS) 1 else 0,
                    onSelected = { onDistanceUnit(if (it == 1) DistanceUnit.KILOMETERS else DistanceUnit.METERS) }
                )
            }
            issues.forEach { issue ->
                Text(
                    text = stringResource(issue.error.labelRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SetApplyActions(
                onApplyRemaining = onApplyRemaining,
                onApplyAll = onApplyAll
            )
        }
    }
}

@Composable
internal fun SetApplyActions(
    onApplyRemaining: () -> Unit,
    onApplyAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SetApplyActionButton(
            label = stringResource(R.string.action_apply_to_remaining),
            onClick = onApplyRemaining
        )
        SetApplyActionButton(
            label = stringResource(R.string.action_apply_to_all),
            onClick = onApplyAll
        )
    }
}

@Composable
private fun SetApplyActionButton(
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
    }
}
