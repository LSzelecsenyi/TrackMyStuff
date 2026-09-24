package hu.laca.weighttracker.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hu.laca.weighttracker.domain.workout.WorkoutCompletionSummary
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class AppRootNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenRootListWhenOverviewIsTappedThenASingleDashboardIsOnTop() {
        val nav = host()
        composeRule.runOnIdle {
            nav.navigateInternal(AppRoutes.TEMPLATES)
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.TEMPLATES, nav.currentDestination?.route)
        composeRule.runOnIdle {
            nav.navigateRoot(AppRoutes.OVERVIEW)
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
        assertFalse(nav.popBackStack(AppRoutes.TEMPLATES, false))

        composeRule.runOnIdle {
            nav.navigateInternal(AppRoutes.EXERCISES)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            nav.navigateRoot(AppRoutes.OVERVIEW)
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
        assertFalse(nav.popBackStack(AppRoutes.EXERCISES, false))

        composeRule.runOnIdle {
            nav.navigateRoot(AppRoutes.JOURNAL)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            nav.navigateRoot(AppRoutes.OVERVIEW)
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
        assertFalse(nav.popBackStack(AppRoutes.JOURNAL, false))
    }

    @Test
    fun givenJournalWhenJournalIsTappedAgainThenTheRouteIsNotDuplicated() {
        val nav = host()
        composeRule.runOnIdle {
            nav.navigateRoot(AppRoutes.JOURNAL)
            nav.navigateRoot(AppRoutes.JOURNAL)
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.JOURNAL, nav.currentDestination?.route)
        composeRule.runOnIdle {
            nav.popBackStack()
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
        assertFalse(nav.popBackStack(AppRoutes.JOURNAL, false))
    }

    @Test
    fun givenFastRepeatedInternalNavigationThenDestinationsDoNotDuplicate() {
        val nav = host()
        composeRule.runOnIdle {
            nav.navigateInternal(AppRoutes.TEMPLATES)
            nav.navigateInternal(AppRoutes.TEMPLATES)
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.TEMPLATES, nav.currentDestination?.route)
        composeRule.runOnIdle {
            nav.popBackStack()
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
        assertFalse(nav.popBackStack(AppRoutes.TEMPLATES, false))

        composeRule.runOnIdle {
            nav.navigateInternal(AppRoutes.EXERCISES)
            nav.navigateInternal(AppRoutes.EXERCISES)
            nav.navigateInternal(AppRoutes.SETTINGS)
            nav.navigateInternal(AppRoutes.SETTINGS)
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.SETTINGS, nav.currentDestination?.route)
        composeRule.runOnIdle {
            nav.popBackStack()
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.EXERCISES, nav.currentDestination?.route)
        composeRule.runOnIdle {
            nav.popBackStack()
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
    }

    @Test
    fun givenWorkoutCompleteWhenReturningToOverviewThenFinishedSessionIsGone() {
        val nav = host()
        val summary = WorkoutCompletionSummary(4, 14, 3_020_000L)
        composeRule.runOnIdle {
            nav.navigate(activeWorkoutRoute(9L))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            nav.openWorkoutComplete(summary)
        }
        composeRule.waitForIdle()
        assertEquals(
            AppRoutes.WORKOUT_COMPLETE,
            AppNavigation.canonicalRoute(nav.currentDestination?.route)
        )
        composeRule.runOnIdle {
            nav.leaveWorkoutComplete()
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
        composeRule.runOnIdle {
            assertFalse(nav.popBackStack(AppRoutes.ACTIVE_WORKOUT_PATTERN, false))
            assertFalse(nav.popBackStack(AppRoutes.WORKOUT_COMPLETE_PATTERN, false))
            assertFalse(nav.popBackStack(AppRoutes.ACTIVE_WORKOUT, false))
            assertFalse(nav.popBackStack(AppRoutes.WORKOUT_COMPLETE, false))
        }
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
    }

    @Test
    fun givenWorkoutCompleteWhenSystemBackThenOverviewIsShownOnce() {
        val nav = host()
        composeRule.runOnIdle {
            nav.navigate(activeWorkoutRoute(9L))
            nav.openWorkoutComplete(WorkoutCompletionSummary(1, 1, 1_000L))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            nav.popBackStack()
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
        composeRule.runOnIdle {
            assertFalse(nav.popBackStack(AppRoutes.ACTIVE_WORKOUT_PATTERN, false))
            assertFalse(nav.popBackStack(AppRoutes.WORKOUT_COMPLETE_PATTERN, false))
        }
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
    }

    private fun host(): NavHostController {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            WeightTrackerThemeForPreview {
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.OVERVIEW
                ) {
                    composable(AppRoutes.OVERVIEW) { Text("overview") }
                    composable(AppRoutes.JOURNAL) { Text("journal") }
                    composable(AppRoutes.TEMPLATES) { Text("templates") }
                    composable(AppRoutes.EXERCISES) { Text("exercises") }
                    composable(AppRoutes.SETTINGS) { Text("settings") }
                    composable(
                        route = AppRoutes.ACTIVE_WORKOUT_PATTERN,
                        arguments = listOf(
                            navArgument("sessionId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            }
                        )
                    ) { Text("active") }
                    composable(
                        route = AppRoutes.WORKOUT_COMPLETE_PATTERN,
                        arguments = listOf(
                            navArgument("exercises") {
                                type = NavType.IntType
                                defaultValue = 0
                            },
                            navArgument("completedSets") {
                                type = NavType.IntType
                                defaultValue = 0
                            },
                            navArgument("durationMillis") {
                                type = NavType.LongType
                                defaultValue = 0L
                            }
                        )
                    ) { Text("complete") }
                }
            }
        }
        composeRule.waitForIdle()
        return navController
    }
}
