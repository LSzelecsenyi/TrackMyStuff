package app.mymusclemap.ui.history

import app.mymusclemap.R
import app.mymusclemap.testString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mymusclemap.domain.journal.JournalEmptyKind
import app.mymusclemap.domain.journal.JournalFilter
import app.mymusclemap.domain.journal.JournalTimeline
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HistoryImportActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun journalImportActionInvokesCallback() {
        var opened = 0
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                HistoryScreen(
                    state = HistoryUiState(
                        loading = false,
                        timeline = JournalTimeline(
                            groups = emptyList(),
                            filter = JournalFilter.ALL,
                            includeAbandoned = false,
                            emptyKind = JournalEmptyKind.NoEntries
                        )
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
                    onOpenImport = { opened += 1 },
                    onFilterSelected = {},
                    onIncludeAbandoned = {},
                    onOpenWorkout = {},
                    onRequestDeleteWorkout = {},
                    onDismissDeleteWorkout = {},
                    onConfirmDeleteWorkout = {}
                )
            }
        }
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_BUTTON).performClick()
        composeRule.onNodeWithText(testString(R.string.workout_import_action)).performClick()
        assertEquals(1, opened)
    }
}
