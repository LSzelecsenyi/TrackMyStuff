package app.mymusclemap.ui.components.musclemap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import app.mymusclemap.domain.exercise.MuscleGroup
import kotlin.math.roundToInt

/**
 * Recency color is multiplied by a lifted copy of the anatomical shading.
 * Highlights stay close to the palette color. Creases stay darker, so the
 * polygonal texture remains visible instead of a flat fill.
 */
object MuscleMapCompositing {
    const val SHADE_WEIGHT = 0.62f
    const val SHADE_LIFT = 0.38f

    fun shadeFactor(red: Int, green: Int, blue: Int): Float {
        val luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f
        return (luminance * SHADE_WEIGHT + SHADE_LIFT).coerceIn(0f, 1f)
    }

    fun lightingBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = Color.alpha(color)
            if (alpha == 0) {
                pixels[index] = Color.TRANSPARENT
                continue
            }
            val gray = (shadeFactor(Color.red(color), Color.green(color), Color.blue(color)) * 255f)
                .roundToInt()
                .coerceIn(0, 255)
            pixels[index] = Color.argb(alpha, gray, gray, gray)
        }
        val lighting = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        lighting.setPixels(pixels, 0, width, 0, 0, width, height)
        return lighting
    }
}

data class MuscleMapAnatomyBitmaps(
    val front: Bitmap,
    val back: Bitmap,
    val frontLighting: Bitmap,
    val backLighting: Bitmap
)

object MuscleMapFigurePainter {
    fun draw(
        canvas: Canvas,
        anatomy: Bitmap,
        lighting: Bitmap,
        regions: List<ParsedMuscleRegion>,
        fills: Map<MuscleGroup, androidx.compose.ui.graphics.Color>,
        unmappedFill: androidx.compose.ui.graphics.Color,
        outline: androidx.compose.ui.graphics.Color,
        mappedOutline: androidx.compose.ui.graphics.Color,
        selectedOutline: androidx.compose.ui.graphics.Color,
        selected: MuscleGroup?,
        viewWidth: Float,
        viewHeight: Float,
        edgeDarkWidth: Float = 0f,
        edgeDarkColor: Int = 0,
        edgeLightWidth: Float = 0f,
        edgeLightColor: Int = 0
    ) {
        val destination = RectF(0f, 0f, viewWidth, viewHeight)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(anatomy, null, destination, bitmapPaint)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val multiplyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val darkEdge = if (edgeDarkWidth > 0f) edgePaint(edgeDarkWidth, edgeDarkColor) else null
        val lightEdge = if (edgeLightWidth > 0f) edgePaint(edgeLightWidth, edgeLightColor) else null
        regions.forEach { parsed ->
            val group = parsed.region.muscleGroup
            val fill = group?.let { fills[it] } ?: unmappedFill
            val path = parsed.path.asAndroidPath()
            val layer = canvas.saveLayer(destination, null)
            canvas.clipPath(path)
            fillPaint.color = fill.toArgb()
            canvas.drawRect(destination, fillPaint)
            canvas.drawBitmap(lighting, null, destination, multiplyPaint)
            canvas.restoreToCount(layer)
            val isSelected = group != null && group == selected
            strokePaint.color = when {
                isSelected -> selectedOutline.toArgb()
                group != null -> mappedOutline.toArgb()
                else -> outline.toArgb()
            }
            strokePaint.strokeWidth = if (isSelected) {
                MUSCLE_MAP_SELECTED_STROKE_WIDTH
            } else {
                MUSCLE_MAP_STROKE_WIDTH
            }
            canvas.drawPath(path, strokePaint)
            if (lightEdge != null && darkEdge != null) {
                canvas.drawPath(path, lightEdge)
                canvas.drawPath(path, darkEdge)
            }
        }
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
