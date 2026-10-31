package app.mymusclemap.ui.components.musclemap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapArtworkSelector
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapFigureArtwork
import kotlin.math.roundToInt

object MuscleMapAnatomy {
    @Volatile
    private var cachedAsset: String? = null

    @Volatile
    private var cached: MuscleMapAnatomyBitmaps? = null

    fun load(context: Context, artwork: MuscleMapFigureArtwork = MuscleMapArtworkSelector.current()): MuscleMapAnatomyBitmaps {
        val existing = cached
        if (existing != null && cachedAsset == artwork.anatomyAsset) {
            return existing
        }
        synchronized(this) {
            val current = cached
            if (current != null && cachedAsset == artwork.anatomyAsset) {
                return current
            }
            val loaded = decode(context.applicationContext, artwork)
            cachedAsset = artwork.anatomyAsset
            cached = loaded
            return loaded
        }
    }

    private fun decode(context: Context, artwork: MuscleMapFigureArtwork): MuscleMapAnatomyBitmaps {
        val full = context.assets.open(artwork.anatomyAsset).use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: error("Muscle map anatomy is missing: ${artwork.anatomyAsset}")
        val split = artwork.splitX
        val front = crop(full, 0f, split)
        val back = crop(full, split, full.width - split)
        if (full !== front && full !== back) {
            full.recycle()
        }
        return MuscleMapAnatomyBitmaps(
            front = front,
            back = back,
            frontLighting = MuscleMapCompositing.lightingBitmap(front),
            backLighting = MuscleMapCompositing.lightingBitmap(back)
        )
    }

    private fun crop(source: Bitmap, x: Float, width: Float): Bitmap {
        if (x == x.toInt().toFloat() && width == width.toInt().toFloat()) {
            val pixelsWide = width.toInt()
            val out = Bitmap.createBitmap(pixelsWide, source.height, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(source, -x, 0f, null)
            return out
        }
        val pixelsWide = width.roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(pixelsWide, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.scale(pixelsWide / width, 1f)
        canvas.drawBitmap(source, -x, 0f, null)
        return out
    }
}
