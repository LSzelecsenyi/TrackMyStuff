package app.mymusclemap.ui.workout

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FreeAccessDecision
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCatalogLogic
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.workout.ExtraExerciseAccess
import app.mymusclemap.ui.exercises.CatalogDropdown
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.exercises.starterExerciseLabel
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.sheetContainerColor

internal const val EXTRA_EXERCISE_PICKER = "extra-exercise-picker"
internal const val EXTRA_EXERCISE_NONE = "extra-exercise-picker-none"
internal const val EXTRA_EXERCISE_NO_RESULTS = "extra-exercise-picker-no-results"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtraExercisePickerSheet(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSelect: (Exercise) -> Unit,
    access: FreeAccessDecision = FreeAccessLogic.unknown()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ExerciseCategory?>(null) }
    var muscle by remember { mutableStateOf<MuscleGroup?>(null) }
    val addable = remember(exercises, access) {
        exercises.filter { ExtraExerciseAccess.addable(it, access) }
    }
    val visible = remember(addable, query, category, muscle) {
        ExerciseCatalogLogic.filter(
            exercises = addable,
            query = query,
            category = category,
            muscle = muscle,
            archiveFilter = ArchiveFilter.ACTIVE
        )
    }
    LaunchedEffect(Unit) {
        keyboard?.hide()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor(),
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(EXTRA_EXERCISE_PICKER)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = AppDimens.scrollEndPadding)
        ) {
            Text(
                text = stringResource(R.string.extra_exercise_picker_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            if (addable.isEmpty()) {
                Text(
                    text = stringResource(R.string.extra_exercise_picker_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(EXTRA_EXERCISE_NONE)
                )
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.exercise_search)) }
            )
            Spacer(Modifier.height(8.dp))
            CatalogDropdown(
                label = stringResource(R.string.exercise_filter_category),
                selected = category,
                options = ExerciseCategory.entries,
                optionLabel = { stringResource(it.labelRes()) },
                noneLabel = stringResource(R.string.exercise_filter_all_categories),
                onSelected = { category = it }
            )
            Spacer(Modifier.height(8.dp))
            CatalogDropdown(
                label = stringResource(R.string.exercise_filter_muscle),
                selected = muscle,
                options = MuscleGroup.entries,
                optionLabel = { stringResource(it.labelRes()) },
                noneLabel = stringResource(R.string.exercise_filter_all_muscles),
                onSelected = { muscle = it }
            )
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.extra_exercise_picker_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(EXTRA_EXERCISE_NO_RESULTS)
                )
            }
            visible.forEach { exercise ->
                TextButton(
                    onClick = {
                        if (!ExtraExerciseAccess.addable(exercise, access)) return@TextButton
                        onSelect(exercise)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(extraExercisePickerRowTag(exercise.id))
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
                    }
                }
            }
        }
    }
}

internal fun extraExercisePickerRowTag(exerciseId: Long): String = "extra-exercise-picker-row-$exerciseId"
