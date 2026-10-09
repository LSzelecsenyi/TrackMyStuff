package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.locale.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.languageDataStore by preferencesDataStore(name = "app_language")

/** Device-wide language choice. It is not cleared when the system language changes. */
class LanguagePreferences(context: Context) {
    private val dataStore = context.applicationContext.languageDataStore

    suspend fun read(): AppLanguage? {
        return dataStore.data.map { preferences ->
            AppLanguage.fromStored(preferences[KEY])
        }.first()
    }

    suspend fun write(language: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[KEY] = language.tag
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("language")
    }
}
