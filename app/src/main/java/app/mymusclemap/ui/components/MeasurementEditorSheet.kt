package app.mymusclemap.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.DateValidationError
import app.mymusclemap.domain.WeightParseError
import app.mymusclemap.domain.WeightParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementEditorSheet(
    state: EditorUiState,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(
                    if (state.isUpdating) R.string.editor_title_edit else R.string.editor_title_add
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = UiFormatters.longDate(state.date),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.field_date)) },
                modifier = Modifier.fillMaxWidth(),
                isError = state.dateError != null,
                supportingText = {
                    state.dateError?.let {
                        Text(stringResource(dateErrorMessage(it)))
                    }
                },
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.action_change_date))
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.weightInput,
                onValueChange = { onWeightChange(WeightParser.filterUserInput(it)) },
                label = { Text(stringResource(R.string.field_weight)) },
                suffix = { Text(stringResource(R.string.unit_kg)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = state.weightError != null,
                supportingText = {
                    Text(
                        text = state.weightError?.let { stringResource(weightErrorMessage(it)) }
                            ?: stringResource(R.string.weight_input_hint)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.isUpdating) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.editor_update_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isUpdating) {
                    TextButton(onClick = onDeleteRequest) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = onSave) {
                    Text(
                        stringResource(
                            if (state.isUpdating) R.string.action_update else R.string.action_save
                        )
                    )
                }
            }
        }
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.toEpochMillisUtc(),
            selectableDates = PastAndTodayDates(today)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(millis.toLocalDateUtc())
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
    if (state.showDeleteConfirm && state.existing != null) {
        DeleteMeasurementDialog(
            measurement = state.existing,
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss
        )
    }
}

fun weightErrorMessage(error: WeightParseError): Int {
    return when (error) {
        WeightParseError.Empty -> R.string.error_weight_empty
        WeightParseError.Malformed -> R.string.error_weight_malformed
        WeightParseError.NotFinite -> R.string.error_weight_malformed
        WeightParseError.TooManyDecimals -> R.string.error_weight_decimals
        WeightParseError.OutOfRange -> R.string.error_weight_range
    }
}

fun dateErrorMessage(error: DateValidationError): Int {
    return when (error) {
        DateValidationError.Future -> R.string.error_date_future
    }
}

private class PastAndTodayDates(
    private val today: LocalDate
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        return !utcTimeMillis.toLocalDateUtc().isAfter(today)
    }
}

fun LocalDate.toEpochMillisUtc(): Long {
    return atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

fun Long.toLocalDateUtc(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
}
