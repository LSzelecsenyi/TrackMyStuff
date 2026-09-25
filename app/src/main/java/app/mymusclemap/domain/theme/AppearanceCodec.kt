package app.mymusclemap.domain.theme

object AppearanceCodec {
    const val KEY_THEME = "theme_preference"
    const val KEY_PALETTE_TYPE = "palette_type"
    const val KEY_LIGHT_BACKGROUND = "light_background"
    const val KEY_LIGHT_PRIMARY = "light_primary"
    const val KEY_LIGHT_SECONDARY = "light_secondary"
    const val KEY_LIGHT_TERTIARY = "light_tertiary"
    const val KEY_DARK_BACKGROUND = "dark_background"
    const val KEY_DARK_PRIMARY = "dark_primary"
    const val KEY_DARK_SECONDARY = "dark_secondary"
    const val KEY_DARK_TERTIARY = "dark_tertiary"
    const val VALUE_DEFAULT = "DEFAULT"
    const val VALUE_CUSTOM = "CUSTOM"

    fun decode(
        themeMode: String?,
        paletteType: String?,
        lightBackground: String?,
        lightPrimary: String?,
        lightSecondary: String?,
        lightTertiary: String?,
        darkBackground: String?,
        darkPrimary: String?,
        darkSecondary: String?,
        darkTertiary: String?
    ): AppearanceSettings {
        val mode = when (themeMode) {
            ThemeMode.Light.name -> ThemeMode.Light
            ThemeMode.Dark.name -> ThemeMode.Dark
            else -> ThemeMode.System
        }
        val type = decodePaletteType(paletteType)
        return AppearanceSettings(
            mode = mode,
            paletteType = type,
            customLight = decodeSeeds(
                lightBackground,
                lightPrimary,
                lightSecondary,
                lightTertiary,
                ThemeSeeds.copyOfFactoryLight()
            ),
            customDark = decodeSeeds(
                darkBackground,
                darkPrimary,
                darkSecondary,
                darkTertiary,
                ThemeSeeds.copyOfFactoryDark()
            )
        )
    }

    fun decodeFrom(prefs: Map<String, String?>): AppearanceSettings {
        return decode(
            themeMode = prefs[KEY_THEME],
            paletteType = prefs[KEY_PALETTE_TYPE],
            lightBackground = prefs[KEY_LIGHT_BACKGROUND],
            lightPrimary = prefs[KEY_LIGHT_PRIMARY],
            lightSecondary = prefs[KEY_LIGHT_SECONDARY],
            lightTertiary = prefs[KEY_LIGHT_TERTIARY],
            darkBackground = prefs[KEY_DARK_BACKGROUND],
            darkPrimary = prefs[KEY_DARK_PRIMARY],
            darkSecondary = prefs[KEY_DARK_SECONDARY],
            darkTertiary = prefs[KEY_DARK_TERTIARY]
        )
    }

    fun encodePaletteType(type: PaletteType): String {
        return if (type == PaletteType.Custom) VALUE_CUSTOM else VALUE_DEFAULT
    }

    fun decodePaletteType(raw: String?): PaletteType {
        return when (raw) {
            VALUE_CUSTOM, PaletteType.Custom.name -> PaletteType.Custom
            else -> PaletteType.Default
        }
    }

    fun persistPaletteType(existing: Map<String, String>, type: PaletteType): Map<String, String> {
        return existing + (KEY_PALETTE_TYPE to encodePaletteType(type))
    }

    fun encode(settings: AppearanceSettings): Map<String, String> {
        val light = settings.customLight.canonical()
        val dark = settings.customDark.canonical()
        return mapOf(
            KEY_THEME to settings.mode.name,
            KEY_PALETTE_TYPE to encodePaletteType(settings.paletteType),
            KEY_LIGHT_BACKGROUND to light.background,
            KEY_LIGHT_PRIMARY to light.primary,
            KEY_LIGHT_SECONDARY to light.secondary,
            KEY_LIGHT_TERTIARY to light.tertiary,
            KEY_DARK_BACKGROUND to dark.background,
            KEY_DARK_PRIMARY to dark.primary,
            KEY_DARK_SECONDARY to dark.secondary,
            KEY_DARK_TERTIARY to dark.tertiary
        )
    }

    private fun decodeSeeds(
        background: String?,
        primary: String?,
        secondary: String?,
        tertiary: String?,
        fallback: ThemeSeeds
    ): ThemeSeeds {
        val parsed = PaletteValidator.parseSeeds(
            background.orEmpty(),
            primary.orEmpty(),
            secondary.orEmpty(),
            tertiary.orEmpty()
        )
        return parsed?.detached() ?: fallback.detached()
    }
}
