package app.mymusclemap.ui.templates

import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.domain.workout.RestDuration
import app.mymusclemap.domain.workout.RestProgressionMode
import app.mymusclemap.domain.workout.WorkDuration
import app.mymusclemap.ui.exercises.starterExerciseLabel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.pro.ProInfoSheet
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedLoadLogic
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.PlannedSetLogic
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateFieldError
import app.mymusclemap.domain.workout.TemplateOrdering
import app.mymusclemap.domain.workout.TemplateValidationIssue
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.components.musclemap.TemplateMuscleMapCard
import app.mymusclemap.ui.exercises.CatalogDropdown
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val TEMPLATE_HEADER_KEY = "template-header"
internal const val TEMPLATE_ADD_KEY = "template-add"
internal const val WORK_MODE_REPS = "work-mode-reps"
internal const val WORK_MODE_TIMED = "work-mode-timed"
internal const val TIMED_EDITOR_PRO_NOTE = "timed-editor-pro"
internal const val TIMED_EDITOR_EXPLORE_PRO = "timed-editor-explore-pro"
internal const val TIMED_MINUTES_FIELD = "timed-minutes"
internal const val TIMED_SECONDS_FIELD = "timed-seconds"

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
    onRestMode: (Boolean) -> Unit = {},
    onExerciseRest: (Long, Int?) -> Unit = { _, _ -> },
    onSetRest: (Long, Long, Int?) -> Unit = { _, _, _ -> },
    onTimedMode: (Long, Boolean) -> Unit = { _, _ -> },
    onWorkDuration: (Long, Long, Int) -> Unit = { _, _, _ -> },
    onSave: () -> Unit,
    onReload: () -> Unit = {},
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onFinished: (Boolean, Boolean) -> Unit,
    onScrollConsumed: () -> Unit,
    onDismissLocked: () -> Unit = {},
    onExploreProBenefits: () -> Unit = {}
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
                            if (state.contentLocked) {
                                app.mymusclemap.ui.entitlement.FreeAccessLockNote(
                                    onExplorePro = onExploreProBenefits
                                )
                                Spacer(Modifier.height(8.dp))
                            }
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
                            if (state.stalePlan) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.error_template_stale),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = onReload) {
                                    Text(stringResource(R.string.action_template_reload))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = state.draft.notes,
                                onValueChange = onNotesChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.template_field_notes)) },
                                minLines = 2
                            )
                            Spacer(Modifier.height(12.dp))
                            RestProgressionRow(
                                strict = state.draft.restProgressionMode == RestProgressionMode.STRICT,
                                onChange = onRestMode
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
                                    onDistanceUnit = { setId, unit -> onDistanceUnit(item.localId, setId, unit) },
                                    onExerciseRest = { onExerciseRest(item.localId, it) },
                                    onSetRest = { setId, seconds -> onSetRest(item.localId, setId, seconds) },
                                    onTimedMode = { onTimedMode(item.localId, it) },
                                    onWorkDuration = { setId, seconds -> onWorkDuration(item.localId, setId, seconds) },
                                    timersEnabled = state.timersEnabled,
                                    timedTabPreview = state.timedTabPreview[item.localId],
                                    onExploreProBenefits = onExploreProBenefits
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.exercise_archived_badge),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    state.lockedFeature?.let { feature ->
        ProInfoSheet(
            feature = feature,
            onDismiss = onDismissLocked
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
                    Text(starterExerciseLabel(exercise.name), style = MaterialTheme.typography.titleMedium)
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
    onDistanceUnit: (Long, DistanceUnit) -> Unit,
    onExerciseRest: (Int?) -> Unit,
    onSetRest: (Long, Int?) -> Unit,
    onTimedMode: (Boolean) -> Unit = {},
    onWorkDuration: (Long, Int) -> Unit = { _, _ -> },
    timersEnabled: Boolean = true,
    timedTabPreview: Boolean? = null,
    onExploreProBenefits: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${index + 1}. ${starterExerciseLabel(exercise.name)}", style = MaterialTheme.typography.titleMedium)
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
                Text(
                    stringResource(
                        if (item.expanded) R.string.action_hide_sets else R.string.action_show_sets
                    )
                )
            }
            if (item.expanded) {
                val persistedTimed = item.sets.isNotEmpty() && item.sets.all { it.timedWork }
                val showingTimed = if (PlannedSetLogic.requiresReps(exercise.measurementType)) {
                    timedTabPreview ?: persistedTimed
                } else {
                    false
                }
                val timedFieldsLocked = showingTimed && !timersEnabled
                val timedStructureLocked = persistedTimed && !timersEnabled
                if (PlannedSetLogic.requiresReps(exercise.measurementType)) {
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.work_mode_reps),
                            stringResource(R.string.work_mode_timed)
                        ),
                        selectedIndex = if (showingTimed) 1 else 0,
                        onSelected = { onTimedMode(it == 1) },
                        modifier = Modifier.testTag("work_mode"),
                        optionTestTags = listOf(WORK_MODE_REPS, WORK_MODE_TIMED)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (timedFieldsLocked) {
                        TimedSetProNote(onExplore = onExploreProBenefits)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                RestChoiceRow(
                    label = stringResource(R.string.rest_between_sets) + "  " + stringResource(R.string.pro_badge),
                    selected = item.restSeconds,
                    includeDefault = false,
                    onSelect = onExerciseRest
                )
                Text(
                    text = stringResource(R.string.rest_requires_pro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (exercise.measurementType != MeasurementType.COMPLETION_ONLY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.field_set_count), modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onSetCount(item.sets.size - 1) },
                            enabled = item.sets.size > 1 && !timedStructureLocked
                        ) {
                            Text("−")
                        }
                        Text("${item.sets.size}")
                        IconButton(
                            onClick = { onSetCount(item.sets.size + 1) },
                            enabled = item.sets.size < PlannedSetLogic.maxSetCount(exercise.measurementType) &&
                                !timedStructureLocked
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
                        onDistanceUnit = { onDistanceUnit(set.localId, it) },
                        onRestSeconds = { onSetRest(set.localId, it) },
                        onWorkDuration = { onWorkDuration(set.localId, it) },
                        displayTimed = if (PlannedSetLogic.requiresReps(exercise.measurementType)) {
                            showingTimed
                        } else {
                            set.timedWork
                        },
                        fieldsLocked = !timersEnabled && (showingTimed || set.timedWork)
                    )
                }
                if (exercise.measurementType != MeasurementType.COMPLETION_ONLY) {
                    TextButton(
                        onClick = onAddSet,
                        enabled = !timedStructureLocked,
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
    onDistanceUnit: (DistanceUnit) -> Unit,
    onRestSeconds: (Int?) -> Unit,
    onWorkDuration: (Int) -> Unit = {},
    displayTimed: Boolean = set.timedWork,
    fieldsLocked: Boolean = false
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
                IconButton(onClick = onRemove, enabled = total > 1 && !fieldsLocked) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_remove_set)
                    )
                }
            }
            if (PlannedSetLogic.requiresReps(exercise.measurementType) && !displayTimed) {
                OutlinedTextField(
                    value = set.minRepsText,
                    onValueChange = onMinReps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reps-min"),
                    label = { Text(stringResource(R.string.field_reps_min)) },
                    isError = issues.any {
                        it.error.name.startsWith("Reps")
                    },
                    enabled = !fieldsLocked,
                    readOnly = fieldsLocked,
                    colors = lockedFieldColors(fieldsLocked),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = set.maxRepsText,
                    onValueChange = onMaxReps,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_reps_max)) },
                    enabled = !fieldsLocked,
                    readOnly = fieldsLocked,
                    colors = lockedFieldColors(fieldsLocked),
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
                    onSelected = { selected ->
                        if (!fieldsLocked) selected?.let(onLoadKind)
                    }
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
                    enabled = !fieldsLocked,
                    readOnly = fieldsLocked,
                    colors = lockedFieldColors(fieldsLocked),
                    singleLine = true
                )
            }
            if (PlannedSetLogic.requiresDuration(exercise.measurementType) || displayTimed) {
                val (minutesShown, secondsShown) = if (displayTimed && !set.timedWork) {
                    previewDurationText(set)
                } else {
                    set.minutesText to set.secondsText
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesShown,
                        onValueChange = onMinutes,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TIMED_MINUTES_FIELD),
                        label = { Text(stringResource(R.string.field_minutes)) },
                        enabled = !fieldsLocked,
                        readOnly = fieldsLocked,
                        colors = lockedFieldColors(fieldsLocked),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secondsShown,
                        onValueChange = onSeconds,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TIMED_SECONDS_FIELD),
                        label = { Text(stringResource(R.string.field_seconds)) },
                        enabled = !fieldsLocked,
                        readOnly = fieldsLocked,
                        colors = lockedFieldColors(fieldsLocked),
                        singleLine = true
                    )
                }
                if (displayTimed) {
                    val selected = QuantityParser.toSeconds(
                        minutesShown.toIntOrNull() ?: 0,
                        secondsShown.toIntOrNull() ?: 0
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WorkDuration.PRESETS_SECONDS.forEach { seconds ->
                            FilterChip(
                                selected = selected == seconds,
                                onClick = { if (!fieldsLocked) onWorkDuration(seconds) },
                                enabled = !fieldsLocked,
                                label = { Text(stringResource(R.string.rest_seconds, seconds)) },
                                colors = if (fieldsLocked) {
                                    FilterChipDefaults.filterChipColors(
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                                        disabledSelectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                } else {
                                    FilterChipDefaults.filterChipColors()
                                }
                            )
                        }
                    }
                }
            }
            if (PlannedSetLogic.requiresDistance(exercise.measurementType)) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = set.distanceText,
                    onValueChange = onDistance,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_distance)) },
                    enabled = !fieldsLocked,
                    readOnly = fieldsLocked,
                    colors = lockedFieldColors(fieldsLocked),
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
            SetRestOverride(
                seconds = set.restSeconds,
                onSelect = onRestSeconds
            )
        }
    }
}

