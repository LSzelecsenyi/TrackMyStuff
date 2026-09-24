package hu.laca.weighttracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.workout.ScheduledWorkout

internal const val UNSCHEDULE_DIALOG = "unschedule-dialog"

@Composable
fun UnscheduleWorkoutDialog(
    item: ScheduledWorkout,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unschedule_title)) },
        text = {
            Text(
                stringResource(
                    R.string.unschedule_message,
                    item.templateName,
                    UiFormatters.longDate(item.scheduledDate)
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("unschedule-confirm")
            ) {
                Text(stringResource(R.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = Modifier.testTag(UNSCHEDULE_DIALOG)
    )
}
