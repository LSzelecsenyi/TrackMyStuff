package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.onboarding.OnboardingFlags
import app.mymusclemap.domain.theme.AppearanceCodec
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.theme.PaletteType
import app.mymusclemap.domain.theme.ThemeMode
import app.mymusclemap.domain.theme.ThemeSeeds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    suspend fun current(): AppearanceSettings = appearance.first()

    /**
     * Progressive onboarding lives in DataStore, not in the v1 backup file.
     * Appearance restore keeps these flags so a backup cannot re-open onboarding
     * or wipe in-progress discoveries on the current device.
     */
    val onboardingFlags: Flow<OnboardingFlags> = dataStore.data.map { preferences ->
        OnboardingFlags(
            completed = preferences[KEY_ONBOARDING_COMPLETED] == true,
            started = preferences[KEY_ONBOARDING_STARTED] == true,
            heatmapSeen = preferences[KEY_ONBOARDING_HEATMAP_SEEN] == true,
            weightIntroduced = preferences[KEY_ONBOARDING_WEIGHT_INTRODUCED] == true,
            weightChartSeen = preferences[KEY_ONBOARDING_WEIGHT_CHART_SEEN] == true,
            calendarSeen = preferences[KEY_ONBOARDING_CALENDAR_SEEN] == true,
            reminderDismissed = preferences[KEY_ONBOARDING_REMINDER_DISMISSED] == true
        )
    }

    suspend fun currentOnboardingFlags(): OnboardingFlags = onboardingFlags.first()

    suspend fun isOnboardingCompleted(): Boolean {
        return currentOnboardingFlags().completed
    }

    suspend fun isOnboardingStarted(): Boolean {
        return currentOnboardingFlags().started
    }

    suspend fun markOnboardingCompleted() {
        setOnboardingCompleted(true)
    }

    suspend fun markOnboardingStarted() {
        dataStore.edit { it[KEY_ONBOARDING_STARTED] = true }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            if (completed) {
                prefs[KEY_ONBOARDING_COMPLETED] = true
            } else {
                prefs.remove(KEY_ONBOARDING_COMPLETED)
            }
        }
    }

    suspend fun setHeatmapSeen() {
        dataStore.edit { it[KEY_ONBOARDING_HEATMAP_SEEN] = true }
    }

    suspend fun setWeightIntroduced() {
        dataStore.edit { it[KEY_ONBOARDING_WEIGHT_INTRODUCED] = true }
    }

    suspend fun setWeightChartSeen() {
        dataStore.edit { it[KEY_ONBOARDING_WEIGHT_CHART_SEEN] = true }
    }

    suspend fun setCalendarSeen() {
        dataStore.edit { it[KEY_ONBOARDING_CALENDAR_SEEN] = true }
    }

    suspend fun setReminderDismissed() {
        dataStore.edit { it[KEY_ONBOARDING_REMINDER_DISMISSED] = true }
    }

    suspend fun clearOnboardingProgress() {
        dataStore.edit { prefs ->
            ONBOARDING_KEYS.forEach { key -> prefs.remove(key) }
        }
    }

    suspend fun replaceAppearance(settings: AppearanceSettings) {
        val encoded = AppearanceCodec.encode(settings)
        dataStore.edit { prefs ->
            val preserved = ONBOARDING_KEYS.associateWith { key -> prefs[key] }
            prefs.clear()
            prefs[KEY_THEME] = encoded.getValue(AppearanceCodec.KEY_THEME)
            prefs[KEY_PALETTE_TYPE] = encoded.getValue(AppearanceCodec.KEY_PALETTE_TYPE)
            prefs[KEY_LIGHT_BACKGROUND] = encoded.getValue(AppearanceCodec.KEY_LIGHT_BACKGROUND)
            prefs[KEY_LIGHT_PRIMARY] = encoded.getValue(AppearanceCodec.KEY_LIGHT_PRIMARY)
            prefs[KEY_LIGHT_SECONDARY] = encoded.getValue(AppearanceCodec.KEY_LIGHT_SECONDARY)
            prefs[KEY_LIGHT_TERTIARY] = encoded.getValue(AppearanceCodec.KEY_LIGHT_TERTIARY)
            prefs[KEY_DARK_BACKGROUND] = encoded.getValue(AppearanceCodec.KEY_DARK_BACKGROUND)
            prefs[KEY_DARK_PRIMARY] = encoded.getValue(AppearanceCodec.KEY_DARK_PRIMARY)
            prefs[KEY_DARK_SECONDARY] = encoded.getValue(AppearanceCodec.KEY_DARK_SECONDARY)
            prefs[KEY_DARK_TERTIARY] = encoded.getValue(AppearanceCodec.KEY_DARK_TERTIARY)
            preserved.forEach { (key, value) ->
                if (value != null) {
                    prefs[key] = value
                }
            }
        }
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
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_ONBOARDING_STARTED = booleanPreferencesKey("onboarding_started")
        val KEY_ONBOARDING_HEATMAP_SEEN = booleanPreferencesKey("onboarding_heatmap_seen")
        val KEY_ONBOARDING_WEIGHT_INTRODUCED = booleanPreferencesKey("onboarding_weight_introduced")
        val KEY_ONBOARDING_WEIGHT_CHART_SEEN = booleanPreferencesKey("onboarding_weight_chart_seen")
        val KEY_ONBOARDING_CALENDAR_SEEN = booleanPreferencesKey("onboarding_calendar_seen")
        val KEY_ONBOARDING_REMINDER_DISMISSED = booleanPreferencesKey("onboarding_reminder_dismissed")
        val ONBOARDING_KEYS = listOf(
            KEY_ONBOARDING_COMPLETED,
            KEY_ONBOARDING_STARTED,
            KEY_ONBOARDING_HEATMAP_SEEN,
            KEY_ONBOARDING_WEIGHT_INTRODUCED,
            KEY_ONBOARDING_WEIGHT_CHART_SEEN,
            KEY_ONBOARDING_CALENDAR_SEEN,
            KEY_ONBOARDING_REMINDER_DISMISSED
        )
    }
}
