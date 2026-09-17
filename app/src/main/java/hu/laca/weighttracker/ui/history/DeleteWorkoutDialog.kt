package hu.laca.weighttracker.ui.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import hu.laca.weighttracker.R

internal const val WORKOUT_DELETE_ACTION = "workout-delete-action"
internal const val WORKOUT_DELETE_CONFIRM = "workout-delete-confirm"
internal const val WORKOUT_DELETE_DISMISS = "workout-delete-dismiss"
internal const val WORKOUT_DELETE_DIALOG = "workout-delete-dialog"

@Composable
fun DeleteWorkoutDialog(
    name: String,
    dateLabel: String,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        modifier = Modifier.testTag(WORKOUT_DELETE_DIALOG),
        title = { Text(stringResource(R.string.delete_workout_title)) },
        text = {
            Text(
                stringResource(R.string.delete_workout_name_date, name, dateLabel) +
                    "\n\n" +
                    stringResource(R.string.delete_workout_body)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !deleting,
                modifier = Modifier.testTag(WORKOUT_DELETE_CONFIRM)
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !deleting,
                modifier = Modifier.testTag(WORKOUT_DELETE_DISMISS)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
