package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAccessLogicTest {
    @Test
    fun withinLimitsDoesNotRequireASelection() {
        val plans = subjects(3)
        val exercises = subjects(5, start = 10)
        val state = FreeAccessLogic.reconcile(null, grantsPro = false, plans, exercises)
        val decision = FreeAccessLogic.decide(state, grantsPro = false, plans, exercises)
        assertEquals(FreeAccessPhase.NotRequired, state.phase)
        assertFalse(decision.selectionRequired)
        assertEquals(plans.map { it.id }.toSet(), decision.accessiblePlanIds)
        assertEquals(exercises.map { it.id }.toSet(), decision.accessibleCustomExerciseIds)
    }

    @Test
    fun downgradeAboveLimitsRequiresConfirmationAndDraftDoesNotGrantAccess() {
        val stayingPro = FreeAccessLogic.reconcile(null, grantsPro = true, emptyList(), emptyList())
        val plans = subjects(4)
        val exercises = subjects(6, start = 20)
        val required = FreeAccessLogic.reconcile(stayingPro, grantsPro = false, plans, exercises)
        assertEquals(FreeAccessPhase.Required, required.phase)
        assertEquals(stayingPro.periodId + 1L, required.periodId)
        val beforeConfirm = FreeAccessLogic.decide(required, grantsPro = false, plans, exercises)
        assertTrue(beforeConfirm.selectionRequired)
        assertTrue(beforeConfirm.accessiblePlanIds.isEmpty())
        assertTrue(beforeConfirm.accessibleCustomExerciseIds.isEmpty())
        assertNull(
            FreeAccessLogic.confirm(
                required,
                planIds = setOf(1L, 2L, 3L, 4L),
                customExerciseIds = setOf(20L),
                plans,
                exercises
            )
        )
    }

    @Test
    fun confirmationIsAtomicAndThenImmutable() {
        val plans = subjects(4)
        val exercises = subjects(6, start = 20)
        val required = FreeAccessLogic.reconcile(
            FreeAccessLogic.reconcile(null, true, emptyList(), emptyList()),
            grantsPro = false,
            plans,
            exercises
        )
        val confirmed = FreeAccessLogic.confirm(
            required,
            planIds = setOf(2L, 4L),
            customExerciseIds = setOf(21L, 22L, 25L),
            plans,
            exercises
        )!!
        assertEquals(FreeAccessPhase.Confirmed, confirmed.phase)
        assertEquals(setOf(2L, 4L), confirmed.selectedPlanIds)
        assertEquals(setOf(21L, 22L, 25L), confirmed.selectedCustomExerciseIds)
        assertNull(
            FreeAccessLogic.confirm(
                confirmed,
                planIds = setOf(1L),
                customExerciseIds = setOf(20L),
                plans,
                exercises
            )
        )
        val renamed = plans.map { it.copy(createdAt = 9_000L - it.id) }
        val restarted = FreeAccessLogic.reconcile(confirmed, grantsPro = false, renamed, exercises)
        assertEquals(confirmed.periodId, restarted.periodId)
        assertEquals(confirmed.selectedPlanIds, restarted.selectedPlanIds)
        val decision = FreeAccessLogic.decide(restarted, grantsPro = false, renamed, exercises)
        assertEquals(setOf(2L, 4L), decision.accessiblePlanIds)
        assertFalse(decision.planAccessible(1L))
        assertTrue(decision.planAccessible(2L))
    }

    @Test
    fun deletingASelectedItemDoesNotUnlockALockedItem() {
        val plans = subjects(4)
        val confirmed = confirmedPlans(setOf(1L, 2L, 3L), plans)
        val remaining = plans.filterNot { it.id == 1L }
        val restarted = FreeAccessLogic.reconcile(confirmed, grantsPro = false, remaining, emptyList())
        val decision = FreeAccessLogic.decide(restarted, grantsPro = false, remaining, emptyList())
        assertEquals(setOf(2L, 3L), decision.accessiblePlanIds)
        assertFalse(decision.planAccessible(4L))
    }

    @Test
    fun newContentFillsAVacatedCountSlotWithoutPromotingLockedItems() {
        val plans = subjects(4)
        val confirmed = confirmedPlans(setOf(1L, 2L), plans)
        val afterDelete = plans.filterNot { it.id == 1L } + FreeAccessSubject(8L, 800L)
        val included = FreeAccessLogic.includeCreated(
            state = confirmed,
            planId = 8L,
            plans = afterDelete,
            customExercises = emptyList()
        )
        val decision = FreeAccessLogic.decide(included, grantsPro = false, afterDelete, emptyList())
        assertTrue(decision.planAccessible(8L))
        assertTrue(decision.planAccessible(2L))
        assertFalse(decision.planAccessible(3L))
        assertFalse(decision.planAccessible(4L))
    }

    @Test
    fun proRestorationUnlocksEverythingAndALaterDowngradeOpensANewPeriod() {
        val plans = subjects(4)
        val confirmed = confirmedPlans(setOf(1L), plans)
        val restored = FreeAccessLogic.decide(confirmed, grantsPro = true, plans, emptyList())
        assertEquals(plans.map { it.id }.toSet(), restored.accessiblePlanIds)
        assertFalse(restored.selectionRequired)
        val remembered = FreeAccessLogic.reconcile(confirmed, grantsPro = true, plans, emptyList())
        val again = FreeAccessLogic.reconcile(remembered, grantsPro = false, plans, emptyList())
        assertEquals(confirmed.periodId + 1L, again.periodId)
        assertEquals(FreeAccessPhase.Required, again.phase)
        assertTrue(FreeAccessLogic.decide(again, false, plans, emptyList()).accessiblePlanIds.isEmpty())
    }

    @Test
    fun stayingFreeAndUnknownStateDoNotOpenAnotherSelection() {
        val plans = subjects(4)
        val required = FreeAccessLogic.reconcile(
            FreeAccessLogic.reconcile(null, true, emptyList(), emptyList()),
            false,
            plans,
            emptyList()
        )
        val restarted = FreeAccessLogic.reconcile(required, grantsPro = false, plans, emptyList())
        assertEquals(required, restarted)
        val unknown = required.copy(lastKnownPro = null)
        val settled = FreeAccessLogic.reconcile(unknown, grantsPro = false, plans, emptyList())
        assertEquals(required.periodId, settled.periodId)
        assertEquals(FreeAccessPhase.Required, settled.phase)
        assertEquals(false, settled.lastKnownPro)
    }

    @Test
    fun founderTrialTesterAndPromotionalProUseTheSameGrant() {
        val plans = subjects(4)
        val free = FreeAccessLogic.reconcile(null, grantsPro = false, plans, emptyList())
        assertEquals(FreeAccessPhase.Required, free.phase)
        listOf(true).forEach { grantsPro ->
            val decision = FreeAccessLogic.decide(free, grantsPro = grantsPro, plans, emptyList())
            assertEquals(plans.map { it.id }.toSet(), decision.accessiblePlanIds)
        }
    }

    @Test
    fun legacyRestoreWithoutASelectionDoesNotOpenAFreshWizard() {
        val plans = subjects(4)
        val exercises = subjects(6, start = 30)
        val restored = FreeAccessLogic.reconcile(
            previous = null,
            grantsPro = false,
            plans = plans,
            customExercises = exercises,
            legacyRestoreWithoutSelection = true
        )
        assertEquals(FreeAccessPhase.Confirmed, restored.phase)
        assertEquals(setOf(1L, 2L, 3L), restored.selectedPlanIds)
        assertEquals(setOf(30L, 31L, 32L, 33L, 34L), restored.selectedCustomExerciseIds)
        val again = FreeAccessLogic.reconcile(restored, false, plans, exercises)
        assertEquals(restored.phase, again.phase)
        assertEquals(restored.selectedPlanIds, again.selectedPlanIds)
    }

    @Test
    fun withinLimitCategoryIsKeptWhenOnlyTheOtherCategoryExceeds() {
        val plans = subjects(2)
        val exercises = subjects(6, start = 40)
        val required = FreeAccessLogic.reconcile(null, false, plans, exercises)
        val confirmed = FreeAccessLogic.confirm(
            required,
            planIds = emptySet(),
            customExerciseIds = setOf(40L, 41L),
            plans,
            exercises
        )!!
        assertEquals(setOf(1L, 2L), confirmed.selectedPlanIds)
        assertEquals(setOf(40L, 41L), confirmed.selectedCustomExerciseIds)
    }

    @Test
    fun unknownEntitlementDoesNotOpenANewFreePeriod() {
        val pro = FreeAccessState(4L, true, FreeAccessPhase.NotRequired)
        assertTrue(FreeAccessLogic.holdForUnknown(pro, grantsPro = false, entitlementReady = false))
        assertFalse(FreeAccessLogic.holdForUnknown(pro, grantsPro = false, entitlementReady = true))
        assertFalse(FreeAccessLogic.holdForUnknown(pro, grantsPro = true, entitlementReady = false))
        val free = pro.copy(lastKnownPro = false, phase = FreeAccessPhase.Confirmed)
        assertFalse(FreeAccessLogic.holdForUnknown(free, grantsPro = false, entitlementReady = false))
        assertTrue(FreeAccessLogic.holdForUnknown(null, grantsPro = false, entitlementReady = false))
    }

    @Test
    fun downgradeLocksEveryPlanUntilConfirmation() {
        val plans = subjects(4)
        val required = FreeAccessLogic.reconcile(null, false, plans, emptyList())
        val decision = FreeAccessLogic.decide(required, false, plans, emptyList())
        assertEquals(plans.map { it.id }.toSet(), decision.lockedPlanIds)
        assertTrue(decision.accessiblePlanIds.isEmpty())
    }

    private fun confirmedPlans(selected: Set<Long>, plans: List<FreeAccessSubject>): FreeAccessState {
        val required = FreeAccessLogic.reconcile(null, false, plans, emptyList())
        return FreeAccessLogic.confirm(required, selected, emptySet(), plans, emptyList())!!
    }

    private fun subjects(count: Int, start: Long = 1L): List<FreeAccessSubject> {
        return (0 until count).map { index ->
            FreeAccessSubject(id = start + index, createdAt = 1_000L + index)
        }
    }
}
