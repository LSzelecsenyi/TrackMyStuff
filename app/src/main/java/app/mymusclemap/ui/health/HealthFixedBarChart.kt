package app.mymusclemap.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.health.HealthTrendSlot

@Composable
internal fun HealthFixedBarChart(
    slots: List<HealthTrendSlot>,
    tagPrefix: String,
    descriptionFor: (HealthTrendSlot) -> String?,
    modifier: Modifier = Modifier
) {
    val maxAmount = slots.mapNotNull { it.amount }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .testTag(tagPrefix),
        content = {
            slots.forEachIndexed { index, slot ->
                val amount = slot.amount
                val description = if (amount == null) null else descriptionFor(slot)
                Box(
                    modifier = Modifier
                        .testTag(tagPrefix + index)
                        .then(
                            if (description != null) {
                                Modifier.semantics { contentDescription = description }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (amount != null) {
                        val fraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f)
                        val barHeight = if (amount == 0.0) 2.dp else (32.dp * fraction).coerceAtLeast(2.dp)
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val count = measurables.size.coerceAtLeast(1)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val base = width / count
        val remainder = width - base * count
        val placeables = measurables.mapIndexed { index, measurable ->
            val slotWidth = base + if (index == count - 1) remainder else 0
            measurable.measure(Constraints.fixed(slotWidth, height))
        }
        layout(width, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width
            }
        }
    }
}
