package app.mymusclemap.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources

fun UserMessage.stringRes(): Int {
    return when (this) {
        UserMessage.Created -> app.mymusclemap.R.string.message_created
        UserMessage.Updated -> app.mymusclemap.R.string.message_updated
        UserMessage.Deleted -> app.mymusclemap.R.string.message_deleted
        UserMessage.WorkoutDeleted -> app.mymusclemap.R.string.message_workout_deleted
        UserMessage.WorkoutDeleteFailed -> app.mymusclemap.R.string.message_workout_delete_failed
        is UserMessage.ImportSucceeded -> app.mymusclemap.R.string.message_import_success
        UserMessage.ExportSucceeded -> app.mymusclemap.R.string.message_export_success
        UserMessage.ExportFailed -> app.mymusclemap.R.string.message_export_failed
        UserMessage.ImportReadFailed -> app.mymusclemap.R.string.message_import_read_failed
        UserMessage.AppBackupExportSucceeded -> app.mymusclemap.R.string.message_app_backup_export_success
        UserMessage.AppBackupExportFailed -> app.mymusclemap.R.string.message_app_backup_export_failed
        UserMessage.AppBackupRestoreSucceeded -> app.mymusclemap.R.string.message_app_backup_restore_success
        UserMessage.AppBackupReadFailed -> app.mymusclemap.R.string.message_app_backup_read_failed
        UserMessage.PaletteSaved -> app.mymusclemap.R.string.message_palette_saved
        UserMessage.ScheduleRemoved -> app.mymusclemap.R.string.message_schedule_removed
        UserMessage.ScheduleDuplicate -> app.mymusclemap.R.string.message_schedule_duplicate
        UserMessage.ScheduleLinked -> app.mymusclemap.R.string.message_schedule_linked
        UserMessage.ScheduleTemplateArchived -> app.mymusclemap.R.string.message_schedule_template_archived
        UserMessage.ScheduleTemplateNotFound -> app.mymusclemap.R.string.message_schedule_template_not_found
        UserMessage.WorkoutAlreadyActive -> app.mymusclemap.R.string.message_workout_already_active
        UserMessage.WorkoutTemplateEmpty -> app.mymusclemap.R.string.message_workout_template_empty
        UserMessage.WorkoutTemplateArchived -> app.mymusclemap.R.string.message_workout_template_archived
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
