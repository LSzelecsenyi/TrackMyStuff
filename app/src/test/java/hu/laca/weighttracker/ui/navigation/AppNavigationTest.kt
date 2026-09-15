package hu.laca.weighttracker.ui.navigation

import hu.laca.weighttracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun bottomNavigationContainsExactlyOverviewWorkoutAndJournal() {
        assertEquals(3, AppNavigation.rootTabs.size)
        assertEquals(
            listOf(R.string.nav_dashboard, R.string.nav_workout, R.string.nav_journal),
            AppNavigation.rootTabs.map { it.labelRes }
        )
        assertEquals(
            listOf(AppRoutes.OVERVIEW, AppRoutes.WORKOUT, AppRoutes.JOURNAL),
            AppNavigation.rootTabs.map { it.route }
        )
    }

    @Test
    fun settingsIsNotABottomNavigationDestination() {
        assertFalse(AppNavigation.isSettingsBottomDestination())
        assertFalse(AppNavigation.rootTabs.any { it.route == AppRoutes.SETTINGS })
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.SETTINGS))
    }

    @Test
    fun settingsIsReachableFromEachRootScreen() {
        AppNavigation.rootTabs.forEach { tab ->
            val navigation = AppNavigation.openSettings(fromRoute = tab.route)
            assertEquals(AppRoutes.SETTINGS, navigation.targetRoute)
            assertEquals(tab.route, navigation.backTarget)
            assertTrue(navigation.shouldPush)
        }
    }

    @Test
    fun backFromSettingsReturnsToOriginatingRoot() {
        val fromWorkout = AppNavigation.openSettings(AppRoutes.WORKOUT)
        assertEquals(AppRoutes.WORKOUT, fromWorkout.backTarget)
        val fromJournal = AppNavigation.openSettings(AppRoutes.JOURNAL)
        assertEquals(AppRoutes.JOURNAL, fromJournal.backTarget)
        val fromOverview = AppNavigation.openSettings(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.OVERVIEW, fromOverview.backTarget)
    }

    @Test
    fun exerciseCatalogIsReachableFromWorkoutHub() {
        val navigation = AppNavigation.openCatalog(AppRoutes.WORKOUT)
        assertEquals(AppRoutes.EXERCISES, navigation.targetRoute)
        assertEquals(AppRoutes.WORKOUT, navigation.backTarget)
        assertEquals(AppRoutes.WORKOUT, AppNavigation.catalogEntryPoint())
    }

    @Test
    fun exerciseCatalogIsNotPresentedInsideSettings() {
        assertFalse(AppNavigation.settingsContainsCatalog())
        assertTrue(AppNavigation.catalogEntryPoint() != AppRoutes.SETTINGS)
    }

    @Test
    fun backFromCatalogReturnsToWorkout() {
        assertEquals(AppRoutes.WORKOUT, AppNavigation.openCatalog(AppRoutes.WORKOUT).backTarget)
    }

    @Test
    fun rootNavigationDoesNotCreateDuplicateDestinations() {
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.OVERVIEW, AppRoutes.OVERVIEW))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.SETTINGS, AppRoutes.SETTINGS))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.EXERCISES, AppRoutes.EXERCISES))
        assertTrue(AppNavigation.shouldNavigate(AppRoutes.WORKOUT, AppRoutes.SETTINGS))
        assertFalse(
            AppNavigation.openSettings(
                fromRoute = AppRoutes.WORKOUT,
                currentRoute = AppRoutes.SETTINGS
            ).shouldPush
        )
    }

    @Test
    fun bottomBarIsLimitedToRootDestinations() {
        assertTrue(AppNavigation.showsBottomBar(AppRoutes.OVERVIEW))
        assertTrue(AppNavigation.showsBottomBar(AppRoutes.WORKOUT))
        assertTrue(AppNavigation.showsBottomBar(AppRoutes.JOURNAL))
        assertFalse(AppNavigation.showsBottomBar("unknown"))
        assertFalse(AppNavigation.showsBottomBar(null))
    }

    @Test
    fun bottomBarIsHiddenOnSettings() {
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.SETTINGS))
    }

    @Test
    fun bottomBarIsHiddenOnCatalogAndEditor() {
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.EXERCISES))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.EXERCISE_EDITOR_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("exercise_editor?exerciseId=12"))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.TEMPLATES))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.TEMPLATE_EDITOR_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("template_editor?templateId=4"))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.ACTIVE_WORKOUT_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("active_workout?sessionId=9"))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WORKOUT_DETAIL_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("workout_detail?sessionId=3"))
    }

    @Test
    fun workoutDetailReturnsToSourceAndDoesNotDuplicate() {
        val fromJournal = AppNavigation.openWorkoutDetail(AppRoutes.JOURNAL)
        assertEquals(AppRoutes.WORKOUT_DETAIL, fromJournal.targetRoute)
        assertEquals(AppRoutes.JOURNAL, fromJournal.backTarget)
        assertTrue(fromJournal.shouldPush)
        val fromOverview = AppNavigation.openWorkoutDetail(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.OVERVIEW, fromOverview.backTarget)
        val fromHub = AppNavigation.openWorkoutDetail(AppRoutes.WORKOUT)
        assertEquals(AppRoutes.WORKOUT, fromHub.backTarget)
        assertFalse(AppNavigation.openWorkoutDetail(AppRoutes.WORKOUT_DETAIL).shouldPush)
        assertFalse(AppNavigation.openWorkoutDetail("workout_detail?sessionId=3").shouldPush)
    }

    @Test
    fun backFromNonDefaultRootReturnsToOverview() {
        assertEquals(AppRoutes.OVERVIEW, AppNavigation.backFromRootTab(AppRoutes.WORKOUT))
        assertEquals(AppRoutes.OVERVIEW, AppNavigation.backFromRootTab(AppRoutes.JOURNAL))
        assertNull(AppNavigation.backFromRootTab(AppRoutes.OVERVIEW))
        assertNull(AppNavigation.backFromRootTab(AppRoutes.SETTINGS))
    }
}
