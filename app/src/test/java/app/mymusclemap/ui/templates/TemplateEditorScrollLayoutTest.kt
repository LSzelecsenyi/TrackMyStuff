package app.mymusclemap.ui.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class TemplateEditorScrollLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveStaysReachableAfterAddedExerciseAtFontScale13() {
        val exercise = pullUp()
        val item = TemplateExerciseDraft(
            localId = -7L,
            exerciseId = exercise.id,
            sets = listOf(PlannedSetDraft(localId = -8L, minRepsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        )
        val state = TemplateEditorUiState(
            draft = TemplateDraft(name = "Push A", exercises = listOf(item)),
            catalog = mapOf(exercise.id to exercise)
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.3f)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxSize()
                    ) {
                        TemplateEditorScreen(
                            state = state,
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
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(templateExerciseKey(-7L)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("template-save").assertIsDisplayed()
    }

    private fun pullUp(): Exercise {
        return Exercise(
            id = 11L,
            name = "Húzódzkodás",
            normalizedName = "huzodzkodas",
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
