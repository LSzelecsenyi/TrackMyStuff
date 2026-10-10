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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.formatTrialRemaining
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.Instant

internal const val WELCOME_BACK_OFFER = "welcome-back-offer"
internal const val WELCOME_BACK_ACTIVATE = "welcome-back-activate"
internal const val WELCOME_BACK_NOT_NOW = "welcome-back-not-now"
internal const val WELCOME_BACK_WARNING = "welcome-back-warning"
internal const val WELCOME_BACK_VIEW_PLANS = "welcome-back-view-plans"
internal const val WELCOME_BACK_MAYBE_LATER = "welcome-back-maybe-later"

@Composable
fun WelcomeBackOfferDialog(
    onActivate: () -> Unit,
    onNotNow: () -> Unit
) {
    Dialog(
        onDismissRequest = onNotNow,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                    .heightIn(max = 560.dp)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .testTag(WELCOME_BACK_OFFER),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.welcome_back_offer_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = StrictBrand.lime,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.welcome_back_offer_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = StrictBrand.light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.welcome_back_offer_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StrictBrand.light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onActivate,
                        shape = AppShapeTokens.button,
                        colors = StrictBrand.actionButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WELCOME_BACK_ACTIVATE)
                    ) {
                        Text(stringResource(R.string.welcome_back_activate))
                    }
                    TextButton(
                        onClick = onNotNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WELCOME_BACK_NOT_NOW)
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_back_not_now),
                            color = StrictBrand.lime
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeBackWarningDialog(
    expiresAt: Instant,
    now: Instant,
    onViewPlans: () -> Unit,
    onMaybeLater: () -> Unit
) {
    Dialog(
        onDismissRequest = onMaybeLater,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                    .heightIn(max = 560.dp)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .testTag(WELCOME_BACK_WARNING),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(
                            R.string.welcome_back_warning_title,
                            formatTrialRemaining(now, expiresAt)
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = StrictBrand.lime,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.welcome_back_warning_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = StrictBrand.light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.welcome_back_warning_when,
                            proDiscoveryDateTime(expiresAt)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StrictBrand.light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onViewPlans,
                        shape = AppShapeTokens.button,
                        colors = StrictBrand.actionButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WELCOME_BACK_VIEW_PLANS)
                    ) {
                        Text(stringResource(R.string.pro_discovery_view_plans))
                    }
                    TextButton(
                        onClick = onMaybeLater,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WELCOME_BACK_MAYBE_LATER)
                    ) {
                        Text(
                            text = stringResource(R.string.pro_discovery_maybe_later),
                            color = StrictBrand.lime
                        )
                    }
                }
            }
        }
    }
}
