package app.mymusclemap.domain.entitlement

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ProDiscoveryDebugMode {
    REAL,
    ELIGIBLE,
    ACTIVE,
    EXPIRED
}

/**
 * Debug-only inputs. Release builds always use [ProDiscoveryDebugConfig] defaults.
 * A non-REAL mode, or any non-empty duration override, is a simulation and must
 * not be written into the real one-time trial record.
 */
data class ProDiscoveryDebugConfig(
    val mode: ProDiscoveryDebugMode = ProDiscoveryDebugMode.REAL,
    val expiresInSeconds: Long? = null,
    val warningBeforeSeconds: Long? = null
) {
    val usesSimulationStore: Boolean
        get() = mode != ProDiscoveryDebugMode.REAL ||
            expiresInSeconds != null ||
            warningBeforeSeconds != null

    fun trialDuration(): Duration {
        val seconds = expiresInSeconds
        return if (seconds == null) ProDiscoveryPolicy.TRIAL_DURATION else Duration.ofSeconds(seconds)
    }

    fun warningLead(): Duration {
        val seconds = warningBeforeSeconds
        return if (seconds == null) ProDiscoveryPolicy.WARNING_LEAD else Duration.ofSeconds(seconds)
    }

    companion object {
        fun parse(mode: String, expiresInSeconds: String, warningBeforeSeconds: String): ProDiscoveryDebugConfig {
            val parsed = when (mode) {
                "REAL" -> ProDiscoveryDebugMode.REAL
                "ELIGIBLE" -> ProDiscoveryDebugMode.ELIGIBLE
                "ACTIVE" -> ProDiscoveryDebugMode.ACTIVE
                "EXPIRED" -> ProDiscoveryDebugMode.EXPIRED
                else -> error(
                    "Unsupported strict.debug.proDiscovery \"$mode\". " +
                        "Use REAL, ELIGIBLE, ACTIVE, or EXPIRED."
                )
            }
            return ProDiscoveryDebugConfig(
                mode = parsed,
                expiresInSeconds = seconds(expiresInSeconds, "strict.debug.proDiscoveryExpiresInSeconds"),
                warningBeforeSeconds = seconds(
                    warningBeforeSeconds,
                    "strict.debug.proDiscoveryWarningBeforeSeconds"
                )
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

/** Device-local trial record. Null instants mean this trial has never been activated. */
data class ProDiscoveryRecord(
    val popupDismissed: Boolean = false,
    val activatedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val warningDismissed: Boolean = false
)

data class ProDiscoveryFacts(
    val nativeCompletions: List<Instant>,
    val zone: ZoneId,
    val now: Instant,
    val currentlyFree: Boolean,
    val promotionsEnabled: Boolean,
    val continuedProBeyondTrial: Boolean,
    val record: ProDiscoveryRecord,
    val debug: ProDiscoveryDebugConfig = ProDiscoveryDebugConfig()
)

data class ProDiscoverySnapshot(
    val offerAvailable: Boolean = false,
    val showAutomaticOffer: Boolean = false,
    val trialActive: Boolean = false,
    val trialExpiresAt: Instant? = null,
    val showAutomaticWarning: Boolean = false,
    val warningOnBenefits: Boolean = false,
    val trialExpired: Boolean = false,
    val subscriptionsAvailable: Boolean = false,
    val nextBoundary: Instant? = null
)

/**
 * Other Pro sources, excluding this promotional trial.
 * [currentlyFree] is the access the user has without the trial.
 * [continuedBeyond] is true when another source still grants Pro at the trial's expiration instant.
 */
data class ProDiscoveryCoverage(
    val currentlyFree: Boolean,
    val continuedBeyond: (Instant) -> Boolean
)

enum class StrictOneTimePrompt {
    FounderApproval,
    WorkoutCelebration,
    ProDiscoveryOffer,
    WelcomeBackOffer,
    ProDiscoveryWarning,
    WelcomeBackWarning
}

/**
 * One dialog at a time. Founder approval comes first, then a workout milestone,
 * then the discovery offer, then the expiration warning.
 * Workout and promotion dialogs wait until the user leaves an active or just-finished workout.
 */
fun selectStrictOneTimePrompt(
    founderApproval: Boolean,
    workoutCelebration: Boolean,
    suppressForRoute: Boolean,
    discoveryOffer: Boolean,
    discoveryWarning: Boolean,
    welcomeOffer: Boolean = false,
    welcomeWarning: Boolean = false
): StrictOneTimePrompt? {
    if (founderApproval) {
        return StrictOneTimePrompt.FounderApproval
    }
    if (suppressForRoute) {
        return null
    }
    if (workoutCelebration) {
        return StrictOneTimePrompt.WorkoutCelebration
    }
    if (discoveryOffer) {
        return StrictOneTimePrompt.ProDiscoveryOffer
    }
    if (welcomeOffer) {
        return StrictOneTimePrompt.WelcomeBackOffer
    }
    if (discoveryWarning) {
        return StrictOneTimePrompt.ProDiscoveryWarning
    }
    if (welcomeWarning) {
        return StrictOneTimePrompt.WelcomeBackWarning
    }
    return null
}

object ProDiscoveryPolicy {
    const val ELIGIBILITY_DAYS = 60L
    const val OLDER_THAN_DAYS = 30L
    val TRIAL_DURATION: Duration = Duration.ofHours(14 * 24)
    val WARNING_LEAD: Duration = Duration.ofHours(48)

    /** Imported workouts carry a fingerprint and do not count toward this offer. */
    fun countsAsNative(importFingerprint: String?): Boolean = importFingerprint == null

    fun calendarDaysSince(completion: Instant, today: LocalDate, zone: ZoneId): Long {
        val completedOn = completion.atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(completedOn, today)
    }

    fun meetsHistory(completions: List<Instant>, today: LocalDate, zone: ZoneId): Boolean {
        if (completions.isEmpty()) {
            return false
        }
        val earliest = completions.min()
        if (calendarDaysSince(earliest, today, zone) < ELIGIBILITY_DAYS) {
            return false
        }
        return completions.any { calendarDaysSince(it, today, zone) > OLDER_THAN_DAYS }
    }

    fun trialIsActive(expiresAt: Instant?, now: Instant): Boolean {
        return expiresAt != null && now.isBefore(expiresAt)
    }

    fun inWarningWindow(expiresAt: Instant, now: Instant, lead: Duration): Boolean {
        val opens = expiresAt.minus(lead)
        return !now.isBefore(opens) && now.isBefore(expiresAt)
    }

    fun nextBoundary(expiresAt: Instant?, now: Instant, lead: Duration): Instant? {
        if (expiresAt == null || !now.isBefore(expiresAt)) {
            return null
        }
        val warningAt = expiresAt.minus(lead)
        return listOf(warningAt, expiresAt).filter { now.isBefore(it) }.minOrNull()
    }

    /**
     * Another entitlement covers the moment this trial becomes inactive.
     * Founder Pro is active only while [now] is strictly before its expiration,
     * matching [EntitlementResolver].
     */
    fun continuedProBeyond(sources: EntitlementSources, trialExpiresAt: Instant): Boolean {
        if (sources.subscription.isValid(trialExpiresAt)) {
            return true
        }
        if (sources.founderLifetime.active) {
            return true
        }
        if (sources.welcomeBack.isActive(trialExpiresAt)) {
            return true
        }
        val trusted = sources.backendFounder.trusted(trialExpiresAt)
        if (trusted.founderLifetime) {
            return true
        }
        val founderUntil = trusted.founderProExpiresAt ?: return false
        return trialExpiresAt.isBefore(founderUntil)
    }

    fun evaluate(facts: ProDiscoveryFacts): ProDiscoverySnapshot {
        val today = facts.now.atZone(facts.zone).toLocalDate()
        val debug = facts.debug
        val alreadyUsed = facts.record.activatedAt != null
        val active = trialIsActive(facts.record.expiresAt, facts.now)
        val expired = alreadyUsed && !active
        val historyMet = meetsHistory(facts.nativeCompletions, today, facts.zone)
        val offerEligible = when (debug.mode) {
            ProDiscoveryDebugMode.ACTIVE,
            ProDiscoveryDebugMode.EXPIRED -> false
            ProDiscoveryDebugMode.ELIGIBLE -> facts.currentlyFree && !alreadyUsed
            ProDiscoveryDebugMode.REAL ->
                historyMet &&
                    facts.currentlyFree &&
                    !alreadyUsed &&
                    facts.promotionsEnabled
        }
        val expiresAt = facts.record.expiresAt
        val lead = debug.warningLead()
        val inWindow = active && expiresAt != null && inWarningWindow(expiresAt, facts.now, lead)
        val suppressWarning = facts.continuedProBeyondTrial
        return ProDiscoverySnapshot(
            offerAvailable = offerEligible,
            showAutomaticOffer = offerEligible && !facts.record.popupDismissed,
            trialActive = active,
            trialExpiresAt = expiresAt?.takeIf { active || expired },
            showAutomaticWarning = inWindow && !facts.record.warningDismissed && !suppressWarning,
            warningOnBenefits = inWindow && !suppressWarning,
            trialExpired = expired,
            nextBoundary = nextBoundary(expiresAt, facts.now, lead)
        )
    }
}
