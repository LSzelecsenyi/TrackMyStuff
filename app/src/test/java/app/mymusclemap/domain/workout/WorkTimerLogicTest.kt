package app.mymusclemap.domain.workout

import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.EffectiveEntitlement
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FeatureAccessPolicy
import app.mymusclemap.domain.entitlement.PolicyBackedEntitlements
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.ResistanceBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkTimerLogicTest {
    @Test
    fun startPauseResumeAndFinishEarlyKeepPausedTimeOut() {
        val clock = RestClockSample(1_000L, 5_000L)
        val started = WorkTimerLogic.started(setId = 4L, targetMillis = 60_000L, clock = clock, generation = 3L)
        val later = clock.copy(elapsedRealtimeMillis = 11_000L, wallClockMillis = 15_000L)
        val paused = WorkTimerLogic.pause(started, later)!!
        assertEquals(WorkPhase.PAUSED, paused.phase)
        assertEquals(10_000L, paused.elapsedActiveMillis)
        val whilePaused = later.copy(elapsedRealtimeMillis = 40_000L, wallClockMillis = 44_000L)
        assertEquals(10, WorkTimerLogic.actualSeconds(paused, whilePaused))
        val resumed = WorkTimerLogic.resume(paused, whilePaused)!!
        assertEquals(whilePaused.elapsedRealtimeMillis + 50_000L, resumed.deadlineElapsedRealtimeMillis)
        val finished = WorkTimerLogic.finishEarly(
            resumed,
            whilePaused.copy(elapsedRealtimeMillis = 43_900L, wallClockMillis = 47_900L)
        )!!
        assertEquals(WorkPhase.AWAITING, finished.phase)
        assertEquals(13, WorkTimerLogic.actualSeconds(finished, whilePaused))
        assertEquals(60_000L, finished.targetMillis)
    }

    @Test
    fun aFreshCountdownNeverDisplaysMoreThanItsInterval() {
        listOf(15_000L to "00:15", 30_000L to "00:30", 60_000L to "01:00").forEach { (target, label) ->
            val clock = RestClockSample(5_000L, 20_000L)
            val started = WorkTimerLogic.started(setId = 1L, targetMillis = target, clock = clock, generation = 1L)
            val exact = WorkTimerLogic.remaining(started, clock) as RestRemaining.Active
            assertEquals(target, exact.remainingMillis)
            assertEquals(label, RestTimerLogic.formatCountdown(exact.remainingMillis))
            val stale = RestClockSample(4_200L, 19_200L)
            val earlySample = WorkTimerLogic.remaining(started, stale) as RestRemaining.Active
            assertEquals(target, earlySample.remainingMillis)
            assertEquals(label, RestTimerLogic.formatCountdown(earlySample.remainingMillis))
        }
    }

    @Test
    fun resumeDoesNotDisplayAnExtraSecond() {
        val started = WorkTimerLogic.started(
            setId = 1L,
            targetMillis = 30_000L,
            clock = RestClockSample(0L, 0L),
            generation = 1L
        )
        val paused = WorkTimerLogic.pause(started, RestClockSample(12_000L, 12_000L))!!
        val resumeClock = RestClockSample(40_000L, 40_000L)
        val resumed = WorkTimerLogic.resume(paused, resumeClock)!!
        val stale = RestClockSample(39_400L, 39_400L)
        val remaining = WorkTimerLogic.remaining(resumed, stale) as RestRemaining.Active
        assertEquals(18_000L, remaining.remainingMillis)
        assertEquals("00:18", RestTimerLogic.formatCountdown(remaining.remainingMillis))
        val caughtUp = WorkTimerLogic.remaining(resumed, resumeClock) as RestRemaining.Active
        assertEquals("00:18", RestTimerLogic.formatCountdown(caughtUp.remainingMillis))
    }

    @Test
    fun pausingAtEighteenSecondsKeepsThatRemainingTimeVisible() {
        val started = WorkTimerLogic.started(
            setId = 8L,
            targetMillis = 30_000L,
            clock = RestClockSample(0L, 0L),
            generation = 1L
        )
        val atTwelveSeconds = RestClockSample(12_000L, 12_000L)
        val paused = WorkTimerLogic.pause(started, atTwelveSeconds)!!
        val tenSecondsLater = RestClockSample(22_000L, 22_000L)
        val remaining = WorkTimerLogic.remaining(paused, tenSecondsLater) as RestRemaining.Active
        assertEquals(18_000L, remaining.remainingMillis)
        assertEquals(30_000L, remaining.durationMillis)
        assertEquals("00:18", RestTimerLogic.formatCountdown(remaining.remainingMillis))
        val progress = RestTimerLogic.progress(remaining)
        val stillPaused = WorkTimerLogic.remaining(
            paused,
            tenSecondsLater.copy(elapsedRealtimeMillis = 50_000L, wallClockMillis = 50_000L)
        ) as RestRemaining.Active
        assertEquals(18_000L, stillPaused.remainingMillis)
        assertEquals(progress, RestTimerLogic.progress(stillPaused), 0f)
        assertEquals(12, WorkTimerLogic.actualSeconds(paused, tenSecondsLater))
        assertNull(WorkTimerLogic.expire(paused))
        assertNull(WorkTimerLogic.pause(paused, tenSecondsLater))
        val resumed = WorkTimerLogic.resume(paused, tenSecondsLater)!!
        val resumedRemaining = WorkTimerLogic.remaining(resumed, tenSecondsLater) as RestRemaining.Active
        assertEquals(18_000L, resumedRemaining.remainingMillis)
        assertEquals(WorkPhase.RUNNING, resumed.phase)
    }

    @Test
    fun naturalExpiryRecordsTheFullTargetAndASecondExpiryDoesNothing() {
        val started = WorkTimerLogic.started(
            setId = 1L,
            targetMillis = 30_000L,
            clock = RestClockSample(0L, 0L),
            generation = 1L
        )
        val expired = WorkTimerLogic.expire(started)!!
        assertEquals(30, WorkTimerLogic.actualSeconds(expired, RestClockSample(30_000L, 30_000L)))
        assertNull(WorkTimerLogic.expire(expired))
        assertEquals(
            RestRemaining.Expired,
            WorkTimerLogic.remaining(started, RestClockSample(30_000L, 30_000L))
        )
    }

    @Test
    fun rebootBeforeTheDeadlineReanchorsAndAfterItExpires() {
        val started = WorkTimerLogic.started(
            setId = 2L,
            targetMillis = 20_000L,
            clock = RestClockSample(50_000L, 100_000L),
            generation = 1L
        )
        val rebooted = RestClockSample(elapsedRealtimeMillis = 1_000L, wallClockMillis = 110_000L)
        val moved = WorkTimerLogic.reanchorAfterReboot(started, rebooted)!!
        assertEquals(11_000L, moved.deadlineElapsedRealtimeMillis)
        val tooLate = RestClockSample(elapsedRealtimeMillis = 500L, wallClockMillis = 130_000L)
        assertNull(WorkTimerLogic.reanchorAfterReboot(started, tooLate))
        assertTrue(WorkTimerLogic.remaining(started, tooLate) is RestRemaining.Expired)
    }

    @Test
    fun recordedDurationCannotBeNegativeOrLongerThanTheTarget() {
        val started = WorkTimerLogic.started(
            setId = 3L,
            targetMillis = 15_000L,
            clock = RestClockSample(1_000L, 1_000L),
            generation = 1L
        )
        val early = WorkTimerLogic.finishEarly(started, RestClockSample(500L, 500L))!!
        assertEquals(0, WorkTimerLogic.actualSeconds(early, RestClockSample(500L, 500L)))
        val over = WorkTimerLogic.finishEarly(started, RestClockSample(80_000L, 80_000L))!!
        assertEquals(15, WorkTimerLogic.actualSeconds(over, RestClockSample(80_000L, 80_000L)))
    }

    @Test
    fun optionalRepsStayNullUntilEnteredAndZeroStaysZero() {
        val blank = ActualSetLogic.parse(
            draft = ActualSetDraft(
                repsText = "",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                minutesText = "0",
                secondsText = "30"
            ),
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            timedWork = true
        ).first
        assertEquals(30, blank?.durationSeconds)
        assertNull(blank?.reps)
        val zero = ActualSetLogic.parse(
            draft = ActualSetDraft(
                repsText = "0",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                minutesText = "0",
                secondsText = "43"
            ),
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            timedWork = true
        ).first
        assertEquals(0, zero?.reps)
        assertEquals(43, zero?.durationSeconds)
    }
}