@Composable
private fun TimedSetProNote(onExplore: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().testTag(TIMED_EDITOR_PRO_NOTE)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.headerStackGap)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.timed_editor_pro_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Text(
            text = stringResource(R.string.timed_editor_pro_body),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        OutlinedButton(
            onClick = onExplore,
            shape = AppShapeTokens.button,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag(TIMED_EDITOR_EXPLORE_PRO)
        ) {
            Text(stringResource(R.string.timed_set_explore_pro))
        }
    }
}

@Composable
private fun lockedFieldColors(locked: Boolean) = if (locked) {
    OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledBorderColor = MaterialTheme.colorScheme.outline
    )
} else {
    OutlinedTextFieldDefaults.colors()
}

private fun previewDurationText(set: PlannedSetDraft): Pair<String, String> {
    val existing = QuantityParser.toSeconds(
        set.minutesText.toIntOrNull() ?: 0,
        set.secondsText.toIntOrNull() ?: 0
    )
    if (existing > 0) return set.minutesText to set.secondsText
    val (minutes, seconds) = QuantityParser.fromSeconds(30)
    return minutes.toString() to seconds.toString()
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

@Composable
private fun RestProgressionRow(
    strict: Boolean,
    onChange: (Boolean) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.rest_auto_advance) + "  " + stringResource(R.string.pro_badge),
                style = MaterialTheme.typography.titleSmall
            )
        }
        IconButton(
            onClick = { showInfo = true },
            modifier = Modifier.testTag("rest_info")
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = stringResource(R.string.rest_auto_advance_info)
            )
        }
        Switch(
            checked = strict,
            onCheckedChange = onChange,
            modifier = Modifier.testTag("rest_auto_advance")
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.rest_auto_advance)) },
            text = { Text(stringResource(R.string.rest_auto_advance_explanation)) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun SetRestOverride(
    seconds: Int?,
    onSelect: (Int?) -> Unit
) {
    var open by remember { mutableStateOf(seconds != null) }
    TextButton(
        onClick = { open = !open },
        modifier = Modifier.testTag("set_rest_override")
    ) {
        Text(
            text = stringResource(
                R.string.rest_set_override,
                restChoiceLabel(seconds, includeDefault = true)
            )
        )
    }
    if (open) {
        RestChoiceRow(
            label = stringResource(R.string.rest_set_override_label),
            selected = seconds,
            includeDefault = true,
            onSelect = onSelect
        )
    }
}

@Composable
private fun RestChoiceRow(
    label: String,
    selected: Int?,
    includeDefault: Boolean,
    onSelect: (Int?) -> Unit
) {
    val custom = selected != null && selected != 0 && selected !in RestDuration.PRESETS_SECONDS
    var customText by remember(selected) {
        mutableStateOf(if (custom) selected.toString() else "")
    }
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (includeDefault) {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text(stringResource(R.string.rest_default)) }
                )
            }
            FilterChip(
                selected = if (includeDefault) selected == 0 else selected == null,
                onClick = { onSelect(if (includeDefault) 0 else null) },
                label = { Text(stringResource(R.string.rest_off)) },
                modifier = Modifier.testTag("rest_off")
            )
            RestDuration.PRESETS_SECONDS.forEach { seconds ->
                FilterChip(
                    selected = selected == seconds,
                    onClick = { onSelect(seconds) },
                    label = { Text(stringResource(R.string.rest_seconds, seconds)) },
                    modifier = Modifier.testTag("rest_preset_$seconds")
                )
            }
            FilterChip(
                selected = custom,
                onClick = {
                    val parsed = customText.toIntOrNull()
                    if (parsed != null && parsed > 0) onSelect(parsed.coerceAtMost(RestDuration.MAX_SECONDS))
                },
                label = { Text(stringResource(R.string.rest_custom)) },
                modifier = Modifier.testTag("rest_custom")
            )
        }
        if (custom || customText.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(4)
                    customText = digits
                    val parsed = digits.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        onSelect(parsed.coerceAtMost(RestDuration.MAX_SECONDS))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rest_custom_seconds)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

@Composable
private fun restChoiceLabel(seconds: Int?, includeDefault: Boolean): String {
    return when {
        seconds == null && includeDefault -> stringResource(R.string.rest_default)
        seconds == null || seconds == 0 -> stringResource(R.string.rest_off)
        else -> stringResource(R.string.rest_seconds, seconds)
    }
}
