package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.workout.TimerAudioMode
import app.mymusclemap.domain.workout.TimerAudioPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.timerSoundDataStore by preferencesDataStore(name = "timer_sounds")

interface TimerSoundStore {
    val mode: Flow<TimerAudioMode>
    suspend fun setMode(mode: TimerAudioMode)
    suspend fun migrate() {}

    companion object {
        val On: TimerSoundStore = object : TimerSoundStore {
            override val mode: Flow<TimerAudioMode> = flowOf(TimerAudioMode.BEEPS)
            override suspend fun setMode(mode: TimerAudioMode) = Unit
        }
    }
}

class TimerSoundPreferences(context: Context) : TimerSoundStore {
    private val dataStore = context.applicationContext.timerSoundDataStore

    override val mode: Flow<TimerAudioMode> = dataStore.data.map { preferences ->
        val legacy = if (preferences.contains(KEY_ENABLED)) preferences[KEY_ENABLED] else null
        TimerAudioPreference.resolve(preferences[KEY_MODE], legacy)
    }

    override suspend fun setMode(mode: TimerAudioMode) {
        dataStore.edit { preferences ->
            preferences[KEY_MODE] = mode.name
            preferences.remove(KEY_ENABLED)
        }
    }

    override suspend fun migrate() {
        dataStore.edit { preferences ->
            val decision = TimerAudioPreference.migration(
                storedMode = preferences[KEY_MODE],
                legacyPresent = preferences.contains(KEY_ENABLED),
                legacyEnabled = preferences[KEY_ENABLED] != false
            )
            if (decision.writeMode != null) {
                preferences[KEY_MODE] = decision.writeMode
            }
            if (decision.removeLegacy) {
                preferences.remove(KEY_ENABLED)
            }
        }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_MODE = stringPreferencesKey("mode")
    }
}
