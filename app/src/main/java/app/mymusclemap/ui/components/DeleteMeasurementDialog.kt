package app.mymusclemap.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mymusclemap.R
import app.mymusclemap.domain.model.WeightMeasurement

@Composable
fun DeleteMeasurementDialog(
    measurement: WeightMeasurement,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.delete_message,
                    UiFormatters.longDate(measurement.date),
                    UiFormatters.weightKg(measurement.weightKg)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
