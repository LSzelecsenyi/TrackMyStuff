package app.mymusclemap.ui.muscleinsights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.muscleinsights.MuscleQuickInsight
import app.mymusclemap.domain.musclemap.MuscleHeatmapEntry
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import app.mymusclemap.ui.components.musclemap.recencyLabel
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val MUSCLE_QUICK_INSIGHTS = "muscle-quick-insights"
internal const val MUSCLE_QUICK_INSIGHTS_OPEN = "muscle-quick-insights-open"
internal const val MUSCLE_QUICK_INSIGHTS_CLOSE = "muscle-quick-insights-close"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleQuickInsightsSheet(
    entry: MuscleHeatmapEntry,
    insight: MuscleQuickInsight?,
    onDismiss: () -> Unit,
    onOpenInsights: (MuscleGroup) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(MUSCLE_QUICK_INSIGHTS)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.headerStackGap)
            ) {
                Spacer(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MuscleMapColors.recencyFill(entry.band))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(entry.group.labelRes()),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = recencyLabel(entry),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(AppDimens.minTouch)
                        .testTag(MUSCLE_QUICK_INSIGHTS_CLOSE)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.muscle_insights_close)
                    )
                }
            }
            Spacer(Modifier.height(AppDimens.sectionGap))
            QuickMetric(
                label = stringResource(R.string.muscle_insights_last_stimulus),
                value = stimulusText(insight?.lastStimulus ?: app.mymusclemap.domain.muscleinsights.StimulusAge.None)
            )
            QuickMetric(
                label = stringResource(R.string.muscle_insights_sets_7d),
                value = pluralStringResource(
                    R.plurals.statistics_muscle_sets,
                    insight?.recentPrimarySets ?: 0,
                    insight?.recentPrimarySets ?: 0
                )
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Text(
                text = stringResource(R.string.muscle_insights_recovery_short),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.sectionGap))
            Button(
                onClick = { onOpenInsights(entry.group) },
                shape = AppShapeTokens.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(MUSCLE_QUICK_INSIGHTS_OPEN)
            ) {
                Text(stringResource(R.string.muscle_insights_view))
            }
        }
    }
}

@Composable
private fun QuickMetric(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
