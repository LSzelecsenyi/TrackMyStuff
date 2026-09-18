package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.WeightParseError
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppShapeTokens
import hu.laca.weighttracker.ui.theme.AppTypeTokens
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val HUB_START_SHEET = "workout-hub-start-sheet"
internal const val HUB_START_CONFIRM = "workout-hub-start-confirm"
internal const val HUB_START_CANCEL = "workout-hub-start-cancel"
internal const val HUB_START_WEIGHT = "workout-hub-start-weight"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartWorkoutSheet(
    draft: StartWorkoutDraft,
    starting: Boolean,
    onDismiss: () -> Unit,
    onWeightChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val sourceText = when (draft.proposal.source) {
        BodyWeightSource.MEASURED_SAME_DAY -> stringResource(
            R.string.start_weight_same_day,
            draft.proposal.kilograms?.let(UiFormatters::weightKg).orEmpty()
        )
        BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT -> stringResource(
            R.string.start_weight_previous,
            draft.proposal.sourceDate?.let(::formatMonthDay).orEmpty(),
            draft.proposal.kilograms?.let(UiFormatters::weightKg).orEmpty()
        )
        BodyWeightSource.MANUAL -> stringResource(R.string.start_weight_manual)
        BodyWeightSource.UNKNOWN -> stringResource(R.string.start_weight_none)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    ModalBottomSheet(
        onDismissRequest = { if (!starting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(HUB_START_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = AppDimens.itemGap)
        ) {
            Text(
                text = stringResource(R.string.start_workout_title),
                style = AppTypeTokens.sectionKicker,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = draft.template.template.name,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = stringResource(
                    R.string.start_workout_body,
                    draft.template.exerciseCount,
                    draft.template.setCount
                ),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Text(
                text = sourceText,
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            CompactStartWeightField(
                value = draft.weightText,
                onValueChange = onWeightChange,
                isError = draft.weightError != null,
                supportingText = draft.weightError?.let { stringResource(it.labelRes()) }
                    ?: stringResource(R.string.start_weight_optional),
                enabled = !starting,
                onDone = { focusManager.clearFocus(force = true) }
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(
                onClick = onConfirm,
                enabled = !starting && draft.weightError == null,
                shape = AppShapeTokens.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(HUB_START_CONFIRM)
            ) {
                if (starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.action_start_workout))
                }
            }
            TextButton(
                onClick = { if (!starting) onDismiss() },
                enabled = !starting,
                modifier = Modifier
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(HUB_START_CANCEL)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
internal fun CompactSheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .size(width = 32.dp, height = 3.dp)
            .clip(AppShapeTokens.compact)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun CompactStartWeightField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supportingText: String,
    enabled: Boolean,
    onDone: () -> Unit
) {
    val label = stringResource(R.string.field_weight)
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != field.text) {
            val cursor = field.selection.start.coerceIn(0, value.length)
            field = TextFieldValue(text = value, selection = TextRange(cursor))
        }
    }
    val lineColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = field,
                onValueChange = { incoming ->
                    field = incoming
                    onValueChange(incoming.text)
                },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label }
                    .testTag(HUB_START_WEIGHT),
                textStyle = AppTypeTokens.statHero.copy(color = MaterialTheme.colorScheme.onBackground),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() })
            )
            Text(
                text = stringResource(R.string.unit_kg),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.strokeThin)
                .background(lineColor)
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        Text(
            text = supportingText,
            style = AppTypeTokens.statCaption,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun formatMonthDay(date: java.time.LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("MMMM d.", Locale.forLanguageTag("hu-HU")))
}

fun WorkoutHubMessage.labelRes(): Int {
    return when (this) {
        WorkoutHubMessage.AlreadyActive -> R.string.message_workout_already_active
        WorkoutHubMessage.TemplateArchived -> R.string.message_workout_template_archived
        WorkoutHubMessage.TemplateEmpty -> R.string.message_workout_template_empty
        WorkoutHubMessage.TemplateNotFound -> R.string.message_workout_template_empty
        WorkoutHubMessage.WorkoutFinished -> R.string.message_workout_finished
        WorkoutHubMessage.WorkoutAbandoned -> R.string.message_workout_abandoned
    }
}

fun WeightParseError.labelRes(): Int {
    return when (this) {
        WeightParseError.Empty -> R.string.error_weight_empty
        WeightParseError.Malformed, WeightParseError.NotFinite -> R.string.error_weight_malformed
        WeightParseError.TooManyDecimals -> R.string.error_weight_decimals
        WeightParseError.OutOfRange -> R.string.error_weight_range
    }
}
