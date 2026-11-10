package app.mymusclemap.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.ui.exercises.EXERCISE_EDITOR_ROOT
import app.mymusclemap.ui.exercises.EXERCISE_EDITOR_SAVE
import app.mymusclemap.ui.exercises.ExerciseEditorScreen
import app.mymusclemap.ui.exercises.ExerciseEditorUiState
import app.mymusclemap.ui.founder.FOUNDER_ENROLL
import app.mymusclemap.ui.founder.FOUNDER_INVITATION
import app.mymusclemap.ui.founder.FounderInvitationScreen
import app.mymusclemap.ui.onboarding.ONBOARDING_PRIMARY
import app.mymusclemap.ui.onboarding.ONBOARDING_SCREEN
import app.mymusclemap.ui.onboarding.OnboardingScreen
import app.mymusclemap.ui.onboarding.OnboardingStep
import app.mymusclemap.ui.settings.AppearancePreferencesScreen
import app.mymusclemap.ui.settings.SettingsUiState
import app.mymusclemap.ui.templates.TemplateEditorPane
import app.mymusclemap.ui.templates.TemplateEditorScreen
import app.mymusclemap.ui.templates.TemplateEditorUiState
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class PhoneResponsiveLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingKeepsPhonePaddingAndPinsTheAction() {
        renderOnboarding(OnboardingStep.Welcome)
        val root = bounds(ONBOARDING_SCREEN)
        val button = bounds(ONBOARDING_PRIMARY)
        assertNear(button.right - button.left, root.width - AppDimens.screenPadding * 2)
        assertTrue(button.left >= AppDimens.screenPadding - 1.dp)
        assertTrue(root.bottom - button.bottom <= AppDimens.sectionGap + 8.dp)
        assertTrue(button.top > root.height / 2)
    }

    @Test
    fun unitChoicesKeepInternalPaddingOnPhones() {
        renderOnboarding(OnboardingStep.MeasurementUnits)
        assertChoicePadding("onboarding-units-metric", "Metric")
        assertChoicePadding("onboarding-units-imperial", "Imperial")
    }

    @Test
    fun bodyModelChoicesKeepInternalPaddingOnPhones() {
        renderOnboarding(OnboardingStep.BodyModel)
        assertChoicePadding("onboarding-body-model-male", "Male")
        assertChoicePadding("onboarding-body-model-female", "Female")
    }

    @Test
    fun settingsChoicesKeepInternalPaddingOnPhones() {
        composeRule.setContent {
            themed {
                AppearancePreferencesScreen(state = SettingsUiState(), onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-units-metric").performScrollTo()
        assertChoicePadding("settings-units-metric", "Metric")
        assertChoicePadding("settings-units-imperial", "Imperial")
        composeRule.onNodeWithTag("settings-body-model-male").performScrollTo()
        assertChoicePadding("settings-body-model-male", "Male")
        assertChoicePadding("settings-body-model-female", "Female")
    }

    private fun assertChoicePadding(tag: String, label: String) {
        val control = bounds(tag, unmerged = true)
        val text = composeRule.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("$label should clear the left border", text.left - control.left >= 16.dp)
        assertTrue("$label should clear the right border", control.right - text.right >= 16.dp)
        assertTrue("$label should clear the top border", text.top - control.top >= 8.dp)
        assertTrue("$label touch height ${control.height}", control.height >= AppDimens.minTouch - 1.dp)
    }

    private fun renderOnboarding(step: OnboardingStep) {
        composeRule.setContent {
            themed {
                OnboardingScreen(step = step, onContinue = {}, onCreatePlan = {}, onSkip = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String, unmerged: Boolean = false): DpRect {
        return composeRule.onNodeWithTag(tag, useUnmergedTree = unmerged).getUnclippedBoundsInRoot()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h960dp")
class SmallTabletPortraitLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingUsesTheAvailableWidthInsideTheMinimumPadding() {
        composeRule.setContent {
            themed {
                OnboardingScreen(
                    step = OnboardingStep.MeasurementUnits,
                    onContinue = {},
                    onCreatePlan = {},
                    onSkip = {}
                )
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onNodeWithTag(ONBOARDING_SCREEN).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithTag(ONBOARDING_PRIMARY).getUnclippedBoundsInRoot()
        val choice = composeRule.onNodeWithTag("onboarding-units-metric").getUnclippedBoundsInRoot()
        assertNear(button.right - button.left, root.width - AppDimens.screenPadding * 2)
        assertNear(choice.right - choice.left, button.right - button.left)
        assertCentered(button, root)
        assertTrue(button.left >= AppDimens.screenPadding)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h480dp")
class SmallTabletLandscapeLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shortLandscapeKeepsTheActionVisibleAndTheChoiceNarrow() {
        composeRule.setContent {
            themed {
                OnboardingScreen(
                    step = OnboardingStep.WeeklyGoal,
                    onContinue = {},
                    onCreatePlan = {},
                    onSkip = {},
                    onSetWeeklyGoal = {},
                    onSkipWeeklyGoal = {}
                )
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onNodeWithTag(ONBOARDING_SCREEN).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithTag(ONBOARDING_PRIMARY).getUnclippedBoundsInRoot()
        val skip = composeRule.onNodeWithText("Not now").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).assertIsDisplayed()
        composeRule.onNodeWithText("Not now").assertIsDisplayed()
        assertTrue(button.right - button.left <= AppDimens.readableMaxWidth + 1.dp)
        assertTrue(button.right <= root.right + 1.dp)
        assertTrue(skip.bottom <= root.bottom + 1.dp)
        assertTrue(root.bottom - skip.bottom <= 36.dp)
        assertTrue(button.bottom <= skip.top + 1.dp)
        assertCentered(button, root)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w840dp-h1280dp")
class MediumTabletPortraitLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun portraitTabletCentersOnboardingAndFounderActions() {
        composeRule.setContent {
            themed {
                OnboardingScreen(
                    step = OnboardingStep.CreatePlan,
                    onContinue = {},
                    onCreatePlan = {},
                    onSkip = {}
                )
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onNodeWithTag(ONBOARDING_SCREEN).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithTag(ONBOARDING_PRIMARY).getUnclippedBoundsInRoot()
        assertNear(button.right - button.left, AppDimens.readableMaxWidth)
        assertCentered(button, root)
        composeRule.onNodeWithText("Create workout plan").assertIsDisplayed()
        composeRule.onNodeWithText("Skip for now").assertIsDisplayed()
    }

    @Test
    fun founderJoinStaysWithinTheReadableWidth() {
        composeRule.setContent {
            themed {
                FounderInvitationScreen(
                    rules = FounderProgramRules.Production,
                    onJoin = {},
                    onNotNow = {}
                )
            }
        }
        composeRule.waitForIdle()
        val join = composeRule.onNodeWithTag(FOUNDER_ENROLL).performScrollTo().getUnclippedBoundsInRoot()
        val founderRoot = composeRule.onNodeWithTag(FOUNDER_INVITATION).getUnclippedBoundsInRoot()
        assertNear(join.right - join.left, AppDimens.readableMaxWidth)
        assertCentered(join, founderRoot)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class MediumTabletLandscapeLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun landscapeTabletConstrainsFormsWithoutConstrainingTheMuscleMap() {
        val exercise = pullUp()
        val item = TemplateExerciseDraft(
            localId = -7L,
            exerciseId = exercise.id,
            sets = listOf(
                PlannedSetDraft(localId = -8L, minRepsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
            )
        )
        composeRule.setContent {
            themed {
                TemplateEditorScreen(
                    state = TemplateEditorUiState(
                        draft = TemplateDraft(name = "Push A", exercises = listOf(item)),
                        catalog = mapOf(exercise.id to exercise)
                    ),
                    onBack = {},
                    onNameChange = {},
                    onNotesChange = {},
                    onOpenPicker = {},
                    onClosePicker = {},
                    onPickerQuery = {},
                    onPickerCategory = {},
                    onPickerMuscle = {},
                    onSelectExercise = {},
                    onConfirmDuplicate = {},
                    onDismissDuplicate = {},
                    onRemoveExercise = {},
                    onMoveExercise = { _, _ -> },
                    onToggleExpanded = {},
                    onSetCount = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = { _, _ -> },
                    onMoveSet = { _, _, _ -> },
                    onApplyRemaining = { _, _ -> },
                    onApplyAll = { _, _ -> },
                    onMinReps = { _, _, _ -> },
                    onMaxReps = { _, _, _ -> },
                    onLoadKind = { _, _, _ -> },
                    onWeight = { _, _, _ -> },
                    onMinutes = { _, _, _ -> },
                    onSeconds = { _, _, _ -> },
                    onDistance = { _, _, _ -> },
                    onDistanceUnit = { _, _, _ -> },
                    onSave = {},
                    onDismissDiscard = {},
                    onConfirmDiscard = {},
                    onFinished = { _, _ -> },
                    onScrollConsumed = {}
                )
            }
        }
        composeRule.waitForIdle()
        val save = composeRule.onNodeWithTag("template-save").getUnclippedBoundsInRoot()
        val map = composeRule.onNodeWithTag("muscle_map_MALE").performScrollTo().getUnclippedBoundsInRoot()
        assertNear(save.right - save.left, AppDimens.readableMaxWidth)
        assertTrue(
            "muscle map should keep the screen width",
            map.right - map.left > save.right - save.left + 40.dp
        )
    }

    @Test
    fun exercisePickerFieldsStayWithinTheReadableWidth() {
        val exercise = pullUp()
        composeRule.setContent {
            themed {
                TemplateEditorScreen(
                    state = TemplateEditorUiState(
                        pane = TemplateEditorPane.Picker,
                        catalog = mapOf(exercise.id to exercise),
                        pickerExercises = listOf(exercise)
                    ),
                    onBack = {},
                    onNameChange = {},
                    onNotesChange = {},
                    onOpenPicker = {},
                    onClosePicker = {},
                    onPickerQuery = {},
                    onPickerCategory = {},
                    onPickerMuscle = {},
                    onSelectExercise = {},
                    onConfirmDuplicate = {},
                    onDismissDuplicate = {},
                    onRemoveExercise = {},
                    onMoveExercise = { _, _ -> },
                    onToggleExpanded = {},
                    onSetCount = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = { _, _ -> },
                    onMoveSet = { _, _, _ -> },
                    onApplyRemaining = { _, _ -> },
                    onApplyAll = { _, _ -> },
                    onMinReps = { _, _, _ -> },
                    onMaxReps = { _, _, _ -> },
                    onLoadKind = { _, _, _ -> },
                    onWeight = { _, _, _ -> },
                    onMinutes = { _, _, _ -> },
                    onSeconds = { _, _, _ -> },
                    onDistance = { _, _, _ -> },
                    onDistanceUnit = { _, _, _ -> },
                    onSave = {},
                    onDismissDiscard = {},
                    onConfirmDiscard = {},
                    onFinished = { _, _ -> },
                    onScrollConsumed = {}
                )
            }
        }
        composeRule.waitForIdle()
        val field = composeRule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        assertTrue(field.right - field.left <= AppDimens.readableMaxWidth + 1.dp)
        assertTrue(field.right - field.left >= 480.dp)
    }

    @Test
    fun exerciseEditorSaveStaysWithinTheReadableWidth() {
        composeRule.setContent {
            themed {
                ExerciseEditorScreen(
                    state = ExerciseEditorUiState(),
                    onBack = {},
                    onNameChange = {},
                    onCategoryChange = {},
                    onMovementChange = {},
                    onMeasurementChange = {},
                    onResistanceChange = {},
                    onWeightInterpretationChange = {},
                    onPrimaryMuscleChange = {},
                    onToggleSecondary = {},
                    onNotesChange = {},
                    onSave = {},
                    onDismissDiscard = {},
                    onConfirmDiscard = {},
                    onFinished = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onNodeWithTag(EXERCISE_EDITOR_ROOT).getUnclippedBoundsInRoot()
        val save = composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).getUnclippedBoundsInRoot()
        assertTrue(save.right - save.left <= AppDimens.readableMaxWidth + AppDimens.screenPadding * 2 + 2.dp)
        assertTrue(root.width - (save.right - save.left) >= 200.dp)
        assertCentered(save, root)
        composeRule.onNodeWithTag(EXERCISE_EDITOR_SAVE).assertIsDisplayed()
    }

    private fun pullUp(): Exercise {
        return Exercise(
            id = 11L,
            name = "Pull-up",
            normalizedName = "pull-up",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.VERTICAL_PULL,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.LATS,
            secondaryMuscles = listOf(MuscleGroup.BICEPS),
            notes = null,
            archived = false,
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}

@Composable
private fun themed(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 1f)) {
        WeightTrackerThemeForPreview {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

private fun assertNear(actual: Dp, expected: Dp) {
    assertTrue("expected $expected but was $actual", abs(actual.value - expected.value) <= 2f)
}

private fun assertCentered(child: DpRect, root: DpRect) {
    val childCenter = (child.left.value + child.right.value) / 2f
    val rootCenter = (root.left.value + root.right.value) / 2f
    assertTrue("child center $childCenter root $rootCenter", abs(childCenter - rootCenter) <= 2f)
}

private val DpRect.width: Dp
    get() = right - left

private val DpRect.height: Dp
    get() = bottom - top
