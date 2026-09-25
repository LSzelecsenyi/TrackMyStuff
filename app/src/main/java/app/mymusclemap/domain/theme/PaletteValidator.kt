package app.mymusclemap.domain.theme

enum class PaletteValidationError {
    InvalidHex,
    BackgroundTextContrast,
    SurfaceTextContrast,
    PrimaryTextContrast,
    SecondaryTextContrast,
    TertiaryTextContrast,
    PrimaryAgainstBackground
}

sealed class PaletteValidationResult {
    data class Valid(val seeds: ThemeSeeds, val scheme: DerivedColorScheme) : PaletteValidationResult()
    data class Invalid(val error: PaletteValidationError) : PaletteValidationResult()
}

object PaletteValidator {
    const val TEXT_CONTRAST = 4.5
    const val UI_CONTRAST = 3.0

    fun validate(seeds: ThemeSeeds, isDark: Boolean): PaletteValidationResult {
        val scheme = ColorSchemeFactory.derive(seeds, isDark)
        return when {
            ColorScience.contrastRatio(scheme.background, scheme.onBackground) < TEXT_CONTRAST ->
                PaletteValidationResult.Invalid(PaletteValidationError.BackgroundTextContrast)
            ColorScience.contrastRatio(scheme.surface, scheme.onSurface) < TEXT_CONTRAST ->
                PaletteValidationResult.Invalid(PaletteValidationError.SurfaceTextContrast)
            ColorScience.contrastRatio(scheme.primary, scheme.onPrimary) < TEXT_CONTRAST ->
                PaletteValidationResult.Invalid(PaletteValidationError.PrimaryTextContrast)
            ColorScience.contrastRatio(scheme.secondary, scheme.onSecondary) < TEXT_CONTRAST ->
                PaletteValidationResult.Invalid(PaletteValidationError.SecondaryTextContrast)
            ColorScience.contrastRatio(scheme.tertiary, scheme.onTertiary) < TEXT_CONTRAST ->
                PaletteValidationResult.Invalid(PaletteValidationError.TertiaryTextContrast)
            ColorScience.contrastRatio(scheme.primary, scheme.background) < UI_CONTRAST ->
                PaletteValidationResult.Invalid(PaletteValidationError.PrimaryAgainstBackground)
            else -> PaletteValidationResult.Valid(seeds, scheme)
        }
    }

    fun parseSeeds(
        background: String,
        primary: String,
        secondary: String,
        tertiary: String
    ): ThemeSeeds? {
        val bg = HexColor.parse(background) as? HexParseResult.Valid ?: return null
        val p = HexColor.parse(primary) as? HexParseResult.Valid ?: return null
        val s = HexColor.parse(secondary) as? HexParseResult.Valid ?: return null
        val t = HexColor.parse(tertiary) as? HexParseResult.Valid ?: return null
        return ThemeSeeds(bg.rgb, p.rgb, s.rgb, t.rgb)
    }
}
