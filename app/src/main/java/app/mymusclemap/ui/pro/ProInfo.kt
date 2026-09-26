package app.mymusclemap.ui.pro

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.ProAccess
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.WeightTrackerTheme

internal const val PRO_INFO_ROOT = "pro-info-root"
internal const val PRO_INFO_TITLE = "pro-info-title"
internal const val PRO_INFO_BODY = "pro-info-body"
internal const val PRO_INFO_DISMISS = "pro-info-dismiss"
internal const val PRO_INFO_SHEET = "pro-info-sheet"

@Composable
fun ProInfoContent(
    modifier: Modifier = Modifier,
    feature: AppFeature? = null,
    onDismiss: (() -> Unit)? = null,
    showHeading: Boolean = true
) {
    val body = if (feature == null) {
        stringResource(R.string.pro_info_body)
    } else {
        stringResource(R.string.pro_info_feature_body, stringResource(feature.titleRes()))
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeading) {
            Text(
                text = stringResource(R.string.pro_info_kicker).uppercase(AppLocale.UI),
                style = AppTypeTokens.sectionKicker,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.pro_info_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PRO_INFO_TITLE)
                )
                ProBadge()
            }
            Spacer(Modifier.height(AppDimens.itemGap))
        } else {
            ProBadge()
            Spacer(Modifier.height(AppDimens.itemGap))
        }
        Text(
            text = body,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(PRO_INFO_BODY)
        )
        if (onDismiss != null) {
            Spacer(Modifier.height(AppDimens.sectionGap))
            Button(
                onClick = onDismiss,
                shape = AppShapeTokens.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(PRO_INFO_DISMISS)
            ) {
                Text(stringResource(R.string.action_ok))
            }
        }
    }
}

@Composable
fun ProInfoScreen(
    onBack: () -> Unit,
    feature: AppFeature? = null
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PRO_INFO_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            ProInfoHeader(onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(
                        top = AppDimens.headerStackGap,
                        bottom = AppDimens.scrollEndPadding
                    )
            ) {
                ProInfoContent(feature = feature, showHeading = false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProInfoSheet(
    onDismiss: () -> Unit,
    feature: AppFeature? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(PRO_INFO_SHEET)
    ) {
        ProInfoContent(
            feature = feature,
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
fun ProAccessHost(
    entitlements: FeatureEntitlements = LocalFeatureEntitlements.current,
    content: @Composable (gate: (AppFeature, () -> Unit) -> Unit) -> Unit
) {
    var lockedFeature by remember { mutableStateOf<AppFeature?>(null) }
    content { feature, action ->
        ProAccess.run(
            entitlements = entitlements,
            feature = feature,
            onLocked = { lockedFeature = feature },
            onAllowed = action
        )
    }
    lockedFeature?.let { feature ->
        ProInfoSheet(
            feature = feature,
            onDismiss = { lockedFeature = null }
        )
    }
}

@Composable
private fun ProInfoHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenPadding)
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(AppDimens.minTouch)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Text(
            text = stringResource(R.string.pro_info_title),
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag(PRO_INFO_TITLE)
        )
    }
}

@Preview(showBackground = true, name = "Pro info light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Pro info dark")
@Composable
private fun ProInfoPreview() {
    WeightTrackerTheme {
        ProInfoScreen(onBack = {}, feature = AppFeature.AdvancedStatistics)
    }
}
