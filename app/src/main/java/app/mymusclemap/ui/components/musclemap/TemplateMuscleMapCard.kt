package app.mymusclemap.ui.components.musclemap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.musclemap.TemplateMuscleMapState
import app.mymusclemap.ui.components.HeroSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateMuscleMapCard(
    state: TemplateMuscleMapState,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val fills = remember(state) { MuscleMapColors.templateFills(state) }
    HeroSurface(modifier = modifier) {
        Text(
            text = stringResource(R.string.template_map_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.template_map_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        MuscleMap(
            fills = fills,
            unmappedFill = MuscleMapColors.unmappedFill(scheme),
            outline = MuscleMapColors.outline(scheme),
            mappedOutline = MuscleMapColors.mappedOutline(scheme),
            selectedOutline = MuscleMapColors.selectedOutline(scheme),
            selected = null,
            onSelect = {},
            contentDescription = stringResource(R.string.template_map_title),
            interactive = false
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegendSwatch(
                color = MuscleMapColors.TemplatePrimary,
                label = stringResource(R.string.template_map_primary)
            )
            LegendSwatch(
                color = MuscleMapColors.TemplateSecondary,
                label = stringResource(R.string.template_map_secondary)
            )
        }
        if (state.hasFullBody) {
            Text(
                text = stringResource(R.string.template_map_full_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (state.hasCardiovascular) {
            Text(
                text = stringResource(R.string.template_map_cardio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun LegendSwatch(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
