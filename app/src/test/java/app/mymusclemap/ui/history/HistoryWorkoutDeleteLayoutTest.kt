package app.mymusclemap.ui.history

import app.mymusclemap.R
import app.mymusclemap.testString
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.journal.JournalDateGroup
import app.mymusclemap.domain.journal.JournalFilter
import app.mymusclemap.domain.journal.JournalTimeline
import app.mymusclemap.domain.journal.WorkoutJournalEntry
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.SessionProgress
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HistoryWorkoutDeleteLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun journalOverflowShowsDestructiveDeleteAndDialogCopy() {
        var confirmed = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                HistoryScreen(
                    state = HistoryUiState(
                        loading = false,
                        timeline = timeline(),
                        pendingWorkoutDelete = summary()
                    ),
                    today = LocalDate.parse("2026-09-16"),
                    onAdd = {},
                    onEdit = {},
                    onDelete = {},
                    onEditorDateChange = {},
                    onEditorWeightChange = {},
                    onSave = {},
                    onDismissEditor = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                    onMessageConsumed = {},
                    onOpenImport = {},
                    onFilterSelected = {},
                    onIncludeAbandoned = {},
                    onOpenWorkout = {},
                    onRequestDeleteWorkout = {},
                    onDismissDeleteWorkout = {},
                    onConfirmDeleteWorkout = { confirmed += 1 }
                )
            }
        }
        composeRule.onNodeWithTag(WORKOUT_DELETE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.delete_workout_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.delete_workout_body),
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_cancel)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.action_delete)).assertCountEquals(1)
        composeRule.onNodeWithTag(WORKOUT_DELETE_CONFIRM).performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun journalCardOverflowOpensDeleteAction() {
        var requested = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                HistoryScreen(
                    state = HistoryUiState(loading = false, timeline = timeline()),
                    today = LocalDate.parse("2026-09-16"),
                    onAdd = {},
                    onEdit = {},
                    onDelete = {},
                    onEditorDateChange = {},
                    onEditorWeightChange = {},
                    onSave = {},
                    onDismissEditor = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                    onMessageConsumed = {},
                    onOpenImport = {},
                    onFilterSelected = {},
                    onIncludeAbandoned = {},
                    onOpenWorkout = {},
                    onRequestDeleteWorkout = { requested += 1 },
                    onDismissDeleteWorkout = {},
                    onConfirmDeleteWorkout = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription(testString(R.string.workout_card_more_actions, "Push A")).performClick()
        composeRule.onNodeWithText(testString(R.string.action_delete_workout)).assertIsDisplayed()
        composeRule.onNodeWithTag(WORKOUT_DELETE_ACTION).performClick()
        assertEquals(1, requested)
    }

    private fun timeline(): JournalTimeline {
        val date = LocalDate.parse("2026-09-16")
        return JournalTimeline(
            groups = listOf(
                JournalDateGroup(
                    date = date,
                    entries = listOf(WorkoutJournalEntry(summary()))
                )
            ),
            filter = JournalFilter.ALL,
            includeAbandoned = false,
            emptyKind = null
        )
    }

    private fun summary(): WorkoutSessionSummary {
        return WorkoutSessionSummary(
            session = WorkoutSession(
                id = 7L,
                templateId = 1L,
                templateName = "Push A",
                status = SessionStatus.COMPLETED,
                workoutDate = LocalDate.parse("2026-09-16"),
                startedAt = 1_000L,
                finishedAt = 2_000L,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1_000L,
                updatedAt = 2_000L
            ),
            progress = SessionProgress(3, 1, 0, 4),
            exerciseCount = 1,
            primaryMuscles = listOf(MuscleGroup.LATS),
            durationMillis = 1_000L
        )
    }
}
