package hu.laca.weighttracker.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexColorTest {
    @Test
    fun parsesCanonicalHashRgb() {
        val parsed = HexColor.parse("#2457C5") as HexParseResult.Valid
        assertEquals("#2457C5", parsed.canonical)
        assertEquals("#2457C5", HexColor.format(parsed.rgb))
    }

    @Test
    fun parsesRgbWithoutHashAndNormalizes() {
        val parsed = HexColor.parse("e8754f") as HexParseResult.Valid
        assertEquals("#E8754F", parsed.canonical)
    }

    @Test
    fun rejectsMalformedHexValues() {
        listOf("", "#FFF", "#2457CG", "2457C", "#2457C5F", "blue", "#2457c").forEach { raw ->
            assertEquals("expected invalid: $raw", HexParseResult.Invalid, HexColor.parse(raw))
        }
    }
}

class ColorScienceTest {
    @Test
    fun selectsForegroundByContrast() {
        assertEquals(ColorScience.WHITE, ColorScience.contrastingForeground(0xFF0C121C.toInt()))
        assertEquals(ColorScience.BLACK, ColorScience.contrastingForeground(0xFFF4F7FB.toInt()))
        assertTrue(
            ColorScience.contrastRatio(0xFFF4F7FB.toInt(), ColorScience.BLACK) >= 4.5
        )
        assertTrue(
            ColorScience.contrastRatio(0xFF0C121C.toInt(), ColorScience.WHITE) >= 4.5
        )
    }
}

class ColorSchemeFactoryTest {
    @Test
    fun defaultPalettesMatchApprovedSeeds() {
        assertEquals("#F4F7FB", ThemeSeeds.DefaultLight.canonical().background)
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
        assertEquals("#DCE7FA", ThemeSeeds.DefaultLight.canonical().secondary)
        assertEquals("#E8754F", ThemeSeeds.DefaultLight.canonical().tertiary)
        assertEquals("#0C121C", ThemeSeeds.DefaultDark.canonical().background)
        assertEquals("#7FA6FF", ThemeSeeds.DefaultDark.canonical().primary)
        assertEquals("#1C2D4A", ThemeSeeds.DefaultDark.canonical().secondary)
        assertEquals("#FF9A78", ThemeSeeds.DefaultDark.canonical().tertiary)
    }

    @Test
    fun derivedSchemeIsDeterministicAndHasContrast() {
        val first = ColorSchemeFactory.derive(ThemeSeeds.DefaultLight, isDark = false)
        val second = ColorSchemeFactory.derive(ThemeSeeds.DefaultLight, isDark = false)
        assertEquals(first, second)
        assertTrue(ColorScience.contrastRatio(first.background, first.onBackground) >= 4.5)
        assertTrue(ColorScience.contrastRatio(first.primary, first.onPrimary) >= 4.5)
        val light = PaletteValidator.validate(ThemeSeeds.DefaultLight, isDark = false)
        val dark = PaletteValidator.validate(ThemeSeeds.DefaultDark, isDark = true)
        assertTrue("light: $light", light is PaletteValidationResult.Valid)
        assertTrue("dark: $dark", dark is PaletteValidationResult.Valid)
    }

    @Test
    fun insufficientContrastIsRejected() {
        val invalid = ThemeSeeds(
            background = 0xFFFFFFFF.toInt(),
            primary = 0xFFFFFFFF.toInt(),
            secondary = 0xFFF5F5F5.toInt(),
            tertiary = 0xFFFFFFFF.toInt()
        )
        val result = PaletteValidator.validate(invalid, isDark = false)
        assertTrue(result is PaletteValidationResult.Invalid)
    }
}

class AppearanceCodecTest {
    @Test
    fun malformedPersistedColorsFallBackToDefaults() {
        val decoded = AppearanceCodec.decode(
            themeMode = "Light",
            paletteType = "Custom",
            lightBackground = "not-a-color",
            lightPrimary = "#2457C5",
            lightSecondary = "#DCE7FA",
            lightTertiary = "#E8754F",
            darkBackground = null,
            darkPrimary = null,
            darkSecondary = null,
            darkTertiary = null
        )
        assertEquals(ThemeMode.Light, decoded.mode)
        assertEquals(ThemeSeeds.DefaultLight, decoded.customLight)
        assertEquals(ThemeSeeds.DefaultDark, decoded.customDark)
    }

    @Test
    fun preservesExistingThemeModeWhenNewKeysAreAbsent() {
        val decoded = AppearanceCodec.decode(
            themeMode = "Dark",
            paletteType = null,
            lightBackground = null,
            lightPrimary = null,
            lightSecondary = null,
            lightTertiary = null,
            darkBackground = null,
            darkPrimary = null,
            darkSecondary = null,
            darkTertiary = null
        )
        assertEquals(ThemeMode.Dark, decoded.mode)
        assertEquals(PaletteType.Default, decoded.paletteType)
        assertEquals(ThemeSeeds.DefaultLight, decoded.customLight)
        assertEquals(ThemeSeeds.DefaultDark, decoded.customDark)
    }

