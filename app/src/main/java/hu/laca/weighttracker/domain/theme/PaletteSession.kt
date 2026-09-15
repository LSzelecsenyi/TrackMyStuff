package hu.laca.weighttracker.domain.theme

data class PaletteSession(
    val appearance: AppearanceSettings,
    val draft: PaletteDraft? = null
)

object PaletteSessionLogic {
    fun selectDefault(session: PaletteSession): PaletteSession {
        return PaletteSession(
            appearance = session.appearance.copy(paletteType = PaletteType.Default),
            draft = null
        )
    }

    fun selectCustom(session: PaletteSession): PaletteSession {
        val light = session.appearance.customLight.detached()
        val dark = session.appearance.customDark.detached()
        return PaletteSession(
            appearance = session.appearance.copy(
                paletteType = PaletteType.Custom,
                customLight = light,
                customDark = dark
            ),
            draft = PaletteDraftLogic.fromSeeds(light, dark, session.draft?.editingDark ?: false)
        )
    }

    fun cancelDraft(session: PaletteSession): PaletteSession {
        return session.copy(
            draft = PaletteDraftLogic.fromSeeds(
                session.appearance.customLight.detached(),
                session.appearance.customDark.detached(),
                session.draft?.editingDark ?: false
            )
        )
    }

    fun resetCustomDraft(session: PaletteSession): PaletteSession {
        val draft = session.draft ?: return session
        return session.copy(
            draft = PaletteDraftLogic.resetToFactory(draft)
        )
    }

    fun saveCustom(session: PaletteSession, light: ThemeSeeds, dark: ThemeSeeds): PaletteSession {
        val lightCopy = light.detached()
        val darkCopy = dark.detached()
        return PaletteSession(
            appearance = session.appearance.copy(
                paletteType = PaletteType.Custom,
                customLight = lightCopy,
                customDark = darkCopy
            ),
            draft = PaletteDraftLogic.fromSeeds(
                lightCopy,
                darkCopy,
                session.draft?.editingDark ?: false
            )
        )
    }
}

class InMemoryAppearanceStore(
    initial: Map<String, String> = emptyMap()
) {
    private val values = initial.toMutableMap()

    fun snapshot(): Map<String, String> = values.toMap()

    fun read(): AppearanceSettings = AppearanceCodec.decodeFrom(values)

    fun writePaletteType(type: PaletteType) {
        values[AppearanceCodec.KEY_PALETTE_TYPE] = AppearanceCodec.encodePaletteType(type)
    }

    fun writeCustomPalette(light: ThemeSeeds, dark: ThemeSeeds) {
        val encoded = AppearanceCodec.encode(
            AppearanceSettings(
                mode = read().mode,
                paletteType = PaletteType.Custom,
                customLight = light.detached(),
                customDark = dark.detached()
            )
        )
        encoded.forEach { (key, value) ->
            if (key != AppearanceCodec.KEY_THEME) {
                values[key] = value
            }
        }
    }

    fun writeThemeMode(mode: ThemeMode) {
        values[AppearanceCodec.KEY_THEME] = mode.name
    }

    fun recreate(): InMemoryAppearanceStore = InMemoryAppearanceStore(snapshot())
}
