package app.mymusclemap.domain.entitlement

import app.mymusclemap.data.preferences.WelcomeBackStateStore
import app.mymusclemap.data.promotion.WelcomeBackAuthorization
import app.mymusclemap.data.promotion.WelcomeBackCoordinator
import app.mymusclemap.data.promotion.WelcomeBackCoverage
import app.mymusclemap.data.promotion.WelcomeBackRemote
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class WelcomeBackPolicyTest {
    private val zone = ZoneOffset.UTC
    private val january = Instant.parse("2026-01-01T10:00:00Z")
    private val day39 = Instant.parse("2026-02-09T10:00:00Z")
    private val day40 = Instant.parse("2026-02-10T10:00:00Z")

    @Test
    fun fortyCalendarDaysQualifiesAndThirtyNineDoesNot() {
        assertNull(qualify("b", completion("a", january), completion("b", day39)))
        assertEquals("b", qualify("b", completion("a", january), completion("b", day40)))
    }

    @Test
    fun firstWorkoutImportsAndOldEditsDoNotQualify() {
        assertNull(qualify("only", completion("only", january)))
        assertNull(qualify("old", completion("old", january), completion("newer", day40)))
        val untouched = WelcomeBackRecord(qualifyingWorkoutId = "kept")
        WelcomeBackPolicy.evaluate(facts(record = untouched, now = day40.plus(Duration.ofDays(8))))
        assertEquals("kept", untouched.qualifyingWorkoutId)
    }

    @Test
    fun existingProAndClosedPromotionsBlockTheOffer() {
        val record = WelcomeBackRecord(qualifyingWorkoutId = "b")
        val history = listOf(completion("a", january), completion("b", day40))
        assertFalse(
            WelcomeBackPolicy.evaluate(
                facts(history, record, day40, currentlyFree = false)
            ).offerAvailable
        )
        assertFalse(
            WelcomeBackPolicy.evaluate(
                facts(history, record, day40, promotionsEnabled = false)
            ).offerAvailable
        )
    }

    @Test
    fun dismissalDoesNotActivateAndARestartDoesNotShowThePopupAgain() = runBlocking {
        val store = MemoryStore()
        val clock = Clock()
        val history = mutableListOf(completion("a", january), completion("b", day40))
        clock.instant = day40
        val first = coordinator(store, clock, history)
        first.noteCompletion("b")
        assertTrue(first.snapshot().showAutomaticOffer)
        assertNull(store.record.activatedAt)
        first.dismissOffer()
        assertFalse(first.snapshot().showAutomaticOffer)
        assertTrue(first.snapshot().offerAvailable)
        assertNull(store.record.cooldownUntil)
        first.noteCompletion("b")
        assertFalse(first.snapshot().showAutomaticOffer)
        val restarted = coordinator(store, clock, history)
        restarted.refreshHistory()
        assertFalse(restarted.snapshot().showAutomaticOffer)
        assertTrue(restarted.snapshot().offerAvailable)
    }

    @Test
    fun openingTheAppDoesNotCreateAnOffer() = runBlocking {
        val store = MemoryStore()
        val clock = Clock()
        val history = mutableListOf(completion("a", january), completion("b", day40))
        clock.instant = day40
        val coordinator = coordinator(store, clock, history)
        coordinator.refreshHistory()
        assertFalse(coordinator.snapshot().offerAvailable)
        assertFalse(coordinator.snapshot().showAutomaticOffer)
    }

    @Test
    fun acceptanceLastsSevenDaysAndASecondActivationIsIgnoredWhileActive() = runBlocking {
        val store = MemoryStore()
        val clock = Clock()
        clock.instant = day40
        var activations = 0
        val coordinator = coordinator(
            store,
            clock,
            mutableListOf(completion("a", january), completion("b", day40)),
            authorize = {
                activations += 1
                WelcomeBackAuthorization.Granted(
                    clock.instant,
                    clock.instant.plus(WelcomeBackPolicy.TRIAL_DURATION),
                    clock.instant.plus(WelcomeBackPolicy.COOLDOWN)
                )
            }
        )
        coordinator.noteCompletion("b")
        coordinator.activate()
        coordinator.activate()
        assertEquals(1, activations)
        assertEquals(day40.plus(Duration.ofHours(7 * 24)), store.record.expiresAt)
        assertEquals(day40.plus(Duration.ofDays(180)), store.record.cooldownUntil)
        assertTrue(coordinator.currentGrant().isActive(store.record.expiresAt!!.minusMillis(1)))
        assertFalse(coordinator.currentGrant().isActive(store.record.expiresAt!!))
    }

    @Test
    fun cooldownAndReplayStopAnotherGrantUntilANewWorkout() = runBlocking {
        val store = MemoryStore()
        val clock = Clock()
        clock.instant = day40
        val history = mutableListOf(completion("a", january), completion("b", day40))
        var activations = 0
        val coordinator = coordinator(store, clock, history, authorize = {
            activations += 1
            if (it == "b" && activations > 1) {
                WelcomeBackAuthorization.Replay
            } else {
                WelcomeBackAuthorization.Granted(
                    clock.instant,
                    clock.instant.plus(WelcomeBackPolicy.TRIAL_DURATION),
                    clock.instant.plus(WelcomeBackPolicy.COOLDOWN)
                )
            }
        })
        coordinator.noteCompletion("b")
        coordinator.activate()
        clock.instant = day40.plus(Duration.ofDays(8))
        history += completion("c", clock.instant)
        coordinator.noteCompletion("c")
        assertFalse(coordinator.snapshot().offerAvailable)
        clock.instant = day40.plus(Duration.ofDays(180))
        coordinator.noteCompletion("b")
        assertFalse(coordinator.snapshot().offerAvailable)
        history += completion("d", clock.instant)
        coordinator.noteCompletion("d")
        assertTrue(coordinator.snapshot().offerAvailable)
    }

    @Test
    fun unavailableActivationDoesNotInventAGrant() = runBlocking {
        val store = MemoryStore()
        val clock = Clock()
        clock.instant = day40
        val coordinator = coordinator(
            store,
            clock,
            mutableListOf(completion("a", january), completion("b", day40)),
            authorize = { WelcomeBackAuthorization.Unavailable }
        )
        coordinator.noteCompletion("b")
        coordinator.activate()
        assertNull(store.record.activatedAt)
        assertEquals(WelcomeBackNotice.Unavailable, coordinator.snapshot().notice)
    }

    @Test
    fun warningShowsOnceInsideFortyEightHoursUnlessOtherProCoversIt() {
        val expiresAt = day40.plus(WelcomeBackPolicy.TRIAL_DURATION)
        val record = WelcomeBackRecord(
            activatedAt = day40,
            expiresAt = expiresAt,
            cooldownUntil = day40.plus(WelcomeBackPolicy.COOLDOWN),
            activatedWorkoutId = "b"
        )
        val open = expiresAt.minus(WelcomeBackPolicy.WARNING_LEAD)
        assertFalse(WelcomeBackPolicy.evaluate(facts(record = record, now = open.minusMillis(1))).showAutomaticWarning)
        assertTrue(WelcomeBackPolicy.evaluate(facts(record = record, now = open)).showAutomaticWarning)
        assertFalse(
            WelcomeBackPolicy.evaluate(
                facts(record = record.copy(warningDismissed = true), now = open)
            ).showAutomaticWarning
        )
        assertFalse(WelcomeBackPolicy.evaluate(facts(record = record, now = expiresAt)).showAutomaticWarning)
        assertFalse(
            WelcomeBackPolicy.evaluate(
                facts(record = record, now = open, continued = true)
            ).showAutomaticWarning
        )
    }

    @Test
    fun eligibleStillNeedsAWorkoutAndTheShortTrialStaysLocal() = runBlocking {
        val store = MemoryStore()
        val real = MemoryStore()
        val clock = Clock()
        clock.instant = day40
        var backendCalls = 0
        val history = mutableListOf<WelcomeBackCompletion>()
        val coordinator = WelcomeBackCoordinator(
            real = real,
            simulation = store,
            now = { clock.instant },
            zone = zone,
            completions = { history.toList() },
            debugConfig = {
                WelcomeBackDebugConfig(
                    mode = WelcomeBackDebugMode.ELIGIBLE,
                    gapDays = 1,
                    expiresInSeconds = 120,
                    warningBeforeSeconds = 60,
                    cooldownSeconds = 180
                )
            },
            promotionsEnabled = { false },
            authorizeActivation = {
                backendCalls += 1
                WelcomeBackAuthorization.Unavailable
            }
        )
        coordinator.refreshHistory()
        assertFalse(coordinator.snapshot().showAutomaticOffer)
        history += completion("first", day40)
        coordinator.noteCompletion("first")
        assertTrue(coordinator.snapshot().showAutomaticOffer)
        coordinator.activate()
        assertEquals(0, backendCalls)
        assertNull(real.record.activatedAt)
        assertEquals(day40.plusSeconds(120), store.record.expiresAt)
        assertEquals(day40.plusSeconds(180), store.record.cooldownUntil)
        clock.instant = day40.plusSeconds(60)
        assertTrue(coordinator.snapshot().showAutomaticWarning)
        clock.instant = day40.plusSeconds(120)
        assertFalse(coordinator.currentGrant().isActive(clock.instant))
        assertTrue(coordinator.snapshot().trialExpired)
        val restarted = WelcomeBackCoordinator(
            real = real,
            simulation = store,
            now = { clock.instant },
            zone = zone,
            completions = { history.toList() },
            debugConfig = {
                WelcomeBackDebugConfig(
                    mode = WelcomeBackDebugMode.ELIGIBLE,
                    expiresInSeconds = 120,
                    warningBeforeSeconds = 60,
                    cooldownSeconds = 180
                )
            },
            promotionsEnabled = { false }
        )
        assertEquals(store.record.expiresAt, restarted.snapshot().trialExpiresAt)
    }

    @Test
    fun activeAndExpiredDebugModesDoNotCallTheBackend() = runBlocking {
        val simulation = MemoryStore()
        val clock = Clock()
        var calls = 0
        val active = WelcomeBackCoordinator(
            real = MemoryStore(),
            simulation = simulation,
            now = { clock.instant },
            zone = zone,
            completions = { emptyList() },
            debugConfig = { WelcomeBackDebugConfig(mode = WelcomeBackDebugMode.ACTIVE, expiresInSeconds = 120) },
            promotionsEnabled = { true },
            authorizeActivation = {
                calls += 1
                WelcomeBackAuthorization.Unavailable
            }
        )
        active.prepare()
        assertTrue(active.snapshot().trialActive)
        assertFalse(active.snapshot().offerAvailable)
        active.activate()
        assertEquals(0, calls)
        val expired = WelcomeBackCoordinator(
            real = MemoryStore(),
            simulation = MemoryStore(),
            now = { clock.instant },
            zone = zone,
            completions = { emptyList() },
            debugConfig = { WelcomeBackDebugConfig(mode = WelcomeBackDebugMode.EXPIRED) },
            promotionsEnabled = { true }
        )
        expired.prepare()
        assertTrue(expired.snapshot().trialExpired)
        assertFalse(expired.currentGrant().isActive(clock.instant))
    }

    @Test
    fun promptsStayBehindFounderDiscoveryAndTheWorkoutCompleteScreen() {
        assertEquals(
            StrictOneTimePrompt.FounderApproval,
            selectStrictOneTimePrompt(true, true, false, true, true, true, true)
        )
        assertNull(selectStrictOneTimePrompt(false, false, true, false, false, true, true))
        assertEquals(
            StrictOneTimePrompt.ProDiscoveryOffer,
            selectStrictOneTimePrompt(false, false, false, true, false, true, false)
        )
        assertEquals(
            StrictOneTimePrompt.WelcomeBackOffer,
            selectStrictOneTimePrompt(false, false, false, false, false, true, false)
        )
        assertEquals(
            StrictOneTimePrompt.WelcomeBackWarning,
            selectStrictOneTimePrompt(false, false, false, false, false, false, true)
        )
    }

    @Test
    fun otherProStillCoversTheWelcomeBackExpirationInstant() {
        val expiry = day40.plus(WelcomeBackPolicy.TRIAL_DURATION)
        val covered = EntitlementSources.of(
            promotionalPro = PromotionalProEntitlement(expiresAt = expiry.plusSeconds(1))
        )
        assertTrue(WelcomeBackPolicy.continuedProBeyond(covered, expiry))
        val sameInstant = EntitlementSources.of(
            promotionalPro = PromotionalProEntitlement(expiresAt = expiry)
        )
        assertFalse(WelcomeBackPolicy.continuedProBeyond(sameInstant, expiry))
    }

    private fun qualify(id: String, vararg completions: WelcomeBackCompletion): String? {
        return WelcomeBackPolicy.qualifyingWorkout(completions.toList(), id, zone, 40, false)
    }

    private fun completion(id: String, at: Instant) = WelcomeBackCompletion(id, at)

    private fun facts(
        history: List<WelcomeBackCompletion> = emptyList(),
        record: WelcomeBackRecord = WelcomeBackRecord(),
        now: Instant = day40,
        currentlyFree: Boolean = true,
        promotionsEnabled: Boolean = true,
        continued: Boolean = false,
        automatic: String? = record.qualifyingWorkoutId
    ) = WelcomeBackFacts(
        completions = history,
        zone = zone,
        now = now,
        currentlyFree = currentlyFree,
        promotionsEnabled = promotionsEnabled,
        continuedProBeyondGrant = continued,
        record = record,
        automaticWorkoutId = automatic
    )

    private fun coordinator(
        store: MemoryStore,
        clock: Clock,
        history: MutableList<WelcomeBackCompletion>,
        authorize: suspend (String) -> WelcomeBackAuthorization = { WelcomeBackAuthorization.Local }
    ): WelcomeBackCoordinator {
        return WelcomeBackCoordinator(
            real = store,
            simulation = MemoryStore(),
            now = { clock.instant },
            zone = zone,
            completions = { history.toList() },
            debugConfig = { WelcomeBackDebugConfig() },
            promotionsEnabled = { true },
            coverage = { WelcomeBackCoverage(currentlyFree = true, continuedBeyond = { false }) },
            authorizeActivation = authorize,
            fetchGrant = { WelcomeBackRemote.Unavailable }
        )
    }

    private class Clock {
        var instant: Instant = Instant.EPOCH
    }

    private class MemoryStore : WelcomeBackStateStore {
        var record = WelcomeBackRecord()
        private var fingerprint: String? = null

        override fun current(): WelcomeBackRecord = record

        override fun fingerprint(): String? = fingerprint

        override suspend fun load() = Unit

        override suspend fun noteQualifying(workoutId: String) {
            record = record.copy(qualifyingWorkoutId = workoutId, popupDismissed = false)
        }

        override suspend fun dismissPopup() {
            record = record.copy(popupDismissed = true)
        }

        override suspend fun dismissWarning() {
            record = record.copy(warningDismissed = true)
        }

        override suspend fun activate(
            activatedAt: Instant,
            expiresAt: Instant,
            cooldownUntil: Instant,
            workoutId: String
        ) {
            record = record.copy(
                activatedAt = activatedAt,
                expiresAt = expiresAt,
                cooldownUntil = cooldownUntil,
                activatedWorkoutId = workoutId,
                qualifyingWorkoutId = null
            )
        }

        override suspend fun applyRemote(record: WelcomeBackRecord?) {
            this.record = if (record == null) {
                this.record.copy(
                    activatedAt = null,
                    expiresAt = null,
                    cooldownUntil = null,
                    activatedWorkoutId = null
                )
            } else {
                this.record.copy(
                    activatedAt = record.activatedAt,
                    expiresAt = record.expiresAt,
                    cooldownUntil = record.cooldownUntil,
                    activatedWorkoutId = record.activatedWorkoutId
                )
            }
        }

        override suspend fun reset(fingerprint: String) {
            record = WelcomeBackRecord()
            this.fingerprint = fingerprint
        }

        override suspend fun clear() {
            record = WelcomeBackRecord()
            fingerprint = null
        }
    }
}
