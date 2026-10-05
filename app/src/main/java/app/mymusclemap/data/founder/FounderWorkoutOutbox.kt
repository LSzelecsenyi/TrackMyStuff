package app.mymusclemap.data.founder

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.founderWorkoutOutboxDataStore by preferencesDataStore(
    name = FounderWorkoutOutbox.PREFERENCES_NAME
)

data class PendingFounderWorkout(
    val clientWorkoutId: String,
    val completedAtEpochMilli: Long,
    val localDate: String,
    val observation: FounderWorkoutObservation? = null
)

/**
 * Durable queue of Founder qualification events. It is not a workout backup.
 * The same [PendingFounderWorkout.clientWorkoutId] is stored once.
 */
class FounderWorkoutOutbox(context: Context) {
    private val dataStore = context.applicationContext.founderWorkoutOutboxDataStore

    suspend fun pending(): List<PendingFounderWorkout> {
        val raw = dataStore.data.first()[KEY_EVENTS].orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("clientWorkoutId")
                val completedAt = item.optLong("completedAtEpochMilli", Long.MIN_VALUE)
                val localDate = item.optString("localDate")
                if (id.isBlank() || completedAt == Long.MIN_VALUE || localDate.isBlank()) {
                    continue
                }
                add(
                    PendingFounderWorkout(
                        clientWorkoutId = id,
                        completedAtEpochMilli = completedAt,
                        localDate = localDate,
                        observation = item.toObservation()
                    )
                )
            }
        }
    }

    suspend fun enqueue(event: PendingFounderWorkout) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_EVENTS])
            if (current.any { it.clientWorkoutId == event.clientWorkoutId }) {
                return@edit
            }
            prefs[KEY_EVENTS] = encode(current + event)
        }
    }

    suspend fun remove(clientWorkoutId: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_EVENTS]).filter { it.clientWorkoutId != clientWorkoutId }
            if (current.isEmpty()) {
                prefs.remove(KEY_EVENTS)
            } else {
                prefs[KEY_EVENTS] = encode(current)
            }
        }
    }

    companion object {
        const val PREFERENCES_NAME = "founder_workout_outbox"
        private val KEY_EVENTS = stringPreferencesKey("events")

        private fun decode(raw: String?): List<PendingFounderWorkout> {
            if (raw.isNullOrBlank()) {
                return emptyList()
            }
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("clientWorkoutId")
                    val completedAt = item.optLong("completedAtEpochMilli", Long.MIN_VALUE)
                    val localDate = item.optString("localDate")
                    if (id.isBlank() || completedAt == Long.MIN_VALUE || localDate.isBlank()) {
                        continue
                    }
                    add(
                        PendingFounderWorkout(
                            clientWorkoutId = id,
                            completedAtEpochMilli = completedAt,
                            localDate = localDate,
                            observation = item.toObservation()
                        )
                    )
                }
            }
        }

        private fun JSONObject.toObservation(): FounderWorkoutObservation? {
            if (!has("displayName") && !has("durationSeconds") && !has("exerciseCount") &&
                !has("completedSetCount") && !has("fromTemplate") && !has("usedExternalLoad")
            ) {
                return null
            }
            return FounderWorkoutObservation(
                displayName = optionalString("displayName"),
                durationSeconds = optionalInt("durationSeconds"),
                exerciseCount = optionalInt("exerciseCount"),
                completedSetCount = optionalInt("completedSetCount"),
                fromTemplate = optionalBoolean("fromTemplate"),
                usedExternalLoad = optionalBoolean("usedExternalLoad")
            )
        }

        private fun JSONObject.putObservation(observation: FounderWorkoutObservation) {
            observation.displayName?.let { put("displayName", it) }
            observation.durationSeconds?.let { put("durationSeconds", it) }
            observation.exerciseCount?.let { put("exerciseCount", it) }
            observation.completedSetCount?.let { put("completedSetCount", it) }
            observation.fromTemplate?.let { put("fromTemplate", it) }
            observation.usedExternalLoad?.let { put("usedExternalLoad", it) }
        }

        private fun JSONObject.optionalString(key: String): String? {
            if (!has(key) || isNull(key)) {
                return null
            }
            return getString(key).ifBlank { null }
        }

        private fun JSONObject.optionalInt(key: String): Int? {
            if (!has(key) || isNull(key)) {
                return null
            }
            return getInt(key)
        }

        private fun JSONObject.optionalBoolean(key: String): Boolean? {
            if (!has(key) || isNull(key)) {
                return null
            }
            return getBoolean(key)
        }

        private fun encode(events: List<PendingFounderWorkout>): String {
            val array = JSONArray()
            events.forEach { event ->
                val item = JSONObject()
                    .put("clientWorkoutId", event.clientWorkoutId)
                    .put("completedAtEpochMilli", event.completedAtEpochMilli)
                    .put("localDate", event.localDate)
                event.observation?.let { observation -> item.putObservation(observation) }
                array.put(item)
            }
            return array.toString()
        }
    }
}
