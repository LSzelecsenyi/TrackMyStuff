package hu.laca.weighttracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "weight_tracker_settings"
)

enum class ThemePreference {
    System,
    Light,
    Dark
}

class ThemePreferences(context: Context) {
    private val dataStore = context.applicationContext.themeDataStore

    val theme: Flow<ThemePreference> = dataStore.data.map { preferences ->
        when (preferences[KEY_THEME]) {
            ThemePreference.Light.name -> ThemePreference.Light
            ThemePreference.Dark.name -> ThemePreference.Dark
            else -> ThemePreference.System
        }
    }

    suspend fun setTheme(preference: ThemePreference) {
        dataStore.edit { it[KEY_THEME] = preference.name }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_preference")
    }
}
