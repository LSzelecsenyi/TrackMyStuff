package app.mymusclemap.ui.units

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.mymusclemap.domain.units.UnitSystem
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnitSystemDisplayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theSameKilogramsRenderInTheProvidedSystem() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                CompositionLocalProvider(LocalUnitSystem provides UnitSystem.IMPERIAL) {
                    Text(
                        text = UiFormatters.weightKg(80.0, LocalUnitSystem.current),
                        modifier = Modifier.testTag("weight")
                    )
                }
            }
        }
        composeRule.onNodeWithTag("weight").assertTextEquals("176.4 lb")
    }

    @Test
    fun settingsSelectionReportsTheChosenSystem() {
        var selected = UnitSystem.METRIC
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                app.mymusclemap.ui.settings.AppearancePreferencesScreen(
                    state = app.mymusclemap.ui.settings.SettingsUiState(),
                    onBack = {},
                    selectedUnitSystem = selected,
                    onUnitSystemSelected = { selected = it }
                )
            }
        }
        composeRule.onNodeWithTag("settings-units-imperial").performClick()
        assertEquals(UnitSystem.IMPERIAL, selected)
    }
}
