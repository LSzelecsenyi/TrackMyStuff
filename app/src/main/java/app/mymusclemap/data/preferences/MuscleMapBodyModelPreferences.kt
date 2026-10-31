package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.muscleMapBodyModelDataStore by preferencesDataStore(name = "muscle_map_body_model")

/**
 * Device-wide heatmap artwork preference. Missing or unreadable data means
 * [MuscleMapBodyModel.MALE], so existing installations keep the current drawing.
 * A normal application update does not write over a saved choice.
 */
class MuscleMapBodyModelPreferences(context: Context) : MuscleMapBodyModelBackupStore {
    private val dataStore = context.applicationContext.muscleMapBodyModelDataStore
    private val stored = MutableStateFlow<MuscleMapBodyModel?>(null)
    private val current = MutableStateFlow(MuscleMapBodyModel.MALE)

    val model: StateFlow<MuscleMapBodyModel> = current

    override fun current(): MuscleMapBodyModel = current.value

    fun storedOrNull(): MuscleMapBodyModel? = stored.value

    suspend fun load() {
        val value = read()
        stored.value = value
        publish(value ?: MuscleMapBodyModel.MALE)
    }

    suspend fun read(): MuscleMapBodyModel? {
        return dataStore.data.map { preferences ->
            MuscleMapBodyModel.fromStored(preferences[KEY])
        }.first()
    }

    override suspend fun replace(model: MuscleMapBodyModel) {
        write(model)
    }

    suspend fun write(model: MuscleMapBodyModel) {
        dataStore.edit { preferences ->
            preferences[KEY] = model.name
        }
        stored.value = model
        publish(model)
    }

    private fun publish(model: MuscleMapBodyModel) {
        current.value = model
        DisplayedMuscleMapBodyModel.current = model
    }

    companion object {
        const val SETTINGS_KEY = "muscle_map_body_model"
        private val KEY = stringPreferencesKey("muscle_map_body_model")
    }
}

interface MuscleMapBodyModelBackupStore {
    fun current(): MuscleMapBodyModel
    suspend fun replace(model: MuscleMapBodyModel)
}

/**
 * Read by the widget renderer, which is not a composition. Compose screens use
 * [app.mymusclemap.ui.components.musclemap.LocalMuscleMapBodyModel].
 */
object DisplayedMuscleMapBodyModel {
    @Volatile
    var current: MuscleMapBodyModel = MuscleMapBodyModel.MALE
}

object MuscleMapBodyModelBackup {
    fun decode(settings: Map<String, String>): MuscleMapBodyModel {
        return MuscleMapBodyModel.fromStored(settings[MuscleMapBodyModelPreferences.SETTINGS_KEY])
            ?: MuscleMapBodyModel.MALE
    }
}