class WorkoutTimerAccessTest {
    @Test
    fun proCanConfigureRestAndTimedWorkAndFreeCanOnlyTurnRestOff() {
        val pro = PolicyBackedEntitlements(policy(EntitlementTier.Pro))
        val free = PolicyBackedEntitlements(policy(EntitlementTier.Free))
        assertTrue(WorkoutTimerAccess.allowed(pro))
        assertFalse(WorkoutTimerAccess.allowed(free))
        assertTrue(WorkoutTimerAccess.allowsRestChange(60, pro = true))
        assertFalse(WorkoutTimerAccess.allowsRestChange(60, pro = false))
        assertTrue(WorkoutTimerAccess.allowsRestChange(0, pro = false))
        assertTrue(WorkoutTimerAccess.allowsRestChange(null, pro = false))
        assertFalse(free.hasAccess(AppFeature.WorkoutTimers))
        assertTrue(pro.hasAccess(AppFeature.WorkoutTimers))
    }

    @Test
    fun founderProTrialAndWelcomeBackGrantTimersButRecognitionDoesNot() {
        assertFalse(policyOf(tier = EntitlementTier.Free, founderRecognized = true).workoutTimers())
        assertTrue(policyOf(tier = EntitlementTier.Pro, founderLifetime = true).workoutTimers())
        assertTrue(policyOf(tier = EntitlementTier.Pro, founderProActive = true).workoutTimers())
        assertTrue(policyOf(tier = EntitlementTier.Pro, promotionalProActive = true).workoutTimers())
        assertTrue(policyOf(tier = EntitlementTier.Pro, welcomeBackActive = true).workoutTimers())
        assertTrue(policyOf(tier = EntitlementTier.Pro, temporaryTesterPro = true).workoutTimers())
    }

