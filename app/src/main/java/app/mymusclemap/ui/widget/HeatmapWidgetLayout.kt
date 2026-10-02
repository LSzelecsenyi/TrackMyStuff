package app.mymusclemap.ui.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesArtwork
import kotlin.math.min

internal data class HeatmapWidgetLayout(
    val padding: Dp,
    val figureWidth: Dp,
    val figureHeight: Dp,
    val gap: Dp
) {
    val pairWidth: Dp = figureWidth * 2 + gap
}

internal fun heatmapWidgetLayout(width: Dp, height: Dp): HeatmapWidgetLayout {
    val padding = HEATMAP_WIDGET_PADDING
    val gap = HEATMAP_WIDGET_GAP
    val availableWidth = (bounded(width) - padding * 2).coerceAtLeast(0.dp)
    val availableHeight = (bounded(height) - padding * 2).coerceAtLeast(0.dp)
    val aspect = BodyMusclesArtwork.VIEW_HEIGHT / BodyMusclesArtwork.VIEW_WIDTH
    val widthLimited = ((availableWidth - gap) / 2f).coerceAtLeast(0.dp)
    val heightLimited = (availableHeight / aspect).coerceAtLeast(0.dp)
    val figureWidth = min(widthLimited.value, heightLimited.value).dp
    return HeatmapWidgetLayout(
        padding = padding,
        figureWidth = figureWidth,
        figureHeight = figureWidth * aspect,
        gap = gap
    )
}

private fun bounded(size: Dp): Dp {
    return if (!size.isSpecified || !size.isFinite || size < 0.dp) 0.dp else size
}

/** Smallest inset that keeps heads and feet off the launcher clip. */
internal val HEATMAP_WIDGET_PADDING = 4.dp

/** Compact gap so the pair reads as one heatmap without the figures touching. */
internal val HEATMAP_WIDGET_GAP = 4.dp
