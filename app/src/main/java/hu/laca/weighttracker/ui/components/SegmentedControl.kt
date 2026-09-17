package hu.laca.weighttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppShapeTokens
import hu.laca.weighttracker.ui.theme.AppTypeTokens

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    optionTestTags: List<String> = emptyList()
) {
    val shape = if (compact) AppShapeTokens.compact else RoundedCornerShape(16.dp)
    val rowModifier = if (compact) {
        modifier
            .clip(shape)
            .border(AppDimens.strokeThin, MaterialTheme.colorScheme.outlineVariant, shape)
            .selectableGroup()
            .padding(2.dp)
    } else {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .selectableGroup()
            .padding(4.dp)
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val itemModifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = AppDimens.minTouch, minWidth = AppDimens.minTouch)
                .then(
                    if (index < optionTestTags.size) {
                        Modifier.testTag(optionTestTags[index])
                    } else {
                        Modifier
                    }
                )
            Box(
                modifier = itemModifier
                    .clip(if (compact) AppShapeTokens.compact else RoundedCornerShape(12.dp))
                    .background(
                        when {
                            compact && selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            compact -> Color.Transparent
                            selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
                    )
                    .clickable { onSelected(index) }
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
                    .padding(horizontal = if (compact) 8.dp else 8.dp, vertical = if (compact) 6.dp else 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = if (compact) AppTypeTokens.statCaption else MaterialTheme.typography.labelLarge,
                    color = when {
                        compact && selected -> MaterialTheme.colorScheme.primary
                        compact -> MaterialTheme.colorScheme.onSurfaceVariant
                        selected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
