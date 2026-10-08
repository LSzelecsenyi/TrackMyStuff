package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.founderApprovalCelebrationDataStore by preferencesDataStore(
    name = FounderApprovalCelebrationStore.PREFERENCES_NAME
)

/**
 * Accounts that have already seen the Founder approval celebration.
 *
 * This is UI metadata keyed by backend user id. It does not grant Pro, change
 * Founder recognition, or participate in entitlement resolution. Sign-out does
 * not clear it. Another account on the same device keeps its own flag.
 */
class FounderApprovalCelebrationStore(
    context: Context,
    private val onChanged: () -> Unit = {}
) {
    private val dataStore = context.applicationContext.founderApprovalCelebrationDataStore
    private val mutex = Mutex()
    private var memory: Set<String> = emptySet()
    private var loaded = false

    fun isLoaded(): Boolean = loaded

    fun isAcknowledged(userId: String): Boolean {
        return userId.isNotBlank() && memory.contains(userId)
    }

    suspend fun load() {
        val stored = read()
        mutex.withLock {
            memory = stored
            loaded = true
        }
        onChanged()
    }

    suspend fun acknowledge(userId: String) {
        if (userId.isBlank() || memory.contains(userId)) {
            return
        }
        mutex.withLock {
            if (memory.contains(userId)) {
                return
            }
            memory = memory + userId
            dataStore.edit { prefs ->
                prefs[KEY_ACCOUNTS] = encode(memory)
            }
        }
        onChanged()
    }

    /** Test isolation. Sign-out does not call this. */
    suspend fun clear() {
        mutex.withLock {
            memory = emptySet()
            loaded = true
            dataStore.edit { prefs -> prefs.clear() }
        }
    }

    private suspend fun read(): Set<String> {
        val raw = dataStore.data.first()[KEY_ACCOUNTS]
        return decode(raw)
    }

    private fun encode(accounts: Set<String>): String {
        return accounts.joinToString(";")
    }

    private fun decode(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) {
            return emptySet()
        }
        return raw.split(';').filter { it.isNotBlank() }.toSet()
    }

    companion object {
        const val PREFERENCES_NAME = "founder_approval_celebration"
        private val KEY_ACCOUNTS = stringPreferencesKey("acknowledged_accounts")
    }
}
