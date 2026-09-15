package hu.laca.weighttracker.domain.theme

data class ColorPickerState(
    val originalRgb: Int,
    val rgb: Int,
    val hsv: ColorScience.Hsv,
    val hexDraft: String,
    val hexValid: Boolean
) {
    val canConfirm: Boolean get() = hexValid
    val previewRgb: Int get() = rgb
}

object ColorPickerLogic {
    val quickSwatches: List<Int> = listOf(
        0xFF2196F3.toInt(),
        0xFF00BCD4.toInt(),
        0xFF43A047.toInt(),
        0xFFFF9800.toInt(),
        0xFFE8754F.toInt(),
        0xFF8A2BE2.toInt(),
        0xFF9E9E9E.toInt()
    )

    fun open(originalRgb: Int): ColorPickerState {
        val rgb = ColorScience.opaque(originalRgb)
        return ColorPickerState(
            originalRgb = rgb,
            rgb = rgb,
            hsv = ColorScience.toHsv(rgb),
            hexDraft = HexColor.format(rgb),
            hexValid = true
        )
    }

    fun applyHsv(state: ColorPickerState, hsv: ColorScience.Hsv): ColorPickerState {
        val displayHue = wrapHueForDisplay(hsv.hue)
        val normalized = hsv.copy(hue = displayHue).normalized()
        val rgb = ColorScience.fromHsv(hsv.copy(hue = displayHue))
        return state.copy(
            rgb = rgb,
            hsv = ColorScience.Hsv(
                hue = displayHue,
                saturation = normalized.saturation,
                value = normalized.value
            ),
            hexDraft = HexColor.format(rgb),
            hexValid = true
        )
    }

    fun applySaturationValue(
        state: ColorPickerState,
        saturation: Float,
        value: Float
    ): ColorPickerState {
        return applyHsv(
            state,
            state.hsv.copy(
                saturation = saturation.coerceIn(0f, 1f),
                value = value.coerceIn(0f, 1f)
            )
        )
    }

    fun applyHue(state: ColorPickerState, hue: Float): ColorPickerState {
        return applyHsv(state, state.hsv.copy(hue = wrapHueForDisplay(hue)))
    }

    fun applyHex(state: ColorPickerState, raw: String): ColorPickerState {
        return when (val parsed = HexColor.parse(raw)) {
            is HexParseResult.Valid -> {
                val rgb = ColorScience.opaque(parsed.rgb)
                state.copy(
                    rgb = rgb,
                    hsv = ColorScience.toHsv(rgb),
                    hexDraft = parsed.canonical,
                    hexValid = true
                )
            }
            HexParseResult.Invalid -> state.copy(
                hexDraft = raw,
                hexValid = false
            )
        }
    }

    fun applySwatch(state: ColorPickerState, rgb: Int): ColorPickerState {
        val opaque = ColorScience.opaque(rgb)
        return state.copy(
            rgb = opaque,
            hsv = ColorScience.toHsv(opaque),
            hexDraft = HexColor.format(opaque),
            hexValid = true
        )
    }

    fun svFromPointer(x: Float, y: Float, width: Float, height: Float): Pair<Float, Float> {
        val boundedWidth = width.coerceAtLeast(1f)
        val boundedHeight = height.coerceAtLeast(1f)
        val saturation = (x / boundedWidth).coerceIn(0f, 1f)
        val value = 1f - (y / boundedHeight).coerceIn(0f, 1f)
        return saturation to value
    }

    fun hueFromPointer(x: Float, width: Float): Float {
        val boundedWidth = width.coerceAtLeast(1f)
        return (x / boundedWidth).coerceIn(0f, 1f) * 360f
    }

    fun hueThumbFraction(hue: Float): Float {
        return (wrapHueForDisplay(hue) / 360f).coerceIn(0f, 1f)
    }

    fun cancel(state: ColorPickerState): Int = state.originalRgb

    fun confirm(state: ColorPickerState): Int? {
        return if (state.hexValid) state.rgb else null
    }

    fun wrapHueForDisplay(hue: Float): Float {
        if (!hue.isFinite()) return 0f
        if (hue == 360f) return 360f
        return ((hue % 360f) + 360f) % 360f
    }
}