    @Test
    fun importedConfigurationDoesNotGrantAccessWithoutTheEntitlement() {
        val free = SelectiveFeatureEntitlements(emptySet())
        assertFalse(WorkoutTimerAccess.allowed(free))
        assertFalse(WorkoutTimerAccess.allowsRestChange(requestedSeconds = 90, pro = false))
    }

    private fun policy(tier: EntitlementTier): FeatureAccessPolicy {
        return policyOf(tier = tier, subscriptionValid = tier == EntitlementTier.Pro)
    }

    private fun policyOf(
        tier: EntitlementTier,
        subscriptionValid: Boolean = false,
        founderLifetime: Boolean = false,
        temporaryTesterPro: Boolean = false,
        founderRecognized: Boolean = false,
        founderProActive: Boolean = false,
        promotionalProActive: Boolean = false,
        welcomeBackActive: Boolean = false
    ): FeatureAccessPolicy {
        return FeatureAccessPolicy(
            EffectiveEntitlement(
                tier = tier,
                subscriptionValid = subscriptionValid,
                founderLifetime = founderLifetime,
                temporaryTesterPro = temporaryTesterPro,
                founderRecognized = founderRecognized,
                founderProActive = founderProActive,
                promotionalProActive = promotionalProActive,
                welcomeBackActive = welcomeBackActive
            )
        )
    }
}
