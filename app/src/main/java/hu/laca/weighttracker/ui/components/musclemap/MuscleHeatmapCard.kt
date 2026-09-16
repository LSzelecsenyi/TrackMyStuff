package hu.laca.weighttracker.ui.components.musclemap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.musclemap.MuscleHeatmapEntry
import hu.laca.weighttracker.domain.musclemap.MuscleHeatmapState
import hu.laca.weighttracker.domain.musclemap.MuscleRecencyBand
import hu.laca.weighttracker.ui.components.HeroSurface
import hu.laca.weighttracker.ui.exercises.labelRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleHeatmapCard(
    state: MuscleHeatmapState,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val fills = remember(state) { MuscleMapColors.heatmapFills(state) }
    var selected by remember { mutableStateOf<MuscleGroup?>(null) }
    val selectedEntry = selected?.let { state.entries[it] }
    HeroSurface(modifier = modifier) {
        Text(
            text = stringResource(R.string.heatmap_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.heatmap_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (!state.hasCompletedWorkouts) {
            Text(
                text = stringResource(R.string.heatmap_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        MuscleMap(
            fills = fills,
            unmappedFill = MuscleMapColors.unmappedFill(scheme),
            outline = MuscleMapColors.outline(scheme),
            mappedOutline = MuscleMapColors.mappedOutline(scheme),
            selectedOutline = MuscleMapColors.selectedOutline(scheme),
            selected = selected,
            onSelect = { selected = it },
            contentDescription = heatmapContentDescription(state),
            accessibilityActions = heatmapActions(state)
        )
        selectedEntry?.let { entry ->
            Spacer(Modifier.height(12.dp))
            val selection = stringResource(
                R.string.heatmap_selection,
                stringResource(entry.group.labelRes()),
                recencyLabel(entry)
            )
            Text(
                text = selection,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { contentDescription = selection }
            )
        }
        Spacer(Modifier.height(12.dp))
        HeatmapRecencyLegend()
        state.fullBody?.let { entry ->
            Text(
                text = stringResource(R.string.heatmap_full_body_note, recencyLabel(entry)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        state.cardiovascular?.let { entry ->
            Text(
                text = stringResource(R.string.heatmap_cardio_note, recencyLabel(entry)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeatmapRecencyLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("heatmap_legend"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MuscleRecencyBand.entries.forEach { band ->
            RecencyLegendItem(band = band)
        }
    }
}

@Composable
private fun RecencyLegendItem(band: MuscleRecencyBand) {
    val label = stringResource(band.labelRes())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = label }
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MuscleMapColors.recencyFill(band))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
internal fun recencyLabel(entry: MuscleHeatmapEntry): String {
    return when (entry.band) {
        MuscleRecencyBand.TODAY -> stringResource(R.string.heatmap_band_today)
        MuscleRecencyBand.NEVER -> stringResource(R.string.heatmap_band_never)
        MuscleRecencyBand.DAYS_14_PLUS -> stringResource(R.string.heatmap_band_inactive)
        else -> stringResource(R.string.heatmap_days_ago, entry.daysAgo ?: 0)
    }
}

@Composable
private fun heatmapContentDescription(state: MuscleHeatmapState): String {
    if (!state.hasCompletedWorkouts) {
        return stringResource(R.string.heatmap_empty)
    }
    val trained = state.entries.values.filter { it.lastTrained != null }
    if (trained.isEmpty()) {
        return stringResource(R.string.heatmap_empty_trained)
    }
    val parts = ArrayList<String>(trained.size)
    for (entry in trained) {
        parts += stringResource(
            R.string.heatmap_selection,
            stringResource(entry.group.labelRes()),
            recencyLabel(entry)
        )
    }
    return parts.joinToString(separator = ". ")
}

@Composable
private fun heatmapActions(state: MuscleHeatmapState): List<Pair<String, MuscleGroup>> {
    val actions = ArrayList<Pair<String, MuscleGroup>>(state.entries.size)
    for (entry in state.entries.values) {
        val label = stringResource(
            R.string.heatmap_selection,
            stringResource(entry.group.labelRes()),
            recencyLabel(entry)
        )
        actions += label to entry.group
    }
    return actions
}

internal fun MuscleRecencyBand.labelRes(): Int {
    return when (this) {
        MuscleRecencyBand.TODAY -> R.string.heatmap_band_today
        MuscleRecencyBand.DAYS_1_2 -> R.string.heatmap_band_recent
        MuscleRecencyBand.DAYS_3_4 -> R.string.heatmap_band_days_3_4
        MuscleRecencyBand.DAYS_5_6 -> R.string.heatmap_band_days_5_6
        MuscleRecencyBand.DAYS_7_13 -> R.string.heatmap_band_old
        MuscleRecencyBand.DAYS_14_PLUS -> R.string.heatmap_band_inactive
        MuscleRecencyBand.NEVER -> R.string.heatmap_band_never
    }
}
