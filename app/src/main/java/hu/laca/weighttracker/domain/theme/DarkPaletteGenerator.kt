package hu.laca.weighttracker.domain.theme

object DarkPaletteGenerator {
    fun fromLight(light: ThemeSeeds): ThemeSeeds {
        val primary = ColorScience.toHsl(light.primary)
        val secondary = ColorScience.toHsl(light.secondary)
        val tertiary = ColorScience.toHsl(light.tertiary)
        val backgroundHue = ColorScience.toHsl(light.background).hue.let { hue ->
            if (ColorScience.toHsl(light.background).saturation < 0.05f) primary.hue else hue
        }
        val dark = ThemeSeeds(
            background = ColorScience.fromHsl(
                ColorScience.Hsl(
                    hue = backgroundHue,
                    saturation = 0.22f,
                    lightness = 0.08f
                )
            ),
            primary = ColorScience.fromHsl(
                ColorScience.Hsl(
                    hue = primary.hue,
                    saturation = primary.saturation.coerceIn(0.42f, 0.78f),
                    lightness = 0.72f
                )
            ),
            secondary = ColorScience.fromHsl(
                ColorScience.Hsl(
                    hue = if (secondary.saturation < 0.08f) primary.hue else secondary.hue,
                    saturation = secondary.saturation.coerceIn(0.18f, 0.42f),
                    lightness = 0.18f
                )
            ),
            tertiary = ColorScience.fromHsl(
                ColorScience.Hsl(
                    hue = tertiary.hue,
                    saturation = tertiary.saturation.coerceIn(0.40f, 0.82f),
                    lightness = 0.70f
                )
            )
        )
        return ensureUsable(dark, isDark = true)
    }

    private fun ensureUsable(seeds: ThemeSeeds, isDark: Boolean): ThemeSeeds {
        var current = seeds
        var lightnessDelta = 0f
        repeat(8) {
            if (PaletteValidator.validate(current, isDark) is PaletteValidationResult.Valid) {
                return current
            }
            lightnessDelta += 0.03f
            val primary = ColorScience.toHsl(current.primary)
            current = current.copy(
                primary = ColorScience.fromHsl(primary.copy(lightness = (primary.lightness + lightnessDelta).coerceAtMost(0.86f)))
            )
        }
        return ThemeSeeds.DefaultDark
    }
}
