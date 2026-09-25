package app.mymusclemap.domain.theme

import kotlin.math.abs
import kotlin.math.roundToInt

object ColorScience {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    fun red(rgb: Int): Int = rgb shr 16 and 0xFF
    fun green(rgb: Int): Int = rgb shr 8 and 0xFF
    fun blue(rgb: Int): Int = rgb and 0xFF

    fun rgb(red: Int, green: Int, blue: Int): Int {
        return 0xFF000000.toInt() or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }

    fun opaque(rgb: Int): Int = rgb(red(rgb), green(rgb), blue(rgb))

    fun alpha(rgb: Int): Int = rgb ushr 24 and 0xFF

    fun relativeLuminance(rgb: Int): Double {
        fun linear(channel: Int): Double {
            val srgb = channel / 255.0
            return if (srgb <= 0.03928) {
                srgb / 12.92
            } else {
                Math.pow((srgb + 0.055) / 1.055, 2.4)
            }
        }
        return 0.2126 * linear(red(rgb)) +
            0.7152 * linear(green(rgb)) +
            0.0722 * linear(blue(rgb))
    }

    fun contrastRatio(first: Int, second: Int): Double {
        val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
        val darker = minOf(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun contrastingForeground(background: Int): Int {
        val whiteContrast = contrastRatio(background, WHITE)
        val blackContrast = contrastRatio(background, BLACK)
        return if (whiteContrast >= blackContrast) WHITE else BLACK
    }

    fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val inverse = 1f - t
        return rgb(
            red = (red(from) * inverse + red(to) * t).toInt(),
            green = (green(from) * inverse + green(to) * t).toInt(),
            blue = (blue(from) * inverse + blue(to) * t).toInt()
        )
    }

    fun mutedForeground(background: Int, foreground: Int, minContrast: Double = 4.5): Int {
        var low = 0f
        var high = 0.55f
        var best = foreground
        repeat(14) {
            val mid = (low + high) / 2f
            val candidate = blend(foreground, background, mid)
            if (contrastRatio(candidate, background) >= minContrast) {
                best = candidate
                low = mid
            } else {
                high = mid
            }
        }
        return if (contrastRatio(best, background) >= minContrast) best else foreground
    }

    data class Hsl(
        val hue: Float,
        val saturation: Float,
        val lightness: Float
    )

    fun toHsl(rgb: Int): Hsl {
        val r = red(rgb) / 255f
        val g = green(rgb) / 255f
        val b = blue(rgb) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val lightness = (max + min) / 2f
        val saturation = when {
            delta == 0f -> 0f
            lightness < 0.5f -> delta / (max + min)
            else -> delta / (2f - max - min)
        }
        val hue = when {
            delta == 0f -> 0f
            max == r -> ((g - b) / delta).let { if (it < 0f) it + 6f else it } * 60f
            max == g -> ((b - r) / delta + 2f) * 60f
            else -> ((r - g) / delta + 4f) * 60f
        }
        return Hsl(hue = hue, saturation = saturation.coerceIn(0f, 1f), lightness = lightness.coerceIn(0f, 1f))
    }

    fun fromHsl(hsl: Hsl): Int {
        val hue = ((hsl.hue % 360f) + 360f) % 360f
        val saturation = hsl.saturation.coerceIn(0f, 1f)
        val lightness = hsl.lightness.coerceIn(0f, 1f)
        val c = (1f - abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = lightness - c / 2f
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return rgb(
            red = ((r1 + m) * 255f).roundToInt(),
            green = ((g1 + m) * 255f).roundToInt(),
            blue = ((b1 + m) * 255f).roundToInt()
        )
    }

    data class Hsv(
        val hue: Float,
        val saturation: Float,
        val value: Float
    ) {
        fun normalized(): Hsv {
            val wrappedHue = ((hue % 360f) + 360f) % 360f
            return Hsv(
                hue = wrappedHue,
                saturation = saturation.coerceIn(0f, 1f),
                value = value.coerceIn(0f, 1f)
            )
        }
    }

    fun toHsv(rgb: Int): Hsv {
        val r = red(rgb) / 255f
        val g = green(rgb) / 255f
        val b = blue(rgb) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> ((g - b) / delta).let { if (it < 0f) it + 6f else it } * 60f
            max == g -> ((b - r) / delta + 2f) * 60f
            else -> ((r - g) / delta + 4f) * 60f
        }
        val saturation = if (max == 0f) 0f else delta / max
        return Hsv(
            hue = hue,
            saturation = saturation.coerceIn(0f, 1f),
            value = max.coerceIn(0f, 1f)
        )
    }

    fun fromHsv(hsv: Hsv): Int {
        val normalized = hsv.normalized()
        val hue = normalized.hue
        val saturation = normalized.saturation
        val value = normalized.value
        val c = value * saturation
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = value - c
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return rgb(
            red = ((r1 + m) * 255f).roundToInt(),
            green = ((g1 + m) * 255f).roundToInt(),
            blue = ((b1 + m) * 255f).roundToInt()
        )
    }
}
