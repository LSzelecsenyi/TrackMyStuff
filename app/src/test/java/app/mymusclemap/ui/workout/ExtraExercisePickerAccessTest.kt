package app.mymusclemap.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FreeAccessDecision
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeAccessState
import app.mymusclemap.domain.entitlement.FreeAccessSubject
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class ExtraExercisePickerAccessTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val starter = exercise(1, "Starter Move", custom = false, muscle = MuscleGroup.QUADRICEPS)
    private val open = exercise(2, "Mine Press", custom = true, muscle = MuscleGroup.CHEST)
    private val locked = exercise(3, "Secret Lift", custom = true, muscle = MuscleGroup.CHEST)
    private val catalog = listOf(starter, open, locked)
    private val subjects = listOf(FreeAccessSubject(2, 2), FreeAccessSubject(3, 3))

    @Test
    fun freeHidesLockedExercisesFromSearchAndMuscleFilters() {
        val decision = confirmed(setOf(2L))
        var selected: Long? = null
        render(decision) { selected = it }
        composeRule.onNodeWithTag(extraExercisePickerRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(3)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.timed_set_explore_pro)).assertDoesNotExist()

        composeRule.onNode(hasSetTextAction()).performTextInput("Secret Lift")
        composeRule.onNodeWithTag(EXTRA_EXERCISE_NO_RESULTS).assertIsDisplayed()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(3)).assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("sec")
        composeRule.onNodeWithTag(extraExercisePickerRowTag(3)).assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(2)).assertIsDisplayed()

        composeRule.onNodeWithText(testString(R.string.exercise_filter_all_muscles)).performClick()
        composeRule.onNodeWithText(testString(R.string.muscle_chest)).performClick()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(3)).assertDoesNotExist()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(1)).assertDoesNotExist()

        composeRule.onNodeWithTag(extraExercisePickerRowTag(2)).performClick()
        assertEquals(2L, selected)
    }

    @Test
    fun changingAccessUpdatesThePickerAndALockedRowCannotBeChosen() {
        var access by mutableStateOf(confirmed(setOf(2L, 3L)))
        var selected by mutableIntStateOf(0)
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                    ExtraExercisePickerSheet(
                        exercises = catalog,
                        onDismiss = {},
                        onSelect = { selected += 1 },
                        access = access
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Secret Lift").assertIsDisplayed()
        access = confirmed(setOf(2L))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Secret Lift").assertDoesNotExist()
        composeRule.onNodeWithText("Mine Press").assertIsDisplayed()
        assertEquals(0, selected)
    }

    @Test
    fun noAddableExercisesShowTheEmptyStateWithoutLockedNames() {
        render(confirmed(emptySet()), exercises = listOf(locked))
        composeRule.onNodeWithTag(EXTRA_EXERCISE_NONE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.extra_exercise_picker_none)).assertIsDisplayed()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(3)).assertDoesNotExist()
        composeRule.onNodeWithTag(EXTRA_EXERCISE_NO_RESULTS).assertDoesNotExist()
    }

    @Test
    fun aSearchWithNoAccessibleMatchKeepsTheNoResultsState() {
        render(confirmed(setOf(2L)), exercises = listOf(open, locked))
        composeRule.onNode(hasSetTextAction()).performTextInput("zzzz")
        composeRule.onNodeWithTag(EXTRA_EXERCISE_NO_RESULTS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.extra_exercise_picker_empty)).assertIsDisplayed()
        composeRule.onNodeWithTag(EXTRA_EXERCISE_NONE).assertDoesNotExist()
        composeRule.onNodeWithTag(extraExercisePickerRowTag(3)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "hu-w360dp-h1200dp")
    fun hungarianEmptyStateIsShown() {
        render(confirmed(emptySet()), exercises = listOf(locked))
        composeRule.onNodeWithText("Nincs hozzáadható gyakorlat.").assertIsDisplayed()
    }

    private fun render(
        access: FreeAccessDecision,
        exercises: List<Exercise> = catalog,
        onSelect: (Long) -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                    ExtraExercisePickerSheet(
                        exercises = exercises,
                        onDismiss = {},
                        onSelect = { onSelect(it.id) },
                        access = access
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun confirmed(selected: Set<Long>): FreeAccessDecision {
        return FreeAccessLogic.decide(
            state = FreeAccessState(
                periodId = 2L,
                lastKnownPro = false,
                phase = FreeAccessPhase.Confirmed,
                selectedCustomExerciseIds = selected
            ),
            grantsPro = false,
            plans = emptyList(),
            customExercises = subjects
        )
    }

    private fun exercise(
        id: Long,
        name: String,
        custom: Boolean,
        muscle: MuscleGroup
    ): Exercise {
        return Exercise(
            id = id,
            name = name,
            normalizedName = name.lowercase(),
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.SQUAT,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = muscle,
            secondaryMuscles = emptyList(),
            notes = null,
            archived = false,
            createdAt = id,
            updatedAt = id,
            custom = custom
        )
    }
}
