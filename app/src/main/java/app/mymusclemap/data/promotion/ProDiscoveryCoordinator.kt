package app.mymusclemap.data.promotion

import app.mymusclemap.domain.entitlement.ProDiscoveryActivation
import app.mymusclemap.domain.entitlement.ProDiscoveryCoverage
import app.mymusclemap.domain.entitlement.ProDiscoveryDebugConfig
import app.mymusclemap.domain.entitlement.ProDiscoveryDebugMode
import app.mymusclemap.domain.entitlement.ProDiscoveryFacts
import app.mymusclemap.domain.entitlement.ProDiscoveryPolicy
import app.mymusclemap.domain.entitlement.ProDiscoverySnapshot
import app.mymusclemap.domain.entitlement.PromotionalProEntitlement
import app.mymusclemap.data.preferences.ProDiscoveryStateStore
import java.time.Instant
import java.time.ZoneId

/**
 * Owns the real trial and the separate debug simulation.
 * Pro access itself is still decided by [app.mymusclemap.domain.entitlement.EntitlementResolver].
 */
class ProDiscoveryCoordinator(
    private val real: ProDiscoveryStateStore,
    private val simulation: ProDiscoveryStateStore,
    private val now: () -> Instant,
    private val zone: ZoneId,
    private val completions: suspend () -> List<Instant>,
    private val debugConfig: () -> ProDiscoveryDebugConfig,
    private val promotionsEnabled: () -> Boolean,
    private val onAvailability: suspend (Boolean) -> Unit = {},
    private val onChanged: () -> Unit = {},
    var coverage: () -> ProDiscoveryCoverage = {
        ProDiscoveryCoverage(currentlyFree = true, continuedBeyond = { false })
    },
    private val authorizeActivation: suspend () -> TrialAuthorization = { TrialAuthorization.Local }
) {
    private var history: List<Instant> = emptyList()

    fun snapshot(): ProDiscoverySnapshot {
        return ProDiscoveryPolicy.evaluate(facts())
    }

    /** Grant read by the entitlement composer. Inactive at the exact expiration instant. */
    fun currentGrant(): PromotionalProEntitlement {
        val record = storeFor(debugConfig()).current()
        val expiresAt = record.expiresAt ?: return PromotionalProEntitlement()
        if (!now().isBefore(expiresAt)) {
            return PromotionalProEntitlement()
        }
        return PromotionalProEntitlement(expiresAt = expiresAt)
    }

    suspend fun refreshHistory() {
        val loaded = completions()
        if (loaded == history) {
            return
        }
        history = loaded
        onChanged()
    }

    suspend fun refreshAvailability(fetch: suspend () -> Boolean?) {
        val enabled = fetch() ?: return
        onAvailability(enabled)
    }

    /**
     * Seeds or clears the simulation record. The real trial record is never written here.
     * An ACTIVE countdown starts once and is kept across restarts until the debug fingerprint changes.
     */
    suspend fun prepare() {
        val config = debugConfig()
        if (!config.usesSimulationStore) {
            if (simulation.current().activatedAt != null || simulation.fingerprint() != null) {
                simulation.clear()
            }
            return
        }
        val fingerprint = fingerprint(config)
        if (simulation.fingerprint() != fingerprint) {
            simulation.reset(fingerprint)
        }
        val start = now()
        when (config.mode) {
            ProDiscoveryDebugMode.ACTIVE -> {
                if (simulation.current().activatedAt == null) {
                    simulation.activate(start, start.plus(config.trialDuration()))
                }
            }
            ProDiscoveryDebugMode.EXPIRED -> {
                if (simulation.current().activatedAt == null) {
                    simulation.activate(Instant.EPOCH, Instant.EPOCH)
                }
            }
            ProDiscoveryDebugMode.REAL,
            ProDiscoveryDebugMode.ELIGIBLE -> Unit
        }
    }

    suspend fun activate(): ProDiscoveryActivation {
        val config = debugConfig()
        val store = storeFor(config)
        val record = store.current()
        val start = now()
        val existingExpiry = record.expiresAt
        if (existingExpiry != null && start.isBefore(existingExpiry)) {
            return ProDiscoveryActivation.AlreadyActive(existingExpiry)
        }
        if (record.activatedAt != null) {
            return ProDiscoveryActivation.AlreadyUsed
        }
        if (!snapshot().offerAvailable) {
            return ProDiscoveryActivation.NotEligible
        }
        if (!config.usesSimulationStore) {
            when (val authorized = authorizeActivation()) {
                TrialAuthorization.Local -> Unit
                is TrialAuthorization.Granted -> {
                    store.activate(authorized.activatedAt, authorized.expiresAt)
                    return ProDiscoveryActivation.Started(authorized.expiresAt)
                }
                TrialAuthorization.AlreadyUsed -> return ProDiscoveryActivation.AlreadyUsed
                TrialAuthorization.Rejected -> return ProDiscoveryActivation.NotEligible
                TrialAuthorization.Unavailable -> return ProDiscoveryActivation.Unavailable
            }
        }
        val expiresAt = start.plus(config.trialDuration())
        store.activate(start, expiresAt)
        return ProDiscoveryActivation.Started(store.current().expiresAt ?: expiresAt)
    }

    suspend fun dismissOffer() {
        storeFor(debugConfig()).dismissPopup()
    }

    suspend fun dismissWarning() {
        storeFor(debugConfig()).dismissWarning()
    }

    fun persistedTrial(): app.mymusclemap.domain.entitlement.ProDiscoveryRecord = real.current()

    private fun facts(): ProDiscoveryFacts {
        val config = debugConfig()
        val record = storeFor(config).current()
        val covered = coverage()
        val expiresAt = record.expiresAt
        return ProDiscoveryFacts(
            nativeCompletions = history,
            zone = zone,
            now = now(),
            currentlyFree = covered.currentlyFree,
            promotionsEnabled = promotionsEnabled(),
            continuedProBeyondTrial = expiresAt != null && covered.continuedBeyond(expiresAt),
            record = record,
            debug = config
        )
    }

    private fun storeFor(config: ProDiscoveryDebugConfig): ProDiscoveryStateStore {
        return if (config.usesSimulationStore) simulation else real
    }

    private fun fingerprint(config: ProDiscoveryDebugConfig): String {
        return "${config.mode}|${config.expiresInSeconds ?: ""}|${config.warningBeforeSeconds ?: ""}"
    }
}
