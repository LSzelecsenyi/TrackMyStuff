package app.mymusclemap.domain.theme

data class DerivedColorScheme(
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val outline: Int,
    val outlineVariant: Int,
    val surfaceTint: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int
)

object ColorSchemeFactory {
    private val lightError = 0xFFBA1A1A.toInt()
    private val lightOnError = ColorScience.WHITE
    private val lightErrorContainer = 0xFFFFDAD6.toInt()
    private val lightOnErrorContainer = 0xFF410002.toInt()
    private val darkError = 0xFFFFB4AB.toInt()
    private val darkOnError = 0xFF690005.toInt()
    private val darkErrorContainer = 0xFF93000A.toInt()
    private val darkOnErrorContainer = 0xFFFFDAD6.toInt()

    fun derive(seeds: ThemeSeeds, isDark: Boolean): DerivedColorScheme {
        val background = seeds.background
        val primary = seeds.primary
        val secondary = seeds.secondary
        val tertiary = seeds.tertiary
        val strictLight = !isDark && StrictBrandTokens.usesStrictPalette(background, primary)
        // Neutral surfaces follow background and secondary only. Mixing primary
        // into them paints dark navy with lime and the result reads as olive.
        // The default Strict light theme lifts those neutrals toward white so
        // cards separate from the cool canvas without becoming pale-blue blocks.
        val surface = if (strictLight) {
            ColorScience.WHITE
        } else {
            ColorScience.blend(background, secondary, if (isDark) 0.42f else 0.28f)
        }
        val surfaceContainer = if (strictLight) {
            ColorScience.blend(background, ColorScience.WHITE, 0.45f)
        } else {
            secondary
        }
        val surfaceContainerHigh = when {
            isDark -> ColorScience.blend(secondary, ColorScience.WHITE, 0.10f)
            strictLight -> ColorScience.blend(background, ColorScience.WHITE, 0.72f)
            else -> ColorScience.blend(secondary, ColorScience.BLACK, 0.05f)
        }
        val surfaceVariant = if (strictLight) {
            ColorScience.blend(background, secondary, 0.22f)
        } else {
            ColorScience.blend(secondary, background, 0.20f)
        }
        val onBackground = ColorScience.contrastingForeground(background)
        val onSurface = ColorScience.contrastingForeground(surface)
        val onSurfaceVariant = ColorScience.mutedForeground(surface, onSurface)
        val primaryContainer = if (strictLight) {
            surfaceVariant
        } else {
            derivePrimaryContainer(primary, background, secondary, isDark)
        }
        val secondaryContainer = if (strictLight) {
            ColorScience.blend(background, secondary, 0.35f)
        } else {
            ColorScience.blend(secondary, background, if (isDark) 0.25f else 0.18f)
        }
        val tertiaryContainer = ColorScience.blend(tertiary, background, if (isDark) 0.58f else 0.76f)
        val outline = ColorScience.mutedForeground(surface, onSurface, minContrast = 3.0)
        val outlineVariant = ColorScience.blend(outline, surface, 0.40f)
        return DerivedColorScheme(
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            primary = primary,
            onPrimary = onPrimary(seeds, primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = ColorScience.contrastingForeground(primaryContainer),
            secondary = secondary,
            onSecondary = ColorScience.contrastingForeground(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = ColorScience.contrastingForeground(secondaryContainer),
            tertiary = tertiary,
            onTertiary = ColorScience.contrastingForeground(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = ColorScience.contrastingForeground(tertiaryContainer),
            outline = outline,
            outlineVariant = outlineVariant,
            surfaceTint = surface,
            error = if (isDark) darkError else lightError,
            onError = if (isDark) darkOnError else lightOnError,
            errorContainer = if (isDark) darkErrorContainer else lightErrorContainer,
            onErrorContainer = if (isDark) darkOnErrorContainer else lightOnErrorContainer
        )
    }

    private fun derivePrimaryContainer(primary: Int, background: Int, secondary: Int, isDark: Boolean): Int {
        if (isDark && primary == StrictBrandTokens.LIME) {
            return ColorScience.blend(secondary, background, 0.22f)
        }
        return ColorScience.blend(primary, background, if (isDark) 0.62f else 0.78f)
    }

    private fun onPrimary(seeds: ThemeSeeds, primary: Int): Int {
        if (seeds == ThemeSeeds.DefaultDark) {
            return ThemeSeeds.DefaultDark.background
        }
        return ColorScience.contrastingForeground(primary)
    }
}
