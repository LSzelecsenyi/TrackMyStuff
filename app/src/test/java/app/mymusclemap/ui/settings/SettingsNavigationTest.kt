package app.mymusclemap.ui.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.mymusclemap.R
import app.mymusclemap.domain.health.HealthSettingsState
import app.mymusclemap.testString
import app.mymusclemap.ui.navigation.AppNavigation
import app.mymusclemap.ui.navigation.AppRoutes
import app.mymusclemap.ui.navigation.navigateInternal
import app.mymusclemap.ui.navigation.openSettingsCategory
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class SettingsNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun categoryBackAndToolbarBackReturnToTheSettingsList() {
        val nav = host()
        composeRule.onNodeWithText("overview").assertIsDisplayed()
        composeRule.runOnIdle { nav.navigateInternal(AppRoutes.SETTINGS) }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.SETTINGS, nav.currentDestination?.route)
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_TRAINING).performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.SETTINGS_TRAINING, nav.currentDestination?.route)
        composeRule.onNodeWithText(testString(R.string.training_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.SETTINGS, nav.currentDestination?.route)
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_APPEARANCE).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.SETTINGS, nav.currentDestination?.route)
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
    }

    @Test
    fun eachCategoryOpensWithoutReplacingTheSettingsList() {
        val nav = host()
        val pages = listOf(
            SETTINGS_CATEGORY_APPEARANCE to AppRoutes.SETTINGS_APPEARANCE,
            SETTINGS_CATEGORY_TRAINING to AppRoutes.SETTINGS_TRAINING,
            SETTINGS_CATEGORY_HEALTH to AppRoutes.SETTINGS_HEALTH,
            SETTINGS_CATEGORY_BACKUP to AppRoutes.SETTINGS_BACKUP,
            SETTINGS_CATEGORY_ACCOUNT to AppRoutes.SETTINGS_ACCOUNT,
            SETTINGS_CATEGORY_ABOUT to AppRoutes.SETTINGS_ABOUT
        )
        composeRule.runOnIdle { nav.navigateInternal(AppRoutes.SETTINGS) }
        composeRule.waitForIdle()
        pages.forEach { (tag, route) ->
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
            assertEquals(route, nav.currentDestination?.route)
            assertFalse(AppNavigation.showsBottomBar(route))
            composeRule.runOnIdle { nav.popBackStack() }
            composeRule.waitForIdle()
            assertEquals(AppRoutes.SETTINGS, nav.currentDestination?.route)
        }
    }

    @Test
    fun healthSettingsDeepLinkLandsOnHealthAndBacksUpToSettings() {
        val nav = host()
        composeRule.runOnIdle { nav.openSettingsCategory(AppRoutes.SETTINGS_HEALTH) }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.SETTINGS_HEALTH, nav.currentDestination?.route)
        composeRule.onNodeWithText(testString(R.string.health_connect_title)).assertIsDisplayed()
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.SETTINGS, nav.currentDestination?.route)
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_HEALTH).assertIsDisplayed()
    }

    @Test
    fun proPlanStaysADirectDestinationBesideSettings() {
        val nav = host()
        composeRule.runOnIdle { nav.navigateInternal(AppRoutes.PRO_BENEFITS) }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.PRO_BENEFITS, nav.currentDestination?.route)
        composeRule.onNodeWithText("pro").assertIsDisplayed()
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.OVERVIEW, nav.currentDestination?.route)
    }

    private fun host() = composeRule.run {
        lateinit var nav: androidx.navigation.NavHostController
        setContent {
            WeightTrackerThemeForPreview {
                val controller = rememberNavController()
                nav = controller
                NavHost(controller, startDestination = AppRoutes.OVERVIEW) {
                    composable(AppRoutes.OVERVIEW) { Text("overview") }
                    composable(AppRoutes.PRO_BENEFITS) { Text("pro") }
                    composable(AppRoutes.SETTINGS) {
                        SettingsScreen(
                            onBack = { controller.popBackStack() },
                            onOpenAppearance = { controller.navigateInternal(AppRoutes.SETTINGS_APPEARANCE) },
                            onOpenTraining = { controller.navigateInternal(AppRoutes.SETTINGS_TRAINING) },
                            onOpenHealth = { controller.navigateInternal(AppRoutes.SETTINGS_HEALTH) },
                            onOpenBackup = { controller.navigateInternal(AppRoutes.SETTINGS_BACKUP) },
                            onOpenAccount = { controller.navigateInternal(AppRoutes.SETTINGS_ACCOUNT) },
                            onOpenAbout = { controller.navigateInternal(AppRoutes.SETTINGS_ABOUT) }
                        )
                    }
                    composable(AppRoutes.SETTINGS_APPEARANCE) {
                        AppearancePreferencesScreen(state = SettingsUiState(), onBack = { controller.popBackStack() })
                    }
                    composable(AppRoutes.SETTINGS_TRAINING) {
                        TrainingSettingsScreen(state = SettingsUiState(), onBack = { controller.popBackStack() })
                    }
                    composable(AppRoutes.SETTINGS_HEALTH) {
                        HealthConnectSettingsPage(
                            health = HealthSettingsState.NotConnected,
                            onBack = { controller.popBackStack() }
                        )
                    }
                    composable(AppRoutes.SETTINGS_BACKUP) {
                        DataBackupScreen(state = SettingsUiState(), onBack = { controller.popBackStack() })
                    }
                    composable(AppRoutes.SETTINGS_ACCOUNT) {
                        AccountMembershipScreen(onBack = { controller.popBackStack() })
                    }
                    composable(AppRoutes.SETTINGS_ABOUT) {
                        AboutStrictScreen(state = SettingsUiState(), onBack = { controller.popBackStack() })
                    }
                }
            }
        }
        waitForIdle()
        nav
    }
}
