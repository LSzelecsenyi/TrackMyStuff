package app.mymusclemap.ui.settings

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

internal const val HELP_ROOT = "help-root"
internal const val HELP_TOPIC_WORKOUT = "help-topic-workout"
internal const val HELP_TOPIC_HEATMAP = "help-topic-heatmap"
internal const val HELP_TOPIC_WEIGHT = "help-topic-weight"
internal const val HELP_TOPIC_CALENDAR = "help-topic-calendar"
internal const val HELP_TOPIC_BACKUP = "help-topic-backup"

private val heatmapBandLabels = listOf(
    R.string.heatmap_band_today,
    R.string.heatmap_band_recent,
    R.string.heatmap_band_days_3_4,
    R.string.heatmap_band_days_5_6,
    R.string.heatmap_band_old,
    R.string.heatmap_band_inactive,
    R.string.heatmap_band_never
)

@Composable
fun HelpTipsScreen(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(HELP_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            HelpHeader(onBack = onBack)
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
                HelpTopic(
                    title = stringResource(R.string.help_workout_title),
                    body = stringResource(R.string.help_workout_body),
                    testTag = HELP_TOPIC_WORKOUT
                )
                CompactEditorDivider()
                HeatmapHelpTopic()
                CompactEditorDivider()
                HelpTopic(
                    title = stringResource(R.string.help_weight_title),
                    body = stringResource(R.string.help_weight_body),
                    testTag = HELP_TOPIC_WEIGHT
                )
                CompactEditorDivider()
                HelpTopic(
                    title = stringResource(R.string.help_calendar_title),
                    body = stringResource(R.string.help_calendar_body),
                    testTag = HELP_TOPIC_CALENDAR
                )
                CompactEditorDivider()
                HelpTopic(
                    title = stringResource(R.string.help_backup_title),
                    body = stringResource(R.string.help_backup_body),
                    testTag = HELP_TOPIC_BACKUP
                )
            }
        }
    }
}

@Composable
private fun HelpHeader(onBack: () -> Unit) {
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
            text = stringResource(R.string.help_title),
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HelpTopic(
    title: String,
    body: String,
    testTag: String
) {
    CompactEditorSection(
        title = title.uppercase(AppLocale.UI),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = body,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeatmapHelpTopic() {
    CompactEditorSection(
        title = stringResource(R.string.help_heatmap_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(HELP_TOPIC_HEATMAP)
    ) {
        Text(
            text = stringResource(R.string.help_heatmap_body),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = stringResource(R.string.help_heatmap_bands_intro),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        heatmapBandLabels.forEach { labelRes ->
            Text(
                text = stringResource(labelRes),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, name = "Help light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Help dark")
@Composable
private fun HelpTipsPreview() {
    WeightTrackerTheme {
        HelpTipsScreen(onBack = {})
    }
}
