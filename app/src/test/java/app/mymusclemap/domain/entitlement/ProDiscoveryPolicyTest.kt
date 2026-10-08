package app.mymusclemap.domain.entitlement

import app.mymusclemap.data.promotion.ProDiscoveryCoordinator
import app.mymusclemap.data.preferences.ProDiscoveryStateStore
import app.mymusclemap.domain.achievements.AchievementAccess
import app.mymusclemap.domain.achievements.AchievementCategory
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.BadgeWallItem
import app.mymusclemap.domain.achievements.visualState
import app.mymusclemap.domain.achievements.BadgeVisualState
import app.mymusclemap.domain.statistics.StatisticsRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ProDiscoveryPolicyTest {
    private val zone = ZoneOffset.UTC
    private val today = Instant.parse("2026-08-08T12:00:00Z")

    @Test
    fun fiftyNineCalendarDaysIsNotEligibleAndSixtyIs() {
        val almost = completion("2026-06-10T12:00:00Z")
        val ready = completion("2026-06-09T12:00:00Z")
        assertEquals(59, ProDiscoveryPolicy.calendarDaysSince(almost, today.atZone(zone).toLocalDate(), zone))
        assertEquals(60, ProDiscoveryPolicy.calendarDaysSince(ready, today.atZone(zone).toLocalDate(), zone))
        assertFalse(history(almost).offerAvailable)
        assertTrue(history(ready).offerAvailable)
    }

    @Test
    fun theFirstNativeCompletionIsTheEligibilityClock() {
        val first = completion("2026-06-09T12:00:00Z")
        val later = completion("2026-08-01T12:00:00Z")
        assertTrue(history(later, first).offerAvailable)
        assertFalse(history(later).offerAvailable)
    }

    @Test
    fun importedWorkoutsDoNotCount() {
        assertFalse(ProDiscoveryPolicy.countsAsNative("import-1"))
        assertTrue(ProDiscoveryPolicy.countsAsNative(null))
        assertFalse(history().offerAvailable)
    }

    @Test
    fun aWorkoutExactlyThirtyCalendarDaysOldDoesNotSatisfyTheOlderRequirement() {
        val todayDate = today.atZone(zone).toLocalDate()
        val exactlyThirty = today.minus(Duration.ofDays(30))
        val older = today.minus(Duration.ofDays(31))
        assertEquals(30, ProDiscoveryPolicy.calendarDaysSince(exactlyThirty, todayDate, zone))
        assertFalse(ProDiscoveryPolicy.meetsHistory(listOf(exactlyThirty), todayDate, zone))
        assertEquals(31, ProDiscoveryPolicy.calendarDaysSince(older, todayDate, zone))
        assertFalse(ProDiscoveryPolicy.meetsHistory(listOf(older), todayDate, zone))
        assertTrue(
            ProDiscoveryPolicy.meetsHistory(
                listOf(completion("2026-06-09T12:00:00Z"), older),
                todayDate,
                zone
            )
        )
    }

    @Test
    fun activeProPreventsTheOffer() {
        val snapshot = history(completion("2026-06-09T12:00:00Z"), currentlyFree = false)
        assertFalse(snapshot.offerAvailable)
        assertFalse(snapshot.showAutomaticOffer)
    }

    @Test
    fun founderProgramSuppressesNewOffersButDoesNotEndAnExistingTrial() {
        val ready = completion("2026-06-09T12:00:00Z")
        assertFalse(history(ready, promotionsEnabled = false).offerAvailable)
        val expiresAt = today.plus(Duration.ofDays(3))
        val running = evaluate(
            completions = listOf(ready),
            promotionsEnabled = false,
            record = ProDiscoveryRecord(activatedAt = today.minus(Duration.ofDays(1)), expiresAt = expiresAt)
        )
        assertTrue(running.trialActive)
        assertFalse(running.offerAvailable)
        assertEquals(expiresAt, running.trialExpiresAt)
    }

    @Test
    fun dismissalDoesNotActivateAndTheOfferStaysAvailable() {
        val coordinator = coordinator(history = listOf(completion("2026-06-09T12:00:00Z")))
        runBlocking { coordinator.dismissOffer() }
        val snapshot = coordinator.snapshot()
        assertFalse(snapshot.showAutomaticOffer)
        assertTrue(snapshot.offerAvailable)
        assertNull(coordinator.realRecord().activatedAt)
        assertFalse(snapshot.trialActive)
    }

    @Test
    fun explicitAcceptanceStartsExactlyFourteenDaysAndRepeatAcceptanceIsTheSameTrial() = runBlocking {
        val coordinator = coordinator(history = listOf(completion("2026-06-09T12:00:00Z")))
        val started = coordinator.activate() as ProDiscoveryActivation.Started
        val again = coordinator.activate() as ProDiscoveryActivation.AlreadyActive
        assertEquals(today.plus(ProDiscoveryPolicy.TRIAL_DURATION), started.expiresAt)
        assertEquals(Duration.ofHours(14 * 24), ProDiscoveryPolicy.TRIAL_DURATION)
        assertEquals(started.expiresAt, again.expiresAt)
        assertTrue(coordinator.snapshot().trialActive)
    }

    @Test
    fun aCompletedTrialCannotBeActivatedAgain() = runBlocking {
        val store = MemoryDiscoveryStore()
        store.activate(today.minus(Duration.ofDays(20)), today)
        val coordinator = coordinator(real = store, now = today)
        assertEquals(ProDiscoveryActivation.AlreadyUsed, coordinator.activate())
        assertFalse(coordinator.snapshot().trialActive)
        assertTrue(coordinator.snapshot().trialExpired)
    }

    @Test
    fun promotionalProIsActiveImmediatelyBeforeExpirationAndInactiveAtThatInstant() {
        val expiresAt = today
        val before = EntitlementResolver.resolve(
            EntitlementSources(promotionalPro = PromotionalProEntitlement(expiresAt)),
            expiresAt.minusMillis(1)
        )
        val atExpiry = EntitlementResolver.resolve(
            EntitlementSources(promotionalPro = PromotionalProEntitlement(expiresAt)),
            expiresAt
        )
        assertTrue(before.promotionalProActive)
        assertTrue(before.grantsPro)
        assertTrue(FeatureAccessPolicy(before).canViewStatistics(StatisticsRange.Months3))
        assertFalse(atExpiry.promotionalProActive)
        assertFalse(atExpiry.grantsPro)
        assertFalse(FeatureAccessPolicy(atExpiry).canViewStatistics(StatisticsRange.Months3))
        assertTrue(FeatureAccessPolicy(atExpiry).canViewStatistics(StatisticsRange.Days30))
    }

    @Test
    fun anotherProEntitlementRemainsValidAfterTheTrialExpires() {
        val expiresAt = today
        val sources = EntitlementSources(
            subscription = SubscriptionEntitlement(paidUntilInclusive = expiresAt.plus(Duration.ofDays(10))),
            promotionalPro = PromotionalProEntitlement(expiresAt)
        )
        val resolved = EntitlementResolver.resolve(sources, expiresAt)
        assertFalse(resolved.promotionalProActive)
        assertTrue(resolved.subscriptionValid)
        assertTrue(resolved.grantsPro)
        assertTrue(ProDiscoveryPolicy.continuedProBeyond(sources, expiresAt))
    }

    @Test
    fun storedAchievementProgressAndWorkoutHistoryStayInPlace() = runBlocking {
        val workouts = mutableListOf(completion("2026-06-09T12:00:00Z"))
        val earnedAt = 1_700_000_000_000L
        val badge = BadgeWallItem(
            id = AchievementId.entries.first(),
            category = AchievementCategory.entries.first(),
            badgeKey = "pro-badge",
            badgeFamily = null,
            badgeTier = null,
            unlocked = false,
            unlockedAt = earnedAt,
            countProgress = null,
            access = AchievementAccess.PRO,
            requirementMet = true
        )
        val coordinator = coordinator(history = workouts)
        val started = coordinator.activate() as ProDiscoveryActivation.Started
        assertEquals(today.plus(ProDiscoveryPolicy.TRIAL_DURATION), started.expiresAt)
        val expired = EntitlementResolver.resolve(EntitlementSources(), started.expiresAt)
        assertFalse(expired.grantsPro)
        assertEquals(BadgeVisualState.LOCKED, badge.visualState())
        assertEquals(earnedAt, badge.unlockedAt)
        assertEquals(listOf(completion("2026-06-09T12:00:00Z")), workouts)
    }

    @Test
    fun warningOpensAtFortyEightHoursShowsOnceAndStopsAfterExpiryOrContinuedPro() {
        val expiresAt = today.plus(ProDiscoveryPolicy.WARNING_LEAD)
        val record = ProDiscoveryRecord(activatedAt = today.minus(Duration.ofDays(12)), expiresAt = expiresAt)
        val tooEarly = evaluate(record = record, now = expiresAt.minus(ProDiscoveryPolicy.WARNING_LEAD).minusMillis(1))
        assertFalse(tooEarly.showAutomaticWarning)
        val due = evaluate(record = record, now = expiresAt.minus(ProDiscoveryPolicy.WARNING_LEAD))
        assertTrue(due.showAutomaticWarning)
        assertTrue(due.warningOnBenefits)
        val dismissed = evaluate(
            record = record.copy(warningDismissed = true),
            now = expiresAt.minus(Duration.ofHours(1))
        )
        assertFalse(dismissed.showAutomaticWarning)
        assertTrue(dismissed.warningOnBenefits)
        val expired = evaluate(record = record, now = expiresAt)
        assertFalse(expired.showAutomaticWarning)
        assertFalse(expired.warningOnBenefits)
        val covered = evaluate(
            record = record,
            now = expiresAt.minus(Duration.ofHours(1)),
            continuedPro = true
        )
        assertFalse(covered.showAutomaticWarning)
        assertFalse(covered.warningOnBenefits)
    }

    @Test
    fun shortDebugTrialAndWarningUseTheSameExpirationAndDoNotTouchTheRealRecord() = runBlocking {
        val real = MemoryDiscoveryStore()
        val simulation = MemoryDiscoveryStore()
        var now = Instant.parse("2026-08-08T12:00:00Z")
        val config = ProDiscoveryDebugConfig(
            mode = ProDiscoveryDebugMode.ELIGIBLE,
            expiresInSeconds = 120,
            warningBeforeSeconds = 60
        )
        val coordinator = coordinator(
            real = real,
            simulation = simulation,
            nowProvider = { now },
            debug = config,
            history = emptyList()
        )
        val started = coordinator.activate() as ProDiscoveryActivation.Started
        assertEquals(now.plusSeconds(120), started.expiresAt)
        assertNull(real.current().activatedAt)
        assertEquals(started.expiresAt, simulation.current().expiresAt)
        now = now.plusSeconds(59)
        assertFalse(coordinator.snapshot().showAutomaticWarning)
        assertTrue(coordinator.currentGrant().isActive(now))
        now = now.plusSeconds(1)
        assertTrue(coordinator.snapshot().showAutomaticWarning)
        coordinator.dismissWarning()
        assertFalse(coordinator.snapshot().showAutomaticWarning)
        val restarted = coordinator(
            real = real,
            simulation = simulation,
            nowProvider = { now },
            debug = config
        )
        assertEquals(started.expiresAt, restarted.currentGrant().expiresAt)
        now = started.expiresAt
        assertFalse(restarted.currentGrant().isActive(now))
        assertFalse(restarted.snapshot().showAutomaticWarning)
        assertNull(real.current().activatedAt)
    }

    @Test
    fun debugModesSimulateEligibilityActivityAndExpiryWithoutTheRealTrial() = runBlocking {
        val real = MemoryDiscoveryStore()
        val eligible = coordinator(real = real, debug = ProDiscoveryDebugConfig(ProDiscoveryDebugMode.ELIGIBLE))
        assertTrue(eligible.snapshot().showAutomaticOffer)
        val activeSimulation = MemoryDiscoveryStore()
        val active = coordinator(
            real = real,
            simulation = activeSimulation,
            debug = ProDiscoveryDebugConfig(ProDiscoveryDebugMode.ACTIVE, expiresInSeconds = 120)
        )
        active.prepare()
        assertTrue(active.snapshot().trialActive)
        assertTrue(active.currentGrant().isActive(today))
        assertNull(real.current().activatedAt)
        val expired = coordinator(
            real = real,
            simulation = MemoryDiscoveryStore(),
            debug = ProDiscoveryDebugConfig(ProDiscoveryDebugMode.EXPIRED)
        )
        expired.prepare()
        assertTrue(expired.snapshot().trialExpired)
        assertFalse(expired.currentGrant().isActive(today))
        assertNull(real.current().activatedAt)
        assertFalse(ProDiscoveryDebugConfig().usesSimulationStore)
        assertTrue(ProDiscoveryDebugConfig(expiresInSeconds = 120).usesSimulationStore)
    }

    @Test
    fun debugParsingRejectsUnknownModesAndReleaseDefaultsStayReal() {
        val parsed = ProDiscoveryDebugConfig.parse("ACTIVE", "120", "60")
        assertEquals(ProDiscoveryDebugMode.ACTIVE, parsed.mode)
        assertEquals(120L, parsed.expiresInSeconds)
        assertEquals(60L, parsed.warningBeforeSeconds)
        val real = ProDiscoveryDebugConfig.parse("REAL", "", "")
        assertEquals(ProDiscoveryDebugConfig(), real)
        try {
            ProDiscoveryDebugConfig.parse("FREE", "", "")
            throw AssertionError("Unknown mode was accepted")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("REAL"))
        }
    }

    @Test
    fun oneTimePromptsDoNotOverlap() {
        assertEquals(
            StrictOneTimePrompt.FounderApproval,
            selectStrictOneTimePrompt(
                founderApproval = true,
                workoutCelebration = true,
                suppressForRoute = false,
                discoveryOffer = true,
                discoveryWarning = true
            )
        )
        assertEquals(
            StrictOneTimePrompt.WorkoutCelebration,
            selectStrictOneTimePrompt(
                founderApproval = false,
                workoutCelebration = true,
                suppressForRoute = false,
                discoveryOffer = true,
                discoveryWarning = false
            )
        )
        assertNull(
            selectStrictOneTimePrompt(
                founderApproval = false,
                workoutCelebration = true,
                suppressForRoute = true,
                discoveryOffer = true,
                discoveryWarning = true
            )
        )
        assertEquals(
            StrictOneTimePrompt.ProDiscoveryOffer,
            selectStrictOneTimePrompt(
                founderApproval = false,
                workoutCelebration = false,
                suppressForRoute = false,
                discoveryOffer = true,
                discoveryWarning = true
            )
        )
        assertEquals(
            StrictOneTimePrompt.ProDiscoveryWarning,
            selectStrictOneTimePrompt(
                founderApproval = false,
                workoutCelebration = false,
                suppressForRoute = false,
                discoveryOffer = false,
                discoveryWarning = true
            )
        )
    }

    @Test
    fun nextBoundaryIsTheWarningInstantThenTheExpirationInstant() {
        val expiresAt = today.plusSeconds(120)
        assertEquals(today.plusSeconds(60), ProDiscoveryPolicy.nextBoundary(expiresAt, today, Duration.ofSeconds(60)))
        assertEquals(
            expiresAt,
            ProDiscoveryPolicy.nextBoundary(expiresAt, today.plusSeconds(60), Duration.ofSeconds(60))
        )
        assertNull(ProDiscoveryPolicy.nextBoundary(expiresAt, expiresAt, Duration.ofSeconds(60)))
    }

    private fun history(
        vararg completions: Instant,
        currentlyFree: Boolean = true,
        promotionsEnabled: Boolean = true
    ): ProDiscoverySnapshot {
        return evaluate(
            completions = completions.toList(),
            currentlyFree = currentlyFree,
            promotionsEnabled = promotionsEnabled
        )
    }

    private fun evaluate(
        completions: List<Instant> = emptyList(),
        now: Instant = today,
        currentlyFree: Boolean = true,
        promotionsEnabled: Boolean = true,
        continuedPro: Boolean = false,
        record: ProDiscoveryRecord = ProDiscoveryRecord(),
        debug: ProDiscoveryDebugConfig = ProDiscoveryDebugConfig()
    ): ProDiscoverySnapshot {
        return ProDiscoveryPolicy.evaluate(
            ProDiscoveryFacts(
                nativeCompletions = completions,
                zone = zone,
                now = now,
                currentlyFree = currentlyFree,
                promotionsEnabled = promotionsEnabled,
                continuedProBeyondTrial = continuedPro,
                record = record,
                debug = debug
            )
        )
    }

    private fun completion(raw: String): Instant = Instant.parse(raw)

    private fun coordinator(
        history: List<Instant> = emptyList(),
        real: MemoryDiscoveryStore = MemoryDiscoveryStore(),
        simulation: MemoryDiscoveryStore = MemoryDiscoveryStore(),
        now: Instant = today,
        nowProvider: () -> Instant = { now },
        debug: ProDiscoveryDebugConfig = ProDiscoveryDebugConfig(),
        promotionsEnabled: Boolean = true
    ): TestCoordinator {
        val created = ProDiscoveryCoordinator(
            real = real,
            simulation = simulation,
            now = nowProvider,
            zone = zone,
            completions = { history },
            debugConfig = { debug },
            promotionsEnabled = { promotionsEnabled }
        )
        runBlocking { created.refreshHistory() }
        return TestCoordinator(created, real)
    }

    private class TestCoordinator(
        private val coordinator: ProDiscoveryCoordinator,
        private val real: MemoryDiscoveryStore
    ) {
        fun snapshot() = coordinator.snapshot()
        fun currentGrant() = coordinator.currentGrant()
        fun realRecord() = real.current()
        suspend fun activate() = coordinator.activate()
        suspend fun dismissOffer() = coordinator.dismissOffer()
        suspend fun dismissWarning() = coordinator.dismissWarning()
        suspend fun prepare() = coordinator.prepare()
    }
}

private class MemoryDiscoveryStore : ProDiscoveryStateStore {
    private var record = ProDiscoveryRecord()
    private var fingerprint: String? = null

    override fun current(): ProDiscoveryRecord = record

    override fun fingerprint(): String? = fingerprint

    override suspend fun load() = Unit

    override suspend fun dismissPopup() {
        record = record.copy(popupDismissed = true)
    }

    override suspend fun dismissWarning() {
        record = record.copy(warningDismissed = true)
    }

    override suspend fun activate(activatedAt: Instant, expiresAt: Instant) {
        if (record.activatedAt != null) {
            return
        }
        record = record.copy(activatedAt = activatedAt, expiresAt = expiresAt)
    }

    override suspend fun reset(fingerprint: String) {
        record = ProDiscoveryRecord()
        this.fingerprint = fingerprint
    }

    override suspend fun clear() {
        record = ProDiscoveryRecord()
        fingerprint = null
    }
}
