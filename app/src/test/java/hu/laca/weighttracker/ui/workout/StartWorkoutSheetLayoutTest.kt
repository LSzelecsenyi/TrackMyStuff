package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.domain.workout.BodyWeightProposal
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.domain.workout.TemplateMuscleSummary
import hu.laca.weighttracker.domain.workout.WorkoutTemplate
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class StartWorkoutSheetLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenStartInProgressWhenConfirmIsTappedAgainThenCallbackIsNotFired() {
        var confirms = 0
        render(starting = true, onConfirm = { confirms += 1 })
        composeRule.onNodeWithTag(HUB_START_CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(HUB_START_CONFIRM).performClick()
        assertEquals(0, confirms)
    }

    @Test
    fun givenSameDayWeightWhenSheetOpensThenValueIsPrefilled() {
        render()
        composeRule.onNodeWithTag(HUB_START_WEIGHT).assertIsDisplayed()
        composeRule.onNodeWithText("82,4").assertIsDisplayed()
        composeRule.onNodeWithText("A mai mérésből: 82,4 kg").assertIsDisplayed()
        composeRule.onNodeWithText("3 gyakorlat · 8 tervezett sorozat").assertIsDisplayed()
    }

    @Test
    fun givenUserCancelsStartWhenSheetClosesThenDismissIsInvokedWithoutConfirm() {
        var dismissed = 0
        var confirmed = 0
        render(
            onDismiss = { dismissed += 1 },
            onConfirm = { confirmed += 1 }
        )
        composeRule.onNodeWithTag(HUB_START_CANCEL).performClick()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
    }

    private fun render(
        starting: Boolean = false,
        onDismiss: () -> Unit = {},
        onConfirm: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                    StartWorkoutSheet(
                        draft = startDraft(),
                        starting = starting,
                        onDismiss = onDismiss,
                        onWeightChange = {},
                        onConfirm = onConfirm
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun startDraft(): StartWorkoutDraft {
        return StartWorkoutDraft(
            template = TemplateListItem(
                template = WorkoutTemplate(
                    id = 4L,
                    name = "Push – Kondipark",
                    normalizedName = "push – kondipark",
                    notes = null,
                    archived = false,
                    createdAt = 1L,
                    updatedAt = 1L
                ),
                exerciseCount = 3,
                setCount = 8,
                primaryMuscles = emptyList(),
                muscleSummary = TemplateMuscleSummary(emptyList(), emptyList())
            ),
            proposal = BodyWeightProposal(
                kilograms = 82.4,
                source = BodyWeightSource.MEASURED_SAME_DAY,
                sourceDate = LocalDate.of(2026, 9, 15)
            ),
            weightText = "82,4",
            editedManually = false
        )
    }
}
