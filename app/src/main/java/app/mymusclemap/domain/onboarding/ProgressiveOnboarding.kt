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
    val showWorkoutActionCoach: Boolean = false,
    val heatmapRevealRequested: Boolean = false,
    val showHeatmapSpotlight: Boolean = false,
    val calendarRevealRequested: Boolean = false,
    val showCalendarWeightSpotlight: Boolean = false,
    val chartRevealRequested: Boolean = false,
    val showChartSpotlight: Boolean = false,
    val showCalendarHistorySpotlight: Boolean = false
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
        val heatmapCoach = facts.hasCompletedWorkout && !flags.heatmapSeen
        val weightChartCoach = flags.heatmapSeen && facts.hasWeight && !flags.weightChartSeen
        val calendarHistoryCoach = flags.heatmapSeen &&
            facts.hasWeight &&
            flags.weightChartSeen &&
            !flags.calendarSeen
        val resume = resumeTarget(flags, facts)
        return OnboardingGuide(
            flags = flags,
            facts = facts,
            reminderVisible = !flags.reminderDismissed && resume != OnboardingResumeTarget.None,
            checklist = OnboardingChecklist(
                planCreated = facts.hasPlan,
                firstWorkoutDone = facts.hasCompletedWorkout,
                heatmapDone = flags.heatmapSeen,
                weightDone = facts.hasWeight,
                historyDone = flags.calendarSeen
            ),
            resumeTarget = resume,
            showHeatmapCoach = heatmapCoach,
            showWeightPrompt = false,
            showWeightChartCoach = weightChartCoach,
            showCalendarCoach = calendarHistoryCoach,
            showHeatmapCompletionCta = heatmapCoach
        )
    }

    fun resumeTarget(flags: OnboardingFlags, facts: OnboardingFacts): OnboardingResumeTarget {
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
        if (!facts.hasWeight) return OnboardingResumeTarget.WeightPrompt
        if (!flags.weightChartSeen) return OnboardingResumeTarget.WeightChart
        if (!flags.calendarSeen) return OnboardingResumeTarget.Calendar
        return OnboardingResumeTarget.None
    }

    fun shouldComplete(flags: OnboardingFlags, facts: OnboardingFacts): Boolean {
        if (!flags.started) return false
        if (flags.completed) return true
        return flags.heatmapSeen &&
            facts.hasWeight &&
            flags.weightChartSeen &&
            flags.calendarSeen
    }
}
