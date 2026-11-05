package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.units.UnitSystem
import app.mymusclemap.domain.units.WeightAdjustmentConfiguration
import app.mymusclemap.domain.units.WeightAdjustmentStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.weightAdjustmentDataStore by preferencesDataStore(name = "weight_adjustment_step")

/**
 * Device-wide plate jump for the workout weight buttons. Missing or unreadable
 * data keeps the metric and imperial defaults, so existing installations do not
 * change their step until the user chooses one.
 */
class WeightAdjustmentPreferences(context: Context) : WeightAdjustmentBackupStore {
    private val dataStore = context.applicationContext.weightAdjustmentDataStore
    private val current = MutableStateFlow(WeightAdjustmentConfiguration())

    val configuration: StateFlow<WeightAdjustmentConfiguration> = current

    override fun current(): WeightAdjustmentConfiguration = current.value

    suspend fun load() {
        publish(read())
    }

    suspend fun read(): WeightAdjustmentConfiguration {
        return dataStore.data.map { preferences ->
            decode(
                preferences[METRIC_KEY],
                preferences[IMPERIAL_KEY]
            )
        }.first()
    }

    override suspend fun replace(configuration: WeightAdjustmentConfiguration) {
        dataStore.edit { preferences ->
            preferences[METRIC_KEY] = WeightAdjustmentStep.storedText(configuration.metric)
            preferences[IMPERIAL_KEY] = WeightAdjustmentStep.storedText(configuration.imperial)
        }
        publish(configuration)
    }

    /**
     * @return false when [raw] is not a positive step. The stored configuration is left unchanged.
     */
    suspend fun write(system: UnitSystem, raw: String): Boolean {
        val parsed = WeightAdjustmentStep.parse(raw, system) ?: return false
        val key = if (system == UnitSystem.IMPERIAL) IMPERIAL_KEY else METRIC_KEY
        dataStore.edit { preferences ->
            preferences[key] = WeightAdjustmentStep.storedText(parsed)
        }
        publish(current.value.withStep(system, parsed))
        return true
    }

    private fun publish(configuration: WeightAdjustmentConfiguration) {
        current.value = configuration
    }

    companion object {
        const val METRIC_SETTINGS_KEY = "weight_adjustment_step_kg"
        const val IMPERIAL_SETTINGS_KEY = "weight_adjustment_step_lb"
        private val METRIC_KEY = stringPreferencesKey(METRIC_SETTINGS_KEY)
        private val IMPERIAL_KEY = stringPreferencesKey(IMPERIAL_SETTINGS_KEY)

        fun decode(metric: String?, imperial: String?): WeightAdjustmentConfiguration {
            return WeightAdjustmentConfiguration(
                metric = WeightAdjustmentStep.parse(metric.orEmpty(), UnitSystem.METRIC)
                    ?: WeightAdjustmentStep.defaultFor(UnitSystem.METRIC),
                imperial = WeightAdjustmentStep.parse(imperial.orEmpty(), UnitSystem.IMPERIAL)
                    ?: WeightAdjustmentStep.defaultFor(UnitSystem.IMPERIAL)
            )
        }
    }
}

interface WeightAdjustmentBackupStore {
    fun current(): WeightAdjustmentConfiguration
    suspend fun replace(configuration: WeightAdjustmentConfiguration)
}

object WeightAdjustmentBackup {
    fun decode(settings: Map<String, String>): WeightAdjustmentConfiguration {
        return WeightAdjustmentPreferences.decode(
            settings[WeightAdjustmentPreferences.METRIC_SETTINGS_KEY],
            settings[WeightAdjustmentPreferences.IMPERIAL_SETTINGS_KEY]
        )
    }
}
