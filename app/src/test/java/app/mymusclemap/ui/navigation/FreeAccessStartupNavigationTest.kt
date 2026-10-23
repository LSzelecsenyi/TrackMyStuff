package app.mymusclemap.ui.navigation

import app.mymusclemap.domain.onboarding.OnboardingGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAccessStartupNavigationTest {
    @Test
    fun requiredSelectionOpensOnceFromAReadyRoute() {
        assertEquals(
            FreeAccessEntry.Open(4L),
            choose(route = AppRoutes.OVERVIEW, offered = null)
        )
        assertEquals(
            FreeAccessEntry.None,
            choose(route = AppRoutes.OVERVIEW, offered = 4L)
        )
    }

    @Test
    fun navigationWaitsUntilTheHostHasARoute() {
        assertEquals(FreeAccessEntry.Defer, choose(route = null, offered = null))
    }

    @Test
    fun activeWorkoutEditorAndOnboardingDeferWithoutConsumingTheOffer() {
        listOf(
            AppRoutes.ACTIVE_WORKOUT,
            "active_workout?sessionId=9",
            AppRoutes.TEMPLATE_EDITOR,
            "template_editor?templateId=3",
            AppRoutes.EXERCISE_EDITOR,
            AppRoutes.WORKOUT_COMPLETE,
            AppRoutes.DELETE_ACCOUNT
        ).forEach { route ->
            assertEquals(route, FreeAccessEntry.Defer, choose(route = route, offered = null))
        }
        assertEquals(FreeAccessEntry.Defer, choose(route = AppRoutes.OVERVIEW, offered = null, blocked = true))
        assertTrue(
            FreeAccessStartupNavigation.onboardingBlocks(
                OnboardingGuide(showWorkoutActionCoach = true)
            )
        )
        assertFalse(FreeAccessStartupNavigation.onboardingBlocks(OnboardingGuide()))
    }

    @Test
    fun anExistingSelectionScreenIsRememberedAndNotStacked() {
        assertEquals(FreeAccessEntry.Remember(4L), choose(route = AppRoutes.FREE_ACCESS, offered = null))
        assertEquals(FreeAccessEntry.None, choose(route = AppRoutes.FREE_ACCESS, offered = 4L))
    }

    @Test
    fun leavingTheScreenDoesNotOpenItAgainForTheSamePeriod() {
        assertEquals(FreeAccessEntry.None, choose(route = AppRoutes.OVERVIEW, offered = 4L))
        assertEquals(FreeAccessEntry.Open(5L), choose(route = AppRoutes.OVERVIEW, offered = 4L, periodId = 5L))
    }

    @Test
    fun unloadedOrSatisfiedAccessDoesNotNavigate() {
        assertEquals(FreeAccessEntry.None, choose(route = AppRoutes.OVERVIEW, loaded = false))
        assertEquals(
            FreeAccessEntry.None,
            choose(route = AppRoutes.OVERVIEW, selectionRequired = false)
        )
    }

    @Test
    fun proBenefitsStayReachableFromTheSelectionRoute() {
        assertTrue(AppNavigation.shouldNavigate(AppRoutes.FREE_ACCESS, AppRoutes.PRO_BENEFITS))
        assertTrue(AppNavigation.shouldNavigate(AppRoutes.ACTIVE_WORKOUT, AppRoutes.PRO_BENEFITS))
        assertEquals(
            FreeAccessEntry.Open(4L),
            choose(route = AppRoutes.PRO_BENEFITS, offered = null)
        )
    }

    private fun choose(
        route: String?,
        offered: Long? = null,
        periodId: Long = 4L,
        loaded: Boolean = true,
        selectionRequired: Boolean = true,
        blocked: Boolean = false
    ): FreeAccessEntry {
        return FreeAccessStartupNavigation.choose(
            loaded = loaded,
            selectionRequired = selectionRequired,
            periodId = periodId,
            offeredPeriodId = offered,
            route = route,
            blocked = blocked
        )
    }
}
