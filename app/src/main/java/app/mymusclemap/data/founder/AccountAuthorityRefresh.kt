package app.mymusclemap.data.founder

import app.mymusclemap.data.auth.FounderEntitlementCall
import app.mymusclemap.data.auth.FounderSnapshotCall
import app.mymusclemap.data.auth.StrictBackendApi
import java.time.ZoneId

/**
 * Startup and foreground refresh for one signed-in account.
 *
 * [StrictBackendApi.currentEntitlements] runs whether or not this account is in the
 * Founder program. The Founder snapshot and workout outbox stay limited to a
 * backend-owned program. A missing session, a transport failure, or a non-401
 * response leaves the last trusted entitlement cache in place.
 */
class AccountAuthorityRefresh(
    private val api: StrictBackendApi,
    private val zone: ZoneId,
    private val founderBackendOwned: () -> Boolean,
    private val applyFounderSnapshot: suspend (BackendFounderSnapshot) -> Unit,
    private val saveEntitlements: suspend (FounderEntitlementCall.Loaded) -> Unit,
    private val dropEntitlementCache: () -> Unit,
    private val founderWorkoutsPending: suspend () -> Boolean,
    private val scheduleFounderWorkoutSync: () -> Unit
) {
    suspend fun refresh() {
        val founderOwned = founderBackendOwned()
        if (founderOwned) {
            when (val current = api.currentFounder(zone)) {
                is FounderSnapshotCall.Loaded -> applyFounderSnapshot(current.snapshot)
                FounderSnapshotCall.Unauthenticated -> dropEntitlementCache()
                else -> Unit
            }
        }
        when (val entitlements = api.currentEntitlements()) {
            is FounderEntitlementCall.Loaded -> saveEntitlements(entitlements)
            FounderEntitlementCall.Unauthenticated -> dropEntitlementCache()
            else -> Unit
        }
        if (founderOwned && founderWorkoutsPending()) {
            scheduleFounderWorkoutSync()
        }
    }
}
