package app.mymusclemap.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class AppearancePaletteContractTest {
    @Test
    fun darkDefaultUsesCurrentStrictDarkFactory() {
        val settings = AppearanceSettings.Default.copy(mode = ThemeMode.Dark)
        val seeds = settings.activeSeeds(settings.mode.isDark(systemInDarkTheme = false))
        assertEquals(ThemeSeeds.DefaultDark.canonical(), seeds.canonical())
        assertEquals("#0C121C", seeds.canonical().background)
        assertEquals("#E2FD6D", seeds.canonical().primary)
        assertEquals(StrictBrandTokens.LIME, StrictBrandTokens.actionContainer(seeds.background, seeds.primary))
        assertEquals(StrictBrandTokens.DARK, StrictBrandTokens.onAction(seeds.background, seeds.primary))
        assertEquals(StrictBrandTokens.LIME, StrictBrandTokens.result(seeds.background, seeds.primary))
    }

    @Test
    fun lightDefaultUsesCurrentStrictLightFactory() {
        val settings = AppearanceSettings.Default.copy(mode = ThemeMode.Light)
        val seeds = settings.activeSeeds(settings.mode.isDark(systemInDarkTheme = true))
        assertEquals(ThemeSeeds.DefaultLight.canonical(), seeds.canonical())
        assertEquals("#F4F7FB", seeds.canonical().background)
        assertEquals("#1548C6", seeds.canonical().primary)
        assertEquals(StrictBrandTokens.LIME, StrictBrandTokens.actionContainer(seeds.background, seeds.primary))
        assertEquals(StrictBrandTokens.DARK, StrictBrandTokens.onAction(seeds.background, seeds.primary))
        assertEquals(StrictBrandTokens.BLUE, StrictBrandTokens.result(seeds.background, seeds.primary))
    }

    @Test
    fun systemDefaultFollowsResolvedAppearanceWithTheCurrentFactory() {
        val settings = AppearanceSettings.Default.copy(mode = ThemeMode.System)
        assertEquals(ThemeSeeds.DefaultLight.canonical(), settings.activeSeeds(settings.mode.isDark(false)).canonical())
        assertEquals(ThemeSeeds.DefaultDark.canonical(), settings.activeSeeds(settings.mode.isDark(true)).canonical())
        assertEquals(false, ThemeMode.Light.isDark(systemInDarkTheme = true))
        assertEquals(true, ThemeMode.Dark.isDark(systemInDarkTheme = false))
    }

    @Test
    fun customUsesStoredPalettesIndependentlyOfAppearanceMode() {
        val settings = historicalBlueCustom()
        assertEquals("#2457C5", settings.activeSeeds(settings.mode.isDark(systemInDarkTheme = true)).canonical().primary)
        val dark = settings.copy(mode = ThemeMode.Dark)
        assertEquals("#7FA6FF", dark.activeSeeds(dark.mode.isDark(systemInDarkTheme = false)).canonical().primary)
        assertEquals("#E8754F", settings.customLight.canonical().tertiary)
        assertEquals("#FF9A78", settings.customDark.canonical().tertiary)
    }

    @Test
    fun strictDefaultLeavesStoredCustomColorsUntouched() {
        val imported = historicalBlueCustom()
        val onDefault = imported.copy(paletteType = PaletteType.Default)
        assertEquals(ThemeSeeds.DefaultDark.canonical(), onDefault.activeSeeds(true).canonical())
        assertEquals(imported.customLight.canonical(), onDefault.customLight.canonical())
        assertEquals(imported.customDark.canonical(), onDefault.customDark.canonical())
        assertNotEquals(ThemeSeeds.DefaultDark.canonical().primary, onDefault.customDark.canonical().primary)
        val back = onDefault.copy(paletteType = PaletteType.Custom)
        assertEquals(imported.customLight.canonical(), back.customLight.canonical())
        assertEquals(imported.customDark.canonical(), back.activeSeeds(true).canonical())
    }

    @Test
    fun defaultResolvesFromFactoryEvenWhenStoredSlotsHoldOtherColors() {
        val stored = historicalBlueCustom().copy(paletteType = PaletteType.Default)
        assertEquals(ThemeSeeds.DefaultLight, stored.activeSeeds(false))
        assertEquals(ThemeSeeds.DefaultDark, stored.activeSeeds(true))
        assertNotSame(stored.customDark, stored.activeSeeds(true))
    }

    @Test
    fun importedCustomPaletteRoundTripsWithoutRewritingColors() {
        val imported = AppearanceCodec.decodeFrom(
            AppearanceCodec.encode(historicalBlueCustom().copy(mode = ThemeMode.Dark))
        )
        assertEquals(ThemeMode.Dark, imported.mode)
        assertEquals(PaletteType.Custom, imported.paletteType)
        assertEquals("#2457C5", imported.customLight.canonical().primary)
        assertEquals("#7FA6FF", imported.activeSeeds(true).canonical().primary)
        assertEquals("#E8754F", imported.customLight.canonical().tertiary)
        assertEquals("#FF9A78", imported.customDark.canonical().tertiary)

        val onDefault = PaletteSessionLogic.selectDefault(PaletteSession(imported))
        assertEquals(PaletteType.Default, onDefault.appearance.paletteType)
        assertEquals(ThemeSeeds.DefaultDark.canonical(), onDefault.appearance.activeSeeds(true).canonical())
        assertEquals("#7FA6FF", onDefault.appearance.customDark.canonical().primary)

        val onCustom = PaletteSessionLogic.selectCustom(onDefault)
        assertEquals(PaletteType.Custom, onCustom.appearance.paletteType)
        assertEquals("#2457C5", onCustom.appearance.customLight.canonical().primary)
        assertEquals("#7FA6FF", onCustom.appearance.activeSeeds(true).canonical().primary)
        assertEquals("#E8754F", onCustom.appearance.customLight.canonical().tertiary)
        assertEquals("#FF9A78", onCustom.appearance.customDark.canonical().tertiary)

        val again = AppearanceCodec.decodeFrom(AppearanceCodec.encode(onCustom.appearance))
        assertEquals(PaletteType.Custom, again.paletteType)
        assertEquals(imported.customLight.canonical(), again.customLight.canonical())
        assertEquals(imported.customDark.canonical(), again.customDark.canonical())
    }

    @Test
    fun resetCustomDraftStaysCustomEvenWhenDraftMatchesFactory() {
        val session = PaletteSessionLogic.selectCustom(PaletteSession(historicalBlueCustom()))
        val reset = PaletteSessionLogic.resetCustomDraft(session)
        assertEquals(PaletteType.Custom, reset.appearance.paletteType)
        assertEquals("#7FA6FF", reset.appearance.activeSeeds(true).canonical().primary)
        assertEquals(ThemeSeeds.DefaultDark.canonical(), reset.draft!!.darkPreview.canonical())
        assertNotSame(ThemeSeeds.DefaultDark, reset.draft.darkPreview)
    }

    private fun historicalBlueCustom(): AppearanceSettings {
        fun rgb(hex: String): Int = (HexColor.parse(hex) as HexParseResult.Valid).rgb
        return AppearanceSettings(
            mode = ThemeMode.Light,
            paletteType = PaletteType.Custom,
            customLight = ThemeSeeds(
                background = rgb("#F4F7FB"),
                primary = rgb("#2457C5"),
                secondary = rgb("#DCE7FA"),
                tertiary = rgb("#E8754F")
            ),
            customDark = ThemeSeeds(
                background = rgb("#0C121C"),
                primary = rgb("#7FA6FF"),
                secondary = rgb("#1C2D4A"),
                tertiary = rgb("#FF9A78")
            )
        )
    }
}
