package app.mymusclemap.domain.onboarding

data class OnboardingFlags(
    val completed: Boolean = false,
    val started: Boolean = false,
    val heatmapSeen: Boolean = false,
    val weightIntroduced: Boolean = false,
    val weightChartSeen: Boolean = false,
    val calendarSeen: Boolean = false,
    val reminderDismissed: Boolean = false
) {
    val isActive: Boolean get() = started && !completed
}

data class OnboardingFacts(
    val hasCompletedWorkout: Boolean = false,
    val hasWeight: Boolean = false,
    val hasPlan: Boolean = false
)

enum class OnboardingResumeTarget {
    None,
    CreatePlan,
    StartWorkout,
    Heatmap,
    WeightPrompt,
    WeightChart,
    Calendar
}

data class OnboardingChecklist(
    val planCreated: Boolean = false,
    val firstWorkoutDone: Boolean = false,
    val heatmapDone: Boolean = false,
    val weightDone: Boolean = false,
    val historyDone: Boolean = false
)

data class OnboardingGuide(
    val flags: OnboardingFlags = OnboardingFlags(),
    val facts: OnboardingFacts = OnboardingFacts(),
    val reminderVisible: Boolean = false,
    val checklist: OnboardingChecklist = OnboardingChecklist(),
    val resumeTarget: OnboardingResumeTarget = OnboardingResumeTarget.None,
    val showHeatmapCoach: Boolean = false,
    val showWeightPrompt: Boolean = false,
    val showWeightChartCoach: Boolean = false,
    val showCalendarCoach: Boolean = false,
    val showHeatmapCompletionCta: Boolean = false,
    val showWorkoutActionCoach: Boolean = false
) {
    companion object {
        val Inactive = OnboardingGuide()
    }
}

object ProgressiveOnboardingLogic {
    fun guide(flags: OnboardingFlags, facts: OnboardingFacts): OnboardingGuide {
        if (!flags.isActive) {
            return OnboardingGuide(flags = flags, facts = facts)
        }
        val weightReady = flags.weightIntroduced || facts.hasWeight
        val heatmapCoach = facts.hasCompletedWorkout && !flags.heatmapSeen
        val weightPrompt = flags.heatmapSeen && !weightReady && !heatmapCoach
        val weightChartCoach = flags.heatmapSeen && weightReady && facts.hasWeight && !flags.weightChartSeen
        val calendarReady = flags.heatmapSeen && weightReady && (!facts.hasWeight || flags.weightChartSeen)
        val calendarCoach = calendarReady && !flags.calendarSeen && !weightPrompt && !heatmapCoach
        val resume = resumeTarget(flags, facts, weightReady)
        return OnboardingGuide(
            flags = flags,
            facts = facts,
            reminderVisible = !flags.reminderDismissed && resume != OnboardingResumeTarget.None,
            checklist = OnboardingChecklist(
                planCreated = facts.hasPlan,
                firstWorkoutDone = facts.hasCompletedWorkout,
                heatmapDone = flags.heatmapSeen,
                weightDone = weightReady,
                historyDone = flags.calendarSeen
            ),
            resumeTarget = resume,
            showHeatmapCoach = heatmapCoach,
            showWeightPrompt = weightPrompt,
            showWeightChartCoach = weightChartCoach,
            showCalendarCoach = calendarCoach,
            showHeatmapCompletionCta = heatmapCoach
        )
    }

    fun resumeTarget(flags: OnboardingFlags, facts: OnboardingFacts): OnboardingResumeTarget {
        val weightReady = flags.weightIntroduced || facts.hasWeight
        return resumeTarget(flags, facts, weightReady)
    }

    fun shouldComplete(flags: OnboardingFlags, facts: OnboardingFacts): Boolean {
        if (!flags.started) return false
        if (flags.completed) return true
        val weightReady = flags.weightIntroduced || facts.hasWeight
        return flags.heatmapSeen &&
            weightReady &&
            (!facts.hasWeight || flags.weightChartSeen) &&
            flags.calendarSeen
    }

    private fun resumeTarget(
        flags: OnboardingFlags,
        facts: OnboardingFacts,
        weightReady: Boolean
    ): OnboardingResumeTarget {
        if (!flags.isActive) return OnboardingResumeTarget.None
        if (!flags.heatmapSeen) {
            if (!facts.hasCompletedWorkout) {
                return if (facts.hasPlan) {
                    OnboardingResumeTarget.StartWorkout
                } else {
                    OnboardingResumeTarget.CreatePlan
                }
            }
            return OnboardingResumeTarget.Heatmap
        }
        if (!weightReady) return OnboardingResumeTarget.WeightPrompt
        if (facts.hasWeight && !flags.weightChartSeen) return OnboardingResumeTarget.WeightChart
        if (!flags.calendarSeen) return OnboardingResumeTarget.Calendar
        return OnboardingResumeTarget.None
    }
}
