package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.entitlement.ProDiscoveryRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

private val Context.proDiscoveryStore by preferencesDataStore(name = "pro_discovery")
private val Context.proDiscoverySimulationStore by preferencesDataStore(name = "pro_discovery_simulation")
private val Context.promotionAvailabilityStore by preferencesDataStore(name = "promotion_availability")

/**
 * Local trial record. The real store and the debug simulation store are separate
 * files so a debug countdown cannot consume the one-time trial.
 *
 * These preferences are not part of the portable ZIP backup. Reinstalling the app
 * without an account clears them.
 */
interface ProDiscoveryStateStore {
    fun current(): ProDiscoveryRecord
    fun fingerprint(): String?
    suspend fun load()
    suspend fun dismissPopup()
    suspend fun dismissWarning()
    suspend fun activate(activatedAt: Instant, expiresAt: Instant)
    suspend fun reset(fingerprint: String)
    suspend fun clear()
}

enum class ProDiscoveryStoreKind {
    REAL,
    SIMULATION
}

class ProDiscoveryStore(
    context: Context,
    kind: ProDiscoveryStoreKind,
    private val onChanged: () -> Unit = {},
    userId: String? = null
) : ProDiscoveryStateStore {
    private val dataStore = if (userId == null) {
        when (kind) {
            ProDiscoveryStoreKind.REAL -> context.applicationContext.proDiscoveryStore
            ProDiscoveryStoreKind.SIMULATION -> context.applicationContext.proDiscoverySimulationStore
        }
    } else {
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ),
            produceFile = {
                val name = if (kind == ProDiscoveryStoreKind.REAL) "pro_discovery" else "pro_discovery_simulation"
                java.io.File(context.applicationContext.filesDir, "datastore/${name}_$userId.preferences_pb")
            }
        )
    }
    private val mutex = Mutex()
    private var memory = ProDiscoveryRecord()
    private var storedFingerprint: String? = null

    override fun current(): ProDiscoveryRecord = memory

    override fun fingerprint(): String? = storedFingerprint

    override suspend fun load() {
        val prefs = dataStore.data.first()
        val loaded = ProDiscoveryRecord(
            popupDismissed = prefs[POPUP_DISMISSED] == true,
            activatedAt = prefs[ACTIVATED_AT]?.let(Instant::ofEpochMilli),
            expiresAt = prefs[EXPIRES_AT]?.let(Instant::ofEpochMilli),
            warningDismissed = prefs[WARNING_DISMISSED] == true
        )
        mutex.withLock {
            memory = loaded
            storedFingerprint = prefs[FINGERPRINT]
        }
        onChanged()
    }

    override suspend fun dismissPopup() {
        if (memory.popupDismissed) {
            return
        }
        mutex.withLock {
            memory = memory.copy(popupDismissed = true)
            dataStore.edit { prefs -> prefs[POPUP_DISMISSED] = true }
        }
        onChanged()
    }

    override suspend fun dismissWarning() {
        if (memory.warningDismissed) {
            return
        }
        mutex.withLock {
            memory = memory.copy(warningDismissed = true)
            dataStore.edit { prefs -> prefs[WARNING_DISMISSED] = true }
        }
        onChanged()
    }

    override suspend fun activate(activatedAt: Instant, expiresAt: Instant) {
        if (memory.activatedAt != null) {
            return
        }
        mutex.withLock {
            if (memory.activatedAt != null) {
                return
            }
            memory = memory.copy(activatedAt = activatedAt, expiresAt = expiresAt)
            dataStore.edit { prefs ->
                prefs[ACTIVATED_AT] = activatedAt.toEpochMilli()
                prefs[EXPIRES_AT] = expiresAt.toEpochMilli()
            }
        }
        onChanged()
    }

    override suspend fun reset(fingerprint: String) {
        mutex.withLock {
            memory = ProDiscoveryRecord()
            storedFingerprint = fingerprint
            dataStore.edit { prefs ->
                prefs.clear()
                prefs[FINGERPRINT] = fingerprint
            }
        }
        onChanged()
    }

    override suspend fun clear() {
        mutex.withLock {
            memory = ProDiscoveryRecord()
            storedFingerprint = null
            dataStore.edit { prefs -> prefs.clear() }
        }
        onChanged()
    }

    private companion object {
        val POPUP_DISMISSED = booleanPreferencesKey("popup_dismissed")
        val ACTIVATED_AT = longPreferencesKey("activated_at")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val WARNING_DISMISSED = booleanPreferencesKey("warning_dismissed")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
    }
}

/**
 * Last successful read of whether general promotions are enabled.
 * Unknown stays disabled so a new offer cannot start while Founder availability is unread.
 */
class PromotionAvailabilityStore(
    context: Context,
    private val onChanged: () -> Unit = {}
) {
    private val dataStore = context.applicationContext.promotionAvailabilityStore
    private val mutex = Mutex()
    private var enabled = false

    fun promotionsEnabled(): Boolean = enabled

    suspend fun load() {
        val stored = dataStore.data.first()[ENABLED] == true
        mutex.withLock { enabled = stored }
        onChanged()
    }

    suspend fun setPromotionsEnabled(value: Boolean) {
        if (enabled == value) {
            return
        }
        mutex.withLock {
            enabled = value
            dataStore.edit { prefs -> prefs[ENABLED] = value }
        }
        onChanged()
    }

    suspend fun clear() {
        mutex.withLock {
            enabled = false
            dataStore.edit { prefs -> prefs.clear() }
        }
        onChanged()
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("promotions_enabled")
    }
}
