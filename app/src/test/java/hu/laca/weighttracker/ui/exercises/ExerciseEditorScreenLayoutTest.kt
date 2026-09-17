package hu.laca.weighttracker.ui.exercises

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseFieldError
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.locale.LocalizedLabelOrder
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class ExerciseEditorScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingLowercaseNameCapitalizesTheFirstLetterAndKeepsTheCursor() {
        render()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performTextInput("tol")
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Tol"))
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performTextInput("ódzkodás")
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Tolódzkodás"))
    }

    @Test
    fun pastedHungarianNameCapitalizesOnlyTheFirstLetter() {
        render()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performTextReplacement("őrségi fekvőtámasz")
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Őrségi fekvőtámasz"))
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performTextReplacement("Biceps curl")
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Biceps curl"))
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performTextReplacement("hammer curl")
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Hammer curl"))
    }

    @Test
    fun categoryDropdownIsSortedByHungarianLabelNotEnumOrder() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val categories = LocalizedLabelOrder.sorted(
            ExerciseCategory.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        assertEquals(
            listOf(
                ExerciseCategory.STRENGTH,
                ExerciseCategory.CARDIO,
                ExerciseCategory.SKILL,
                ExerciseCategory.MOBILITY,
                ExerciseCategory.STATIC_HOLD
            ),
            categories
        )
        assertTrue(categories != ExerciseCategory.entries)
        val patterns = LocalizedLabelOrder.sorted(
            MovementPattern.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        assertEquals("Cipelés", resources.getString(patterns.first().labelRes()))
        val measurements = LocalizedLabelOrder.sorted(
            MeasurementType.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        assertEquals("Csak teljesítve", resources.getString(measurements.first().labelRes()))
        render()
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Készség / technika").assertIsDisplayed()
        composeRule.onNodeWithText("Statikus tartás").assertIsDisplayed()
    }

    @Test
    fun dropdownMenuIsAnchoredToTheLocalFieldNotTheFullScreen() {
        render(width = 360.dp)
        val field = composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY).getBoundsInRoot()
        val anchor = composeRule.onNodeWithTag(
            "$EXERCISE_DROPDOWN_CATEGORY-anchor",
            useUnmergedTree = true
        ).getBoundsInRoot()
        assertTrue(
            "anchor should wrap the chevron: anchor=$anchor field=$field",
            anchor.right - anchor.left < (field.right - field.left) / 2
        )
        assertTrue(
            "anchor should sit on the right of the field: anchor=$anchor field=$field",
            anchor.left > (field.left + field.right) / 2
        )
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("$EXERCISE_DROPDOWN_CATEGORY-menu").assertIsDisplayed()
        composeRule.onAllNodesWithText("Erőgyakorlat").assertCountEquals(2)
    }

    @Test
    fun selectedSecondaryMusclesAppearInHungarianAbcOrder() {
        render(
            state = ExerciseEditorUiState(
                draft = ExerciseDraft(
                    name = "Evezés",
                    primaryMuscle = MuscleGroup.LATS,
                    secondaryMuscles = listOf(
                        MuscleGroup.TRICEPS,
                        MuscleGroup.FOREARMS,
                        MuscleGroup.BICEPS
                    )
                )
            )
        )
        val forearms = composeRule.onNodeWithTag(secondaryChipTag(MuscleGroup.FOREARMS)).getBoundsInRoot()
        val biceps = composeRule.onNodeWithTag(secondaryChipTag(MuscleGroup.BICEPS)).getBoundsInRoot()
        val triceps = composeRule.onNodeWithTag(secondaryChipTag(MuscleGroup.TRICEPS)).getBoundsInRoot()
        assertTrue(comesBefore(forearms, biceps))
        assertTrue(comesBefore(biceps, triceps))
        assertTrue(forearms.right - forearms.left >= 48.dp || forearms.bottom - forearms.top >= 48.dp)
        assertTrue(biceps.bottom - biceps.top >= 48.dp)
        assertTrue(triceps.bottom - triceps.top >= 48.dp)
    }

    @Test
    fun primaryMuscleIsNotOfferedAsASecondaryOption() {
        render(
            state = ExerciseEditorUiState(
                draft = ExerciseDraft(
                    name = "Evezés",
                    primaryMuscle = MuscleGroup.LATS,
                    secondaryMuscles = listOf(MuscleGroup.BICEPS)
                )
            )
        )
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_ADD_SECONDARY).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Széles hátizom").assertCountEquals(1)
        composeRule.onAllNodesWithText("Bicepsz").assertCountEquals(1)
        composeRule.onNodeWithText("Alkar").assertIsDisplayed()
    }

    @Test
    fun neckIsOfferedAsPrimaryAndSecondaryInHungarianAbcOrder() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val muscles = LocalizedLabelOrder.sorted(
            MuscleGroup.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        val labels = muscles.map { resources.getString(it.labelRes()) }
        assertTrue(MuscleGroup.NECK in MuscleGroup.entries)
        assertEquals("Nyak", resources.getString(MuscleGroup.NECK.labelRes()))
        assertEquals(labels.indexOf("Mell") + 1, labels.indexOf("Nyak"))
        assertEquals(labels.indexOf("Nyak") + 1, labels.indexOf("Oldalsó váll"))
        render()
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PRIMARY).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Nyak").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Mell").assertCountEquals(2)
    }

    @Test
    fun neckIsOfferedAsASecondaryOptionWhenNotPrimary() {
        render(
            state = ExerciseEditorUiState(
                draft = ExerciseDraft(
                    name = "Nyakhajlítás",
                    primaryMuscle = MuscleGroup.CHEST
                )
            )
        )
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_ADD_SECONDARY).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Nyak").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saveShowsNameErrorAndStaysOnScreen() {
        var saved = 0
        render(
            state = ExerciseEditorUiState(
                fieldErrors = listOf(ExerciseFieldError.NameBlank)
            ),
            onSave = { saved += 1 }
        )
        composeRule.onNodeWithText("Add meg a gyakorlat nevét.").assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).performClick()
        assertEquals(1, saved)
        composeRule.onNodeWithText("Új gyakorlat").assertIsDisplayed()
    }

    @Test
    fun editorFitsNarrowPhoneAtLargeFontScale() {
        render(width = 360.dp, fontScale = 1.3f)
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsDisplayed()
        composeRule.onNodeWithText("Alapadatok").assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).assertIsDisplayed()
        val save = composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).getBoundsInRoot()
        assertTrue(save.bottom - save.top >= 48.dp)
        assertTrue(save.right - save.left >= 48.dp)
        val name = composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).getBoundsInRoot()
        assertTrue("name field should not overflow 360dp", name.right <= 360.dp + 8.dp)
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PRIMARY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NOTES).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Vissza").assertIsDisplayed()
    }

    @Test
    fun createFlowShowsSharedEditorFields() {
        render(state = ExerciseEditorUiState(isEditing = false))
        composeRule.onNodeWithText("Új gyakorlat").assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).assertIsDisplayed()
    }

    @Test
    fun editFlowShowsTheSameSharedEditorFields() {
        render(state = ExerciseEditorUiState(isEditing = true, draft = ExerciseDraft(name = "Tolódzkodás")))
        composeRule.onNodeWithText("Gyakorlat szerkesztése").assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Tolódzkodás"))
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).assertIsDisplayed()
    }

    private fun comesBefore(first: DpRect, second: DpRect): Boolean {
        return first.top < second.top - 1.dp ||
            (kotlin.math.abs(first.top.value - second.top.value) <= 1f && first.left < second.left)
    }

    private fun render(
        state: ExerciseEditorUiState = ExerciseEditorUiState(),
        width: Dp = 360.dp,
        fontScale: Float = 1f,
        onSave: () -> Unit = {},
        onNameChange: (String) -> Unit = {},
        onToggleSecondary: (MuscleGroup) -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(width)
                            .fillMaxSize()
                    ) {
                        ExerciseEditorScreen(
                            state = state,
                            onBack = {},
                            onNameChange = onNameChange,
                            onCategoryChange = {},
                            onMovementChange = {},
                            onMeasurementChange = {},
                            onResistanceChange = {},
                            onWeightInterpretationChange = {},
                            onPrimaryMuscleChange = {},
                            onToggleSecondary = onToggleSecondary,
                            onNotesChange = {},
                            onSave = onSave,
                            onDismissDiscard = {},
                            onConfirmDiscard = {},
                            onFinished = { _, _ -> }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
