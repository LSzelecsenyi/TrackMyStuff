package app.mymusclemap.domain.theme

data class PaletteDraft(
    val editingDark: Boolean,
    val lightBackground: String,
    val lightPrimary: String,
    val lightSecondary: String,
    val lightTertiary: String,
    val darkBackground: String,
    val darkPrimary: String,
    val darkSecondary: String,
    val darkTertiary: String,
    val lightPreview: ThemeSeeds,
    val darkPreview: ThemeSeeds
) {
    val activePreview: ThemeSeeds get() = if (editingDark) darkPreview else lightPreview
    val previewIsDark: Boolean get() = editingDark
}

object PaletteDraftLogic {
    fun fromSeeds(light: ThemeSeeds, dark: ThemeSeeds, editingDark: Boolean = false): PaletteDraft {
        val lightHex = light.canonical()
        val darkHex = dark.canonical()
        return PaletteDraft(
            editingDark = editingDark,
            lightBackground = lightHex.background,
            lightPrimary = lightHex.primary,
            lightSecondary = lightHex.secondary,
            lightTertiary = lightHex.tertiary,
            darkBackground = darkHex.background,
            darkPrimary = darkHex.primary,
            darkSecondary = darkHex.secondary,
            darkTertiary = darkHex.tertiary,
            lightPreview = light.detached(),
            darkPreview = dark.detached()
        )
    }

    fun updateField(draft: PaletteDraft, field: SeedField, raw: String): PaletteDraft {
        val updated = when {
            !draft.editingDark && field == SeedField.Background -> draft.copy(lightBackground = raw)
            !draft.editingDark && field == SeedField.Primary -> draft.copy(lightPrimary = raw)
            !draft.editingDark && field == SeedField.Secondary -> draft.copy(lightSecondary = raw)
            !draft.editingDark && field == SeedField.Tertiary -> draft.copy(lightTertiary = raw)
            draft.editingDark && field == SeedField.Background -> draft.copy(darkBackground = raw)
            draft.editingDark && field == SeedField.Primary -> draft.copy(darkPrimary = raw)
            draft.editingDark && field == SeedField.Secondary -> draft.copy(darkSecondary = raw)
            else -> draft.copy(darkTertiary = raw)
        }
        return updated.copy(
            lightPreview = previewFrom(
                updated.lightBackground,
                updated.lightPrimary,
                updated.lightSecondary,
                updated.lightTertiary,
                updated.lightPreview
            ),
            darkPreview = previewFrom(
                updated.darkBackground,
                updated.darkPrimary,
                updated.darkSecondary,
                updated.darkTertiary,
                updated.darkPreview
            )
        )
    }

    fun applyParsedColor(draft: PaletteDraft, field: SeedField, rgb: Int): PaletteDraft {
        return updateField(draft, field, HexColor.format(rgb))
    }

    fun resetToFactory(draft: PaletteDraft): PaletteDraft {
        return fromSeeds(
            ThemeSeeds.copyOfFactoryLight(),
            ThemeSeeds.copyOfFactoryDark(),
            draft.editingDark
        )
    }

    fun generateDark(draft: PaletteDraft): PaletteDraft {
        val generated = DarkPaletteGenerator.fromLight(draft.lightPreview)
        return fromSeeds(draft.lightPreview, generated, editingDark = true).copy(
            lightBackground = draft.lightBackground,
            lightPrimary = draft.lightPrimary,
            lightSecondary = draft.lightSecondary,
            lightTertiary = draft.lightTertiary
        )
    }

    fun fieldError(raw: String): ColorFieldError? {
        return if (HexColor.parse(raw) is HexParseResult.Valid) null else ColorFieldError.Malformed
    }

    fun validateForSave(draft: PaletteDraft): PaletteSaveResult {
        val lightSeeds = PaletteValidator.parseSeeds(
            draft.lightBackground,
            draft.lightPrimary,
            draft.lightSecondary,
            draft.lightTertiary
        ) ?: return PaletteSaveResult.InvalidHex
        val darkSeeds = PaletteValidator.parseSeeds(
            draft.darkBackground,
            draft.darkPrimary,
            draft.darkSecondary,
            draft.darkTertiary
        ) ?: return PaletteSaveResult.InvalidHex
        when (val light = PaletteValidator.validate(lightSeeds, isDark = false)) {
            is PaletteValidationResult.Invalid -> return PaletteSaveResult.Contrast(light.error, isDark = false)
            is PaletteValidationResult.Valid -> Unit
        }
        when (val dark = PaletteValidator.validate(darkSeeds, isDark = true)) {
            is PaletteValidationResult.Invalid -> return PaletteSaveResult.Contrast(dark.error, isDark = true)
            is PaletteValidationResult.Valid -> Unit
        }
        return PaletteSaveResult.Success(lightSeeds, darkSeeds)
    }

    private fun previewFrom(
        background: String,
        primary: String,
        secondary: String,
        tertiary: String,
        previous: ThemeSeeds
    ): ThemeSeeds {
        return ThemeSeeds(
            background = parsedOr(background, previous.background),
            primary = parsedOr(primary, previous.primary),
            secondary = parsedOr(secondary, previous.secondary),
            tertiary = parsedOr(tertiary, previous.tertiary)
        )
    }

    private fun parsedOr(raw: String, fallback: Int): Int {
        return (HexColor.parse(raw) as? HexParseResult.Valid)?.rgb ?: fallback
    }
}

enum class SeedField {
    Background,
    Primary,
    Secondary,
    Tertiary
}

enum class ColorFieldError {
    Malformed
}

sealed class PaletteSaveResult {
    data class Success(val light: ThemeSeeds, val dark: ThemeSeeds) : PaletteSaveResult()
    data object InvalidHex : PaletteSaveResult()
    data class Contrast(val error: PaletteValidationError, val isDark: Boolean) : PaletteSaveResult()
}
