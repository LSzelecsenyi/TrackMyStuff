package app.mymusclemap.ui.exercises

import app.mymusclemap.R
import app.mymusclemap.testString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseDraftLogic
import app.mymusclemap.domain.exercise.ExerciseFieldError
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.locale.LocalizedLabelOrder
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
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
                ExerciseCategory.CARDIO,
                ExerciseCategory.STATIC_HOLD,
                ExerciseCategory.MOBILITY,
                ExerciseCategory.SKILL,
                ExerciseCategory.STRENGTH
            ),
            categories
        )
        assertTrue(categories != ExerciseCategory.entries)
        val patterns = LocalizedLabelOrder.sorted(
            MovementPattern.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        assertEquals(
            resources.getString(R.string.exercise_pattern_cardio),
            resources.getString(patterns.first().labelRes())
        )
        val measurements = LocalizedLabelOrder.sorted(
            MeasurementType.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        assertEquals(
            resources.getString(R.string.exercise_measure_completion),
            resources.getString(measurements.first().labelRes())
        )
        render()
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(testString(R.string.exercise_category_skill)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.exercise_category_static)).assertIsDisplayed()
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
        composeRule.onAllNodesWithText(testString(R.string.exercise_category_strength)).assertCountEquals(2)
    }

    @Test
    fun selectedSecondaryMusclesAppearInEnglishAbcOrder() {
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
        assertTrue(comesBefore(biceps, forearms))
        assertTrue(comesBefore(forearms, triceps))
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
        composeRule.onAllNodesWithText(testString(R.string.muscle_lats)).assertCountEquals(1)
        composeRule.onAllNodesWithText(testString(R.string.muscle_biceps)).assertCountEquals(1)
        composeRule.onNodeWithText(testString(R.string.muscle_forearms)).assertIsDisplayed()
    }

    @Test
    fun neckIsOfferedAsPrimaryAndSecondaryInEnglishAbcOrder() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val muscles = LocalizedLabelOrder.sorted(
            MuscleGroup.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        val labels = muscles.map { resources.getString(it.labelRes()) }
        assertTrue(MuscleGroup.NECK in MuscleGroup.entries)
        assertEquals(testString(R.string.muscle_neck), resources.getString(MuscleGroup.NECK.labelRes()))
        assertEquals(labels.indexOf(testString(R.string.muscle_lower_back)) + 1, labels.indexOf(testString(R.string.muscle_neck)))
        assertEquals(labels.indexOf(testString(R.string.muscle_neck)) + 1, labels.indexOf(testString(R.string.muscle_obliques)))
        render()
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PRIMARY).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(testString(R.string.muscle_neck)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.muscle_chest)).assertCountEquals(2)
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
        composeRule.onNodeWithText(testString(R.string.muscle_neck)).performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithText(testString(R.string.error_exercise_name_blank)).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).performClick()
        assertEquals(1, saved)
        composeRule.onNodeWithText(testString(R.string.exercise_editor_add)).assertIsDisplayed()
    }

    @Test
    fun editorFitsNarrowPhoneAtLargeFontScale() {
        render(width = 360.dp, fontScale = 1.3f)
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.exercise_section_basics)).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).assertIsDisplayed()
        val save = composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).getBoundsInRoot()
        assertTrue(save.bottom - save.top >= 48.dp)
        assertTrue(save.right - save.left >= 48.dp)
        val name = composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).getBoundsInRoot()
        assertTrue("name field should not overflow 360dp", name.right <= 360.dp + 8.dp)
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PRIMARY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NOTES).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).assertIsDisplayed()
    }

    @Test
    fun createFlowShowsSharedEditorFields() {
        render(state = ExerciseEditorUiState(isEditing = false))
        composeRule.onNodeWithText(testString(R.string.exercise_editor_add)).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).assertIsDisplayed()
    }

    @Test
    fun editFlowShowsTheSameSharedEditorFields() {
        render(state = ExerciseEditorUiState(isEditing = true, draft = ExerciseDraft(name = "Tolódzkodás")))
        composeRule.onNodeWithText(testString(R.string.exercise_editor_edit)).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Tolódzkodás"))
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).assertIsDisplayed()
    }

    @Test
    fun givenNameFocusedWhenCategoryDropdownOpensThenNameLosesFocusAndImeCloses() {
        renderInteractive()
        focusNameField()
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("$EXERCISE_DROPDOWN_CATEGORY-menu").assertIsDisplayed()
        assertTextFieldsDoNotHaveInputFocus()
    }

    @Test
    fun givenCategorySelectedThenValueUpdatesAndNameDoesNotRegainFocus() {
        renderInteractive()
        focusNameField()
        selectDropdown(EXERCISE_DROPDOWN_CATEGORY, testString(R.string.exercise_category_cardio))
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY)
            .assert(hasContentDescription(testString(R.string.exercise_field_category) + ": " + testString(R.string.exercise_category_cardio)))
        assertTextFieldsDoNotHaveInputFocus()
    }

    @Test
    fun givenOtherDropdownsSelectedThenNoneRefocusesTheNameField() {
        renderInteractive()
        focusNameField()
        selectDropdown(EXERCISE_DROPDOWN_WEIGHT, testString(R.string.exercise_weight_per_side))
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_WEIGHT)
            .assert(hasContentDescription(testString(R.string.exercise_field_weight) + ": " + testString(R.string.exercise_weight_per_side)))
        assertTextFieldsDoNotHaveInputFocus()

        selectDropdown(EXERCISE_DROPDOWN_PATTERN, testString(R.string.exercise_pattern_squat))
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PATTERN)
            .assert(hasContentDescription(testString(R.string.exercise_field_pattern) + ": " + testString(R.string.exercise_pattern_squat)))
        assertTextFieldsDoNotHaveInputFocus()

        selectDropdown(EXERCISE_DROPDOWN_MEASUREMENT, testString(R.string.exercise_measure_duration))
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_MEASUREMENT)
            .assert(hasContentDescription(testString(R.string.exercise_field_measurement) + ": " + testString(R.string.exercise_measure_duration)))
        assertTextFieldsDoNotHaveInputFocus()

        selectDropdown(EXERCISE_DROPDOWN_RESISTANCE, testString(R.string.exercise_resistance_bodyweight))
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_RESISTANCE)
            .assert(hasContentDescription(testString(R.string.exercise_field_resistance) + ": " + testString(R.string.exercise_resistance_bodyweight)))
        assertTextFieldsDoNotHaveInputFocus()

        selectDropdown(EXERCISE_DROPDOWN_PRIMARY, testString(R.string.muscle_neck))
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PRIMARY)
            .assert(hasContentDescription(testString(R.string.exercise_field_primary_muscle) + ": " + testString(R.string.muscle_neck)))
        assertTextFieldsDoNotHaveInputFocus()

        selectDropdown(EXERCISE_DROPDOWN_ADD_SECONDARY, testString(R.string.muscle_biceps))
        composeRule.onNodeWithTag(secondaryChipTag(MuscleGroup.BICEPS)).assertIsDisplayed()
        assertTextFieldsDoNotHaveInputFocus()
    }

    @Test
    fun givenDropdownRevealsConditionalFieldThenNoTextFieldReceivesAutofocus() {
        renderInteractive(
            initial = ExerciseEditorUiState(
                draft = ExerciseDraft(
                    measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                    resistanceBasis = ResistanceBasis.BODYWEIGHT
                )
            )
        )
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_WEIGHT).assertDoesNotExist()
        assertTextFieldsDoNotHaveInputFocus()
        selectDropdown(EXERCISE_DROPDOWN_RESISTANCE, testString(R.string.exercise_resistance_external))
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_WEIGHT).assertIsDisplayed()
        assertTextFieldsDoNotHaveInputFocus()
    }

    @Test
    fun givenNameTextAndCursorWhenDropdownSelectedThenTextAndCursorStayUnchanged() {
        renderInteractive()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performTextInput("abx")
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Abx"))
        val cursorBefore = nameSelection()
        selectDropdown(EXERCISE_DROPDOWN_CATEGORY, testString(R.string.exercise_category_cardio))
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Abx"))
        val cursorAfter = nameSelection()
        if (cursorBefore != null) {
            assertEquals(cursorBefore, cursorAfter)
        }
        assertTextFieldsDoNotHaveInputFocus()
    }

    @Test
    fun givenDropdownSelectionThenScrollDoesNotJumpToTheNameField() {
        renderInteractive(height = 560.dp)
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PRIMARY).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsNotDisplayed()
        val before = composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PRIMARY).getBoundsInRoot()
        selectDropdown(EXERCISE_DROPDOWN_PRIMARY, testString(R.string.muscle_neck))
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsNotDisplayed()
        val after = composeRule.onNodeWithTag(EXERCISE_DROPDOWN_PRIMARY).getBoundsInRoot()
        assertTrue(
            "primary dropdown should stay in place after selection: before=$before after=$after",
            kotlin.math.abs(before.top.value - after.top.value) <= 8f
        )
        assertTextFieldsDoNotHaveInputFocus()
    }

    @Test
    fun givenLaterManualTapOnNameThenFieldIsEditableAndKeyboardCanOpen() {
        renderInteractive()
        selectDropdown(EXERCISE_DROPDOWN_CATEGORY, testString(R.string.exercise_category_cardio))
        assertTextFieldsDoNotHaveInputFocus()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performClick()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsFocused()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performTextInput("húzó")
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Húzó"))
    }

    @Test
    fun givenSuccessiveDropdownSelectionsThenImeDoesNotReopenByItself() {
        renderInteractive()
        focusNameField()
        selectDropdown(EXERCISE_DROPDOWN_CATEGORY, testString(R.string.exercise_category_mobility))
        assertTextFieldsDoNotHaveInputFocus()
        selectDropdown(EXERCISE_DROPDOWN_PATTERN, testString(R.string.exercise_pattern_isolation))
        assertTextFieldsDoNotHaveInputFocus()
        selectDropdown(EXERCISE_DROPDOWN_MEASUREMENT, testString(R.string.exercise_measure_reps))
        assertTextFieldsDoNotHaveInputFocus()
        selectDropdown(EXERCISE_DROPDOWN_RESISTANCE, testString(R.string.exercise_resistance_none))
        assertTextFieldsDoNotHaveInputFocus()
        selectDropdown(EXERCISE_DROPDOWN_PRIMARY, testString(R.string.muscle_neck))
        assertTextFieldsDoNotHaveInputFocus()
        selectDropdown(EXERCISE_DROPDOWN_ADD_SECONDARY, testString(R.string.muscle_forearms))
        assertTextFieldsDoNotHaveInputFocus()
    }

    @Test
    fun givenEditingExistingExerciseThenDropdownsDoNotRefocusTheNameField() {
        renderInteractive(
            initial = ExerciseEditorUiState(
                isEditing = true,
                draft = ExerciseDraft(
                    id = 12L,
                    name = "Tolódzkodás",
                    category = ExerciseCategory.STRENGTH,
                    primaryMuscle = MuscleGroup.CHEST
                )
            )
        )
        composeRule.onNodeWithText(testString(R.string.exercise_editor_edit)).assertIsDisplayed()
        focusNameField()
        selectDropdown(EXERCISE_DROPDOWN_CATEGORY, testString(R.string.exercise_category_skill))
        composeRule.onNodeWithTag(EXERCISE_DROPDOWN_CATEGORY)
            .assert(hasContentDescription(testString(R.string.exercise_field_category) + ": " + testString(R.string.exercise_category_skill)))
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assert(hasText("Tolódzkodás"))
        assertTextFieldsDoNotHaveInputFocus()
        selectDropdown(EXERCISE_DROPDOWN_PATTERN, testString(R.string.exercise_pattern_vertical_pull))
        assertTextFieldsDoNotHaveInputFocus()
        selectDropdown(EXERCISE_DROPDOWN_WEIGHT, testString(R.string.exercise_weight_per_side))
        assertTextFieldsDoNotHaveInputFocus()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performClick()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsFocused()
    }

    @Test
    fun givenRecompositionThenNameIsNotAutofocusedAndHasNoRequestFocusLoop() {
        val state = renderInteractive()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsNotFocused()
        selectDropdown(EXERCISE_DROPDOWN_CATEGORY, testString(R.string.exercise_category_cardio))
        assertTextFieldsDoNotHaveInputFocus()
        state.value = state.value.copy(duplicateName = true)
        composeRule.waitForIdle()
        assertTextFieldsDoNotHaveInputFocus()
        state.value = state.value.copy(
            duplicateName = false,
            fieldErrors = listOf(ExerciseFieldError.NameTooLong)
        )
        composeRule.waitForIdle()
        assertTextFieldsDoNotHaveInputFocus()
        state.value = state.value.copy(fieldErrors = emptyList(), saving = true)
        composeRule.waitForIdle()
        assertTextFieldsDoNotHaveInputFocus()
        state.value = state.value.copy(saving = false)
        composeRule.waitForIdle()
        assertTextFieldsDoNotHaveInputFocus()
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

    private fun renderInteractive(
        initial: ExerciseEditorUiState = ExerciseEditorUiState(),
        width: Dp = 360.dp,
        height: Dp? = null,
        fontScale: Float = 1f
    ): MutableState<ExerciseEditorUiState> {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = if (height != null) {
                            Modifier.width(width).height(height)
                        } else {
                            Modifier.width(width).fillMaxSize()
                        }
                    ) {
                        ExerciseEditorScreen(
                            state = state.value,
                            onBack = {},
                            onNameChange = { name ->
                                state.value = state.value.withName(name)
                            },
                            onCategoryChange = { category ->
                                state.value = state.value.withCategory(category)
                            },
                            onMovementChange = { pattern ->
                                state.value = state.value.copy(
                                    draft = state.value.draft.copy(movementPattern = pattern)
                                )
                            },
                            onMeasurementChange = { measurement ->
                                state.value = state.value.copy(
                                    draft = ExerciseDraftLogic.applyMeasurement(state.value.draft, measurement)
                                )
                            },
                            onResistanceChange = { resistance ->
                                state.value = state.value.copy(
                                    draft = ExerciseDraftLogic.applyResistance(state.value.draft, resistance)
                                )
                            },
                            onWeightInterpretationChange = { weight ->
                                state.value = state.value.copy(
                                    draft = state.value.draft.copy(weightInterpretation = weight)
                                )
                            },
                            onPrimaryMuscleChange = { muscle ->
                                state.value = state.value.copy(
                                    draft = ExerciseDraftLogic.applyPrimaryMuscle(state.value.draft, muscle)
                                )
                            },
                            onToggleSecondary = { muscle ->
                                state.value = state.value.copy(
                                    draft = ExerciseDraftLogic.toggleSecondary(state.value.draft, muscle)
                                )
                            },
                            onNotesChange = { notes ->
                                state.value = state.value.copy(
                                    draft = state.value.draft.copy(notes = notes)
                                )
                            },
                            onSave = {},
                            onDismissDiscard = {},
                            onConfirmDiscard = {},
                            onFinished = { _, _ -> }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private fun ExerciseEditorUiState.withName(name: String): ExerciseEditorUiState {
        return copy(draft = draft.copy(name = ExerciseNaming.capitalizeFirstLetter(name)))
    }

    private fun ExerciseEditorUiState.withCategory(category: ExerciseCategory): ExerciseEditorUiState {
        val next = if (draft.id == null) {
            ExerciseDraftLogic.applyCategory(draft, category)
        } else {
            draft.copy(category = category)
        }
        return copy(draft = next)
    }

    private fun focusNameField() {
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).performClick()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsFocused()
    }

    private fun selectDropdown(tag: String, option: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("$tag-menu").assertIsDisplayed()
        composeRule.onNode(
            hasText(option) and hasAnyAncestor(hasTestTag("$tag-menu"))
        ).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun assertTextFieldsDoNotHaveInputFocus() {
        composeRule.onNodeWithTag(EXERCISE_FIELD_NAME).assertIsNotFocused()
        composeRule.onNodeWithTag(EXERCISE_FIELD_NOTES).assertIsNotFocused()
    }

    private fun nameSelection(): TextRange? {
        return composeRule.onNodeWithTag(EXERCISE_FIELD_NAME)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.TextSelectionRange)
    }
}
