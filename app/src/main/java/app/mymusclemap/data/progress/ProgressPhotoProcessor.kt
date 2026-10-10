package app.mymusclemap.data.progress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import kotlin.math.max

data class ProcessedProgressPhoto(
    val capturedOn: LocalDate?
)

object ProgressPhotoProcessor {
    const val MAX_EDGE = 1600
    const val JPEG_QUALITY = 85

    fun process(input: InputStream, output: File, maxEdge: Int = MAX_EDGE): ProcessedProgressPhoto? {
        val part = File(output.parentFile, output.name + ".part")
        part.parentFile?.mkdirs()
        try {
            part.outputStream().use { destination -> input.copyTo(destination) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(part.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val decoded = BitmapFactory.decodeFile(
                part.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: return null
            val capturedOn = exifDate(part)
            val orientation = runCatching {
                ExifInterface(part.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val oriented = applyOrientation(decoded, orientation)
            val scaled = scaleToEdge(oriented, maxEdge)
            val written = output.outputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            }
            if (scaled !== oriented) scaled.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            if (!written || !output.isFile || output.length() == 0L) {
                output.delete()
                return null
            }
            return ProcessedProgressPhoto(capturedOn)
        } catch (_: Exception) {
            output.delete()
            return null
        } finally {
            part.delete()
        }
    }

    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var sampledWidth = width
        var sampledHeight = height
        while (max(sampledWidth, sampledHeight) / 2 >= maxEdge && sample < 64) {
            sampledWidth /= 2
            sampledHeight /= 2
            sample *= 2
        }
        return sample
    }

    fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun scaleToEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxEdge) return source
        val scale = maxEdge.toFloat() / longest.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    fun parseExifDate(raw: String?): LocalDate? {
        if (raw == null || raw.length < 10) return null
        return runCatching { LocalDate.parse(raw.take(10).replace(':', '-')) }.getOrNull()
    }

    private fun exifDate(file: File): LocalDate? {
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return null
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        return parseExifDate(raw)
    }
}
