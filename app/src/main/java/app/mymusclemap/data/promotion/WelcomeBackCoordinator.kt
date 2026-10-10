package app.mymusclemap.data.promotion

import app.mymusclemap.data.preferences.WelcomeBackStateStore
import app.mymusclemap.domain.entitlement.PromotionalProEntitlement
import app.mymusclemap.domain.entitlement.WelcomeBackCompletion
import app.mymusclemap.domain.entitlement.WelcomeBackDebugConfig
import app.mymusclemap.domain.entitlement.WelcomeBackDebugMode
import app.mymusclemap.domain.entitlement.WelcomeBackFacts
import app.mymusclemap.domain.entitlement.WelcomeBackNotice
import app.mymusclemap.domain.entitlement.WelcomeBackPolicy
import app.mymusclemap.domain.entitlement.WelcomeBackRecord
import app.mymusclemap.domain.entitlement.WelcomeBackSnapshot
import java.time.Instant
import java.time.ZoneId

/**
 * Local eligibility and the cached Welcome Back grant.
 * REAL activation asks the backend. Debug simulation never does.
 * The automatic popup is process memory, so a restart does not show it again.
 */
class WelcomeBackCoordinator(
    private val real: WelcomeBackStateStore,
    private val simulation: WelcomeBackStateStore,
    private val now: () -> Instant,
    private val zone: ZoneId,
    private val completions: suspend () -> List<WelcomeBackCompletion>,
    private val debugConfig: () -> WelcomeBackDebugConfig,
    private val promotionsEnabled: () -> Boolean,
    private val onChanged: () -> Unit = {},
    var coverage: () -> WelcomeBackCoverage = {
        WelcomeBackCoverage(currentlyFree = true, continuedBeyond = { false })
    },
    private val authorizeActivation: suspend (String) -> WelcomeBackAuthorization = {
        WelcomeBackAuthorization.Local
    },
    private val fetchGrant: suspend () -> WelcomeBackRemote = { WelcomeBackRemote.Unavailable }
) {
    private var history: List<WelcomeBackCompletion> = emptyList()
    private var automaticWorkoutId: String? = null
    private var notice = WelcomeBackNotice.None

    fun snapshot(): WelcomeBackSnapshot {
        return WelcomeBackPolicy.evaluate(facts())
    }

    fun currentGrant(): PromotionalProEntitlement {
        val expiresAt = storeFor(debugConfig()).current().expiresAt ?: return PromotionalProEntitlement()
        if (!now().isBefore(expiresAt)) {
            return PromotionalProEntitlement()
        }
        return PromotionalProEntitlement(expiresAt = expiresAt)
    }

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
            WelcomeBackDebugMode.ACTIVE -> {
                if (simulation.current().activatedAt == null) {
                    simulation.activate(
                        start,
                        start.plus(config.trialDuration()),
                        start.plus(config.cooldown()),
                        "debug-active"
                    )
                }
            }
            WelcomeBackDebugMode.EXPIRED -> {
                if (simulation.current().activatedAt == null) {
                    simulation.activate(Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, "debug-expired")
                }
            }
            WelcomeBackDebugMode.REAL,
            WelcomeBackDebugMode.ELIGIBLE -> Unit
        }
    }

    suspend fun refreshRemote() {
        if (debugConfig().usesSimulationStore) {
            return
        }
        when (val remote = fetchGrant()) {
            WelcomeBackRemote.Unavailable -> Unit
            WelcomeBackRemote.None -> real.applyRemote(null)
            is WelcomeBackRemote.Grant -> real.applyRemote(
                WelcomeBackRecord(
                    activatedAt = remote.activatedAt,
                    expiresAt = remote.expiresAt,
                    cooldownUntil = remote.cooldownUntil,
                    activatedWorkoutId = remote.workoutId
                )
            )
        }
    }

    suspend fun refreshHistory() {
        val loaded = completions()
        if (loaded == history) {
            return
        }
        history = loaded
        onChanged()
    }

    /**
     * Called only after a native workout has been saved.
     * Opening the app does not call this.
     */
    suspend fun noteCompletion(workoutId: String) {
        refreshHistory()
        val config = debugConfig()
        val qualifying = WelcomeBackPolicy.qualifyingWorkout(
            history,
            workoutId,
            zone,
            config.gapDays(),
            config.mode == WelcomeBackDebugMode.ELIGIBLE
        ) ?: return
        val store = storeFor(config)
        val record = store.current()
        if (record.qualifyingWorkoutId == qualifying || record.activatedWorkoutId == qualifying) {
            return
        }
        if (record.cooldownUntil != null && now().isBefore(record.cooldownUntil)) {
            return
        }
        if (config.mode == WelcomeBackDebugMode.ACTIVE || config.mode == WelcomeBackDebugMode.EXPIRED) {
            return
        }
        store.noteQualifying(qualifying)
        automaticWorkoutId = qualifying
        notice = WelcomeBackNotice.None
        onChanged()
    }

    suspend fun dismissOffer() {
        automaticWorkoutId = null
        storeFor(debugConfig()).dismissPopup()
    }

    suspend fun dismissWarning() {
        storeFor(debugConfig()).dismissWarning()
    }

    suspend fun activate() {
        val config = debugConfig()
        val store = storeFor(config)
        val record = store.current()
        if (ProDiscoveryPolicy_trialActive(record.expiresAt)) {
            return
        }
        val workoutId = record.qualifyingWorkoutId ?: return
        if (record.activatedWorkoutId == workoutId) {
            notice = WelcomeBackNotice.Replay
            onChanged()
            return
        }
        if (config.usesSimulationStore) {
            val start = now()
            store.activate(
                start,
                start.plus(config.trialDuration()),
                start.plus(config.cooldown()),
                workoutId
            )
            automaticWorkoutId = null
            notice = WelcomeBackNotice.None
            onChanged()
            return
        }
        when (val result = authorizeActivation(workoutId)) {
            WelcomeBackAuthorization.Local -> {
                val start = now()
                store.activate(
                    start,
                    start.plus(WelcomeBackPolicy.TRIAL_DURATION),
                    start.plus(WelcomeBackPolicy.COOLDOWN),
                    workoutId
                )
                automaticWorkoutId = null
                notice = WelcomeBackNotice.None
            }
            is WelcomeBackAuthorization.Granted -> {
                store.activate(result.activatedAt, result.expiresAt, result.cooldownUntil, workoutId)
                automaticWorkoutId = null
                notice = WelcomeBackNotice.None
            }
            is WelcomeBackAuthorization.Cooldown -> {
                result.cooldownUntil?.let { until ->
                    store.applyRemote(
                        WelcomeBackRecord(
                            activatedAt = record.activatedAt,
                            expiresAt = record.expiresAt,
                            cooldownUntil = until,
                            activatedWorkoutId = record.activatedWorkoutId
                        )
                    )
                }
                notice = WelcomeBackNotice.Cooldown
            }
            WelcomeBackAuthorization.Replay -> notice = WelcomeBackNotice.Replay
            WelcomeBackAuthorization.Rejected -> notice = WelcomeBackNotice.Incompatible
            WelcomeBackAuthorization.Unavailable -> notice = WelcomeBackNotice.Unavailable
        }
        onChanged()
    }

    private fun ProDiscoveryPolicy_trialActive(expiresAt: Instant?): Boolean {
        return expiresAt != null && now().isBefore(expiresAt)
    }

    private fun facts(): WelcomeBackFacts {
        val config = debugConfig()
        val covered = coverage()
        val record = storeFor(config).current()
        val expiresAt = record.expiresAt
        return WelcomeBackFacts(
            completions = history,
            zone = zone,
            now = now(),
            currentlyFree = covered.currentlyFree,
            promotionsEnabled = promotionsEnabled(),
            continuedProBeyondGrant = expiresAt != null && covered.continuedBeyond(expiresAt),
            record = record,
            automaticWorkoutId = automaticWorkoutId,
            debug = config,
            notice = notice
        )
    }

    private fun storeFor(config: WelcomeBackDebugConfig): WelcomeBackStateStore {
        return if (config.usesSimulationStore) simulation else real
    }

    private fun fingerprint(config: WelcomeBackDebugConfig): String {
        return listOf(
            config.mode.name,
            config.gapDays?.toString().orEmpty(),
            config.expiresInSeconds?.toString().orEmpty(),
            config.warningBeforeSeconds?.toString().orEmpty(),
            config.cooldownSeconds?.toString().orEmpty()
        ).joinToString("|")
    }
}

data class WelcomeBackCoverage(
    val currentlyFree: Boolean,
    val continuedBeyond: (Instant) -> Boolean
)
