package app.mymusclemap.ui.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
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
import app.mymusclemap.testString
import app.mymusclemap.ui.pro.PRO_INFO_SHEET
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class TemplateEditorTimedPreviewLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freeUserCanOpenTimedPreviewWithoutAPaywallOrProBadge() {
        val opens = AtomicInteger(0)
        val modeChanges = AtomicInteger(0)
        val displayed = mutableStateOf(editorState(timersEnabled = false))
        render(displayed, onTimedMode = { _, timed ->
            modeChanges.incrementAndGet()
            displayed.value = displayed.value.copy(
                timedTabPreview = mapOf(EXERCISE_ID to timed)
            )
        }, onExploreProBenefits = { opens.incrementAndGet() })
        composeRule.onNodeWithTag(WORK_MODE_REPS).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag(WORK_MODE_TIMED).assertIsDisplayed().assertIsNotSelected()
        assertFalse(testString(R.string.work_mode_timed).contains("PRO"))
        composeRule.onNodeWithText(testString(R.string.work_mode_timed)).assertIsDisplayed()
        composeRule.onNodeWithTag(WORK_MODE_TIMED).performClick()
        assertEquals(1, modeChanges.get())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WORK_MODE_TIMED).assertIsSelected()
        composeRule.onAllNodesWithTag(PRO_INFO_SHEET).assertCountEquals(0)
        composeRule.onNodeWithTag(TIMED_EDITOR_PRO_NOTE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.timed_editor_pro_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.timed_editor_pro_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(TIMED_MINUTES_FIELD).assertTextContains("0").assertIsNotEnabled()
        composeRule.onNodeWithTag(TIMED_SECONDS_FIELD).assertTextContains("30").assertIsNotEnabled()
        composeRule.onAllNodesWithTag("reps-min").assertCountEquals(0)
        composeRule.onNodeWithTag(TIMED_EDITOR_EXPLORE_PRO).performClick()
        assertEquals(1, opens.get())
        assertEquals(1, modeChanges.get())
        assertEquals("8", displayed.value.draft.exercises.single().sets.single().minRepsText)
        assertFalse(displayed.value.draft.exercises.single().sets.single().timedWork)
        composeRule.onNodeWithTag(WORK_MODE_REPS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WORK_MODE_REPS).assertIsSelected()
        composeRule.onNodeWithTag("reps-min").assertTextContains("8").assertIsEnabled()
        composeRule.onAllNodesWithTag(TIMED_EDITOR_PRO_NOTE).assertCountEquals(0)
    }

    @Test
    fun savedTimedSetStaysReadableAndLockedWithoutPro() {
        render(
            mutableStateOf(
                editorState(
                    timersEnabled = false,
                    minutes = "1",
                    seconds = "15",
                    timedWork = true
                )
            )
        )
        composeRule.onNodeWithTag(WORK_MODE_TIMED).assertIsSelected()
        composeRule.onNodeWithTag(TIMED_MINUTES_FIELD).assertTextContains("1").assertIsNotEnabled()
        composeRule.onNodeWithTag(TIMED_SECONDS_FIELD).assertTextContains("15").assertIsNotEnabled()
        composeRule.onNodeWithTag(TIMED_EDITOR_PRO_NOTE).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PRO_INFO_SHEET).assertCountEquals(0)
    }

    @Test
    fun proTimedEditingHasNoDisclaimerAndRegainingAccessUnlocksThePreview() {
        val displayed = mutableStateOf(
            editorState(timersEnabled = true, minutes = "1", seconds = "15", timedWork = true)
        )
        render(displayed)
        composeRule.onNodeWithTag(TIMED_MINUTES_FIELD).assertIsEnabled()
        composeRule.onAllNodesWithTag(TIMED_EDITOR_PRO_NOTE).assertCountEquals(0)
        displayed.value = editorState(timersEnabled = false, timedTabPreview = mapOf(EXERCISE_ID to true))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TIMED_SECONDS_FIELD).assertTextContains("30").assertIsNotEnabled()
        displayed.value = displayed.value.copy(timersEnabled = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WORK_MODE_TIMED).assertIsSelected()
        composeRule.onNodeWithTag(TIMED_MINUTES_FIELD).assertIsEnabled()
        composeRule.onAllNodesWithTag(TIMED_EDITOR_PRO_NOTE).assertCountEquals(0)
    }

    private fun render(
        state: MutableState<TemplateEditorUiState>,
        onTimedMode: (Long, Boolean) -> Unit = { _, _ -> },
        onExploreProBenefits: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                    Editor(state.value, onTimedMode, onExploreProBenefits)
                }
            }
        }
    }

    @Composable
    private fun Editor(
        state: TemplateEditorUiState,
        onTimedMode: (Long, Boolean) -> Unit,
        onExploreProBenefits: () -> Unit
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
            onTimedMode = onTimedMode,
            onSave = {},
            onDismissDiscard = {},
            onConfirmDiscard = {},
            onFinished = { _, _ -> },
            onScrollConsumed = {},
            onExploreProBenefits = onExploreProBenefits
        )
    }

    private fun editorState(
        timersEnabled: Boolean,
        minutes: String = "0",
        seconds: String = "0",
        timedWork: Boolean = false,
        timedTabPreview: Map<Long, Boolean> = emptyMap()
    ): TemplateEditorUiState {
        val exercise = pullUp()
        val set = PlannedSetDraft(
            localId = -8L,
            minRepsText = "8",
            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            minutesText = minutes,
            secondsText = seconds,
            timedWork = timedWork
        )
        return TemplateEditorUiState(
            draft = TemplateDraft(
                name = "Push A",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = EXERCISE_ID,
                        exerciseId = exercise.id,
                        sets = listOf(set)
                    )
                )
            ),
            catalog = mapOf(exercise.id to exercise),
            timersEnabled = timersEnabled,
            timedTabPreview = timedTabPreview
        )
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

    private companion object {
        const val EXERCISE_ID = -7L
    }
}
