package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

private val Context.founderEntitlementDataStore by preferencesDataStore(
    name = FounderEntitlementCache.PREFERENCES_NAME
)

/**
 * Cache of the last backend Founder entitlement.
 *
 * Trusted through [validUntil], which is [TRUST] after a successful read.
 * After that instant, Founder-derived Pro is off until the next successful read.
 * Clearing the bearer drops the cache immediately. This store is not a grant by itself
 * and is not part of the portable backup.
 */
class FounderEntitlementCache(
    context: Context,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit = {}
) {
    private val dataStore = context.applicationContext.founderEntitlementDataStore
    private val mutex = Mutex()
    private val generation = AtomicInteger()
    private var memory = BackendFounderEntitlement()

    fun current(): BackendFounderEntitlement = memory

    suspend fun load() {
        val ticket = generation.get()
        val loaded = read()
        mutex.withLock {
            if (generation.get() != ticket) {
                return
            }
            memory = loaded
        }
        onChanged()
    }

    suspend fun save(temporaryFounderPro: Boolean, founderLifetime: Boolean, validUntil: Instant) {
        val next = BackendFounderEntitlement(
            temporaryFounderPro = temporaryFounderPro,
            founderLifetime = founderLifetime,
            validUntil = validUntil
        )
        mutex.withLock {
            generation.incrementAndGet()
            memory = next
            dataStore.edit { prefs ->
                prefs[KEY_TEMPORARY] = temporaryFounderPro
                prefs[KEY_LIFETIME] = founderLifetime
                prefs[KEY_VALID_UNTIL] = validUntil.toEpochMilli()
            }
        }
        onChanged()
    }

    fun drop() {
        generation.incrementAndGet()
        memory = BackendFounderEntitlement()
        onChanged()
        scope.launch {
            val ticket = generation.get()
            dataStore.edit { prefs ->
                if (generation.get() != ticket) {
                    return@edit
                }
                prefs.remove(KEY_TEMPORARY)
                prefs.remove(KEY_LIFETIME)
                prefs.remove(KEY_VALID_UNTIL)
            }
        }
    }

    private suspend fun read(): BackendFounderEntitlement {
        val prefs = dataStore.data.first()
        val until = prefs[KEY_VALID_UNTIL] ?: return BackendFounderEntitlement()
        return BackendFounderEntitlement(
            temporaryFounderPro = prefs[KEY_TEMPORARY] == true,
            founderLifetime = prefs[KEY_LIFETIME] == true,
            validUntil = Instant.ofEpochMilli(until)
        )
    }

    companion object {
        const val PREFERENCES_NAME = "founder_entitlement_cache"
        val TRUST: Duration = Duration.ofHours(24)
        private val KEY_TEMPORARY = booleanPreferencesKey("temporary_founder_pro")
        private val KEY_LIFETIME = booleanPreferencesKey("founder_lifetime")
        private val KEY_VALID_UNTIL = longPreferencesKey("valid_until_epoch_milli")
    }
}
