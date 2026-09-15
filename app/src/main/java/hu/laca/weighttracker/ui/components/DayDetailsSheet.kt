package hu.laca.weighttracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.DaySheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsSheet(
    state: DaySheetState,
    onRecordWeight: () -> Unit,
    onEditWeight: () -> Unit,
    onDeleteWeight: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = UiFormatters.longDate(state.date),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            if (state.measurement != null) {
                Text(
                    text = UiFormatters.weightKg(state.measurement.weightKg),
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = state.differenceFromPreviousKg?.let {
                        stringResource(R.string.change_from_previous_value, UiFormatters.signedWeightKg(it))
                    } ?: stringResource(R.string.no_previous_measurement),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onEditWeight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_edit_weight))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDeleteWeight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            } else {
                Text(
                    text = stringResource(R.string.day_sheet_no_weight),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onRecordWeight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_record_weight))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}
