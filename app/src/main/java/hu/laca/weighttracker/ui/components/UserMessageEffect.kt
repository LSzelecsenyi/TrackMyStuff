package hu.laca.weighttracker.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources

@Composable
fun UserMessageEffect(
    message: UserMessage?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit
) {
    val resources = LocalResources.current
    LaunchedEffect(message) {
        if (message != null) {
            val text = resources.getString(message.toStringRes(), *message.args())
            snackbarHostState.showSnackbar(text)
            onConsumed()
        }
    }
}

private fun UserMessage.toStringRes(): Int {
    return when (this) {
        UserMessage.Created -> hu.laca.weighttracker.R.string.message_created
        UserMessage.Updated -> hu.laca.weighttracker.R.string.message_updated
        UserMessage.Deleted -> hu.laca.weighttracker.R.string.message_deleted
        is UserMessage.ImportSucceeded -> hu.laca.weighttracker.R.string.message_import_success
        UserMessage.ExportSucceeded -> hu.laca.weighttracker.R.string.message_export_success
        UserMessage.ExportFailed -> hu.laca.weighttracker.R.string.message_export_failed
        UserMessage.ImportReadFailed -> hu.laca.weighttracker.R.string.message_import_read_failed
    }
}

private fun UserMessage.args(): Array<Any> {
    return when (this) {
        is UserMessage.ImportSucceeded -> arrayOf(created, updated)
        else -> emptyArray()
    }
}
