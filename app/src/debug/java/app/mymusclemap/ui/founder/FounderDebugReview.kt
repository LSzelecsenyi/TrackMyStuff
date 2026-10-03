package app.mymusclemap.ui.founder

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.ui.theme.AppDimens

@Composable
fun FounderDebugReview(
    status: FounderProgramStatus,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    if (status != FounderProgramStatus.PendingApproval) {
        return
    }
    var rejectOpen by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    var reasonBlank by rememberSaveable { mutableStateOf(false) }
    Spacer(Modifier.height(AppDimens.sectionGap))
    Text(text = stringResource(R.string.founder_debug_review_title))
    Spacer(Modifier.height(AppDimens.itemGap))
    Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.founder_debug_approve))
    }
    Spacer(Modifier.height(AppDimens.itemGap))
    TextButton(onClick = { rejectOpen = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.founder_debug_reject))
    }
    if (rejectOpen) {
        AlertDialog(
            onDismissRequest = { rejectOpen = false },
            title = { Text(stringResource(R.string.founder_debug_reject)) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        reasonBlank = false
                    },
                    label = { Text(stringResource(R.string.founder_debug_reject_reason)) },
                    isError = reasonBlank,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (reason.isBlank()) {
                            reasonBlank = true
                        } else {
                            rejectOpen = false
                            onReject(reason)
                        }
                    }
                ) {
                    Text(stringResource(R.string.founder_debug_reject))
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
