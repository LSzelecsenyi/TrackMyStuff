package hu.laca.weighttracker.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class PalettePersistenceTest {
    private val purple = parse("#8A2BE2")
    private val customLight = ThemeSeeds.copyOfFactoryLight().copy(primary = purple)
    private val customDark = ThemeSeeds.copyOfFactoryDark().copy(primary = parse("#BA81EE"))

    @Test
    fun editingCustomColorsDoesNotChangeFactoryPalette() {
        val session = PaletteSessionLogic.selectCustom(PaletteSession(AppearanceSettings.Default))
        val edited = PaletteDraftLogic.applyParsedColor(session.draft!!, SeedField.Primary, purple)
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
        assertEquals("#7FA6FF", ThemeSeeds.DefaultDark.canonical().primary)
        assertEquals("#8A2BE2", edited.lightPrimary)
        assertNotEquals(ThemeSeeds.DefaultLight.primary, edited.lightPreview.primary)
    }

    @Test
    fun savingCustomColorsDoesNotChangeFactoryPalette() {
        val opened = PaletteSessionLogic.selectCustom(PaletteSession(AppearanceSettings.Default))
        val saved = PaletteSessionLogic.saveCustom(opened, customLight, customDark)
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
        assertEquals("#7FA6FF", ThemeSeeds.DefaultDark.canonical().primary)
        assertEquals("#8A2BE2", saved.appearance.customLight.canonical().primary)
        assertEquals(PaletteType.Custom, saved.appearance.paletteType)
        assertEquals(customLight.canonical(), saved.appearance.activeSeeds(false).canonical())
    }

    @Test
    fun selectingDefaultAfterCustomActivatesExactFactoryColors() {
        val saved = savedCustomSession()
        val backToDefault = PaletteSessionLogic.selectDefault(saved)
        assertEquals(PaletteType.Default, backToDefault.appearance.paletteType)
        assertNull(backToDefault.draft)
        assertEquals(ThemeSeeds.DefaultLight.canonical(), backToDefault.appearance.activeSeeds(false).canonical())
        assertEquals(ThemeSeeds.DefaultDark.canonical(), backToDefault.appearance.activeSeeds(true).canonical())
        assertEquals("#2457C5", backToDefault.appearance.activeSeeds(false).canonical().primary)
    }

    @Test
    fun selectingCustomAgainRestoresPreviouslySavedCustomColors() {
        val saved = savedCustomSession()
        val defaulted = PaletteSessionLogic.selectDefault(saved)
        val restored = PaletteSessionLogic.selectCustom(defaulted)
        assertEquals(PaletteType.Custom, restored.appearance.paletteType)
        assertEquals("#8A2BE2", restored.appearance.activeSeeds(false).canonical().primary)
        assertEquals("#8A2BE2", restored.draft!!.lightPrimary)
        assertEquals(customDark.canonical(), restored.appearance.activeSeeds(true).canonical())
    }

    @Test
    fun selectingDefaultDoesNotDeleteCustomPersistence() {
        val store = InMemoryAppearanceStore()
        store.writeCustomPalette(customLight, customDark)
        store.writePaletteType(PaletteType.Default)
        val loaded = store.read()
        assertEquals(PaletteType.Default, loaded.paletteType)
        assertEquals(ThemeSeeds.DefaultLight.canonical(), loaded.activeSeeds(false).canonical())
        assertEquals("#8A2BE2", loaded.customLight.canonical().primary)
        store.writePaletteType(PaletteType.Custom)
        assertEquals("#8A2BE2", store.read().activeSeeds(false).canonical().primary)
    }

    @Test
    fun paletteTypeAndCustomValuesSurviveStoreRecreation() {
        val store = InMemoryAppearanceStore()
        store.writeThemeMode(ThemeMode.Light)
        store.writeCustomPalette(customLight, customDark)
        val restored = store.recreate().read()
        assertEquals(ThemeMode.Light, restored.mode)
        assertEquals(PaletteType.Custom, restored.paletteType)
        assertEquals(AppearanceCodec.VALUE_CUSTOM, store.snapshot()[AppearanceCodec.KEY_PALETTE_TYPE])
        assertEquals("#8A2BE2", restored.customLight.canonical().primary)
        assertEquals(customDark.canonical(), restored.customDark.canonical())
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
    }

    @Test
    fun firstTimeCustomDraftStartsAsFactoryCopy() {
        val session = PaletteSessionLogic.selectCustom(PaletteSession(AppearanceSettings.Default))
        assertEquals(ThemeSeeds.DefaultLight.canonical(), session.draft!!.lightPreview.canonical())
        assertEquals(ThemeSeeds.DefaultDark.canonical(), session.draft.darkPreview.canonical())
        assertNotSame(ThemeSeeds.DefaultLight, session.draft.lightPreview)
        assertNotSame(ThemeSeeds.DefaultDark, session.draft.darkPreview)
        assertEquals(PaletteType.Custom, session.appearance.paletteType)
    }

    @Test
    fun cancellingCustomEditingPreservesSavedCustomPalette() {
        val saved = savedCustomSession()
        val edited = saved.copy(
            draft = PaletteDraftLogic.applyParsedColor(saved.draft!!, SeedField.Primary, parse("#00FF00"))
        )
        val cancelled = PaletteSessionLogic.cancelDraft(edited)
        assertEquals("#8A2BE2", cancelled.appearance.customLight.canonical().primary)
        assertEquals("#8A2BE2", cancelled.draft!!.lightPrimary)
        assertEquals(PaletteType.Custom, cancelled.appearance.paletteType)
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
    }

    @Test
    fun resettingChangesOnlyDraftUntilSave() {
        val saved = savedCustomSession()
        val reset = PaletteSessionLogic.resetCustomDraft(saved)
        assertEquals("#2457C5", reset.draft!!.lightPrimary)
        assertEquals("#8A2BE2", reset.appearance.customLight.canonical().primary)
        assertEquals(PaletteType.Custom, reset.appearance.paletteType)
        assertEquals("#8A2BE2", reset.appearance.activeSeeds(false).canonical().primary)
        val afterSave = PaletteSessionLogic.saveCustom(
            reset,
            reset.draft.lightPreview,
            reset.draft.darkPreview
        )
        assertEquals("#2457C5", afterSave.appearance.customLight.canonical().primary)
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
        assertNotSame(ThemeSeeds.DefaultLight, afterSave.appearance.customLight)
    }

    @Test
    fun malformedPersistedCustomValuesDoNotAffectFactoryPalette() {
        val decoded = AppearanceCodec.decode(
            themeMode = "Light",
            paletteType = AppearanceCodec.VALUE_CUSTOM,
            lightBackground = "not-a-color",
            lightPrimary = "#2457C5",
            lightSecondary = "#DCE7FA",
            lightTertiary = "#E8754F",
            darkBackground = "bad",
            darkPrimary = null,
            darkSecondary = null,
            darkTertiary = null
        )
        assertEquals(PaletteType.Custom, decoded.paletteType)
        assertEquals(ThemeSeeds.DefaultLight.canonical(), decoded.customLight.canonical())
        assertEquals(ThemeSeeds.DefaultDark.canonical(), decoded.customDark.canonical())
        assertNotSame(ThemeSeeds.DefaultLight, decoded.customLight)
        assertNotSame(ThemeSeeds.DefaultDark, decoded.customDark)
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
        assertEquals("#7FA6FF", ThemeSeeds.DefaultDark.canonical().primary)
    }

    @Test
    fun factoryPaletteInstancesCannotBeMutatedThroughSharedReferences() {
        val settings = AppearanceSettings.Default
        assertNotSame(ThemeSeeds.DefaultLight, settings.customLight)
        assertNotSame(ThemeSeeds.DefaultDark, settings.customDark)
        val mutated = settings.customLight.copy(primary = purple)
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
        assertEquals("#2457C5", settings.customLight.canonical().primary)
        assertEquals("#8A2BE2", HexColor.format(mutated.primary))
        val session = PaletteSessionLogic.saveCustom(
            PaletteSession(settings),
            mutated,
            ThemeSeeds.copyOfFactoryDark()
        )
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
        assertNotSame(ThemeSeeds.DefaultLight, session.appearance.customLight)
        assertEquals(ThemeSeeds.DefaultLight, session.appearance.copy(paletteType = PaletteType.Default).activeSeeds(false))
    }

    @Test
    fun paletteTypeIsPersistedIndependentlyAsDefaultOrCustom() {
        val encodedCustom = AppearanceCodec.encode(
            AppearanceSettings(
                mode = ThemeMode.System,
                paletteType = PaletteType.Custom,
                customLight = customLight,
                customDark = customDark
            )
        )
        assertEquals(AppearanceCodec.VALUE_CUSTOM, encodedCustom.getValue(AppearanceCodec.KEY_PALETTE_TYPE))
        val switched = AppearanceCodec.persistPaletteType(encodedCustom, PaletteType.Default)
        assertEquals(AppearanceCodec.VALUE_DEFAULT, switched.getValue(AppearanceCodec.KEY_PALETTE_TYPE))
        assertEquals("#8A2BE2", switched.getValue(AppearanceCodec.KEY_LIGHT_PRIMARY))
        val decodedDefault = AppearanceCodec.decodeFrom(switched)
        assertEquals(PaletteType.Default, decodedDefault.paletteType)
        assertEquals(ThemeSeeds.DefaultLight.canonical(), decodedDefault.activeSeeds(false).canonical())
        val legacy = AppearanceCodec.decodePaletteType(PaletteType.Custom.name)
        assertEquals(PaletteType.Custom, legacy)
    }

    private fun savedCustomSession(): PaletteSession {
        return PaletteSessionLogic.saveCustom(
            PaletteSessionLogic.selectCustom(PaletteSession(AppearanceSettings.Default)),
            customLight,
            customDark
        )
    }

    private fun parse(hex: String): Int {
        return (HexColor.parse(hex) as HexParseResult.Valid).rgb
    }
}
