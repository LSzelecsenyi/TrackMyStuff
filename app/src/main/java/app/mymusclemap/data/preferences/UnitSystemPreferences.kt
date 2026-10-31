package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.units.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.unitSystemDataStore by preferencesDataStore(name = "unit_system")

/**
 * Device-wide measurement preference. Missing data means metric so existing
 * installations keep their current screens. The device region never overwrites
 * a saved choice.
 */
class UnitSystemPreferences(context: Context) : UnitSystemBackupStore {
    private val dataStore = context.applicationContext.unitSystemDataStore
    private val stored = MutableStateFlow<UnitSystem?>(null)
    private val current = MutableStateFlow(UnitSystem.METRIC)

    val system: StateFlow<UnitSystem> = current

    override fun current(): UnitSystem = current.value

    fun storedOrNull(): UnitSystem? = stored.value

    suspend fun load() {
        val value = read()
        stored.value = value
        publish(value ?: UnitSystem.METRIC)
    }

    suspend fun read(): UnitSystem? {
        return dataStore.data.map { preferences ->
            UnitSystem.fromStored(preferences[KEY])
        }.first()
    }

    override suspend fun replace(system: UnitSystem) {
        write(system)
    }

    suspend fun write(system: UnitSystem) {
        dataStore.edit { preferences ->
            preferences[KEY] = system.name
        }
        stored.value = system
        publish(system)
    }

    private fun publish(system: UnitSystem) {
        current.value = system
        DisplayedUnitSystem.current = system
    }

    companion object {
        const val SETTINGS_KEY = "unit_system"
        private val KEY = stringPreferencesKey("unit_system")
    }
}

interface UnitSystemBackupStore {
    fun current(): UnitSystem
    suspend fun replace(system: UnitSystem)
}

/**
 * Read by notification formatting, which is not a composition. Compose screens
 * use [app.mymusclemap.ui.units.LocalUnitSystem] so they recompose on change.
 */
object DisplayedUnitSystem {
    @Volatile
    var current: UnitSystem = UnitSystem.METRIC
}

object UnitSystemBackup {
    fun decode(settings: Map<String, String>): UnitSystem {
        return UnitSystem.fromStored(settings[UnitSystemPreferences.SETTINGS_KEY]) ?: UnitSystem.METRIC
    }
}
