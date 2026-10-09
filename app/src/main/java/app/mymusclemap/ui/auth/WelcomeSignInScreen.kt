package app.mymusclemap.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.StrictBrand

internal const val WELCOME_ROOT = "welcome-root"
internal const val WELCOME_CONTINUE = "welcome-continue"
internal const val WELCOME_CLAIM = "welcome-claim"

@Composable
fun WelcomeSignInScreen(
    busy: Boolean,
    message: String?,
    claimOpen: Boolean,
    onContinue: () -> Unit,
    onConfirmClaim: () -> Unit,
    onCancelClaim: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp)
            .testTag(WELCOME_ROOT),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.strict_wordmark_dark),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.72f)
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            color = StrictBrand.lime,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.welcome_body),
            color = StrictBrand.light,
            textAlign = TextAlign.Center
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(text = message, color = StrictBrand.lime, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        if (busy) {
            CircularProgressIndicator(color = StrictBrand.lime)
        } else {
            Button(
                onClick = onContinue,
                shape = AppShapeTokens.button,
                colors = StrictBrand.actionButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WELCOME_CONTINUE)
            ) {
                Text(stringResource(R.string.welcome_continue))
            }
        }
        Spacer(Modifier.weight(1f))
    }
    if (claimOpen) {
        AlertDialog(
            onDismissRequest = onCancelClaim,
            containerColor = StrictBrand.dark,
            title = { Text(stringResource(R.string.welcome_claim_title), color = StrictBrand.lime) },
            text = { Text(stringResource(R.string.welcome_claim_body), color = StrictBrand.light) },
            confirmButton = {
                TextButton(onClick = onConfirmClaim, modifier = Modifier.testTag(WELCOME_CLAIM)) {
                    Text(stringResource(R.string.welcome_claim_confirm), color = StrictBrand.lime)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelClaim) {
                    Text(stringResource(R.string.welcome_claim_cancel), color = StrictBrand.light)
                }
            }
        )
    }
}
