package hu.laca.weighttracker.ui.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseFieldError
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.locale.LocalizedLabelOrder
import hu.laca.weighttracker.ui.components.CompactChoiceChip
import hu.laca.weighttracker.ui.components.CompactDropdown
import hu.laca.weighttracker.ui.components.CompactEditorDivider
import hu.laca.weighttracker.ui.components.CompactEditorSection
import hu.laca.weighttracker.ui.components.CompactTextField
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppShapeTokens
import hu.laca.weighttracker.ui.theme.AppTypeTokens

internal const val EXERCISE_EDITOR_ROOT = "exercise-editor-root"
internal const val EXERCISE_EDITOR_SAVE = "exercise-editor-save"
internal const val EXERCISE_FIELD_NAME = "exercise-field-name"
internal const val EXERCISE_FIELD_NOTES = "exercise-field-notes"
internal const val EXERCISE_DROPDOWN_CATEGORY = "exercise-dropdown-category"
internal const val EXERCISE_DROPDOWN_PATTERN = "exercise-dropdown-pattern"
internal const val EXERCISE_DROPDOWN_MEASUREMENT = "exercise-dropdown-measurement"
internal const val EXERCISE_DROPDOWN_RESISTANCE = "exercise-dropdown-resistance"
internal const val EXERCISE_DROPDOWN_WEIGHT = "exercise-dropdown-weight"
internal const val EXERCISE_DROPDOWN_PRIMARY = "exercise-dropdown-primary"
internal const val EXERCISE_DROPDOWN_ADD_SECONDARY = "exercise-dropdown-add-secondary"
internal const val EXERCISE_SECONDARY_CHIPS = "exercise-secondary-chips"

