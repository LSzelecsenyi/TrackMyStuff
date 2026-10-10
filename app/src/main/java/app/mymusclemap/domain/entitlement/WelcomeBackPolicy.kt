package app.mymusclemap.domain.entitlement

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class WelcomeBackDebugMode {
    REAL,
    ELIGIBLE,
    ACTIVE,
    EXPIRED
}

/**
 * Debug-only inputs. Release builds always use the defaults.
 *
 * Precedence: ELIGIBLE ignores [gapDays] and treats the next completed native
 * workout as the returning workout. A REAL [gapDays] override shortens the
 * calendar gap and, like any other override, stays on the simulation record.
 * ELIGIBLE still does nothing until a native workout is completed.
 */
data class WelcomeBackDebugConfig(
    val mode: WelcomeBackDebugMode = WelcomeBackDebugMode.REAL,
    val gapDays: Long? = null,
    val expiresInSeconds: Long? = null,
    val warningBeforeSeconds: Long? = null,
    val cooldownSeconds: Long? = null
) {
    val usesSimulationStore: Boolean
        get() = mode != WelcomeBackDebugMode.REAL ||
            gapDays != null ||
            expiresInSeconds != null ||
            warningBeforeSeconds != null ||
            cooldownSeconds != null

    fun gapDays(): Long = gapDays ?: WelcomeBackPolicy.GAP_DAYS

    fun trialDuration(): Duration {
        val seconds = expiresInSeconds
        return if (seconds == null) WelcomeBackPolicy.TRIAL_DURATION else Duration.ofSeconds(seconds)
    }

    fun warningLead(): Duration {
        val seconds = warningBeforeSeconds
        return if (seconds == null) WelcomeBackPolicy.WARNING_LEAD else Duration.ofSeconds(seconds)
    }

    fun cooldown(): Duration {
        val seconds = cooldownSeconds
        return if (seconds == null) WelcomeBackPolicy.COOLDOWN else Duration.ofSeconds(seconds)
    }

    companion object {
        fun parse(
            mode: String,
            gapDays: String,
            expiresInSeconds: String,
            warningBeforeSeconds: String,
            cooldownSeconds: String
        ): WelcomeBackDebugConfig {
            val parsed = when (mode) {
                "REAL" -> WelcomeBackDebugMode.REAL
                "ELIGIBLE" -> WelcomeBackDebugMode.ELIGIBLE
                "ACTIVE" -> WelcomeBackDebugMode.ACTIVE
                "EXPIRED" -> WelcomeBackDebugMode.EXPIRED
                else -> error(
                    "Unsupported strict.debug.welcomeBack \"$mode\". " +
                        "Use REAL, ELIGIBLE, ACTIVE, or EXPIRED."
                )
            }
            return WelcomeBackDebugConfig(
                mode = parsed,
                gapDays = seconds(gapDays, "strict.debug.welcomeBackGapDays"),
                expiresInSeconds = seconds(expiresInSeconds, "strict.debug.welcomeBackExpiresInSeconds"),
                warningBeforeSeconds = seconds(
                    warningBeforeSeconds,
                    "strict.debug.welcomeBackWarningBeforeSeconds"
                ),
                cooldownSeconds = seconds(cooldownSeconds, "strict.debug.welcomeBackCooldownSeconds")
            )
        }

        private fun seconds(raw: String, name: String): Long? {
            if (raw.isBlank()) {
                return null
            }
            return raw.toLongOrNull()?.takeIf { it >= 0 }
                ?: error("$name must be a non-negative integer. Found \"$raw\".")
        }
    }
}

/** One completed native workout. Imported and abandoned sessions are excluded before this list. */
data class WelcomeBackCompletion(
    val clientWorkoutId: String,
    val completedAt: Instant
)

/**
 * Account-scoped Welcome Back record.
 * Activation instants are copied from the backend in REAL mode.
 * Dismissal is local and does not start the cooldown.
 */
data class WelcomeBackRecord(
    val qualifyingWorkoutId: String? = null,
    val popupDismissed: Boolean = false,
    val activatedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val cooldownUntil: Instant? = null,
    val activatedWorkoutId: String? = null,
    val warningDismissed: Boolean = false
)

enum class WelcomeBackNotice {
    None,
    Unavailable,
    Cooldown,
    Replay,
    Incompatible
}

data class WelcomeBackFacts(
    val completions: List<WelcomeBackCompletion>,
    val zone: ZoneId,
    val now: Instant,
    val currentlyFree: Boolean,
    val promotionsEnabled: Boolean,
    val continuedProBeyondGrant: Boolean,
    val record: WelcomeBackRecord,
    val automaticWorkoutId: String? = null,
    val debug: WelcomeBackDebugConfig = WelcomeBackDebugConfig(),
    val notice: WelcomeBackNotice = WelcomeBackNotice.None
)

