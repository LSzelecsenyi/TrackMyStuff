package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.SpecialAchievementGrant
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
 * Cache of the last backend Founder entitlement and account-status grants.
 *
 * Trusted through [validUntil], which is [TRUST] after a successful read.
 * After that instant, Founder-derived Pro and the special badges are off until the next
 * successful read. The cache is bound to [userId]. Logout, a rejected bearer, and a
 * different signed-in account drop it. A failed refresh does not. This store is not a
 * grant by itself and is not part of the portable backup.
 */
class FounderEntitlementCache(
    context: Context,
    private val scope: CoroutineScope,
    private val sessionUserId: () -> String? = { null },
    private val onChanged: () -> Unit = {}
) {
    private val dataStore = context.applicationContext.founderEntitlementDataStore
    private val mutex = Mutex()
    private val generation = AtomicInteger()
    private var memory = BackendFounderEntitlement()

    fun current(): BackendFounderEntitlement {
        val value = memory
        val session = sessionUserId()
        if (session == null || value.userId == null || value.userId != session) {
            return BackendFounderEntitlement()
        }
        return value
    }

    fun retainAccount(userId: String?) {
        val stored = memory.userId
        if (stored != null && stored != userId) {
            drop()
        }
    }

    suspend fun load() {
        val ticket = generation.get()
        val loaded = read()
        val session = sessionUserId()
        val accepted = loaded.userId != null && loaded.userId == session
        mutex.withLock {
            if (generation.get() != ticket) {
                return
            }
            memory = if (accepted) loaded else BackendFounderEntitlement()
        }
        if (loaded.userId != null && !accepted) {
            drop()
        } else {
            onChanged()
        }
    }

    suspend fun save(
        temporaryFounderPro: Boolean,
        founderLifetime: Boolean,
        validUntil: Instant,
        userId: String,
        founderGrantedAt: Instant? = null,
        specialGrants: List<SpecialAchievementGrant> = emptyList()
    ) {
        if (userId.isBlank()) {
            return
        }
        val next = BackendFounderEntitlement(
            temporaryFounderPro = temporaryFounderPro,
            founderLifetime = founderLifetime,
            validUntil = validUntil,
            userId = userId,
            founderGrantedAt = founderGrantedAt,
            specialGrants = specialGrants.filter { it.key == "EARLY_ADOPTER" || it.key == "DEVELOPER" }
        )
        mutex.withLock {
            generation.incrementAndGet()
            memory = next
            dataStore.edit { prefs ->
                prefs[KEY_USER_ID] = userId
                prefs[KEY_TEMPORARY] = temporaryFounderPro
                prefs[KEY_LIFETIME] = founderLifetime
                prefs[KEY_VALID_UNTIL] = validUntil.toEpochMilli()
                if (founderGrantedAt == null) {
                    prefs.remove(KEY_FOUNDER_GRANTED_AT)
                } else {
                    prefs[KEY_FOUNDER_GRANTED_AT] = founderGrantedAt.toEpochMilli()
                }
                prefs[KEY_SPECIALS] = encodeSpecials(next.specialGrants)
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
                prefs.remove(KEY_USER_ID)
                prefs.remove(KEY_TEMPORARY)
                prefs.remove(KEY_LIFETIME)
                prefs.remove(KEY_VALID_UNTIL)
                prefs.remove(KEY_FOUNDER_GRANTED_AT)
                prefs.remove(KEY_SPECIALS)
            }
        }
    }

    private suspend fun read(): BackendFounderEntitlement {
        val prefs = dataStore.data.first()
        val userId = prefs[KEY_USER_ID] ?: return BackendFounderEntitlement()
        val until = prefs[KEY_VALID_UNTIL] ?: return BackendFounderEntitlement()
        val founderGrantedAt = prefs[KEY_FOUNDER_GRANTED_AT]?.let(Instant::ofEpochMilli)
        return BackendFounderEntitlement(
            temporaryFounderPro = prefs[KEY_TEMPORARY] == true,
            founderLifetime = prefs[KEY_LIFETIME] == true,
            validUntil = Instant.ofEpochMilli(until),
            userId = userId,
            founderGrantedAt = founderGrantedAt,
            specialGrants = decodeSpecials(prefs[KEY_SPECIALS])
        )
    }

    private fun encodeSpecials(grants: List<SpecialAchievementGrant>): String {
        return grants.joinToString(";") { "${it.key}=${it.grantedAt.toEpochMilli()}" }
    }

    private fun decodeSpecials(raw: String?): List<SpecialAchievementGrant> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return raw.split(';').mapNotNull { part ->
            val bits = part.split('=')
            if (bits.size != 2 || (bits[0] != "EARLY_ADOPTER" && bits[0] != "DEVELOPER")) {
                return@mapNotNull null
            }
            val millis = bits[1].toLongOrNull() ?: return@mapNotNull null
            SpecialAchievementGrant(bits[0], Instant.ofEpochMilli(millis))
        }
    }

    companion object {
        const val PREFERENCES_NAME = "founder_entitlement_cache"
        val TRUST: Duration = Duration.ofHours(24)
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_TEMPORARY = booleanPreferencesKey("temporary_founder_pro")
        private val KEY_LIFETIME = booleanPreferencesKey("founder_lifetime")
        private val KEY_VALID_UNTIL = longPreferencesKey("valid_until_epoch_milli")
        private val KEY_FOUNDER_GRANTED_AT = longPreferencesKey("founder_granted_at_epoch_milli")
        private val KEY_SPECIALS = stringPreferencesKey("special_achievements")
    }
}