internal fun secondaryChipTag(group: MuscleGroup): String = "exercise-secondary-chip-${group.name}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorScreen(
    state: ExerciseEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onCategoryChange: (ExerciseCategory) -> Unit,
    onMovementChange: (MovementPattern) -> Unit,
    onMeasurementChange: (MeasurementType) -> Unit,
    onResistanceChange: (ResistanceBasis) -> Unit,
    onWeightInterpretationChange: (WeightInterpretation) -> Unit,
    onPrimaryMuscleChange: (MuscleGroup) -> Unit,
    onToggleSecondary: (MuscleGroup) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onFinished: (saved: Boolean, created: Boolean) -> Unit
) {
    BackHandler { onBack() }
    LaunchedEffect(state.finished) {
        if (state.finished) {
            onFinished(state.saved, state.created)
        }
    }
    val resources = LocalResources.current
    val categories = rememberSortedOptions(ExerciseCategory.entries) { it.labelRes() }
    val patterns = rememberSortedOptions(MovementPattern.entries) { it.labelRes() }
    val measurements = rememberSortedOptions(MeasurementType.entries) { it.labelRes() }
    val resistances = rememberSortedOptions(ResistanceBasis.entries) { it.labelRes() }
    val weightOptions = rememberSortedOptions(
        listOf(WeightInterpretation.TOTAL, WeightInterpretation.PER_SIDE)
    ) { it.labelRes() }
    val primaryMuscles = rememberSortedOptions(MuscleGroup.entries) { it.labelRes() }
    val selectedSecondaries = rememberSortedOptions(state.draft.secondaryMuscles) { it.labelRes() }
    val addableSecondaries = rememberSortedOptions(
        MuscleGroup.entries.filter { group ->
            group != state.draft.primaryMuscle && group !in state.draft.secondaryMuscles
        }
    ) { it.labelRes() }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(EXERCISE_EDITOR_ROOT),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp)
                    .defaultMinSize(minHeight = AppDimens.minTouch),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
                Text(
                    text = stringResource(
                        if (state.isEditing) R.string.exercise_editor_edit else R.string.exercise_editor_add
                    ),
                    style = AppTypeTokens.sectionTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = AppDimens.screenPadding, vertical = 8.dp)
            ) {
                Button(
                    onClick = onSave,
                    enabled = !state.saving && !state.loading,
                    shape = AppShapeTokens.button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(EXERCISE_EDITOR_SAVE)
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 8.dp, bottom = AppDimens.scrollEndPadding)
        ) {
            CompactEditorSection(title = stringResource(R.string.exercise_section_basics)) {
                val nameError = state.fieldErrors.firstOrNull {
                    it == ExerciseFieldError.NameBlank || it == ExerciseFieldError.NameTooLong
                }
                CompactTextField(
                    value = state.draft.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.exercise_field_name),
                    isError = nameError != null || state.duplicateName,
                    supportingText = when {
                        state.duplicateName -> stringResource(R.string.error_exercise_duplicate)
                        nameError != null -> stringResource(nameError.labelRes())
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    sentenceCapitalize = true,
                    testTag = EXERCISE_FIELD_NAME
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                CompactDropdown(
                    label = stringResource(R.string.exercise_field_category),
                    selected = state.draft.category,
                    options = categories,
                    optionLabel = { resources.getString(it.labelRes()) },
                    onSelected = onCategoryChange,
                    testTag = EXERCISE_DROPDOWN_CATEGORY
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                CompactDropdown(
                    label = stringResource(R.string.exercise_field_pattern),
                    selected = state.draft.movementPattern,
                    options = patterns,
                    optionLabel = { resources.getString(it.labelRes()) },
                    onSelected = onMovementChange,
                    testTag = EXERCISE_DROPDOWN_PATTERN
                )
            }
            CompactEditorDivider()
            CompactEditorSection(title = stringResource(R.string.exercise_section_measurement)) {
                CompactDropdown(
                    label = stringResource(R.string.exercise_field_measurement),
                    selected = state.draft.measurementType,
                    options = measurements,
                    optionLabel = { resources.getString(it.labelRes()) },
                    onSelected = onMeasurementChange,
                    testTag = EXERCISE_DROPDOWN_MEASUREMENT
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                CompactDropdown(
                    label = stringResource(R.string.exercise_field_resistance),
                    selected = state.draft.resistanceBasis,
                    options = resistances,
                    optionLabel = { resources.getString(it.labelRes()) },
                    onSelected = onResistanceChange,
                    testTag = EXERCISE_DROPDOWN_RESISTANCE
                )
                if (state.bodyweightHelperVisible) {
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    Text(
                        text = stringResource(R.string.exercise_bodyweight_helper),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.weightInterpretationVisible) {
                    Spacer(Modifier.height(AppDimens.itemGap))
                    CompactDropdown(
                        label = stringResource(R.string.exercise_field_weight),
                        selected = state.draft.weightInterpretation,
                        options = weightOptions,
                        optionLabel = { resources.getString(it.labelRes()) },
                        onSelected = onWeightInterpretationChange,
                        isError = ExerciseFieldError.WeightInterpretationRequired in state.fieldErrors,
                        supportingText = if (ExerciseFieldError.WeightInterpretationRequired in state.fieldErrors) {
                            stringResource(R.string.error_exercise_weight_required)
                        } else {
                            null
                        },
                        testTag = EXERCISE_DROPDOWN_WEIGHT
                    )
                }
            }
            CompactEditorDivider()
            CompactEditorSection(title = stringResource(R.string.exercise_section_muscles)) {
                CompactDropdown(
                    label = stringResource(R.string.exercise_field_primary_muscle),
                    selected = state.draft.primaryMuscle,
                    options = primaryMuscles,
                    optionLabel = { resources.getString(it.labelRes()) },
                    onSelected = onPrimaryMuscleChange,
                    testTag = EXERCISE_DROPDOWN_PRIMARY
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = stringResource(R.string.exercise_field_secondary_muscles),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(EXERCISE_SECONDARY_CHIPS),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedSecondaries.forEach { group ->
                        val label = stringResource(group.labelRes())
                        CompactChoiceChip(
                            label = label,
                            onRemove = { onToggleSecondary(group) },
                            removeDescription = stringResource(
                                R.string.exercise_remove_secondary_muscle,
                                label
                            ),
                            modifier = Modifier.testTag(secondaryChipTag(group))
                        )
                    }
                }
                if (addableSecondaries.isNotEmpty()) {
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    CompactDropdown(
                        label = stringResource(R.string.exercise_add_secondary_muscle),
                        selected = null,
                        options = addableSecondaries,
                        optionLabel = { resources.getString(it.labelRes()) },
                        onSelected = onToggleSecondary,
                        placeholder = stringResource(R.string.exercise_add_secondary_muscle),
                        testTag = EXERCISE_DROPDOWN_ADD_SECONDARY
                    )
                }
                if (ExerciseFieldError.PrimaryAlsoSecondary in state.fieldErrors) {
                    Spacer(Modifier.height(AppDimens.statSecondaryGap))
                    Text(
                        text = stringResource(R.string.error_exercise_primary_secondary),
                        color = MaterialTheme.colorScheme.error,
                        style = AppTypeTokens.statSecondary
                    )
                }
            }
            CompactEditorDivider()
            CompactEditorSection(title = stringResource(R.string.exercise_section_notes)) {
                val notesError = state.fieldErrors.firstOrNull { it == ExerciseFieldError.NotesTooLong }
                CompactTextField(
                    value = state.draft.notes,
                    onValueChange = onNotesChange,
                    label = stringResource(R.string.exercise_field_notes),
                    singleLine = false,
                    minLines = 3,
                    isError = notesError != null,
                    supportingText = notesError?.let { stringResource(it.labelRes()) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    testTag = EXERCISE_FIELD_NOTES
                )
            }
        }
    }
    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.exercise_discard_title)) },
            text = { Text(stringResource(R.string.exercise_discard_body)) },
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
private fun <T> rememberSortedOptions(
    options: List<T>,
    labelRes: (T) -> Int
): List<T> {
    val resources = LocalResources.current
    val configuration = resources.configuration
    return remember(options, configuration) {
        LocalizedLabelOrder.sorted(
            items = options,
            label = { resources.getString(labelRes(it)) },
            key = { (it as? Enum<*>)?.name ?: it.toString() }
        )
    }
}
