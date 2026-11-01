package app.mymusclemap.ui.library

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.mymusclemap.R
import app.mymusclemap.testString
import app.mymusclemap.ui.navigation.AppRoutes
import app.mymusclemap.ui.navigation.navigateInternal
import app.mymusclemap.ui.navigation.navigateRoot
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class LibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryOpensOnWorkoutPlansAndSwitchesToExercises() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                LibraryScreen(
                    plans = { Text("plans", modifier = Modifier.testTag(PLANS)) },
                    exercises = { Text("exercises", modifier = Modifier.testTag(EXERCISES)) }
                )
            }
        }
        composeRule.onNodeWithTag(LIBRARY_TAB_PLANS).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_TAB_EXERCISES).assertIsDisplayed()
        composeRule.onNodeWithTag(PLANS).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISES).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.templates_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_TAB_EXERCISES).performClick()
        composeRule.onNodeWithTag(EXERCISES).assertIsDisplayed()
        composeRule.onNodeWithTag(PLANS).assertDoesNotExist()
    }

    @Test
    fun selectedTabSurvivesConfigurationChange() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            WeightTrackerThemeForPreview {
                LibraryScreen(
                    plans = { Text("plans", modifier = Modifier.testTag(PLANS)) },
                    exercises = { Text("exercises", modifier = Modifier.testTag(EXERCISES)) }
                )
            }
        }
        composeRule.onNodeWithTag(LIBRARY_TAB_EXERCISES).performClick()
        composeRule.onNodeWithTag(EXERCISES).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(EXERCISES).assertIsDisplayed()
        composeRule.onNodeWithTag(PLANS).assertDoesNotExist()
    }

    @Test
    fun selectedTabSurvivesLeavingAndReturningAndADetailPop() {
        lateinit var nav: NavHostController
        composeRule.setContent {
            nav = rememberNavController()
            WeightTrackerThemeForPreview {
                NavHost(navController = nav, startDestination = AppRoutes.OVERVIEW) {
                    composable(AppRoutes.OVERVIEW) {
                        Text("overview", modifier = Modifier.testTag("overview"))
                    }
                    composable(AppRoutes.JOURNAL) {
                        Text("journal", modifier = Modifier.testTag("journal"))
                    }
                    composable(AppRoutes.LIBRARY) {
                        LibraryScreen(
                            plans = { Text("plans", modifier = Modifier.testTag(PLANS)) },
                            exercises = { Text("exercises", modifier = Modifier.testTag(EXERCISES)) }
                        )
                    }
                    composable("plan-editor") {
                        Text("editor", modifier = Modifier.testTag("editor"))
                    }
                }
            }
        }
        composeRule.runOnIdle { nav.navigateRoot(AppRoutes.LIBRARY) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PLANS).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_TAB_EXERCISES).performClick()
        composeRule.runOnIdle { nav.navigateRoot(AppRoutes.JOURNAL) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("journal").assertIsDisplayed()
        composeRule.runOnIdle { nav.navigateRoot(AppRoutes.LIBRARY) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(EXERCISES).assertIsDisplayed()
        composeRule.runOnIdle { nav.navigateInternal("plan-editor") }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor").assertIsDisplayed()
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(EXERCISES).assertIsDisplayed()
        composeRule.onNodeWithTag(PLANS).assertDoesNotExist()
    }

    private companion object {
        const val PLANS = "plans-pane"
        const val EXERCISES = "exercises-pane"
    }
}
