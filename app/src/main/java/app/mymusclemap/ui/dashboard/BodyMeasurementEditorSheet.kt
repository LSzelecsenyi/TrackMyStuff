package app.mymusclemap.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.mymusclemap.domain.body.BodyMeasurementParseError
import app.mymusclemap.domain.body.BodyMeasurementUnit
import app.mymusclemap.ui.components.PastAndTodayDates
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.components.toEpochMillisUtc
import app.mymusclemap.ui.components.toLocalDateUtc
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeasurementEditorSheet(
    state: BodyEditorUiState,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val unit = stringResource(state.type.unit.labelRes())
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
                text = stringResource(state.type.labelRes()),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(
                    if (state.isUpdating) R.string.editor_title_edit else R.string.editor_title_add
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = UiFormatters.longDate(state.date),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.field_date)) },
                supportingText = state.dateError?.let {
                    { Text(stringResource(R.string.error_date_future)) }
                },
                isError = state.dateError != null,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { showDatePicker = true }) {
                Text(stringResource(R.string.action_change_date))
            }
            OutlinedTextField(
                value = state.valueInput,
                onValueChange = onValueChange,
                label = { Text(unit) },
                supportingText = state.valueError?.let { error ->
                    { Text(stringResource(error.messageRes(), unit)) }
                },
                isError = state.valueError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
            if (state.isUpdating) {
                TextButton(onClick = onDeleteRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_delete))
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
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.body_measurement_delete_message,
                        stringResource(state.type.labelRes()),
                        UiFormatters.longDate(state.existing.date)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun BodyMeasurementParseError.messageRes(): Int {
    return when (this) {
        BodyMeasurementParseError.Empty -> R.string.body_measurement_error_empty
        BodyMeasurementParseError.Malformed -> R.string.body_measurement_error_malformed
        BodyMeasurementParseError.NotFinite -> R.string.body_measurement_error_malformed
        BodyMeasurementParseError.TooManyDecimals -> R.string.error_weight_decimals
        BodyMeasurementParseError.OutOfRange -> R.string.body_measurement_error_range
    }
}

internal fun BodyMeasurementUnit.labelRes(): Int {
    return when (this) {
        BodyMeasurementUnit.CENTIMETERS -> R.string.body_unit_cm
        BodyMeasurementUnit.PERCENT -> R.string.body_unit_percent
    }
}
