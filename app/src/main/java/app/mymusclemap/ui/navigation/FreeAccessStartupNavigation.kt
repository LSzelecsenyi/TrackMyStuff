package app.mymusclemap.ui.navigation

import app.mymusclemap.domain.onboarding.OnboardingGuide

/**
 * One automatic opening of the existing FREE access screen per period.
 * A blocked launch stays deferred until the blocker clears, and leaving the
 * screen does not open it again for that same period.
 */
sealed interface FreeAccessEntry {
    data object None : FreeAccessEntry
    data object Defer : FreeAccessEntry
    data class Remember(val periodId: Long) : FreeAccessEntry
    data class Open(val periodId: Long) : FreeAccessEntry
}

object FreeAccessStartupNavigation {
    const val NONE: Long = -1L

    fun choose(
        loaded: Boolean,
        selectionRequired: Boolean,
        periodId: Long,
        offeredPeriodId: Long?,
        route: String?,
        blocked: Boolean
    ): FreeAccessEntry {
        if (!loaded || !selectionRequired) return FreeAccessEntry.None
        val canonical = AppNavigation.canonicalRoute(route)
        if (canonical == AppRoutes.FREE_ACCESS) {
            return if (offeredPeriodId == periodId) {
                FreeAccessEntry.None
            } else {
                FreeAccessEntry.Remember(periodId)
            }
        }
        if (offeredPeriodId == periodId) return FreeAccessEntry.None
        if (route == null || blocked || interrupts(canonical)) return FreeAccessEntry.Defer
        return FreeAccessEntry.Open(periodId)
    }

    fun onboardingBlocks(guide: OnboardingGuide): Boolean {
        return guide.showWorkoutActionCoach ||
            guide.showHeatmapSpotlight ||
            guide.showCalendarWeightSpotlight ||
            guide.showChartSpotlight ||
            guide.showCalendarHistorySpotlight ||
            guide.showWeightPrompt ||
            guide.showHeatmapCoach ||
            guide.showWeightChartCoach ||
            guide.showCalendarCoach
    }

    private fun interrupts(route: String?): Boolean {
        return route == null || route in INTERRUPTING
    }

    private val INTERRUPTING = setOf(
        AppRoutes.ACTIVE_WORKOUT,
        AppRoutes.WORKOUT_COMPLETE,
        AppRoutes.TEMPLATE_EDITOR,
        AppRoutes.EXERCISE_EDITOR,
        AppRoutes.DELETE_ACCOUNT
    )
}
