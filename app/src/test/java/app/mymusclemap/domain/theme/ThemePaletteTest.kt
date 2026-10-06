package app.mymusclemap.domain.theme

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
        assertEquals("#1548C6", ThemeSeeds.DefaultLight.canonical().primary)
        assertEquals("#DCE7FA", ThemeSeeds.DefaultLight.canonical().secondary)
        assertEquals("#5C6770", ThemeSeeds.DefaultLight.canonical().tertiary)
        assertEquals("#0C121C", ThemeSeeds.DefaultDark.canonical().background)
        assertEquals("#E2FD6D", ThemeSeeds.DefaultDark.canonical().primary)
        assertEquals("#1C2D4A", ThemeSeeds.DefaultDark.canonical().secondary)
        assertEquals("#C5CED6", ThemeSeeds.DefaultDark.canonical().tertiary)
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
        val lightScheme = ColorSchemeFactory.derive(ThemeSeeds.DefaultLight, isDark = false)
        val darkScheme = ColorSchemeFactory.derive(ThemeSeeds.DefaultDark, isDark = true)
        assertEquals(ColorScience.WHITE, lightScheme.onPrimary)
        assertEquals(ThemeSeeds.DefaultDark.background, darkScheme.onPrimary)
        assertTrue(ColorScience.contrastRatio(lightScheme.primary, lightScheme.background) >= 3.0)
        assertTrue(ColorScience.contrastRatio(darkScheme.primary, darkScheme.background) >= 3.0)
        assertTrue(ColorScience.contrastRatio(darkScheme.primary, darkScheme.onPrimary) >= 4.5)
    }

    @Test
    fun defaultDarkNeutralsDoNotInheritLime() {
        val scheme = ColorSchemeFactory.derive(ThemeSeeds.DefaultDark, isDark = true)
        val limeTintedSurface = ColorScience.blend(ThemeSeeds.DefaultDark.background, StrictBrandTokens.LIME, 0.10f)
        assertEquals(StrictBrandTokens.DARK, scheme.background)
        assertEquals(StrictBrandTokens.LIME, scheme.primary)
        assertEquals(0xFF121D2F.toInt(), scheme.surface)
        assertEquals(0xFF1C2D4A.toInt(), scheme.surfaceContainer)
        assertEquals(0xFF32425C.toInt(), scheme.surfaceContainerHigh)
        assertEquals(0xFF182740.toInt(), scheme.surfaceVariant)
        assertEquals(0xFF18273F.toInt(), scheme.primaryContainer)
        assertEquals(0xFF18263E.toInt(), scheme.secondaryContainer)
        assertEquals(scheme.surface, scheme.surfaceTint)
        assertNotEquals(limeTintedSurface, scheme.surface)
        assertTrue(ColorScience.green(limeTintedSurface) > ColorScience.blue(limeTintedSurface))
        listOf(
            scheme.surface,
            scheme.surfaceVariant,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.primaryContainer
        ).forEach { color ->
            assertTrue(
                "expected blue-gray, got ${HexColor.format(color)}",
                ColorScience.blue(color) > ColorScience.green(color)
            )
        }
        assertNotEquals(scheme.background, scheme.surface)
        assertNotEquals(scheme.surface, scheme.surfaceContainer)
        assertNotEquals(scheme.surfaceContainer, scheme.surfaceContainerHigh)
        assertEquals(StrictBrandTokens.BLUE, StrictBrandTokens.field(isDark = true))
        assertEquals(StrictBrandTokens.LIME, StrictBrandTokens.ink(isDark = true))
        assertTrue(ColorScience.contrastRatio(StrictBrandTokens.BLUE, StrictBrandTokens.DARK) < 3.0)
        assertTrue(ColorScience.contrastRatio(StrictBrandTokens.LIME, StrictBrandTokens.DARK) >= 4.5)
    }

    @Test
    fun defaultLightKeepsBlueInkAndLimeActionField() {
        val scheme = ColorSchemeFactory.derive(ThemeSeeds.DefaultLight, isDark = false)
        assertEquals(StrictBrandTokens.BLUE, scheme.primary)
        assertEquals(StrictBrandTokens.LIGHT, scheme.background)
        assertEquals(StrictBrandTokens.BLUE, StrictBrandTokens.ink(isDark = false))
        assertEquals(StrictBrandTokens.LIME, StrictBrandTokens.field(isDark = false))
        assertEquals(StrictBrandTokens.LIME, StrictBrandTokens.actionContainer(scheme.background, scheme.primary))
        assertEquals(StrictBrandTokens.DARK, StrictBrandTokens.onAction(scheme.background, scheme.primary))
        assertTrue(ColorScience.contrastRatio(StrictBrandTokens.BLUE, StrictBrandTokens.LIGHT) >= 4.5)
        assertTrue(ColorScience.contrastRatio(StrictBrandTokens.LIME, StrictBrandTokens.LIGHT) < 3.0)
        assertTrue(ColorScience.contrastRatio(StrictBrandTokens.DARK, StrictBrandTokens.LIME) >= 4.5)
        assertEquals(ColorScience.WHITE, scheme.surface)
        assertEquals(0xFFF8FAFC.toInt(), scheme.surfaceContainer)
        assertEquals(0xFFFBFCFD.toInt(), scheme.surfaceContainerHigh)
        assertEquals(0xFFEEF3FA.toInt(), scheme.surfaceVariant)
        assertEquals(0xFFEBF1FA.toInt(), scheme.secondaryContainer)
        assertEquals(scheme.surfaceVariant, scheme.primaryContainer)
        assertEquals(scheme.surface, scheme.surfaceTint)
        assertNotEquals(ThemeSeeds.DefaultLight.secondary, scheme.surfaceContainer)
        assertTrue(ColorScience.relativeLuminance(scheme.surface) > ColorScience.relativeLuminance(scheme.background))
        assertTrue(ColorScience.relativeLuminance(scheme.surfaceContainer) > ColorScience.relativeLuminance(scheme.background))
        assertTrue(ColorScience.relativeLuminance(scheme.surface) > ColorScience.relativeLuminance(scheme.surfaceContainer))
        assertTrue(ColorScience.relativeLuminance(scheme.surfaceContainerHigh) > ColorScience.relativeLuminance(scheme.surfaceContainer))
        assertTrue(ColorScience.contrastRatio(scheme.onSurface, scheme.surface) >= 4.5)
        assertTrue(ColorScience.contrastRatio(scheme.onSurfaceVariant, scheme.surface) >= 4.5)
        listOf(
            scheme.surface,
            scheme.surfaceVariant,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.secondaryContainer
        ).forEach { color ->
            assertNotEquals(StrictBrandTokens.LIME, color)
            assertTrue(
                "light surface picked up green ${HexColor.format(color)}",
                ColorScience.blue(color) >= ColorScience.green(color)
            )
        }
        assertEquals(
            StrictNavigationSelection.FieldBehindIcon,
            StrictBrandTokens.navigationSelection(scheme.background, scheme.primary)
        )
        assertEquals(StrictBrandTokens.DARK, StrictBrandTokens.onField(isDark = false))
        assertEquals(StrictBrandTokens.BLUE, StrictBrandTokens.ink(isDark = false))
    }

    @Test
    fun strictActionsStayOnPalettePrimaryWhenCustomized() {
        val customLight = ThemeSeeds.DefaultLight.copy(primary = 0xFF1B4BA8.toInt())
        val customDark = ThemeSeeds.DefaultDark.copy(primary = 0xFF8FB2FF.toInt())
        val customScheme = ColorSchemeFactory.derive(customLight, isDark = false)
        assertEquals(customLight.secondary, customScheme.surfaceContainer)
        assertNotEquals(ColorScience.WHITE, customScheme.surface)
        assertEquals(customLight.primary, StrictBrandTokens.actionContainer(customLight.background, customLight.primary))
        assertEquals(customDark.primary, StrictBrandTokens.actionContainer(customDark.background, customDark.primary))
        assertEquals(StrictNavigationSelection.Scheme, StrictBrandTokens.navigationSelection(customLight.background, customLight.primary))
        assertEquals(StrictNavigationSelection.Scheme, StrictBrandTokens.navigationSelection(customDark.background, customDark.primary))
        val scheme = ColorSchemeFactory.derive(
            ThemeSeeds(
                background = StrictBrandTokens.DARK,
                primary = 0xFFE53935.toInt(),
                secondary = 0xFF1C2D4A.toInt(),
                tertiary = 0xFFC5CED6.toInt()
            ),
            isDark = true
        )
        assertTrue(ColorScience.blue(scheme.surface) > ColorScience.green(scheme.surface))
        assertTrue(ColorScience.red(scheme.primaryContainer) > ColorScience.blue(scheme.primaryContainer))
        assertTrue(PaletteValidator.validate(customLight, isDark = false) is PaletteValidationResult.Valid)
        assertTrue(PaletteValidator.validate(customDark, isDark = true) is PaletteValidationResult.Valid)
    }

    @Test
    fun darkStrictNavigationUsesLimeInk() {
        assertEquals(
            StrictNavigationSelection.Ink,
            StrictBrandTokens.navigationSelection(StrictBrandTokens.DARK, StrictBrandTokens.LIME)
        )
        assertEquals(StrictBrandTokens.LIME, StrictBrandTokens.actionContainer(StrictBrandTokens.DARK, StrictBrandTokens.LIME))
        assertEquals(StrictBrandTokens.DARK, StrictBrandTokens.onAction(StrictBrandTokens.DARK, StrictBrandTokens.LIME))
        assertEquals(StrictBrandTokens.LIME, StrictBrandTokens.result(StrictBrandTokens.DARK, StrictBrandTokens.LIME))
    }

    @Test
    fun defaultLightWorkoutActionStaysLimeWithDarkContent() {
        assertEquals(
            StrictBrandTokens.LIME,
            StrictBrandTokens.actionContainer(StrictBrandTokens.LIGHT, StrictBrandTokens.BLUE)
        )
        assertEquals(
            StrictBrandTokens.DARK,
            StrictBrandTokens.onAction(StrictBrandTokens.LIGHT, StrictBrandTokens.BLUE)
        )
    }

    @Test
    fun prominentResultsUseReadableInkAndNotLimeOnTheLightCanvas() {
        assertEquals(StrictBrandTokens.BLUE, StrictBrandTokens.result(StrictBrandTokens.LIGHT, StrictBrandTokens.BLUE))
        assertNotEquals(StrictBrandTokens.LIME, StrictBrandTokens.result(StrictBrandTokens.LIGHT, StrictBrandTokens.BLUE))
        assertTrue(
            ColorScience.contrastRatio(StrictBrandTokens.result(StrictBrandTokens.LIGHT, StrictBrandTokens.BLUE), StrictBrandTokens.LIGHT) >= 4.5
        )
        assertTrue(ColorScience.contrastRatio(StrictBrandTokens.LIME, StrictBrandTokens.LIGHT) < 3.0)
        val customLight = ThemeSeeds.DefaultLight.copy(primary = 0xFF1B4BA8.toInt())
        val customDark = ThemeSeeds.DefaultDark.copy(primary = 0xFF8FB2FF.toInt())
        assertEquals(customLight.primary, StrictBrandTokens.result(customLight.background, customLight.primary))
        assertEquals(customDark.primary, StrictBrandTokens.result(customDark.background, customDark.primary))
        assertNotEquals(StrictBrandTokens.LIME, StrictBrandTokens.result(customDark.background, customDark.primary))
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

    @Test
    fun legacyCustomPaletteKeepsStoredTertiary() {
        val decoded = AppearanceCodec.decode(
            themeMode = "Light",
            paletteType = AppearanceCodec.VALUE_CUSTOM,
            lightBackground = "#F4F7FB",
            lightPrimary = "#2457C5",
            lightSecondary = "#DCE7FA",
            lightTertiary = "#E8754F",
            darkBackground = "#0C121C",
            darkPrimary = "#7FA6FF",
            darkSecondary = "#1C2D4A",
            darkTertiary = "#FF9A78"
        )
        assertEquals(PaletteType.Custom, decoded.paletteType)
        assertEquals("#2457C5", decoded.activeSeeds(false).canonical().primary)
        assertEquals("#E8754F", decoded.customLight.canonical().tertiary)
        assertEquals("#FF9A78", decoded.customDark.canonical().tertiary)
        assertEquals("#1548C6", ThemeSeeds.DefaultLight.canonical().primary)
        val again = AppearanceCodec.decodeFrom(AppearanceCodec.encode(decoded))
        assertEquals("#E8754F", again.customLight.canonical().tertiary)
        assertEquals("#FF9A78", again.customDark.canonical().tertiary)
        assertEquals("#2457C5", again.customLight.canonical().primary)
        assertEquals("#7FA6FF", again.customDark.canonical().primary)
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
