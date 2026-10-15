package app.mymusclemap.ui.components.musclemap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapArtworkSelector
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapFigureArtwork

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
        val split = artwork.splitX.toInt()
        val front = crop(full, 0, split)
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

    private fun crop(source: Bitmap, x: Int, width: Int): Bitmap {
        val out = Bitmap.createBitmap(width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, -x.toFloat(), 0f, null)
        return out
    }
}
