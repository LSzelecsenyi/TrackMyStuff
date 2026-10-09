package app.mymusclemap.data.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.billing.VerifiedPaidSubscription
import app.mymusclemap.domain.billing.paidSubscriptionEntitlement
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import app.mymusclemap.domain.entitlement.SubscriptionEntitlementProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

private val Context.verifiedSubscriptionStore by preferencesDataStore(name = "verified_play_subscription")

interface VerifiedSubscriptionCache : SubscriptionEntitlementProvider {
    fun snapshot(): VerifiedPaidSubscription?
    suspend fun load()
    suspend fun save(subscription: VerifiedPaidSubscription)
    suspend fun clear()
}

/**
 * Last subscription snapshot accepted from the backend. Pro ends locally when
 * [VerifiedPaidSubscription.expiresAt] passes, without waiting for a network call.
 * Fake billing never writes this store.
 */
class VerifiedSubscriptionStore(
    context: Context,
    private val onChanged: () -> Unit = {},
    userId: String? = null
) : VerifiedSubscriptionCache {
    private val dataStore = if (userId == null) {
        context.applicationContext.verifiedSubscriptionStore
    } else {
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ),
            produceFile = {
                java.io.File(
                    context.applicationContext.filesDir,
                    "datastore/verified_play_subscription_$userId.preferences_pb"
                )
            }
        )
    }
    private val mutex = Mutex()
    private var memory: VerifiedPaidSubscription? = null

    override fun current(): SubscriptionEntitlement = paidSubscriptionEntitlement(memory)

    override fun snapshot(): VerifiedPaidSubscription? = memory

    override suspend fun load() {
        val prefs = dataStore.data.first()
        val productId = prefs[PRODUCT] ?: return
        val loaded = VerifiedPaidSubscription(
            productId = productId,
            expiresAt = prefs[EXPIRES]?.let(Instant::ofEpochMilli),
            autoRenewing = prefs[RENEWS] == true,
            state = prefs[STATE].orEmpty(),
            entitled = prefs[ENTITLED] == true
        )
        mutex.withLock { memory = loaded }
        onChanged()
    }

    override suspend fun save(subscription: VerifiedPaidSubscription) {
        mutex.withLock {
            memory = subscription
            dataStore.edit { prefs ->
                prefs[PRODUCT] = subscription.productId
                prefs[STATE] = subscription.state
                prefs[RENEWS] = subscription.autoRenewing
                prefs[ENTITLED] = subscription.entitled
                val expiresAt = subscription.expiresAt
                if (expiresAt == null) {
                    prefs.remove(EXPIRES)
                } else {
                    prefs[EXPIRES] = expiresAt.toEpochMilli()
                }
            }
        }
        onChanged()
    }

    override suspend fun clear() {
        mutex.withLock {
            memory = null
            dataStore.edit { prefs -> prefs.clear() }
        }
        onChanged()
    }

    private companion object {
        val PRODUCT = stringPreferencesKey("product_id")
        val STATE = stringPreferencesKey("state")
        val EXPIRES = longPreferencesKey("expires_at")
        val RENEWS = booleanPreferencesKey("auto_renewing")
        val ENTITLED = booleanPreferencesKey("entitled")
    }
}