    @Test
    fun customPaletteRoundTrip() {
        val settings = AppearanceSettings(
            mode = ThemeMode.Light,
            paletteType = PaletteType.Custom,
            customLight = ThemeSeeds.DefaultLight.copy(primary = 0xFF1B4BA8.toInt()),
            customDark = ThemeSeeds.DefaultDark.copy(primary = 0xFF8FB2FF.toInt())
        )
        val encoded = AppearanceCodec.encode(settings)
        val decoded = AppearanceCodec.decode(
            themeMode = encoded.getValue(AppearanceCodec.KEY_THEME),
            paletteType = encoded.getValue(AppearanceCodec.KEY_PALETTE_TYPE),
            lightBackground = encoded.getValue(AppearanceCodec.KEY_LIGHT_BACKGROUND),
            lightPrimary = encoded.getValue(AppearanceCodec.KEY_LIGHT_PRIMARY),
            lightSecondary = encoded.getValue(AppearanceCodec.KEY_LIGHT_SECONDARY),
            lightTertiary = encoded.getValue(AppearanceCodec.KEY_LIGHT_TERTIARY),
            darkBackground = encoded.getValue(AppearanceCodec.KEY_DARK_BACKGROUND),
            darkPrimary = encoded.getValue(AppearanceCodec.KEY_DARK_PRIMARY),
            darkSecondary = encoded.getValue(AppearanceCodec.KEY_DARK_SECONDARY),
            darkTertiary = encoded.getValue(AppearanceCodec.KEY_DARK_TERTIARY)
        )
        assertEquals(settings.mode, decoded.mode)
        assertEquals(settings.paletteType, decoded.paletteType)
        assertEquals(settings.customLight.canonical(), decoded.customLight.canonical())
        assertEquals(settings.customDark.canonical(), decoded.customDark.canonical())
    }

    @Test
    fun resetToDefaultUsesApprovedPalette() {
        val restored = AppearanceCodec.decode(
            themeMode = "Light",
            paletteType = PaletteType.Default.name,
            lightBackground = null,
            lightPrimary = null,
            lightSecondary = null,
            lightTertiary = null,
            darkBackground = null,
            darkPrimary = null,
            darkSecondary = null,
            darkTertiary = null
        )
        assertEquals(PaletteType.Default, restored.paletteType)
        assertEquals(ThemeSeeds.DefaultLight, restored.activeSeeds(false))
        assertEquals(ThemeSeeds.DefaultDark, restored.activeSeeds(true))
    }
}

class DarkPaletteGeneratorTest {
    @Test
    fun generatesUsableDarkPaletteWithSameHueIdentity() {
        val generated = DarkPaletteGenerator.fromLight(ThemeSeeds.DefaultLight)
        assertTrue(PaletteValidator.validate(generated, isDark = true) is PaletteValidationResult.Valid)
        val lightHue = ColorScience.toHsl(ThemeSeeds.DefaultLight.primary).hue
        val darkHue = ColorScience.toHsl(generated.primary).hue
        val hueDelta = kotlin.math.abs(lightHue - darkHue).let { delta ->
            minOf(delta, 360f - delta)
        }
        assertTrue("hue drift too large: $hueDelta", hueDelta < 8f)
        assertNotEquals(ThemeSeeds.DefaultLight.background, generated.background)
    }
}

class PaletteDraftLogicTest {
    @Test
    fun keepsLastValidPreviewWhileHexIsMalformed() {
        val start = PaletteDraftLogic.fromSeeds(ThemeSeeds.DefaultLight, ThemeSeeds.DefaultDark)
        val updated = PaletteDraftLogic.updateField(start, SeedField.Primary, "#ZZZZZZ")
        assertEquals(start.lightPreview.primary, updated.lightPreview.primary)
        assertEquals(ColorFieldError.Malformed, PaletteDraftLogic.fieldError(updated.lightPrimary))
        assertTrue(PaletteDraftLogic.validateForSave(updated) is PaletteSaveResult.InvalidHex)
    }

    @Test
    fun generateDarkDoesNotOverwriteUntilAppliedToDraft() {
        val start = PaletteDraftLogic.fromSeeds(ThemeSeeds.DefaultLight, ThemeSeeds.DefaultDark)
        val generated = PaletteDraftLogic.generateDark(start)
        assertEquals(start.lightPreview, generated.lightPreview)
        assertTrue(generated.editingDark)
        assertNotEquals(start.darkPreview, generated.darkPreview)
    }
}
