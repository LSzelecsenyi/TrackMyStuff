package app.mymusclemap.ui.pro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.mymusclemap.R
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.StrictBrand

internal const val PRIVACY_NOTICE = "privacy-notice"
internal const val PRIVACY_NOTICE_CONTINUE = "privacy-notice-continue"
internal const val PRIVACY_NOTICE_VIEW = "privacy-notice-view"

@Composable
fun PrivacyPolicyNoticeDialog(
    onContinue: () -> Unit,
    onViewPolicy: () -> Unit
) {
    Dialog(onDismissRequest = onContinue) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .testTag(PRIVACY_NOTICE),
            shape = AppShapeTokens.surface,
            color = StrictBrand.dark
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.privacy_notice_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = StrictBrand.lime
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.privacy_notice_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = StrictBrand.light
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onContinue,
                    shape = AppShapeTokens.button,
                    colors = StrictBrand.actionButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PRIVACY_NOTICE_CONTINUE)
                ) {
                    Text(stringResource(R.string.privacy_notice_continue))
                }
                TextButton(
                    onClick = onViewPolicy,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag(PRIVACY_NOTICE_VIEW)
                ) {
                    Text(stringResource(R.string.action_privacy_policy))
                }
            }
        }
    }
}
