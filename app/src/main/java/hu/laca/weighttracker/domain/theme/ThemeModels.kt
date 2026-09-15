package hu.laca.weighttracker.domain.theme

data class ThemeSeeds(
    val background: Int,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int
) {
    fun canonical(): CanonicalSeeds {
        return CanonicalSeeds(
            background = HexColor.format(background),
            primary = HexColor.format(primary),
            secondary = HexColor.format(secondary),
            tertiary = HexColor.format(tertiary)
        )
    }

    fun detached(): ThemeSeeds = copy()

    companion object {
        val DefaultLight = ThemeSeeds(
            background = parseOrDefault("#F4F7FB"),
            primary = parseOrDefault("#2457C5"),
            secondary = parseOrDefault("#DCE7FA"),
            tertiary = parseOrDefault("#E8754F")
        )
        val DefaultDark = ThemeSeeds(
            background = parseOrDefault("#0C121C"),
            primary = parseOrDefault("#7FA6FF"),
            secondary = parseOrDefault("#1C2D4A"),
            tertiary = parseOrDefault("#FF9A78")
        )

        fun copyOfFactoryLight(): ThemeSeeds = DefaultLight.detached()

        fun copyOfFactoryDark(): ThemeSeeds = DefaultDark.detached()

        private fun parseOrDefault(hex: String): Int {
            return (HexColor.parse(hex) as HexParseResult.Valid).rgb
        }
    }
}

data class CanonicalSeeds(
    val background: String,
    val primary: String,
    val secondary: String,
    val tertiary: String
)

enum class PaletteType {
    Default,
    Custom
}

data class AppearanceSettings(
    val mode: ThemeMode,
    val paletteType: PaletteType,
    val customLight: ThemeSeeds,
    val customDark: ThemeSeeds
) {
    fun activeSeeds(isDark: Boolean): ThemeSeeds {
        val custom = if (isDark) customDark else customLight
        val defaults = if (isDark) ThemeSeeds.DefaultDark else ThemeSeeds.DefaultLight
        return if (paletteType == PaletteType.Custom) custom else defaults
    }

    companion object {
        val Default = AppearanceSettings(
            mode = ThemeMode.System,
            paletteType = PaletteType.Default,
            customLight = ThemeSeeds.copyOfFactoryLight(),
            customDark = ThemeSeeds.copyOfFactoryDark()
        )
    }
}

enum class ThemeMode {
    System,
    Light,
    Dark
}
