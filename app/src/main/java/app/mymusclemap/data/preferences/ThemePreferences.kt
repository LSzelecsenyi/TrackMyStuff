package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.theme.AppearanceCodec
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.theme.PaletteType
import app.mymusclemap.domain.theme.ThemeMode
import app.mymusclemap.domain.theme.ThemeSeeds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "weight_tracker_settings"
)

class ThemePreferences(context: Context) {
    private val dataStore = context.applicationContext.themeDataStore

    val appearance: Flow<AppearanceSettings> = dataStore.data.map { preferences ->
        AppearanceCodec.decode(
            themeMode = preferences[KEY_THEME],
            paletteType = preferences[KEY_PALETTE_TYPE],
            lightBackground = preferences[KEY_LIGHT_BACKGROUND],
            lightPrimary = preferences[KEY_LIGHT_PRIMARY],
            lightSecondary = preferences[KEY_LIGHT_SECONDARY],
            lightTertiary = preferences[KEY_LIGHT_TERTIARY],
            darkBackground = preferences[KEY_DARK_BACKGROUND],
            darkPrimary = preferences[KEY_DARK_PRIMARY],
            darkSecondary = preferences[KEY_DARK_SECONDARY],
            darkTertiary = preferences[KEY_DARK_TERTIARY]
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setPaletteType(type: PaletteType) {
        dataStore.edit { prefs ->
            prefs[KEY_PALETTE_TYPE] = AppearanceCodec.encodePaletteType(type)
        }
    }

    suspend fun saveCustomPalette(light: ThemeSeeds, dark: ThemeSeeds) {
        val encoded = AppearanceCodec.encode(
            AppearanceSettings(
                mode = ThemeMode.System,
                paletteType = PaletteType.Custom,
                customLight = light,
                customDark = dark
            )
        )
        dataStore.edit { prefs ->
            prefs[KEY_PALETTE_TYPE] = AppearanceCodec.encodePaletteType(PaletteType.Custom)
            prefs[KEY_LIGHT_BACKGROUND] = encoded.getValue(AppearanceCodec.KEY_LIGHT_BACKGROUND)
            prefs[KEY_LIGHT_PRIMARY] = encoded.getValue(AppearanceCodec.KEY_LIGHT_PRIMARY)
            prefs[KEY_LIGHT_SECONDARY] = encoded.getValue(AppearanceCodec.KEY_LIGHT_SECONDARY)
            prefs[KEY_LIGHT_TERTIARY] = encoded.getValue(AppearanceCodec.KEY_LIGHT_TERTIARY)
            prefs[KEY_DARK_BACKGROUND] = encoded.getValue(AppearanceCodec.KEY_DARK_BACKGROUND)
            prefs[KEY_DARK_PRIMARY] = encoded.getValue(AppearanceCodec.KEY_DARK_PRIMARY)
            prefs[KEY_DARK_SECONDARY] = encoded.getValue(AppearanceCodec.KEY_DARK_SECONDARY)
            prefs[KEY_DARK_TERTIARY] = encoded.getValue(AppearanceCodec.KEY_DARK_TERTIARY)
        }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey(AppearanceCodec.KEY_THEME)
        val KEY_PALETTE_TYPE = stringPreferencesKey(AppearanceCodec.KEY_PALETTE_TYPE)
        val KEY_LIGHT_BACKGROUND = stringPreferencesKey(AppearanceCodec.KEY_LIGHT_BACKGROUND)
        val KEY_LIGHT_PRIMARY = stringPreferencesKey(AppearanceCodec.KEY_LIGHT_PRIMARY)
        val KEY_LIGHT_SECONDARY = stringPreferencesKey(AppearanceCodec.KEY_LIGHT_SECONDARY)
        val KEY_LIGHT_TERTIARY = stringPreferencesKey(AppearanceCodec.KEY_LIGHT_TERTIARY)
        val KEY_DARK_BACKGROUND = stringPreferencesKey(AppearanceCodec.KEY_DARK_BACKGROUND)
        val KEY_DARK_PRIMARY = stringPreferencesKey(AppearanceCodec.KEY_DARK_PRIMARY)
        val KEY_DARK_SECONDARY = stringPreferencesKey(AppearanceCodec.KEY_DARK_SECONDARY)
        val KEY_DARK_TERTIARY = stringPreferencesKey(AppearanceCodec.KEY_DARK_TERTIARY)
    }
}
