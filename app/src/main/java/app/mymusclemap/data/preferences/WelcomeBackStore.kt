package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.entitlement.WelcomeBackRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant

private val Context.welcomeBackSimulationStore by preferencesDataStore(name = "welcome_back_simulation")

/**
 * Welcome Back UI and the cached backend grant.
 * The real file is named for the Strict user. The simulation file is debug-only
 * and is never sent to the backend.
 */
interface WelcomeBackStateStore {
    fun current(): WelcomeBackRecord
    fun fingerprint(): String?
    suspend fun load()
    suspend fun noteQualifying(workoutId: String)
    suspend fun dismissPopup()
    suspend fun dismissWarning()
    suspend fun activate(
        activatedAt: Instant,
        expiresAt: Instant,
        cooldownUntil: Instant,
        workoutId: String
    )
    suspend fun applyRemote(record: WelcomeBackRecord?)
    suspend fun reset(fingerprint: String)
    suspend fun clear()
}

enum class WelcomeBackStoreKind {
    REAL,
    SIMULATION
}

class WelcomeBackStore(
    context: Context,
    kind: WelcomeBackStoreKind,
    private val onChanged: () -> Unit = {},
    userId: String? = null
) : WelcomeBackStateStore {
    private val dataStore = if (kind == WelcomeBackStoreKind.SIMULATION || userId == null) {
        context.applicationContext.welcomeBackSimulationStore
    } else {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = {
                File(context.applicationContext.filesDir, "datastore/welcome_back_$userId.preferences_pb")
            }
        )
    }
    private val mutex = Mutex()
    private var memory = WelcomeBackRecord()
    private var storedFingerprint: String? = null

    override fun current(): WelcomeBackRecord = memory

    override fun fingerprint(): String? = storedFingerprint

    override suspend fun load() {
        val prefs = dataStore.data.first()
        val loaded = WelcomeBackRecord(
            qualifyingWorkoutId = prefs[QUALIFYING],
            popupDismissed = prefs[POPUP_DISMISSED] == true,
            activatedAt = prefs[ACTIVATED_AT]?.let(Instant::ofEpochMilli),
            expiresAt = prefs[EXPIRES_AT]?.let(Instant::ofEpochMilli),
            cooldownUntil = prefs[COOLDOWN_UNTIL]?.let(Instant::ofEpochMilli),
            activatedWorkoutId = prefs[ACTIVATED_WORKOUT],
            warningDismissed = prefs[WARNING_DISMISSED] == true
        )
        mutex.withLock {
            memory = loaded
            storedFingerprint = prefs[FINGERPRINT]
        }
        onChanged()
    }

    override suspend fun noteQualifying(workoutId: String) {
        mutex.withLock {
            memory = memory.copy(qualifyingWorkoutId = workoutId, popupDismissed = false)
            dataStore.edit { prefs ->
                prefs[QUALIFYING] = workoutId
                prefs[POPUP_DISMISSED] = false
            }
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

    override suspend fun activate(
        activatedAt: Instant,
        expiresAt: Instant,
        cooldownUntil: Instant,
        workoutId: String
    ) {
        mutex.withLock {
            memory = memory.copy(
                activatedAt = activatedAt,
                expiresAt = expiresAt,
                cooldownUntil = cooldownUntil,
                activatedWorkoutId = workoutId,
                qualifyingWorkoutId = null,
                popupDismissed = false,
                warningDismissed = false
            )
            dataStore.edit { prefs ->
                prefs[ACTIVATED_AT] = activatedAt.toEpochMilli()
                prefs[EXPIRES_AT] = expiresAt.toEpochMilli()
                prefs[COOLDOWN_UNTIL] = cooldownUntil.toEpochMilli()
                prefs[ACTIVATED_WORKOUT] = workoutId
                prefs.remove(QUALIFYING)
                prefs[POPUP_DISMISSED] = false
                prefs[WARNING_DISMISSED] = false
            }
        }
        onChanged()
    }

    override suspend fun applyRemote(record: WelcomeBackRecord?) {
        mutex.withLock {
            memory = if (record == null) {
                memory.copy(
                    activatedAt = null,
                    expiresAt = null,
                    cooldownUntil = null,
                    activatedWorkoutId = null
                )
            } else {
                memory.copy(
                    activatedAt = record.activatedAt,
                    expiresAt = record.expiresAt,
                    cooldownUntil = record.cooldownUntil,
                    activatedWorkoutId = record.activatedWorkoutId
                )
            }
            dataStore.edit { prefs ->
                if (record?.activatedAt == null || record.expiresAt == null || record.cooldownUntil == null) {
                    prefs.remove(ACTIVATED_AT)
                    prefs.remove(EXPIRES_AT)
                    prefs.remove(COOLDOWN_UNTIL)
                    prefs.remove(ACTIVATED_WORKOUT)
                } else {
                    prefs[ACTIVATED_AT] = record.activatedAt.toEpochMilli()
                    prefs[EXPIRES_AT] = record.expiresAt.toEpochMilli()
                    prefs[COOLDOWN_UNTIL] = record.cooldownUntil.toEpochMilli()
                    record.activatedWorkoutId?.let { prefs[ACTIVATED_WORKOUT] = it }
                }
            }
        }
        onChanged()
    }

    override suspend fun reset(fingerprint: String) {
        mutex.withLock {
            memory = WelcomeBackRecord()
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
            memory = WelcomeBackRecord()
            storedFingerprint = null
            dataStore.edit { prefs -> prefs.clear() }
        }
        onChanged()
    }

    private companion object {
        val QUALIFYING = stringPreferencesKey("qualifying_workout_id")
        val POPUP_DISMISSED = booleanPreferencesKey("popup_dismissed")
        val ACTIVATED_AT = longPreferencesKey("activated_at")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val COOLDOWN_UNTIL = longPreferencesKey("cooldown_until")
        val ACTIVATED_WORKOUT = stringPreferencesKey("activated_workout_id")
        val WARNING_DISMISSED = booleanPreferencesKey("warning_dismissed")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
    }
}
