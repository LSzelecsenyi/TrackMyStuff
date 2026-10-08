package app.mymusclemap.ui.founder

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FounderApprovalCelebration
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.ZoneId

internal const val FOUNDER_APPROVAL_CELEBRATION = "founder-approval-celebration"
internal const val FOUNDER_APPROVAL_BADGE = "founder-approval-badge"
internal const val FOUNDER_APPROVAL_START = "founder-approval-start"

/**
 * Congratulates a backend-confirmed Founder approval. Dismissal acknowledges it.
 * The primary action does not open a paywall or change entitlement.
 */
@Composable
fun FounderApprovalCelebrationDialog(
    celebration: FounderApprovalCelebration,
    onGetStarted: () -> Unit,
    onDismiss: () -> Unit
) {
    var delivered by remember { mutableStateOf(false) }
    val deliver: (() -> Unit) -> Unit = { action ->
        if (!delivered) {
            delivered = true
            action()
        }
    }
    val expiresOn = celebration.proExpiresAt.atZone(ZoneId.systemDefault()).toLocalDate()
    Dialog(
        onDismissRequest = { deliver(onDismiss) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = AppShapeTokens.surface,
            color = StrictBrand.dark
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .testTag(FOUNDER_APPROVAL_CELEBRATION),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.founder_badge),
                        contentDescription = stringResource(R.string.founder_badge),
                        modifier = Modifier
                            .size(120.dp)
                            .testTag(FOUNDER_APPROVAL_BADGE)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.founder_approval_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = StrictBrand.lime,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.founder_approval_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = StrictBrand.light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.founder_approval_reward),
                        style = MaterialTheme.typography.titleMedium,
                        color = StrictBrand.lime,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.founder_approval_until,
                            UiFormatters.longDate(expiresOn)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StrictBrand.light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.founder_approval_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StrictBrand.light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.founder_approval_badge),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StrictBrand.light,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { deliver(onGetStarted) },
                    shape = AppShapeTokens.button,
                    colors = StrictBrand.actionButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FOUNDER_APPROVAL_START)
                ) {
                    Text(stringResource(R.string.founder_approval_start))
                }
            }
        }
    }
}
