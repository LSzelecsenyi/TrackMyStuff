package app.mymusclemap.ui.entitlement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FreeAccessIntro
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.testString
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class FreeAccessSelectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downgradeIntroStatesTheFreeLimitsAndThatTheChoiceIsFinal() {
        show(sample(intro = FreeAccessIntro.ProEnded))
        composeRule.onNodeWithText(testString(R.string.free_access_ended_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_ended_body, 3, 5)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_keep_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_final_note)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_choose_title)).assertDoesNotExist()
        composeRule.onNodeWithTag(FREE_ACCESS_PLANS_HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(FREE_ACCESS_EXERCISES_HEADER).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_plans_hint, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_exercises_hint, 5)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_ratio, 0, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_ratio, 1, 5)).assertIsDisplayed()
    }

    @Test
    fun aFreePeriodWithoutADowngradeDoesNotSayThatProEnded() {
        show(sample(intro = FreeAccessIntro.WithinFreeLimits))
        composeRule.onNodeWithText(testString(R.string.free_access_choose_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_limit_body, 3, 5)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_ended_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.free_access_ended_body, 3, 5)).assertDoesNotExist()
    }

    @Test
    fun countsFollowTheDraftAndMuscleLabelsStayHumanReadable() {
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                var state by remember { mutableStateOf(sample(intro = FreeAccessIntro.ProEnded)) }
                FreeAccessSelectionScreen(
                    state = state,
                    onBack = {},
                    onTogglePlan = { id ->
                        state = state.copy(
                            draftPlanIds = if (id in state.draftPlanIds) {
                                state.draftPlanIds - id
                            } else {
                                state.draftPlanIds + id
                            }
                        )
                    },
                    onToggleExercise = { id ->
                        state = state.copy(
                            draftExerciseIds = if (id in state.draftExerciseIds) {
                                state.draftExerciseIds - id
                            } else {
                                state.draftExerciseIds + id
                            }
                        )
                    },
                    onRequestConfirm = {},
                    onDismissConfirm = {},
                    onConfirm = {},
                    onExplorePro = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(MuscleGroup.FRONT_DELTOID.labelRes())).assertIsDisplayed()
        composeRule.onNodeWithText(MuscleGroup.FRONT_DELTOID.name).assertDoesNotExist()
        composeRule.onNodeWithText("Bench, Press").assertIsDisplayed()
        composeRule.onNodeWithTag("free-access-plan-1").performClick()
        composeRule.onNodeWithText(testString(R.string.free_access_ratio, 1, 3)).assertIsDisplayed()
        composeRule.onNodeWithTag("free-access-plan-1").assertIsOn()
        composeRule.onNodeWithTag(FREE_ACCESS_LIST).performScrollToNode(hasTestTag("free-access-exercise-8"))
        composeRule.onNodeWithTag("free-access-exercise-8").performClick()
        composeRule.onNodeWithText(testString(R.string.free_access_ratio, 0, 5)).assertIsDisplayed()
    }

    @Test
    fun stickyHeaderReplacesThePreviousSectionAndTheLastItemStaysAboveTheActions() {
        val plans = (1..8).map { index ->
            FreeAccessChoiceItem(
                id = index.toLong(),
                title = "Plan $index with a long name that should wrap onto another line",
                summary = "Bench press, incline press, fly, pushdown, and a longer summary"
            )
        }
        val exercises = (1..8).map { index ->
            FreeAccessChoiceItem(
                id = index.toLong(),
                title = "Exercise $index",
                summary = "",
                primaryMuscle = MuscleGroup.UPPER_BACK
            )
        }
        show(
            sample(intro = FreeAccessIntro.ProEnded).copy(
                plans = plans,
                exercises = exercises,
                draftExerciseIds = emptySet()
            )
        )
        composeRule.onNodeWithText(testString(R.string.free_access_final_note)).assertIsDisplayed()
        composeRule.onNodeWithTag(FREE_ACCESS_CONFIRM).assertIsDisplayed()
        composeRule.onNodeWithTag(FREE_ACCESS_LIST).performScrollToNode(hasTestTag("free-access-exercise-8"))
        composeRule.onAllNodesWithText(testString(R.string.free_access_final_note)).assertCountEquals(0)
        composeRule.onNodeWithText(testString(R.string.free_access_bar_title)).assertIsDisplayed()
        val barBottom = composeRule.onNodeWithTag(FREE_ACCESS_BAR).getBoundsInRoot().bottom
        val header = composeRule.onNodeWithTag(FREE_ACCESS_EXERCISES_HEADER).getBoundsInRoot()
        assertTrue(
            "Exercises header top ${header.top} should meet the app bar bottom $barBottom",
            header.top.value <= barBottom.value + 2f
        )
        composeRule.onAllNodesWithText(testString(R.string.free_access_plans)).assertCountEquals(0)
        val itemBottom = composeRule.onNodeWithTag("free-access-exercise-8").getBoundsInRoot().bottom
        val actionsTop = composeRule.onNodeWithTag(FREE_ACCESS_ACTIONS).getBoundsInRoot().top
        assertTrue(itemBottom.value <= actionsTop.value + 1f)
        composeRule.onNodeWithTag(FREE_ACCESS_CONFIRM).assertIsDisplayed()
        composeRule.onNodeWithTag(FREE_ACCESS_EXPLORE).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(MuscleGroup.UPPER_BACK.labelRes()))[0].assertIsDisplayed()
        composeRule.onNodeWithText(MuscleGroup.UPPER_BACK.name).assertDoesNotExist()
    }

    @Test
    fun primaryActionOpensTheDialogAndDoesNotCommitUntilTheDialogConfirms() {
        var requested = 0
        var confirmed = 0
        show(
            sample(intro = FreeAccessIntro.ProEnded).copy(confirmEnabled = true),
            onRequestConfirm = { requested += 1 },
            onConfirm = { confirmed += 1 }
        )
        composeRule.onNodeWithTag(FREE_ACCESS_CONFIRM).assertIsEnabled().performClick()
        assertEquals(1, requested)
        assertEquals(0, confirmed)
    }

    @Test
    fun confirmationDialogUsesTheFinalCopyAndDoesNotConfirmOnDismiss() {
        var confirmed = 0
        var dismissed = 0
        show(
            sample(intro = FreeAccessIntro.ProEnded).copy(
                draftPlanIds = setOf(1L),
                draftExerciseIds = setOf(8L),
                confirmEnabled = true,
                confirmVisible = true
            ),
            onDismissConfirm = { dismissed += 1 },
            onConfirm = { confirmed += 1 }
        )
        composeRule.onNodeWithText(testString(R.string.free_access_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_confirm_body)).assertIsDisplayed()
        composeRule.onNodeWithTag("free-access-dialog-cancel").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
        composeRule.onNodeWithTag("free-access-dialog-confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun confirmStaysDisabledUntilTheDraftFits() {
        show(sample(intro = FreeAccessIntro.ProEnded).copy(confirmEnabled = false))
        composeRule.onNodeWithTag(FREE_ACCESS_CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun exploringProKeepsTheDraftAndDoesNotConfirmOrResetIt() {
        var explored = 0
        var confirmed = 0
        var requested = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                var state by remember {
                    mutableStateOf(
                        sample(intro = FreeAccessIntro.ProEnded).copy(
                            draftPlanIds = setOf(1L),
                            confirmEnabled = true
                        )
                    )
                }
                FreeAccessSelectionScreen(
                    state = state,
                    onBack = {},
                    onTogglePlan = {},
                    onToggleExercise = {},
                    onRequestConfirm = { requested += 1 },
                    onDismissConfirm = {},
                    onConfirm = { confirmed += 1 },
                    onExplorePro = { explored += 1 }
                )
            }
        }
        composeRule.onNodeWithTag("free-access-plan-1").assertIsOn()
        composeRule.onNodeWithTag(FREE_ACCESS_EXPLORE).performClick()
        assertEquals(1, explored)
        assertEquals(0, confirmed)
        assertEquals(0, requested)
        composeRule.onNodeWithTag("free-access-plan-1").assertIsOn()
        composeRule.onNodeWithText(testString(R.string.free_access_ratio, 1, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_confirm_title)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 1.6f)
    fun largeTextKeepsTheLastExerciseAboveTheFixedActions() {
        val exercises = (1..6).map { index ->
            FreeAccessChoiceItem(index.toLong(), "Exercise $index", "", MuscleGroup.SIDE_DELTOID)
        }
        show(
            sample(intro = FreeAccessIntro.WithinFreeLimits).copy(
                plans = emptyList(),
                plansRequireChoice = false,
                exercises = exercises,
                draftExerciseIds = emptySet()
            )
        )
        composeRule.onNodeWithTag(FREE_ACCESS_LIST).performScrollToNode(hasTestTag("free-access-exercise-6"))
        val itemBottom = composeRule.onNodeWithTag("free-access-exercise-6").getBoundsInRoot().bottom
        val actionsTop = composeRule.onNodeWithTag(FREE_ACCESS_ACTIONS).getBoundsInRoot().top
        assertTrue(itemBottom.value <= actionsTop.value + 1f)
        composeRule.onNodeWithText(testString(R.string.free_access_confirm_button)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(MuscleGroup.SIDE_DELTOID.labelRes()))[0].assertIsDisplayed()
        composeRule.onNodeWithText(MuscleGroup.SIDE_DELTOID.name).assertDoesNotExist()
    }

    private fun show(
        state: FreeAccessSelectionUiState,
        onRequestConfirm: () -> Unit = {},
        onDismissConfirm: () -> Unit = {},
        onConfirm: () -> Unit = {},
        onExplorePro: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                FreeAccessSelectionScreen(
                    state = state,
                    onBack = {},
                    onTogglePlan = {},
                    onToggleExercise = {},
                    onRequestConfirm = onRequestConfirm,
                    onDismissConfirm = onDismissConfirm,
                    onConfirm = onConfirm,
                    onExplorePro = onExplorePro
                )
            }
        }
    }

    private fun sample(intro: FreeAccessIntro): FreeAccessSelectionUiState {
        return FreeAccessSelectionUiState(
            plans = listOf(FreeAccessChoiceItem(1L, "Push", "Bench, Press")),
            exercises = listOf(
                FreeAccessChoiceItem(8L, "Curl", "", MuscleGroup.FRONT_DELTOID)
            ),
            draftExerciseIds = setOf(8L),
            planLimit = 3,
            exerciseLimit = 5,
            plansRequireChoice = true,
            exercisesRequireChoice = true,
            confirmEnabled = false,
            intro = intro
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "hu-w360dp-h800dp")
class FreeAccessSelectionHungarianTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hungarianDowngradeCopyAndMuscleLabel() {
        composeRule.setContent {
            WeightTrackerThemeForPreview(seeds = ThemeSeeds.DefaultDark, darkTheme = true) {
                FreeAccessSelectionScreen(
                    state = FreeAccessSelectionUiState(
                        plans = listOf(FreeAccessChoiceItem(1L, "Tolódzkodás", "Fekvenyomás")),
                        exercises = listOf(
                            FreeAccessChoiceItem(8L, "Bicepsz", "", MuscleGroup.FRONT_DELTOID)
                        ),
                        planLimit = 3,
                        exerciseLimit = 5,
                        plansRequireChoice = true,
                        exercisesRequireChoice = true,
                        intro = FreeAccessIntro.ProEnded
                    ),
                    onBack = {},
                    onTogglePlan = {},
                    onToggleExercise = {},
                    onRequestConfirm = {},
                    onDismissConfirm = {},
                    onConfirm = {},
                    onExplorePro = {}
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.free_access_ended_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_ended_body, 3, 5)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_keep_body)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_final_note)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.free_access_confirm_button)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.timed_set_explore_pro)).assertIsDisplayed()
        composeRule.onNodeWithText("Elülső delta").assertIsDisplayed()
        composeRule.onNodeWithText(MuscleGroup.FRONT_DELTOID.name).assertDoesNotExist()
    }
}
