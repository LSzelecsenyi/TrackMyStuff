package app.mymusclemap.ui.widget

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapState
import app.mymusclemap.ui.components.musclemap.MUSCLE_MAP_STROKE_WIDTH
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import app.mymusclemap.ui.components.musclemap.ParsedMuscleRegion
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView
import app.mymusclemap.ui.components.musclemap.figureLayout
import app.mymusclemap.ui.components.musclemap.paintOrderedRegions
import app.mymusclemap.ui.components.musclemap.parseMuscleRegions
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MuscleMapBitmapChrome(
    val unmappedFill: Color,
    val outline: Color,
    val mappedOutline: Color
)

data class MuscleMapBitmaps(
    val front: Bitmap,
    val back: Bitmap
)

object MuscleMapBitmapRenderer {
    const val MAX_FIGURE_WIDTH_PX = 400
    /** Narrow dark rim so a pale untrained body still reads on a light wallpaper. */
    const val EDGE_DARK_WIDTH = 0.34f
    const val EDGE_DARK_COLOR = 0x73000000
    /** Wider light rim underneath it, so the silhouette still reads on a dark wallpaper. */
    const val EDGE_LIGHT_WIDTH = 0.85f
    val EDGE_LIGHT_COLOR = 0xB3FFFFFF.toInt()

    /**
     * Keeps one figure under the RemoteViews binder budget. A 400 px-wide body is
     * about 1,060 px tall, which is too large to ship twice in one widget update.
     */
    const val MAX_FIGURE_BYTES = 350_000

    fun render(
        state: MuscleHeatmapState,
        widthPx: Int,
        heightPx: Int,
        chrome: MuscleMapBitmapChrome
    ): MuscleMapBitmaps {
        val (width, height) = pixelSize(widthPx, heightPx)
        val parsed = parseMuscleRegions()
        val fills = MuscleMapColors.heatmapFills(state)
        return MuscleMapBitmaps(
            front = drawFigure(
                regions = paintOrderedRegions(parsed.filter { it.region.view == MuscleMapView.FRONT }),
                fills = fills,
                widthPx = width,
                heightPx = height,
                chrome = chrome
            ),
            back = drawFigure(
                regions = paintOrderedRegions(parsed.filter { it.region.view == MuscleMapView.BACK }),
                fills = fills,
                widthPx = width,
                heightPx = height,
                chrome = chrome
            )
        )
    }

    fun pixelSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        var width = widthPx.coerceAtLeast(1)
        var height = heightPx.coerceAtLeast(1)
        if (width > MAX_FIGURE_WIDTH_PX) {
            val scale = MAX_FIGURE_WIDTH_PX.toFloat() / width
            width = MAX_FIGURE_WIDTH_PX
            height = (height * scale).roundToInt().coerceAtLeast(1)
        }
        val bytes = width.toLong() * height * 4L
        if (bytes > MAX_FIGURE_BYTES) {
            val scale = sqrt(MAX_FIGURE_BYTES.toFloat() / bytes.toFloat())
            width = (width * scale).roundToInt().coerceAtLeast(1)
            height = (height * scale).roundToInt().coerceAtLeast(1)
        }
        return width to height
    }

    private fun drawFigure(
        regions: List<ParsedMuscleRegion>,
        fills: Map<MuscleGroup, Color>,
        widthPx: Int,
        heightPx: Int,
        chrome: MuscleMapBitmapChrome
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val layout = figureLayout(widthPx.toFloat(), heightPx.toFloat())
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val themeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = MUSCLE_MAP_STROKE_WIDTH
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val darkEdge = edgePaint(EDGE_DARK_WIDTH, EDGE_DARK_COLOR)
        val lightEdge = edgePaint(EDGE_LIGHT_WIDTH, EDGE_LIGHT_COLOR)
        canvas.save()
        canvas.translate(layout.offsetX, layout.offsetY)
        canvas.scale(layout.scale, layout.scale)
        regions.forEach { parsed ->
            val fill = parsed.region.muscleGroup?.let { fills[it] } ?: chrome.unmappedFill
            fillPaint.color = fill.toArgb()
            canvas.drawPath(parsed.path.asAndroidPath(), fillPaint)
        }
        regions.forEach { parsed ->
            val path = parsed.path.asAndroidPath()
            themeStroke.color = if (parsed.region.muscleGroup != null) {
                chrome.mappedOutline.toArgb()
            } else {
                chrome.outline.toArgb()
            }
            canvas.drawPath(path, themeStroke)
            canvas.drawPath(path, lightEdge)
            canvas.drawPath(path, darkEdge)
        }
        canvas.restore()
        return bitmap
    }

    private fun edgePaint(width: Float, color: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            this.color = color
        }
    }
}
