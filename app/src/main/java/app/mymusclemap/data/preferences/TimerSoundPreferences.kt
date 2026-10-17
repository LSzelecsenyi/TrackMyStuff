package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.timerSoundDataStore by preferencesDataStore(name = "timer_sounds")

interface TimerSoundStore {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)

    companion object {
        val On: TimerSoundStore = object : TimerSoundStore {
            override val enabled: Flow<Boolean> = flowOf(true)
            override suspend fun setEnabled(enabled: Boolean) = Unit
        }
    }
}

class TimerSoundPreferences(context: Context) : TimerSoundStore {
    private val dataStore = context.applicationContext.timerSoundDataStore

    override val enabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ENABLED] != false
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = enabled
        }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
    }
}
