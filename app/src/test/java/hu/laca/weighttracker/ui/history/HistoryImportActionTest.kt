package hu.laca.weighttracker.ui.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import hu.laca.weighttracker.domain.journal.JournalEmptyKind
import hu.laca.weighttracker.domain.journal.JournalFilter
import hu.laca.weighttracker.domain.journal.JournalTimeline
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
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
                    onOpenSettings = {},
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
        composeRule.onNodeWithContentDescription("Edzések importálása").performClick()
        assertEquals(1, opened)
    }
}
