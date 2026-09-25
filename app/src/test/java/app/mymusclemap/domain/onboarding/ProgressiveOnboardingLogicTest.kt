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
    fun heatmapCoachIsShownOnlyUntilSeen() {
        val facts = OnboardingFacts(hasCompletedWorkout = true)
        val pending = ProgressiveOnboardingLogic.guide(started, facts)
        assertTrue(pending.showHeatmapCoach)
        val seen = ProgressiveOnboardingLogic.guide(started.copy(heatmapSeen = true), facts)
        assertFalse(seen.showHeatmapCoach)
        assertFalse(seen.showHeatmapCompletionCta)
        assertTrue(seen.showWeightPrompt)
        assertEquals(OnboardingResumeTarget.WeightPrompt, seen.resumeTarget)
    }

    @Test
    fun weightPromptCanBeSkippedAndOnboardingCanStillComplete() {
        val afterSkip = started.copy(heatmapSeen = true, weightIntroduced = true)
        val facts = OnboardingFacts(hasCompletedWorkout = true)
        val guide = ProgressiveOnboardingLogic.guide(afterSkip, facts)
        assertFalse(guide.showWeightPrompt)
        assertFalse(guide.showWeightChartCoach)
        assertTrue(guide.showCalendarCoach)
        assertEquals(OnboardingResumeTarget.Calendar, guide.resumeTarget)
        assertTrue(guide.checklist.weightDone)

        val completeFlags = afterSkip.copy(calendarSeen = true)
        assertTrue(ProgressiveOnboardingLogic.shouldComplete(completeFlags, facts))
        val completed = ProgressiveOnboardingLogic.guide(
            completeFlags.copy(completed = true),
            facts
        )
        assertFalse(completed.reminderVisible)
        assertEquals(OnboardingResumeTarget.None, completed.resumeTarget)
    }

    @Test
    fun savingWeightUsesChartDiscoveryThenCalendar() {
        val afterSave = started.copy(heatmapSeen = true, weightIntroduced = true)
        val facts = OnboardingFacts(hasCompletedWorkout = true, hasWeight = true)
        val chart = ProgressiveOnboardingLogic.guide(afterSave, facts)
        assertTrue(chart.showWeightChartCoach)
        assertFalse(chart.showCalendarCoach)
        assertEquals(OnboardingResumeTarget.WeightChart, chart.resumeTarget)

        val afterChart = ProgressiveOnboardingLogic.guide(
            afterSave.copy(weightChartSeen = true),
            facts
        )
        assertFalse(afterChart.showWeightChartCoach)
        assertTrue(afterChart.showCalendarCoach)
        assertEquals(OnboardingResumeTarget.Calendar, afterChart.resumeTarget)
    }

    @Test
    fun calendarAndChartGuidanceAreShownOnlyOnce() {
        val flags = started.copy(
            heatmapSeen = true,
            weightIntroduced = true,
            weightChartSeen = true,
            calendarSeen = true
        )
        val facts = OnboardingFacts(hasCompletedWorkout = true, hasWeight = true)
        val guide = ProgressiveOnboardingLogic.guide(flags, facts)
        assertFalse(guide.showWeightChartCoach)
        assertFalse(guide.showCalendarCoach)
        assertTrue(ProgressiveOnboardingLogic.shouldComplete(flags, facts))
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
