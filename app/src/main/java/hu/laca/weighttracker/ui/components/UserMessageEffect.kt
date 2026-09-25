package hu.laca.weighttracker.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources

fun UserMessage.stringRes(): Int {
    return when (this) {
        UserMessage.Created -> hu.laca.weighttracker.R.string.message_created
        UserMessage.Updated -> hu.laca.weighttracker.R.string.message_updated
        UserMessage.Deleted -> hu.laca.weighttracker.R.string.message_deleted
        UserMessage.WorkoutDeleted -> hu.laca.weighttracker.R.string.message_workout_deleted
        UserMessage.WorkoutDeleteFailed -> hu.laca.weighttracker.R.string.message_workout_delete_failed
        is UserMessage.ImportSucceeded -> hu.laca.weighttracker.R.string.message_import_success
        UserMessage.ExportSucceeded -> hu.laca.weighttracker.R.string.message_export_success
        UserMessage.ExportFailed -> hu.laca.weighttracker.R.string.message_export_failed
        UserMessage.ImportReadFailed -> hu.laca.weighttracker.R.string.message_import_read_failed
        UserMessage.AppBackupExportSucceeded -> hu.laca.weighttracker.R.string.message_app_backup_export_success
        UserMessage.AppBackupExportFailed -> hu.laca.weighttracker.R.string.message_app_backup_export_failed
        UserMessage.AppBackupRestoreSucceeded -> hu.laca.weighttracker.R.string.message_app_backup_restore_success
        UserMessage.AppBackupReadFailed -> hu.laca.weighttracker.R.string.message_app_backup_read_failed
        UserMessage.PaletteSaved -> hu.laca.weighttracker.R.string.message_palette_saved
        UserMessage.ScheduleRemoved -> hu.laca.weighttracker.R.string.message_schedule_removed
        UserMessage.ScheduleDuplicate -> hu.laca.weighttracker.R.string.message_schedule_duplicate
        UserMessage.ScheduleLinked -> hu.laca.weighttracker.R.string.message_schedule_linked
        UserMessage.ScheduleTemplateArchived -> hu.laca.weighttracker.R.string.message_schedule_template_archived
        UserMessage.ScheduleTemplateNotFound -> hu.laca.weighttracker.R.string.message_schedule_template_not_found
        UserMessage.WorkoutAlreadyActive -> hu.laca.weighttracker.R.string.message_workout_already_active
        UserMessage.WorkoutTemplateEmpty -> hu.laca.weighttracker.R.string.message_workout_template_empty
        UserMessage.WorkoutTemplateArchived -> hu.laca.weighttracker.R.string.message_workout_template_archived
    }
}

@Composable
fun UserMessageEffect(
    message: UserMessage?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit
) {
    val resources = LocalResources.current
    LaunchedEffect(message) {
        if (message != null) {
            val text = resources.getString(message.stringRes(), *message.args())
            snackbarHostState.showSnackbar(text)
            onConsumed()
        }
    }
}

private fun UserMessage.args(): Array<Any> {
    return when (this) {
        is UserMessage.ImportSucceeded -> arrayOf(created, updated)
        else -> emptyArray()
    }
}
