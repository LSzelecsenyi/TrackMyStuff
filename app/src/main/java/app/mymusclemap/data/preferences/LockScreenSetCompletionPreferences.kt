package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.lockScreenSetCompletionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lock_screen_set_completion"
)

interface LockScreenSetCompletionStore {
    val enabled: Flow<Boolean>
    val runtimePermissionRequested: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun markRuntimePermissionRequested()

    companion object {
        val Off: LockScreenSetCompletionStore = object : LockScreenSetCompletionStore {
            override val enabled: Flow<Boolean> = flowOf(false)
            override val runtimePermissionRequested: Flow<Boolean> = flowOf(false)
            override suspend fun setEnabled(enabled: Boolean) = Unit
            override suspend fun markRuntimePermissionRequested() = Unit
        }
    }
}

class LockScreenSetCompletionPreferences(context: Context) : LockScreenSetCompletionStore {
    private val dataStore = context.applicationContext.lockScreenSetCompletionDataStore

    override val enabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ENABLED] == true
    }

    override val runtimePermissionRequested: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_RUNTIME_PERMISSION_REQUESTED] == true
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            if (enabled) {
                preferences[KEY_ENABLED] = true
            } else {
                preferences.remove(KEY_ENABLED)
            }
        }
    }

    override suspend fun markRuntimePermissionRequested() {
        dataStore.edit { preferences ->
            preferences[KEY_RUNTIME_PERMISSION_REQUESTED] = true
        }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_RUNTIME_PERMISSION_REQUESTED = booleanPreferencesKey("runtime_permission_requested")
    }
}
