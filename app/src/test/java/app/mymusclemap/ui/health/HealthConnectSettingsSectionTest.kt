package app.mymusclemap.ui.health

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mymusclemap.R
import app.mymusclemap.domain.health.HealthSettingsAction
import app.mymusclemap.domain.health.HealthSettingsState
import app.mymusclemap.domain.health.HealthSettingsStatus
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class HealthConnectSettingsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unavailableHasNoAction() {
        render(status(HealthSettingsStatus.Unavailable, HealthSettingsAction.None))
        composeRule.onNodeWithText(testString(R.string.health_connect_status_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_summary)).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_HEALTH_ACTION).assertDoesNotExist()
    }

    @Test
    fun updateRequiredOffersInstall() {
        render(status(HealthSettingsStatus.UpdateRequired, HealthSettingsAction.InstallOrUpdate))
        composeRule.onNodeWithText(testString(R.string.health_connect_status_update)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_action_install)).assertIsDisplayed()
    }

    @Test
    fun notConnectedOffersConnect() {
        val connects = intArrayOf(0)
        render(
            status(HealthSettingsStatus.NotConnected, HealthSettingsAction.RequestPermissions),
            onAction = { connects[0] += 1 }
        )
        composeRule.onNodeWithText(testString(R.string.health_connect_status_not_connected)).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_HEALTH_ACTION).performClick()
        assertEquals(1, connects[0])
    }

    @Test
    fun stepsOnlySaysHeartRateIsOff() {
        render(
            HealthSettingsState(
                ready = true,
                status = HealthSettingsStatus.Connected,
                stepsGranted = true,
                restingHeartRateGranted = false,
                action = HealthSettingsAction.ManageAccess
            )
        )
        composeRule.onNodeWithText(testString(R.string.health_connect_status_connected)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_partial_steps)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_action_manage)).assertIsDisplayed()
    }

    @Test
    fun heartRateOnlySaysStepsAreOff() {
        render(
            HealthSettingsState(
                ready = true,
                status = HealthSettingsStatus.Connected,
                stepsGranted = false,
                restingHeartRateGranted = true,
                action = HealthSettingsAction.ManageAccess
            )
        )
        composeRule.onNodeWithText(testString(R.string.health_connect_partial_heart)).assertIsDisplayed()
    }

    @Test
    fun bothGrantedListsBothMetrics() {
        render(
            HealthSettingsState(
                ready = true,
                status = HealthSettingsStatus.Connected,
                stepsGranted = true,
                restingHeartRateGranted = true,
                action = HealthSettingsAction.ManageAccess
            )
        )
        composeRule.onNodeWithText(testString(R.string.health_connect_both)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_view_data)).assertIsDisplayed()
    }

    @Test
    fun partialNewGrantsListOnlyWhatIsOn() {
        val opens = intArrayOf(0)
        render(
            HealthSettingsState(
                ready = true,
                status = HealthSettingsStatus.Connected,
                stepsGranted = true,
                restingHeartRateGranted = false,
                action = HealthSettingsAction.ManageAccess,
                exerciseGranted = true,
                hrvGranted = false,
                sleepGranted = true
            ),
            onOpenDetails = { opens[0] += 1 }
        )
        composeRule.onNodeWithText("Steps, Exercise sessions, Sleep").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_partial_steps)).assertDoesNotExist()
        composeRule.onNodeWithTag(SETTINGS_HEALTH_DETAILS).performClick()
        assertEquals(1, opens[0])
    }

    private fun status(status: HealthSettingsStatus, action: HealthSettingsAction): HealthSettingsState {
        return HealthSettingsState(
            ready = true,
            status = status,
            stepsGranted = false,
            restingHeartRateGranted = false,
            action = action
        )
    }

    private fun render(
        state: HealthSettingsState,
        onAction: () -> Unit = {},
        onOpenDetails: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                HealthConnectSettingsSection(
                    state = state,
                    onAction = onAction,
                    onOpenDetails = onOpenDetails
                )
            }
        }
    }
}
