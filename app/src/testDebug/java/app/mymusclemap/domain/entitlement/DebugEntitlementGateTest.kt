package app.mymusclemap.domain.entitlement

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.DebugEntitlementGateSelection
import app.mymusclemap.domain.workout.WorkoutTimerAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class DebugEntitlementGateTest {
    private val now = Instant.parse("2026-10-21T08:00:00Z")

    @Before
    fun clearDebugPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(
            DebugEntitlementPreferenceStore.FILE,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun realForceProAndForceFreeStayConsistentWithoutChangingBilling() {
        val paidUntil = now.plusSeconds(3_600)
        val subscription = SubscriptionEntitlement(paidUntilInclusive = paidUntil, renewalCancelled = true)
        val sources = EntitlementSources(subscription = subscription)
        val actual = EntitlementResolver.resolve(sources, now)
        assertSame(actual, DebugEntitlementOverlay.apply(actual, DebugEntitlementMode.Real))
        val forcedPro = DebugEntitlementOverlay.apply(
            EffectiveEntitlement(EntitlementTier.Free, false, false, false),
            DebugEntitlementMode.ForcePro
        )
        val forcedFree = DebugEntitlementOverlay.apply(actual, DebugEntitlementMode.ForceFree)
        listOf(actual, forcedPro, forcedFree).forEach { entitlement ->
            assertTrue(DebugEntitlementOverlay.isConsistent(entitlement))
        }
        assertTrue(forcedPro.grantsPro)
        assertFalse(forcedPro.subscriptionValid)
        assertTrue(forcedPro.temporaryTesterPro)
        assertFalse(forcedFree.grantsPro)
        assertFalse(forcedFree.subscriptionValid)
        assertFalse(forcedFree.founderLifetime)
        assertFalse(forcedFree.temporaryTesterPro)
        assertFalse(forcedFree.founderProActive)
        assertFalse(forcedFree.promotionalProActive)
        assertFalse(forcedFree.welcomeBackActive)
        assertEquals(paidUntil, sources.subscription.paidUntilInclusive)
        assertTrue(sources.subscription.renewalCancelled)
        assertTrue(actual.subscriptionValid)
    }

    @Test
    fun everyRealGrantSurvivesRealModeAndIsRemovedByForceFree() {
        val grants = listOf(
            EntitlementSources(subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(60))),
            EntitlementSources(founderLifetime = FounderLifetimeEntitlement(active = true)),
            EntitlementSources(
                backendFounder = BackendFounderEntitlement(
                    temporaryFounderPro = true,
                    validUntil = now.plusSeconds(60)
                )
            ),
            EntitlementSources(
                backendFounder = BackendFounderEntitlement(
                    founderRecognized = true,
                    founderProExpiresAt = now.plusSeconds(60),
                    validUntil = now.plusSeconds(60)
                )
            ),
            EntitlementSources(promotionalPro = PromotionalProEntitlement(expiresAt = now.plusSeconds(60))),
            EntitlementSources(welcomeBack = PromotionalProEntitlement(expiresAt = now.plusSeconds(60)))
        )
        grants.forEach { sources ->
            val actual = EntitlementResolver.resolve(sources, now)
            assertTrue(actual.grantsPro)
            assertSame(actual, DebugEntitlementOverlay.apply(actual, DebugEntitlementMode.Real))
            val hidden = DebugEntitlementOverlay.apply(actual, DebugEntitlementMode.ForceFree)
            assertFalse(hidden.grantsPro)
            assertTrue(DebugEntitlementOverlay.isConsistent(hidden))
            assertTrue(EntitlementResolver.resolve(sources, now).grantsPro)
        }
    }

    @Test
    fun founderRecognitionSurvivesForceFreeAndTimersFollowTheEffectiveTier() {
        val actual = EntitlementResolver.resolve(
            EntitlementSources(
                subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(60)),
                backendFounder = BackendFounderEntitlement(
                    founderRecognized = true,
                    founderLifetime = true,
                    validUntil = now.plusSeconds(60)
                )
            ),
            now
        )
        val hidden = DebugEntitlementOverlay.apply(actual, DebugEntitlementMode.ForceFree)
        assertTrue(hidden.founderRecognized)
        assertFalse(hidden.founderLifetime)
        assertFalse(policy(hidden).workoutTimers())
        assertFalse(WorkoutTimerAccess.allowed(PolicyBackedEntitlements(policy(hidden))))
        val promoted = DebugEntitlementOverlay.apply(
            EffectiveEntitlement(EntitlementTier.Free, false, false, false, founderRecognized = true),
            DebugEntitlementMode.ForcePro
        )
        assertTrue(promoted.founderRecognized)
        assertTrue(policy(promoted).workoutTimers())
        assertTrue(policy(promoted).workoutPlans(99).canCreate)
        assertTrue(policy(promoted).customExercises(99).canCreate)
        assertTrue(WorkoutTimerAccess.allowed(PolicyBackedEntitlements(policy(promoted))))
    }

    @Test
    fun composerAppliesTheOverlayAfterResolution() {
        val paidUntil = now.plusSeconds(120)
        val composer = EntitlementComposer(
            subscriptionProvider = object : SubscriptionEntitlementProvider {
                override fun current() = SubscriptionEntitlement(paidUntilInclusive = paidUntil)
            },
            founderLifetimeProvider = InactiveFounderLifetimeProvider,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            applyEffective = { DebugEntitlementOverlay.apply(it, DebugEntitlementMode.ForceFree) }
        )
        assertTrue(composer.resolveActual().grantsPro)
        assertFalse(composer.resolve().grantsPro)
        assertEquals(paidUntil, composer.sources().subscription.paidUntilInclusive)
    }

    @Test
    fun modeDefaultsToRealAndSurvivesANewStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = DebugEntitlementPreferenceStore(context)
        assertEquals(DebugEntitlementControl(), first.read())
        first.write(DebugEntitlementControl(mode = DebugEntitlementMode.ForcePro))
        val second = DebugEntitlementPreferenceStore(context)
        assertEquals(DebugEntitlementMode.ForcePro, second.read().mode)
        assertNotEquals(
            "verified_play_subscription",
            DebugEntitlementPreferenceStore.FILE
        )
        assertFalse(
            context.getSharedPreferences("verified_play_subscription", Context.MODE_PRIVATE)
                .contains(DebugEntitlementPreferenceStore.KEY_MODE)
        )
    }

    @Test
    fun selectionFactoryReadsTheDebugPreferenceFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DebugEntitlementPreferenceStore(context).write(
            DebugEntitlementControl(mode = DebugEntitlementMode.ForcePro)
        )
        val gate = DebugEntitlementGateSelection.open(context) {}
        assertEquals(DebugEntitlementMode.ForcePro, gate.control.value.mode)
        assertTrue(gate.apply(free()).grantsPro)
        assertFalse(free().grantsPro)
    }

    @Test
    fun pendingNextLaunchSurvivesUntilTheNextGateConsumesItOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DebugEntitlementPreferenceStore(context)
        var events = 0
        val running = StoredDebugEntitlementGate(store) { events += 1 }
        running.select(DebugEntitlementMode.ForcePro)
        assertEquals(1, events)
        val pro = pro()
        assertTrue(running.armFreeOnNextLaunch(running.apply(pro).grantsPro))
        assertEquals(1, events)
        assertEquals(DebugEntitlementMode.ForcePro, running.control.value.mode)
        assertTrue(running.control.value.pendingFreeOnNextLaunch)
        assertTrue(running.apply(pro).grantsPro)
        val restarted = StoredDebugEntitlementGate(store) { events += 1 }
        assertEquals(1, events)
        assertEquals(DebugEntitlementMode.ForceFree, restarted.control.value.mode)
        assertFalse(restarted.control.value.pendingFreeOnNextLaunch)
        assertFalse(restarted.apply(pro).grantsPro)
        val again = StoredDebugEntitlementGate(store) { events += 1 }
        assertEquals(DebugEntitlementMode.ForceFree, again.control.value.mode)
        assertFalse(again.control.value.pendingFreeOnNextLaunch)
        assertEquals(1, events)
    }

    @Test
    fun armIsRefusedUnlessTheEffectiveEntitlementIsPro() {
        val gate = StoredDebugEntitlementGate(MemoryDebugEntitlementStore()) {}
        assertFalse(gate.armFreeOnNextLaunch(false))
        assertFalse(gate.control.value.pendingFreeOnNextLaunch)
        gate.select(DebugEntitlementMode.ForceFree)
        assertFalse(gate.armFreeOnNextLaunch(true))
        assertEquals(DebugEntitlementMode.ForceFree, gate.control.value.mode)
        assertFalse(gate.control.value.pendingFreeOnNextLaunch)
    }

    @Test
    fun repeatedSelectionDoesNotPublishAnotherEntitlementEvent() {
        var events = 0
        val gate = StoredDebugEntitlementGate(MemoryDebugEntitlementStore()) { events += 1 }
        gate.select(DebugEntitlementMode.ForcePro)
        gate.select(DebugEntitlementMode.ForcePro)
        gate.select(DebugEntitlementMode.Real)
        gate.select(DebugEntitlementMode.Real)
        assertEquals(2, events)
    }

    @Test
    fun liveProToFreeOpensOneRequiredPeriodAndADraftDoesNotGrantAccess() {
        val session = Session()
        session.billingReady = true
        session.gate.select(DebugEntitlementMode.ForcePro)
        session.refresh()
        val proPeriod = session.state!!.periodId
        session.gate.select(DebugEntitlementMode.ForceFree)
        val required = session.refresh()
        assertFalse(session.effective().grantsPro)
        assertEquals(FreeAccessPhase.Required, required.state.phase)
        assertEquals(proPeriod + 1L, required.state.periodId)
        assertTrue(required.selectionRequired)
        assertTrue(required.accessiblePlanIds.isEmpty())
        assertTrue(required.accessibleCustomExerciseIds.isEmpty())
        assertNull(
            FreeAccessLogic.confirm(
                required.state,
                planIds = session.plans.map { it.id }.toSet(),
                customExerciseIds = setOf(session.exercises.first().id),
                plans = session.plans,
                customExercises = session.exercises
            )
        )
        val repeated = session.refresh()
        assertEquals(required.state.periodId, repeated.state.periodId)
        assertEquals(FreeAccessPhase.Required, repeated.state.phase)
        val confirmed = FreeAccessLogic.confirm(
            required.state,
            planIds = session.plans.take(3).map { it.id }.toSet(),
            customExerciseIds = session.exercises.take(5).map { it.id }.toSet(),
            plans = session.plans,
            customExercises = session.exercises
        )!!
        session.state = confirmed
        val locked = session.refresh()
        assertEquals(FreeAccessPhase.Confirmed, locked.state.phase)
        assertEquals(session.plans.take(3).map { it.id }.toSet(), locked.accessiblePlanIds)
        assertEquals(session.exercises.take(5).map { it.id }.toSet(), locked.accessibleCustomExerciseIds)
        assertEquals(setOf(session.plans.last().id), locked.lockedPlanIds)
        assertEquals(setOf(session.exercises.last().id), locked.lockedCustomExerciseIds)
        assertFalse(locked.planAccessible(session.plans.last().id))
        assertFalse(locked.customExerciseAccessible(session.exercises.last().id))
    }

    @Test
    fun closedAppDowngradeUsesPersistedProAndAppliesFreeBeforeReconciliation() {
        val session = Session()
        session.billingReady = true
        session.gate.select(DebugEntitlementMode.ForcePro)
        val before = session.refresh()
        assertTrue(before.state.lastKnownPro == true)
        val events = session.liveEvents
        assertTrue(session.gate.armFreeOnNextLaunch(session.effective().grantsPro))
        assertEquals(events, session.liveEvents)
        val stillPro = session.refresh()
        assertEquals(before.state.periodId, stillPro.state.periodId)
        assertTrue(session.effective().grantsPro)
        session.restart()
        session.billingReady = false
        assertEquals(DebugEntitlementMode.ForceFree, session.gate.control.value.mode)
        assertTrue(session.ready())
        val required = session.refresh()
        assertEquals(FreeAccessPhase.Required, required.state.phase)
        assertEquals(before.state.periodId + 1L, required.state.periodId)
        assertTrue(required.selectionRequired)
        val again = session.refresh()
        assertEquals(required.state.periodId, again.state.periodId)
    }

    @Test
    fun confirmedFreeSelectionSurvivesRestartAndASecondDowngradeIsANewPeriod() {
        val session = Session()
        session.billingReady = true
        session.gate.select(DebugEntitlementMode.ForcePro)
        session.refresh()
        session.gate.select(DebugEntitlementMode.ForceFree)
        val required = session.refresh()
        session.state = FreeAccessLogic.confirm(
            required.state,
            planIds = session.plans.take(3).map { it.id }.toSet(),
            customExerciseIds = session.exercises.take(5).map { it.id }.toSet(),
            plans = session.plans,
            customExercises = session.exercises
        )
        val confirmed = session.refresh()
        session.restart()
        val restarted = session.refresh()
        assertEquals(confirmed.state.periodId, restarted.state.periodId)
        assertEquals(FreeAccessPhase.Confirmed, restarted.state.phase)
        assertEquals(confirmed.accessiblePlanIds, restarted.accessiblePlanIds)
        assertEquals(confirmed.accessibleCustomExerciseIds, restarted.accessibleCustomExerciseIds)
        assertFalse(restarted.selectionRequired)
        session.gate.select(DebugEntitlementMode.ForcePro)
        val restored = session.refresh()
        assertTrue(session.effective().grantsPro)
        assertEquals(session.plans.map { it.id }.toSet(), restored.accessiblePlanIds)
        assertEquals(session.exercises.map { it.id }.toSet(), restored.accessibleCustomExerciseIds)
        assertEquals(confirmed.state.selectedPlanIds, restored.state.selectedPlanIds)
        assertEquals(confirmed.state.selectedCustomExerciseIds, restored.state.selectedCustomExerciseIds)
        assertTrue(policy(session.effective()).workoutTimers())
        session.gate.select(DebugEntitlementMode.ForceFree)
        val second = session.refresh()
        assertEquals(confirmed.state.periodId + 1L, second.state.periodId)
        assertEquals(FreeAccessPhase.Required, second.state.phase)
        assertTrue(second.state.selectedPlanIds.isEmpty())
        assertTrue(second.selectionRequired)
        val repeated = session.refresh()
        assertEquals(second.state.periodId, repeated.state.periodId)
    }

    @Test
    fun returningToRealRestoresTheActualEntitlementWithoutErasingAConfirmedSelection() {
        val session = Session()
        session.billingReady = true
        session.gate.select(DebugEntitlementMode.ForcePro)
        session.refresh()
        session.gate.select(DebugEntitlementMode.ForceFree)
        val required = session.refresh()
        val chosenPlans = session.plans.take(3).map { it.id }.toSet()
        session.state = FreeAccessLogic.confirm(
            required.state,
            planIds = chosenPlans,
            customExerciseIds = session.exercises.take(5).map { it.id }.toSet(),
            plans = session.plans,
            customExercises = session.exercises
        )
        val confirmed = session.refresh()
        session.gate.select(DebugEntitlementMode.Real)
        val restored = session.refresh()
        assertTrue(session.effective().grantsPro)
        assertTrue(session.effective().subscriptionValid)
        assertEquals(confirmed.state.periodId, restored.state.periodId)
        assertEquals(chosenPlans, restored.state.selectedPlanIds)
        assertEquals(session.plans.map { it.id }.toSet(), restored.accessiblePlanIds)
        session.actual = free()
        val downgraded = session.refresh()
        assertFalse(session.effective().grantsPro)
        assertEquals(confirmed.state.periodId + 1L, downgraded.state.periodId)
        assertEquals(FreeAccessPhase.Required, downgraded.state.phase)
        assertTrue(downgraded.state.selectedPlanIds.isEmpty())
        val settled = session.refresh()
        assertEquals(downgraded.state.periodId, settled.state.periodId)
    }

    @Test
    fun unknownRealEntitlementDoesNotOpenAPeriodOrTrustStalePro() {
        val session = Session()
        session.billingReady = true
        session.gate.select(DebugEntitlementMode.ForcePro)
        val pro = session.refresh()
        session.gate.select(DebugEntitlementMode.Real)
        session.actual = free()
        session.billingReady = false
        val held = session.refresh()
        assertEquals(pro.state.periodId, session.state!!.periodId)
        assertEquals(pro.state.periodId, held.state.periodId)
        assertFalse(held.selectionRequired)
        assertFalse(session.effective().grantsPro)
        session.billingReady = true
        val required = session.refresh()
        assertEquals(pro.state.periodId + 1L, required.state.periodId)
        assertEquals(FreeAccessPhase.Required, required.state.phase)
        val again = session.refresh()
        assertEquals(required.state.periodId, again.state.periodId)
    }

    @Test
    fun anUnknownPreviousReadingDoesNotIncrementThePeriod() {
        val session = Session()
        session.state = FreeAccessState(
            periodId = 2L,
            lastKnownPro = null,
            phase = FreeAccessPhase.NotRequired
        )
        session.actual = free()
        session.billingReady = false
        session.refresh()
        assertEquals(2L, session.state!!.periodId)
        assertNull(session.state!!.lastKnownPro)
        session.billingReady = true
        val settled = session.refresh()
        assertEquals(2L, settled.state.periodId)
        assertEquals(false, settled.state.lastKnownPro)
        assertEquals(FreeAccessPhase.NotRequired, settled.state.phase)
    }

    private fun policy(entitlement: EffectiveEntitlement) = FeatureAccessPolicy(entitlement)

    private fun pro(): EffectiveEntitlement {
        return EffectiveEntitlement(
            tier = EntitlementTier.Pro,
            subscriptionValid = true,
            founderLifetime = false,
            temporaryTesterPro = false
        )
    }

    private fun free(): EffectiveEntitlement {
        return EffectiveEntitlement(
            tier = EntitlementTier.Free,
            subscriptionValid = false,
            founderLifetime = false,
            temporaryTesterPro = false
        )
    }

    private inner class Session(
        val store: DebugEntitlementStore = MemoryDebugEntitlementStore()
    ) {
        var billingReady: Boolean = false
        var actual: EffectiveEntitlement = pro()
        var liveEvents: Int = 0
        var gate: StoredDebugEntitlementGate = StoredDebugEntitlementGate(store) { liveEvents += 1 }
        var state: FreeAccessState? = null
        val plans: List<FreeAccessSubject> = List(4) { FreeAccessSubject(it + 1L, it.toLong()) }
        val exercises: List<FreeAccessSubject> = List(6) { FreeAccessSubject(100L + it, it.toLong()) }

        fun restart() {
            gate = StoredDebugEntitlementGate(store) { liveEvents += 1 }
        }

        fun effective(): EffectiveEntitlement = gate.apply(actual)

        fun ready(): Boolean {
            return billingReady || gate.control.value.mode != DebugEntitlementMode.Real
        }

        fun refresh(): FreeAccessDecision {
            val grantsPro = effective().grantsPro
            val previous = state
            if (FreeAccessLogic.holdForUnknown(previous, grantsPro, ready())) {
                return if (previous == null) {
                    FreeAccessLogic.unknown()
                } else {
                    FreeAccessLogic.decide(previous, previous.lastKnownPro == true, plans, exercises)
                }
            }
            val next = FreeAccessLogic.reconcile(previous, grantsPro, plans, exercises)
            state = next
            return FreeAccessLogic.decide(next, grantsPro, plans, exercises)
        }
    }
}
