package app.mymusclemap.domain.theme

/**
 * Strict brand colors that Material [primary] cannot express on its own.
 *
 * Ink is the readable emphasis color: blue in light, lime in dark.
 * Field is the large fill: lime in light, blue in dark.
 * Filled Strict actions use lime with dark content in both themes.
 * Prominent results use that same ink on the default palette.
 */
object StrictBrandTokens {
    const val BLUE = 0xFF1548C6.toInt()
    const val LIME = 0xFFE2FD6D.toInt()
    const val DARK = 0xFF0C121C.toInt()
    const val LIGHT = 0xFFF4F7FB.toInt()

    fun ink(isDark: Boolean): Int = if (isDark) LIME else BLUE

    fun field(isDark: Boolean): Int = if (isDark) BLUE else LIME

    fun onField(isDark: Boolean): Int = if (isDark) LIGHT else DARK

    fun usesStrictPalette(background: Int, primary: Int): Boolean {
        return (background == LIGHT && primary == BLUE) || (background == DARK && primary == LIME)
    }

    fun actionContainer(background: Int, primary: Int): Int {
        return if (usesStrictPalette(background, primary)) LIME else primary
    }

    fun onAction(background: Int, primary: Int): Int {
        return if (usesStrictPalette(background, primary)) {
            DARK
        } else {
            ColorScience.contrastingForeground(primary)
        }
    }

    /**
     * Prominent summary result. Default Strict dark uses lime; default Strict
     * light uses readable blue ink. Custom palettes keep their own primary.
     */
    fun result(background: Int, primary: Int): Int {
        return if (usesStrictPalette(background, primary)) {
            ink(isDark = background == DARK)
        } else {
            primary
        }
    }

    fun navigationSelection(background: Int, primary: Int): StrictNavigationSelection {
        return when {
            background == LIGHT && primary == BLUE -> StrictNavigationSelection.FieldBehindIcon
            background == DARK && primary == LIME -> StrictNavigationSelection.Ink
            else -> StrictNavigationSelection.Scheme
        }
    }
}

enum class StrictNavigationSelection {
    FieldBehindIcon,
    Ink,
    Scheme
}
