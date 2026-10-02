package app.mymusclemap.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.mymusclemap.R
import app.mymusclemap.ui.components.CompactEditorDivider
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.WeightTrackerTheme

internal const val OPEN_SOURCE_LICENSES_ROOT = "open-source-licenses-root"

internal object OpenSourceNoticeAssets {
    const val NOTICE = "third_party/body-muscles/NOTICE"
    const val LICENSE = "third_party/body-muscles/LICENSE"

    fun read(open: (String) -> String): Pair<String, String>? {
        return runCatching {
            open(NOTICE) to open(LICENSE)
        }.getOrNull()
    }
}

@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val documents = remember(context) {
        OpenSourceNoticeAssets.read { name ->
            context.assets.open(name).bufferedReader().use { it.readText() }
        }
    }
    OpenSourceLicensesContent(
        notice = documents?.first,
        license = documents?.second,
        onBack = onBack
    )
}

@Composable
private fun OpenSourceLicensesContent(
    notice: String?,
    license: String?,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(OPEN_SOURCE_LICENSES_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
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
                    text = stringResource(R.string.open_source_licenses_title),
                    style = AppTypeTokens.sectionTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
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
                Text(
                    text = stringResource(R.string.open_source_body_muscles_summary),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CompactEditorDivider()
                if (notice == null || license == null) {
                    Text(
                        text = stringResource(R.string.open_source_notices_unavailable),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CompactEditorSection(title = "NOTICE") {
                        Text(
                            text = notice,
                            style = AppTypeTokens.statSecondary.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CompactEditorDivider()
                    CompactEditorSection(title = "Apache License 2.0") {
                        Text(
                            text = license,
                            style = AppTypeTokens.statCaption.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Open source licenses")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Open source licenses dark")
@Composable
private fun OpenSourceLicensesPreview() {
    WeightTrackerTheme {
        OpenSourceLicensesContent(
            notice = "Body Muscles\nCopyright 2024 Ivan Vulović",
            license = "Apache License\nVersion 2.0, January 2004",
            onBack = {}
        )
    }
}
