package app.mymusclemap.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import app.mymusclemap.domain.musclemap.MuscleHeatmapState
import app.mymusclemap.ui.components.musclemap.MuscleMapAnatomy
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import app.mymusclemap.ui.components.musclemap.MuscleMapFigurePainter
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapArtworkSelector
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
    const val EDGE_DARK_WIDTH = 0.34f * 526f / 35f
    const val EDGE_DARK_COLOR = 0x73000000
    /** Wider light rim underneath it, so the silhouette still reads on a dark wallpaper. */
    const val EDGE_LIGHT_WIDTH = 0.85f * 526f / 35f
    val EDGE_LIGHT_COLOR = 0xB3FFFFFF.toInt()

    /**
     * Keeps one figure under the RemoteViews binder budget. A 400 px-wide body is
     * about 1,060 px tall, which is too large to ship twice in one widget update.
     */
    const val MAX_FIGURE_BYTES = 350_000

    fun render(
        context: Context,
        state: MuscleHeatmapState,
        widthPx: Int,
        heightPx: Int,
        chrome: MuscleMapBitmapChrome
    ): MuscleMapBitmaps {
        val (width, height) = pixelSize(widthPx, heightPx)
        val parsed = parseMuscleRegions()
        val fills = MuscleMapColors.heatmapFills(state)
        val anatomy = MuscleMapAnatomy.load(context)
        val artwork = MuscleMapArtworkSelector.current()
        return MuscleMapBitmaps(
            front = drawFigure(
                anatomy = anatomy.front,
                lighting = anatomy.frontLighting,
                regions = paintOrderedRegions(parsed.filter { it.region.view == MuscleMapView.FRONT }),
                fills = fills,
                widthPx = width,
                heightPx = height,
                chrome = chrome,
                viewWidth = artwork.viewWidth,
                viewHeight = artwork.viewHeight
            ),
            back = drawFigure(
                anatomy = anatomy.back,
                lighting = anatomy.backLighting,
                regions = paintOrderedRegions(parsed.filter { it.region.view == MuscleMapView.BACK }),
                fills = fills,
                widthPx = width,
                heightPx = height,
                chrome = chrome,
                viewWidth = artwork.viewWidth,
                viewHeight = artwork.viewHeight
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
        anatomy: Bitmap,
        lighting: Bitmap,
        regions: List<app.mymusclemap.ui.components.musclemap.ParsedMuscleRegion>,
        fills: Map<app.mymusclemap.domain.exercise.MuscleGroup, Color>,
        widthPx: Int,
        heightPx: Int,
        chrome: MuscleMapBitmapChrome,
        viewWidth: Float,
        viewHeight: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val layout = figureLayout(widthPx.toFloat(), heightPx.toFloat())
        canvas.save()
        canvas.translate(layout.offsetX, layout.offsetY)
        canvas.scale(layout.scale, layout.scale)
        MuscleMapFigurePainter.draw(
            canvas = canvas,
            anatomy = anatomy,
            lighting = lighting,
            regions = regions,
            fills = fills,
            unmappedFill = chrome.unmappedFill,
            outline = chrome.outline,
            mappedOutline = chrome.mappedOutline,
            selectedOutline = chrome.mappedOutline,
            selected = null,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            edgeDarkWidth = EDGE_DARK_WIDTH,
            edgeDarkColor = EDGE_DARK_COLOR,
            edgeLightWidth = EDGE_LIGHT_WIDTH,
            edgeLightColor = EDGE_LIGHT_COLOR
        )
        canvas.restore()
        return bitmap
    }
}
