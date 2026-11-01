package app.mymusclemap.ui.components.musclemap

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.mymusclemap.data.preferences.DisplayedMuscleMapBodyModel
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel
import app.mymusclemap.ui.components.musclemap.artwork.FemaleMuscleMapGeometry
import app.mymusclemap.ui.components.musclemap.artwork.MaleMuscleMapGeometry
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapArtworkSelector
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class MuscleMapBodyModelUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun restoreMaleArtwork() {
        DisplayedMuscleMapBodyModel.current = MuscleMapBodyModel.MALE
    }

    @Test
    fun settingsSelectionReportsTheChosenBodyModel() {
        var selected = MuscleMapBodyModel.MALE
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                app.mymusclemap.ui.settings.AppearancePreferencesScreen(
                    state = app.mymusclemap.ui.settings.SettingsUiState(),
                    onBack = {},
                    selectedBodyModel = selected,
                    onBodyModelSelected = { selected = it }
                )
            }
        }
        composeRule.onNodeWithTag("settings-body-model-female").performClick()
        assertEquals(MuscleMapBodyModel.FEMALE, selected)
        composeRule.onNodeWithTag("settings-body-model-male").performClick()
        assertEquals(MuscleMapBodyModel.MALE, selected)
    }

    @Test
    fun theVisibleMapFollowsTheSelectedBodyModel() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                CompositionLocalProvider(LocalMuscleMapBodyModel provides MuscleMapBodyModel.FEMALE) {
                    MuscleMap(
                        fills = emptyMap(),
                        unmappedFill = Color.Gray,
                        outline = Color.Black,
                        mappedOutline = Color.DarkGray,
                        selectedOutline = Color.White,
                        selected = null,
                        onSelect = {},
                        contentDescription = "Muscle map"
                    )
                }
            }
        }
        composeRule.onNodeWithTag("muscle_map_FEMALE").assertIsDisplayed()
        composeRule.onNodeWithTag("muscle_map_MALE").assertDoesNotExist()
        DisplayedMuscleMapBodyModel.current = MuscleMapBodyModel.FEMALE
        assertEquals(
            FemaleMuscleMapGeometry.ANATOMY_ASSET,
            MuscleMapArtworkSelector.current().anatomyAsset
        )
        DisplayedMuscleMapBodyModel.current = MuscleMapBodyModel.MALE
        assertEquals(
            MaleMuscleMapGeometry.ANATOMY_ASSET,
            MuscleMapArtworkSelector.current().anatomyAsset
        )
        assertEquals(
            MuscleMapArtworkSelector.select(MuscleMapBodyModel.MALE).regions.map { it.id },
            parseMuscleRegions(MuscleMapArtworkSelector.select(MuscleMapBodyModel.MALE)).map { it.region.id }
        )
    }
}
