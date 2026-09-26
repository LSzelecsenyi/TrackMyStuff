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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.mymusclemap.R
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.components.CompactEditorDivider
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.WeightTrackerTheme

internal const val PRIVACY_POLICY_ROOT = "privacy-policy-root"

private data class PrivacyPolicySection(
    val titleRes: Int,
    val bodyRes: Int,
    val formatWithContact: Boolean = false
)

private val privacyPolicySections = listOf(
    PrivacyPolicySection(R.string.privacy_policy_intro_title, R.string.privacy_policy_intro_body),
    PrivacyPolicySection(R.string.privacy_policy_data_title, R.string.privacy_policy_data_body),
    PrivacyPolicySection(R.string.privacy_policy_storage_title, R.string.privacy_policy_storage_body),
    PrivacyPolicySection(
        R.string.privacy_policy_transmission_title,
        R.string.privacy_policy_transmission_body
    ),
    PrivacyPolicySection(R.string.privacy_policy_export_title, R.string.privacy_policy_export_body),
    PrivacyPolicySection(
        R.string.privacy_policy_feedback_title,
        R.string.privacy_policy_feedback_body,
        formatWithContact = true
    ),
    PrivacyPolicySection(R.string.privacy_policy_deletion_title, R.string.privacy_policy_deletion_body),
    PrivacyPolicySection(
        R.string.privacy_policy_third_parties_title,
        R.string.privacy_policy_third_parties_body
    ),
    PrivacyPolicySection(
        R.string.privacy_policy_contact_title,
        R.string.privacy_policy_contact_body,
        formatWithContact = true
    )
)

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PRIVACY_POLICY_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            PrivacyPolicyHeader(onBack = onBack)
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
                    text = stringResource(R.string.privacy_policy_updated),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                privacyPolicySections.forEachIndexed { index, section ->
                    if (index == 0) {
                        CompactEditorDivider()
                    }
                    CompactEditorSection(title = stringResource(section.titleRes).uppercase(AppLocale.UI)) {
                        val body = if (section.formatWithContact) {
                            stringResource(section.bodyRes, FeedbackComposer.RECIPIENT)
                        } else {
                            stringResource(section.bodyRes)
                        }
                        Text(
                            text = body,
                            style = AppTypeTokens.statSecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index != privacyPolicySections.lastIndex) {
                        CompactEditorDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicyHeader(onBack: () -> Unit) {
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
            text = stringResource(R.string.privacy_policy_title),
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview(showBackground = true, name = "Privacy light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Privacy dark")
@Composable
private fun PrivacyPolicyPreview() {
    WeightTrackerTheme {
        PrivacyPolicyScreen(onBack = {})
    }
}
