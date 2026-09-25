package app.mymusclemap.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveOnboardingLogicTest {
    private val started = OnboardingFlags(started = true)
    private val existingUser = OnboardingFlags(completed = true)

    @Test
    fun freshStartedUserWithoutDataResumesPlanCreationAndShowsReminder() {
        val guide = ProgressiveOnboardingLogic.guide(started, OnboardingFacts())
        assertTrue(guide.reminderVisible)
        assertEquals(OnboardingResumeTarget.CreatePlan, guide.resumeTarget)
        assertFalse(guide.showHeatmapCoach)
        assertFalse(guide.showHeatmapCompletionCta)
        assertFalse(guide.checklist.planCreated)
        assertFalse(guide.checklist.firstWorkoutDone)
        assertFalse(guide.showWorkoutActionCoach)
        assertFalse(guide.heatmapRevealRequested)
        assertFalse(guide.showHeatmapSpotlight)
        assertFalse(guide.showWeightPrompt)
    }

    @Test
    fun existingCompletedUserNeverReceivesProgressiveGuidance() {
        val guide = ProgressiveOnboardingLogic.guide(
            existingUser,
            OnboardingFacts(hasCompletedWorkout = true, hasWeight = true, hasPlan = true)
        )
        assertFalse(guide.reminderVisible)
        assertEquals(OnboardingResumeTarget.None, guide.resumeTarget)
        assertFalse(guide.showHeatmapCoach)
        assertFalse(guide.showWeightPrompt)
        assertFalse(guide.showWeightChartCoach)
        assertFalse(guide.showCalendarCoach)
        assertFalse(guide.showHeatmapCompletionCta)
    }

    @Test
    fun skipKeepsReminderUntilDismissed() {
        val skipped = ProgressiveOnboardingLogic.guide(started, OnboardingFacts())
        assertTrue(skipped.reminderVisible)
        val dismissed = ProgressiveOnboardingLogic.guide(
            started.copy(reminderDismissed = true),
            OnboardingFacts()
        )
        assertFalse(dismissed.reminderVisible)
        assertEquals(OnboardingResumeTarget.CreatePlan, dismissed.resumeTarget)
    }

    @Test
    fun continueResumesHeatmapAfterFirstCompletedWorkout() {
        val facts = OnboardingFacts(hasCompletedWorkout = true)
        val guide = ProgressiveOnboardingLogic.guide(started, facts)
        assertEquals(OnboardingResumeTarget.Heatmap, guide.resumeTarget)
        assertTrue(guide.showHeatmapCoach)
        assertTrue(guide.showHeatmapCompletionCta)
        assertTrue(guide.checklist.firstWorkoutDone)
        assertFalse(guide.checklist.heatmapDone)
        assertFalse(guide.showWorkoutActionCoach)
        assertFalse(guide.heatmapRevealRequested)
        assertFalse(guide.showHeatmapSpotlight)
    }

    @Test
    fun heatmapAcknowledgementAdvancesToCalendarWeightDiscoveryWithoutOpeningWeightEntry() {
        val facts = OnboardingFacts(hasCompletedWorkout = true, hasPlan = true)
        val pending = ProgressiveOnboardingLogic.guide(started, facts)
        assertTrue(pending.showHeatmapCoach)
        val seen = ProgressiveOnboardingLogic.guide(started.copy(heatmapSeen = true), facts)
        assertFalse(seen.showHeatmapCoach)
        assertFalse(seen.showHeatmapCompletionCta)
        assertFalse(seen.showWeightPrompt)
        assertEquals(OnboardingResumeTarget.WeightPrompt, seen.resumeTarget)
        assertTrue(seen.checklist.heatmapDone)
        assertFalse(seen.checklist.weightDone)
        assertFalse(seen.showWeightChartCoach)
        assertFalse(seen.showCalendarCoach)
    }

    @Test
    fun calendarWeightCoachAcknowledgementDoesNotCompleteWeightChecklist() {
        val facts = OnboardingFacts(hasCompletedWorkout = true, hasPlan = true)
        val afterCoach = ProgressiveOnboardingLogic.guide(
            started.copy(heatmapSeen = true, weightIntroduced = true),
            facts
        )
        assertEquals(OnboardingResumeTarget.WeightPrompt, afterCoach.resumeTarget)
        assertFalse(afterCoach.checklist.weightDone)
        assertFalse(afterCoach.showWeightPrompt)
        assertFalse(afterCoach.showWeightChartCoach)
        assertFalse(afterCoach.showCalendarCoach)
        assertFalse(ProgressiveOnboardingLogic.shouldComplete(afterCoach.flags, facts))
    }

    @Test
    fun realWeightSaveCompletesWeightChecklistAndUnlocksChartDiscovery() {
        val flags = started.copy(heatmapSeen = true, weightIntroduced = true)
        val facts = OnboardingFacts(hasCompletedWorkout = true, hasPlan = true, hasWeight = true)
        val chart = ProgressiveOnboardingLogic.guide(flags, facts)
        assertTrue(chart.checklist.weightDone)
        assertTrue(chart.showWeightChartCoach)
        assertFalse(chart.showCalendarCoach)
        assertEquals(OnboardingResumeTarget.WeightChart, chart.resumeTarget)

        val afterChart = ProgressiveOnboardingLogic.guide(flags.copy(weightChartSeen = true), facts)
        assertFalse(afterChart.showWeightChartCoach)
        assertTrue(afterChart.showCalendarCoach)
        assertEquals(OnboardingResumeTarget.Calendar, afterChart.resumeTarget)
        assertFalse(afterChart.checklist.historyDone)
    }

    @Test
    fun existingWeightSkipsCalendarWeightDiscoveryAndGoesToChart() {
        val guide = ProgressiveOnboardingLogic.guide(
            started.copy(heatmapSeen = true),
            OnboardingFacts(hasCompletedWorkout = true, hasWeight = true, hasPlan = true)
        )
        assertEquals(OnboardingResumeTarget.WeightChart, guide.resumeTarget)
        assertTrue(guide.checklist.weightDone)
        assertTrue(guide.showWeightChartCoach)
        assertFalse(guide.showWeightPrompt)
    }

    @Test
    fun finalCalendarGotItCompletesOnboarding() {
        val flags = started.copy(
            heatmapSeen = true,
            weightIntroduced = true,
            weightChartSeen = true,
            calendarSeen = true
        )
        val facts = OnboardingFacts(hasCompletedWorkout = true, hasWeight = true, hasPlan = true)
        assertTrue(ProgressiveOnboardingLogic.shouldComplete(flags, facts))
        val completed = ProgressiveOnboardingLogic.guide(flags.copy(completed = true), facts)
        assertFalse(completed.reminderVisible)
        assertEquals(OnboardingResumeTarget.None, completed.resumeTarget)
        assertFalse(completed.showWeightChartCoach)
        assertFalse(completed.showCalendarCoach)
    }

    @Test
    fun onboardingCannotCompleteWithoutARealWeightMeasurement() {
        val flags = started.copy(
            heatmapSeen = true,
            weightIntroduced = true,
            weightChartSeen = true,
            calendarSeen = true
        )
        val facts = OnboardingFacts(hasCompletedWorkout = true, hasPlan = true)
        assertFalse(ProgressiveOnboardingLogic.shouldComplete(flags, facts))
        assertFalse(ProgressiveOnboardingLogic.guide(flags, facts).checklist.weightDone)
    }

    @Test
    fun planWithoutCompletedWorkoutResumesStartWorkoutAndMarksPlanCreated() {
        val guide = ProgressiveOnboardingLogic.guide(
            started,
            OnboardingFacts(hasPlan = true)
        )
        assertEquals(OnboardingResumeTarget.StartWorkout, guide.resumeTarget)
        assertTrue(guide.reminderVisible)
        assertTrue(guide.checklist.planCreated)
        assertFalse(guide.checklist.firstWorkoutDone)
        assertFalse(guide.showHeatmapCoach)
        assertFalse(guide.showWorkoutActionCoach)
    }

    @Test
    fun heatmapIsNotShownBeforeACompletedWorkout() {
        val guide = ProgressiveOnboardingLogic.guide(
            started.copy(heatmapSeen = false),
            OnboardingFacts(hasPlan = true)
        )
        assertFalse(guide.showHeatmapCoach)
        assertFalse(guide.showHeatmapCompletionCta)
        assertFalse(guide.heatmapRevealRequested)
        assertFalse(guide.showHeatmapSpotlight)
    }
}
