package app.mymusclemap.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import kotlinx.coroutines.launch

internal const val DELETE_ACCOUNT_CONFIRM = "delete-account-confirm"
internal const val DELETE_ACCOUNT_ACTION = "delete-account-action"
internal const val DELETE_ACCOUNT_PRIVACY = "delete-account-privacy"
internal const val DELETE_ACCOUNT_MANAGE = "delete-account-manage"

enum class DeleteAccountResult {
    Deleted,
    NeedsNetwork,
    Cancelled,
    ReauthenticationRequired,
    Failed,
    LocalCleanupIncomplete
}

private val Destructive = Color(0xFF8C1D18)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    paidSubscriptionKnown: Boolean,
    onManageSubscription: () -> Unit,
    onDelete: suspend () -> DeleteAccountResult,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit = {}
) {
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.delete_account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(stringResource(R.string.delete_account_intro))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.delete_account_founder))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.delete_account_local))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.delete_account_photos))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.delete_account_subscription))
            if (paidSubscriptionKnown) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.delete_account_subscription_known),
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(
                    onClick = onManageSubscription,
                    modifier = Modifier.testTag(DELETE_ACCOUNT_MANAGE)
                ) {
                    Text(stringResource(R.string.delete_account_manage_subscription))
                }
            }
            TextButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.testTag(DELETE_ACCOUNT_PRIVACY)
            ) {
                Text(stringResource(R.string.action_privacy_policy))
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.delete_account_undo))
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = confirmed,
                    onCheckedChange = { if (!busy) confirmed = it },
                    modifier = Modifier.testTag(DELETE_ACCOUNT_CONFIRM)
                )
                Text(stringResource(R.string.delete_account_confirm))
            }
            message?.let { res ->
                Spacer(Modifier.height(12.dp))
                Text(stringResource(res), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (!confirmed || busy) return@Button
                    busy = true
                    message = null
                    scope.launch {
                        when (onDelete()) {
                            DeleteAccountResult.Deleted,
                            DeleteAccountResult.LocalCleanupIncomplete -> onFinished()
                            DeleteAccountResult.NeedsNetwork -> message = R.string.delete_account_offline
                            DeleteAccountResult.Cancelled -> Unit
                            DeleteAccountResult.ReauthenticationRequired ->
                                message = R.string.delete_account_reauth
                            DeleteAccountResult.Failed -> message = R.string.delete_account_failed
                        }
                        busy = false
                    }
                },
                enabled = confirmed && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DELETE_ACCOUNT_ACTION),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Destructive,
                    contentColor = Color.White,
                    disabledContainerColor = Destructive.copy(alpha = 0.38f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Text(
                    stringResource(
                        if (busy) R.string.delete_account_working else R.string.delete_account_action
                    )
                )
            }
        }
    }
}
