package hu.laca.weighttracker.ui.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseFieldError
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.exercise_editor_edit else R.string.exercise_editor_add
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(top = 8.dp, bottom = AppDimens.scrollEndPadding)
            ) {
                val nameError = state.fieldErrors.firstOrNull {
                    it == ExerciseFieldError.NameBlank || it == ExerciseFieldError.NameTooLong
                }
                OutlinedTextField(
                    value = state.draft.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.exercise_field_name)) },
                    isError = nameError != null || state.duplicateName,
                    supportingText = {
                        val message = when {
                            state.duplicateName -> stringResource(R.string.error_exercise_duplicate)
                            nameError != null -> stringResource(nameError.labelRes())
                            else -> null
                        }
                        if (message != null) {
                            Text(message)
                        }
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                CatalogDropdown(
                    label = stringResource(R.string.exercise_field_category),
                    selected = state.draft.category,
                    options = ExerciseCategory.entries,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelected = { value -> value?.let(onCategoryChange) }
                )
                Spacer(Modifier.height(12.dp))
                CatalogDropdown(
                    label = stringResource(R.string.exercise_field_pattern),
                    selected = state.draft.movementPattern,
                    options = MovementPattern.entries,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelected = { value -> value?.let(onMovementChange) }
                )
                Spacer(Modifier.height(12.dp))
                CatalogDropdown(
                    label = stringResource(R.string.exercise_field_measurement),
                    selected = state.draft.measurementType,
                    options = MeasurementType.entries,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelected = { value -> value?.let(onMeasurementChange) }
                )
                Spacer(Modifier.height(12.dp))
                CatalogDropdown(
                    label = stringResource(R.string.exercise_field_resistance),
                    selected = state.draft.resistanceBasis,
                    options = ResistanceBasis.entries,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelected = { value -> value?.let(onResistanceChange) }
                )
                if (state.bodyweightHelperVisible) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.exercise_bodyweight_helper),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.weightInterpretationVisible) {
                    Spacer(Modifier.height(12.dp))
                    CatalogDropdown(
                        label = stringResource(R.string.exercise_field_weight),
                        selected = state.draft.weightInterpretation,
                        options = listOf(WeightInterpretation.TOTAL, WeightInterpretation.PER_SIDE),
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelected = { value -> value?.let(onWeightInterpretationChange) }
                    )
                    if (ExerciseFieldError.WeightInterpretationRequired in state.fieldErrors) {
                        Text(
                            text = stringResource(R.string.error_exercise_weight_required),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                CatalogDropdown(
                    label = stringResource(R.string.exercise_field_primary_muscle),
                    selected = state.draft.primaryMuscle,
                    options = MuscleGroup.entries,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelected = { value -> value?.let(onPrimaryMuscleChange) }
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.exercise_field_secondary_muscles),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MuscleGroup.entries.forEach { group ->
                        val selected = group in state.draft.secondaryMuscles
                        val enabled = group != state.draft.primaryMuscle
                        FilterChip(
                            selected = selected,
                            onClick = { if (enabled) onToggleSecondary(group) },
                            enabled = enabled,
                            label = { Text(stringResource(group.labelRes())) }
                        )
                    }
                }
                if (ExerciseFieldError.PrimaryAlsoSecondary in state.fieldErrors) {
                    Text(
                        text = stringResource(R.string.error_exercise_primary_secondary),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(16.dp))
                val notesError = state.fieldErrors.firstOrNull { it == ExerciseFieldError.NotesTooLong }
                OutlinedTextField(
                    value = state.draft.notes,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.exercise_field_notes)) },
                    minLines = 3,
                    isError = notesError != null,
                    supportingText = notesError?.let { { Text(stringResource(it.labelRes())) } }
                )
            }
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.screenPadding, vertical = 12.dp),
                enabled = state.canSave && !state.duplicateName
            ) {
                Text(stringResource(R.string.action_save))
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
