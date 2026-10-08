package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

private val Context.founderRecognitionDataStore by preferencesDataStore(
    name = FounderRecognitionStore.PREFERENCES_NAME
)

/**
 * Permanent Founding Member recognition for accounts the backend has approved.
 *
 * This is not an entitlement. It does not grant Pro, and it is not consulted by
 * [app.mymusclemap.domain.entitlement.EntitlementResolver]. The 24-hour entitlement
 * cache still decides Founder Pro. A signed-out session or a different account cannot
 * read another user's record. Reinstall clears this store; the next backend entitlement
 * read writes it again.
 */
class FounderRecognitionStore(
    context: Context,
    private val sessionUserId: () -> String? = { null },
    private val onChanged: () -> Unit = {}
) {
    private val dataStore = context.applicationContext.founderRecognitionDataStore
    private val mutex = Mutex()
    private var memory: Map<String, Instant?> = emptyMap()

    fun current(): FounderRecognition {
        val session = sessionUserId() ?: return FounderRecognition()
        if (!memory.containsKey(session)) {
            return FounderRecognition()
        }
        return FounderRecognition(recognized = true, grantedAt = memory[session])
    }

    suspend fun load() {
        val loaded = read()
        mutex.withLock {
            memory = loaded
        }
        onChanged()
    }

    /** Test isolation. Sign-out does not call this; it only hides the signed-in account. */
    suspend fun clear() {
        mutex.withLock {
            memory = emptyMap()
            dataStore.edit { prefs -> prefs.clear() }
        }
    }

    suspend fun confirm(userId: String, grantedAt: Instant?) {
        if (userId.isBlank()) {
            return
        }
        mutex.withLock {
            val existing = memory[userId]
            val nextGrantedAt = grantedAt ?: existing
            if (memory.containsKey(userId) && nextGrantedAt == existing) {
                return
            }
            memory = memory + (userId to nextGrantedAt)
            dataStore.edit { prefs ->
                prefs[KEY_RECORDS] = encode(memory)
            }
        }
        onChanged()
    }

    private suspend fun read(): Map<String, Instant?> {
        val raw = dataStore.data.first()[KEY_RECORDS]
        return decode(raw)
    }

    private fun encode(records: Map<String, Instant?>): String {
        return records.entries.joinToString(";") { (userId, grantedAt) ->
            val millis = grantedAt?.toEpochMilli()?.toString().orEmpty()
            "$userId=$millis"
        }
    }

    private fun decode(raw: String?): Map<String, Instant?> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        val records = linkedMapOf<String, Instant?>()
        raw.split(';').forEach { part ->
            val bits = part.split('=', limit = 2)
            if (bits.size != 2 || bits[0].isBlank()) {
                return@forEach
            }
            val grantedAt = bits[1].toLongOrNull()?.let(Instant::ofEpochMilli)
            records[bits[0]] = grantedAt
        }
        return records
    }

    companion object {
        const val PREFERENCES_NAME = "founder_recognition"
        private val KEY_RECORDS = stringPreferencesKey("recognized_accounts")
    }
}

/** Local Founding Member record. It has no Pro field. */
data class FounderRecognition(
    val recognized: Boolean = false,
    val grantedAt: Instant? = null
)