data class WelcomeBackSnapshot(
    val offerAvailable: Boolean = false,
    val showAutomaticOffer: Boolean = false,
    val trialActive: Boolean = false,
    val trialExpiresAt: Instant? = null,
    val showAutomaticWarning: Boolean = false,
    val warningOnBenefits: Boolean = false,
    val trialExpired: Boolean = false,
    val nextBoundary: Instant? = null,
    val notice: WelcomeBackNotice = WelcomeBackNotice.None
)

object WelcomeBackPolicy {
    const val GAP_DAYS = 40L
    val TRIAL_DURATION: Duration = Duration.ofHours(7 * 24)
    val WARNING_LEAD: Duration = Duration.ofHours(48)
    val COOLDOWN: Duration = Duration.ofDays(180)

    /**
     * The candidate must be the latest native completion.
     * [skipGap] is the ELIGIBLE debug mode: the completed workout still has to happen,
     * but a previous workout and the 40-day gap are not required.
     * Missing finishedAt values are already replaced with startedAt by the caller,
     * matching Pro Discovery.
     */
    fun qualifyingWorkout(
        completions: List<WelcomeBackCompletion>,
        candidateId: String,
        zone: ZoneId,
        gapDays: Long,
        skipGap: Boolean
    ): String? {
        if (candidateId.isBlank() || completions.isEmpty()) {
            return null
        }
        val latest = completions.last()
        if (latest.clientWorkoutId != candidateId) {
            return null
        }
        if (skipGap) {
            return candidateId
        }
        if (completions.size < 2) {
            return null
        }
        val previous = completions[completions.size - 2].completedAt.atZone(zone).toLocalDate()
        val current = latest.completedAt.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(previous, current)
        return if (days >= gapDays) candidateId else null
    }

    /**
     * Another entitlement still grants Pro at the instant this grant becomes inactive.
     */
    fun continuedProBeyond(sources: EntitlementSources, grantExpiresAt: Instant): Boolean {
        if (sources.subscription.isValid(grantExpiresAt)) {
            return true
        }
        if (sources.founderLifetime.active) {
            return true
        }
        if (sources.promotionalPro.isActive(grantExpiresAt)) {
            return true
        }
        val trusted = sources.backendFounder.trusted(grantExpiresAt)
        if (trusted.founderLifetime) {
            return true
        }
        val founderUntil = trusted.founderProExpiresAt ?: return false
        return grantExpiresAt.isBefore(founderUntil)
    }

    fun evaluate(facts: WelcomeBackFacts): WelcomeBackSnapshot {
        val debug = facts.debug
        val record = facts.record
        val active = ProDiscoveryPolicy.trialIsActive(record.expiresAt, facts.now)
        val expired = record.activatedAt != null && !active
        val inCooldown = record.cooldownUntil != null && facts.now.isBefore(record.cooldownUntil)
        val skipGap = debug.mode == WelcomeBackDebugMode.ELIGIBLE
        val noted = record.qualifyingWorkoutId
        val stillQualifies = noted != null && qualifyingWorkout(
            facts.completions,
            noted,
            facts.zone,
            debug.gapDays(),
            skipGap
        ) == noted
        val promotionsAllow = skipGap || facts.promotionsEnabled
        val offerEligible = when (debug.mode) {
            WelcomeBackDebugMode.ACTIVE,
            WelcomeBackDebugMode.EXPIRED -> false
            WelcomeBackDebugMode.ELIGIBLE,
            WelcomeBackDebugMode.REAL ->
                stillQualifies &&
                    facts.currentlyFree &&
                    promotionsAllow &&
                    !inCooldown &&
                    !active &&
                    record.activatedWorkoutId != noted
        }
        val expiresAt = record.expiresAt
        val lead = debug.warningLead()
        val inWindow = active && expiresAt != null &&
            ProDiscoveryPolicy.inWarningWindow(expiresAt, facts.now, lead)
        val suppressWarning = facts.continuedProBeyondGrant
        return WelcomeBackSnapshot(
            offerAvailable = offerEligible,
            showAutomaticOffer = offerEligible &&
                !record.popupDismissed &&
                facts.automaticWorkoutId == noted,
            trialActive = active,
            trialExpiresAt = expiresAt?.takeIf { active || expired },
            showAutomaticWarning = inWindow && !record.warningDismissed && !suppressWarning,
            warningOnBenefits = inWindow && !suppressWarning,
            trialExpired = expired,
            nextBoundary = ProDiscoveryPolicy.nextBoundary(expiresAt, facts.now, lead),
            notice = facts.notice
        )
    }
}
