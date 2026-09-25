package app.mymusclemap.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.DaySheetState
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class DayDetailsSheetLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun givenEmptyDayWhenOpenedThenScheduleEmptyStateIsShownWithoutWeightOnFuture() {
        render(
            state = DaySheetState(
                date = today.plusDays(2),
                measurement = null,
                differenceFromPreviousKg = null,
                canRecordWeight = false
            )
        )
        composeRule.onNodeWithTag(DAY_DETAILS_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("Erre a napra nincs edzés ütemezve.").assertIsDisplayed()
        composeRule.onNodeWithTag(DAY_SHEET_SCHEDULE_ACTION).assertIsDisplayed()
        composeRule.onAllNodesWithText("Testsúly rögzítése").assertCountEquals(0)
        val action = composeRule.onNodeWithTag(DAY_SHEET_SCHEDULE_ACTION).getBoundsInRoot()
        assertTrue(action.bottom - action.top >= 48.dp)
    }

    @Test
    fun givenTodayPlannedWhenShownThenStartIsAvailableAndDoubleTapFiresOnce() {
        var starts = 0
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(scheduled(4, today, "Push A"))
            ),
            onStart = { starts += 1 }
        )
        composeRule.onNodeWithTag(daySheetScheduledRowTag(4)).assertIsDisplayed()
        composeRule.onNodeWithText("Push A").assertIsDisplayed()
        composeRule.onNodeWithText("1 gyakorlat · 2 sorozat").assertIsDisplayed()
        composeRule.onNodeWithText("Edzés indítása").performClick()
        composeRule.onNodeWithText("Edzés indítása").performClick()
        assertEquals(1, starts)
        val row = composeRule.onNodeWithTag(daySheetScheduledRowTag(4)).getBoundsInRoot()
        assertTrue(row.bottom - row.top >= 48.dp)
        assertTrue(row.right <= 360.dp + 8.dp)
    }

    @Test
    fun givenFuturePlannedWhenShownThenStatusIsPlannedWithoutStart() {
        render(
            state = DaySheetState(
                date = today.plusDays(3),
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(scheduled(5, today.plusDays(3), "Pull A")),
                canRecordWeight = false
            )
        )
        composeRule.onNodeWithText("Tervezve").assertIsDisplayed()
        composeRule.onAllNodesWithText("Edzés indítása").assertCountEquals(0)
    }

    @Test
    fun givenPastUnfulfilledWhenShownThenStatusIsMissed() {
        render(
            state = DaySheetState(
                date = today.minusDays(1),
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(scheduled(6, today.minusDays(1), "Láb"))
            )
        )
        composeRule.onNodeWithText("Nem teljesült").assertIsDisplayed()
        composeRule.onAllNodesWithText("Edzés indítása").assertCountEquals(0)
    }

    @Test
    fun givenInProgressWhenShownThenContinueIsAvailable() {
        var continues = 0
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(
                    scheduled(7, today, "Push A", sessionId = 70L, sessionStatus = SessionStatus.IN_PROGRESS)
                )
            ),
            onContinue = { continues += 1 }
        )
        composeRule.onNodeWithText("Folyamatban").assertIsDisplayed()
        composeRule.onNodeWithText("Edzés folytatása").performClick()
        assertEquals(1, continues)
    }

    @Test
    fun givenCompletedWhenShownThenJournalActionIsAvailable() {
        var journal = 0L
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(
                    scheduled(8, today, "Push A", sessionId = 80L, sessionStatus = SessionStatus.COMPLETED)
                )
            ),
            onJournal = { journal = it }
        )
        composeRule.onNodeWithText("Teljesítve").assertIsDisplayed()
        composeRule.onNodeWithText("Megtekintés a Naplóban").performClick()
        assertEquals(8L, journal)
        composeRule.onAllNodesWithText("Edzés indítása").assertCountEquals(0)
    }

    @Test
    fun givenArchivedTemplateWhenShownThenItCannotStart() {
        var starts = 0
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(scheduled(9, today, "Régi", archived = true))
            ),
            onStart = { starts += 1 }
        )
        composeRule.onNodeWithText("Archivált edzésterv").assertIsDisplayed()
        composeRule.onAllNodesWithText("Edzés indítása").assertCountEquals(0)
        assertEquals(0, starts)
    }

    @Test
    fun givenFontScale13WhenRowsRenderThenTheyStay48DpWithoutOverflow() {
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(
                    scheduled(1, today, "Nagyon hosszú edzéstervnév keskeny telefonra"),
                    scheduled(2, today, "Második")
                )
            ),
            fontScale = 1.3f
        )
        val first = composeRule.onNodeWithTag(daySheetScheduledRowTag(1)).getBoundsInRoot()
        val second = composeRule.onNodeWithTag(daySheetScheduledRowTag(2)).getBoundsInRoot()
        assertTrue(first.top < second.top)
        assertTrue(first.bottom - first.top >= 48.dp)
        assertTrue(first.right <= 360.dp + 8.dp)
        assertTrue(second.right <= 360.dp + 8.dp)
        val action = composeRule.onNodeWithTag(DAY_SHEET_SCHEDULE_ACTION).getBoundsInRoot()
        assertTrue(action.bottom - action.top >= 48.dp)
    }

    private fun render(
        state: DaySheetState,
        fontScale: Float = 1f,
        onStart: (Long) -> Unit = {},
        onContinue: (Long) -> Unit = {},
        onJournal: (Long) -> Unit = {}
    ) {
        var started = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                        DayDetailsSheet(
                            state = state,
                            today = today,
                            onRecordWeight = {},
                            onEditWeight = {},
                            onDeleteWeight = {},
                            onOpenWorkout = {},
                            onScheduleWorkout = {},
                            onStartScheduled = { id ->
                                if (!started) {
                                    started = true
                                    onStart(id)
                                }
                            },
                            onContinueScheduled = onContinue,
                            onOpenScheduledJournal = onJournal,
                            onReschedule = {},
                            onUnschedule = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scheduled(
        id: Long,
        date: LocalDate,
        name: String,
        archived: Boolean = false,
        sessionId: Long? = null,
        sessionStatus: SessionStatus? = null
    ): ScheduledWorkout {
        return ScheduledWorkout(
            id = id,
            scheduledDate = date,
            templateId = id,
            templateName = name,
            exerciseCount = 1,
            plannedSetCount = 2,
            templateArchived = archived,
            sessionId = sessionId,
            sessionStatus = sessionStatus,
            createdAt = id
        )
    }
}
