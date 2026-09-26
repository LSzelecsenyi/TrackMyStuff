package app.mymusclemap.ui.navigation

import app.mymusclemap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun bottomNavigationContainsExactlyOverviewAndJournal() {
        assertEquals(2, AppNavigation.rootTabs.size)
        assertEquals(
            listOf(R.string.nav_dashboard, R.string.nav_journal),
            AppNavigation.rootTabs.map { it.labelRes }
        )
        assertEquals(
            listOf(AppRoutes.OVERVIEW, AppRoutes.JOURNAL),
            AppNavigation.rootTabs.map { it.route }
        )
        assertFalse(AppNavigation.rootTabs.any { it.route == AppRoutes.TEMPLATES })
        assertFalse(AppNavigation.rootTabs.any { it.route == AppRoutes.EXERCISES })
        assertFalse(AppNavigation.rootTabs.any { it.route == AppRoutes.SETTINGS })
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
        val fromJournal = AppNavigation.openSettings(AppRoutes.JOURNAL)
        assertEquals(AppRoutes.JOURNAL, fromJournal.backTarget)
        val fromOverview = AppNavigation.openSettings(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.OVERVIEW, fromOverview.backTarget)
    }

    @Test
    fun helpOpensFromSettingsWithoutDuplicatingAndHidesBottomBar() {
        val navigation = AppNavigation.openHelp(AppRoutes.SETTINGS)
        assertEquals(AppRoutes.HELP, navigation.targetRoute)
        assertEquals(AppRoutes.SETTINGS, navigation.backTarget)
        assertTrue(navigation.shouldPush)
        assertFalse(AppNavigation.openHelp(AppRoutes.HELP).shouldPush)
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.HELP))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.HELP, AppRoutes.HELP))
        assertTrue(AppNavigation.shouldNavigate(AppRoutes.SETTINGS, AppRoutes.HELP))
    }

    @Test
    fun privacyOpensFromSettingsWithoutDuplicatingAndHidesBottomBar() {
        val navigation = AppNavigation.openPrivacy(AppRoutes.SETTINGS)
        assertEquals(AppRoutes.PRIVACY, navigation.targetRoute)
        assertEquals(AppRoutes.SETTINGS, navigation.backTarget)
        assertTrue(navigation.shouldPush)
        assertFalse(AppNavigation.openPrivacy(AppRoutes.PRIVACY).shouldPush)
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.PRIVACY))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.PRIVACY, AppRoutes.PRIVACY))
        assertTrue(AppNavigation.shouldNavigate(AppRoutes.SETTINGS, AppRoutes.PRIVACY))
    }

    @Test
    fun statisticsOpensFromOverviewWithoutDuplicatingAndHidesBottomBar() {
        val navigation = AppNavigation.openStatistics(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.STATISTICS, navigation.targetRoute)
        assertEquals(AppRoutes.OVERVIEW, navigation.backTarget)
        assertTrue(navigation.shouldPush)
        assertFalse(AppNavigation.openStatistics(AppRoutes.STATISTICS).shouldPush)
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.STATISTICS))
        assertFalse(AppNavigation.rootTabs.any { it.route == AppRoutes.STATISTICS })
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.STATISTICS, AppRoutes.STATISTICS))
        assertTrue(AppNavigation.shouldNavigate(AppRoutes.OVERVIEW, AppRoutes.STATISTICS))
    }

    @Test
    fun proInfoOpensWithoutDuplicatingAndHidesBottomBar() {
        val fromSettings = AppNavigation.openProInfo(AppRoutes.SETTINGS)
        assertEquals(AppRoutes.PRO_INFO, fromSettings.targetRoute)
        assertEquals(AppRoutes.SETTINGS, fromSettings.backTarget)
        assertTrue(fromSettings.shouldPush)
        val fromOverview = AppNavigation.openProInfo(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.OVERVIEW, fromOverview.backTarget)
        assertFalse(AppNavigation.openProInfo(AppRoutes.PRO_INFO).shouldPush)
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.PRO_INFO))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.PRO_INFO, AppRoutes.PRO_INFO))
        assertTrue(AppNavigation.shouldNavigate(AppRoutes.SETTINGS, AppRoutes.PRO_INFO))
    }

    @Test
    fun exerciseCatalogAndTemplatesOpenFromOverviewWithoutDuplicating() {
        val catalog = AppNavigation.openCatalog(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.EXERCISES, catalog.targetRoute)
        assertEquals(AppRoutes.OVERVIEW, catalog.backTarget)
        assertEquals(AppRoutes.OVERVIEW, AppNavigation.catalogEntryPoint())
        assertFalse(AppNavigation.openCatalog(AppRoutes.EXERCISES).shouldPush)
        val templates = AppNavigation.openTemplates(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.TEMPLATES, templates.targetRoute)
        assertEquals(AppRoutes.OVERVIEW, templates.backTarget)
        assertFalse(AppNavigation.openTemplates(AppRoutes.TEMPLATES).shouldPush)
    }

    @Test
    fun exerciseCatalogIsNotPresentedInsideSettings() {
        assertFalse(AppNavigation.settingsContainsCatalog())
        assertTrue(AppNavigation.catalogEntryPoint() != AppRoutes.SETTINGS)
    }

    @Test
    fun rootNavigationDoesNotCreateDuplicateDestinations() {
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.OVERVIEW, AppRoutes.OVERVIEW))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.JOURNAL, AppRoutes.JOURNAL))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.SETTINGS, AppRoutes.SETTINGS))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.EXERCISES, AppRoutes.EXERCISES))
        assertFalse(AppNavigation.shouldNavigate(AppRoutes.TEMPLATES, AppRoutes.TEMPLATES))
        assertTrue(AppNavigation.shouldNavigate(AppRoutes.OVERVIEW, AppRoutes.SETTINGS))
        assertFalse(
            AppNavigation.openSettings(
                fromRoute = AppRoutes.JOURNAL,
                currentRoute = AppRoutes.SETTINGS
            ).shouldPush
        )
    }

    @Test
    fun bottomBarIsVisibleOnOverviewJournalAndRootLists() {
        assertTrue(AppNavigation.showsBottomBar(AppRoutes.OVERVIEW))
        assertTrue(AppNavigation.showsBottomBar(AppRoutes.JOURNAL))
        assertTrue(AppNavigation.showsBottomBar(AppRoutes.EXERCISES))
        assertTrue(AppNavigation.showsBottomBar(AppRoutes.TEMPLATES))
        assertFalse(AppNavigation.showsBottomBar("unknown"))
        assertFalse(AppNavigation.showsBottomBar(null))
    }

    @Test
    fun bottomBarIsHiddenOnActiveEditorsDetailsAndImport() {
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.SETTINGS))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.HELP))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.PRIVACY))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.PRO_INFO))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.STATISTICS))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.EXERCISE_EDITOR_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("exercise_editor?exerciseId=12"))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.TEMPLATE_EDITOR_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("template_editor?templateId=4"))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.ACTIVE_WORKOUT_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("active_workout?sessionId=9"))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WORKOUT_DETAIL_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("workout_detail?sessionId=3"))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WEIGHT_DETAILS))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WORKOUT_IMPORT))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WORKOUT_COMPLETE))
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WORKOUT_COMPLETE_PATTERN))
        assertFalse(AppNavigation.showsBottomBar("workout_complete?exercises=4&completedSets=14&durationMillis=3020000"))
    }

    @Test
    fun weightDetailsOpensFromOverviewAndHidesBottomBar() {
        val navigation = AppNavigation.openWeightDetails(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.WEIGHT_DETAILS, navigation.targetRoute)
        assertEquals(AppRoutes.OVERVIEW, navigation.backTarget)
        assertTrue(navigation.shouldPush)
        assertFalse(AppNavigation.openWeightDetails(AppRoutes.WEIGHT_DETAILS).shouldPush)
    }

    @Test
    fun workoutDetailReturnsToSourceAndDoesNotDuplicate() {
        val fromJournal = AppNavigation.openWorkoutDetail(AppRoutes.JOURNAL)
        assertEquals(AppRoutes.WORKOUT_DETAIL, fromJournal.targetRoute)
        assertEquals(AppRoutes.JOURNAL, fromJournal.backTarget)
        assertTrue(fromJournal.shouldPush)
        val fromOverview = AppNavigation.openWorkoutDetail(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.OVERVIEW, fromOverview.backTarget)
        assertEquals(AppRoutes.JOURNAL, AppNavigation.openWorkoutDetail(AppRoutes.TEMPLATES).backTarget)
        assertFalse(AppNavigation.openWorkoutDetail(AppRoutes.WORKOUT_DETAIL).shouldPush)
        assertFalse(AppNavigation.openWorkoutDetail("workout_detail?sessionId=3").shouldPush)
    }

    @Test
    fun workoutImportOpensFromJournalHidesBottomBarAndDoesNotDuplicate() {
        val navigation = AppNavigation.openWorkoutImport(AppRoutes.JOURNAL)
        assertEquals(AppRoutes.WORKOUT_IMPORT, navigation.targetRoute)
        assertEquals(AppRoutes.JOURNAL, navigation.backTarget)
        assertTrue(navigation.shouldPush)
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WORKOUT_IMPORT))
        assertFalse(AppNavigation.openWorkoutImport(AppRoutes.WORKOUT_IMPORT).shouldPush)
        assertEquals(AppRoutes.JOURNAL, AppNavigation.openWorkoutImport(AppRoutes.WORKOUT_IMPORT).backTarget)
        assertEquals(AppRoutes.OVERVIEW, AppNavigation.catalogEntryPoint())
        assertTrue(AppNavigation.catalogEntryPoint() != AppRoutes.WORKOUT_IMPORT)
    }

    @Test
    fun activeWorkoutOpensFromOverviewWithoutDuplicating() {
        val navigation = AppNavigation.openActiveWorkout(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.ACTIVE_WORKOUT, navigation.targetRoute)
        assertEquals(AppRoutes.OVERVIEW, navigation.backTarget)
        assertTrue(navigation.shouldPush)
        assertFalse(AppNavigation.openActiveWorkout(AppRoutes.ACTIVE_WORKOUT_PATTERN).shouldPush)
        assertFalse(AppNavigation.openActiveWorkout("active_workout?sessionId=9").shouldPush)
    }

    @Test
    fun workoutCompleteOpensFromActiveWorkoutHidesBottomBarAndReturnsToOverview() {
        val navigation = AppNavigation.openWorkoutComplete(AppRoutes.ACTIVE_WORKOUT)
        assertEquals(AppRoutes.WORKOUT_COMPLETE, navigation.targetRoute)
        assertEquals(AppRoutes.OVERVIEW, navigation.backTarget)
        assertTrue(navigation.shouldPush)
        assertEquals(AppRoutes.OVERVIEW, AppNavigation.leaveWorkoutComplete())
        assertFalse(AppNavigation.openWorkoutComplete(AppRoutes.WORKOUT_COMPLETE).shouldPush)
        assertFalse(AppNavigation.openWorkoutComplete(AppRoutes.WORKOUT_COMPLETE_PATTERN).shouldPush)
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WORKOUT_COMPLETE))
    }

    @Test
    fun backFromNonDefaultRootReturnsToOverview() {
        assertEquals(AppRoutes.OVERVIEW, AppNavigation.backFromRootTab(AppRoutes.JOURNAL))
        assertNull(AppNavigation.backFromRootTab(AppRoutes.OVERVIEW))
        assertNull(AppNavigation.backFromRootTab(AppRoutes.SETTINGS))
        assertNull(AppNavigation.backFromRootTab(AppRoutes.TEMPLATES))
        assertNull(AppNavigation.backFromRootTab(AppRoutes.EXERCISES))
    }
}
