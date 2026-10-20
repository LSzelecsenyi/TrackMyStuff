package app.mymusclemap.ui.components

import app.mymusclemap.R
import app.mymusclemap.testString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.DaySheetState
import app.mymusclemap.domain.WeightParseError
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.SessionProgress
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun givenEmptyFutureDayWhenOpenedThenScheduleRemainsAndWeightEntryIsHidden() {
        render(
            state = DaySheetState(
                date = today.plusDays(2),
                measurement = null,
                differenceFromPreviousKg = null,
                canRecordWeight = false
            )
        )
        composeRule.onNodeWithTag(DAY_DETAILS_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(DAY_SHEET_CLOSE).assertIsDisplayed()
        composeRule.onNodeWithTag(DAY_SHEET_SCHEDULE_ACTION).assertIsDisplayed()
        composeRule.onAllNodesWithTag(DAY_SHEET_WEIGHT_INPUT).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.day_sheet_no_scheduled)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.action_cancel)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.day_sheet_today).uppercase()).assertCountEquals(0)
        composeRule.onNodeWithText(UiFormatters.daySheetTitle(today.plusDays(2), today)).assertIsDisplayed()
        val action = composeRule.onNodeWithTag(DAY_SHEET_SCHEDULE_ACTION).getBoundsInRoot()
        assertTrue(action.bottom - action.top >= 48.dp)
    }

    @Test
    fun givenTodayWithoutWeightWhenOpenedThenEntryIsEmptyAndKeyboardStaysClosed() {
        var saves = 0
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null
            ),
            onSaveWeight = { saves += 1 }
        )
        composeRule.onNodeWithText(testString(R.string.day_sheet_today).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(UiFormatters.daySheetTitle(today, today)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.day_sheet_weight_placeholder)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.unit_kg)).assertIsDisplayed()
        composeRule.onNodeWithTag(DAY_SHEET_WEIGHT_INPUT).assertTextEquals("")
        composeRule.onNodeWithTag(DAY_SHEET_WEIGHT_INPUT).assertIsNotFocused()
        composeRule.onNodeWithTag(DAY_SHEET_WEIGHT_SAVE).performClick()
        assertEquals(1, saves)
        composeRule.onAllNodesWithText(testString(R.string.action_cancel)).assertCountEquals(0)
    }

    @Test
    fun daySheetTitleOmitsTheYearInsideTheCurrentYear() {
        val current = UiFormatters.daySheetTitle(today, today)
        val previousYear = UiFormatters.daySheetTitle(today.minusYears(1), today)
        assertFalse(current.contains(today.year.toString()))
        assertTrue(current.contains(today.dayOfMonth.toString()))
        assertTrue(previousYear.contains(today.minusYears(1).year.toString()))
    }

    @Test
    fun givenSavedWeightWhenShownThenValueAndEditReplaceTheField() {
        var edits = 0
        render(
            state = DaySheetState(
                date = today,
                measurement = WeightMeasurement(3, today, 88.1, 0, 0),
                differenceFromPreviousKg = 0.4
            ),
            onBeginEdit = { edits += 1 }
        )
        composeRule.onNodeWithText(UiFormatters.weightValue(88.1)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.unit_kg)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(DAY_SHEET_WEIGHT_INPUT).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.action_delete)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.change_from_previous_value, "+0.4 kg")).assertCountEquals(0)
        composeRule.onNodeWithTag(DAY_SHEET_WEIGHT_EDIT).performClick()
        assertEquals(1, edits)
    }

    @Test
    fun givenTodayPlannedWhenShownThenRowStartsOnceAndUsesTheCalendarIcon() {
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
        composeRule.onNodeWithTag(daySheetCalendarIconTag(4), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Push A").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.day_sheet_scheduled_heading)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.template_row_meta, 1, 2)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.action_start_workout)).assertCountEquals(0)
        composeRule.onNodeWithText("Push A").performClick()
        composeRule.onNodeWithText("Push A").performClick()
        assertEquals(1, starts)
        val row = composeRule.onNodeWithTag(daySheetScheduledRowTag(4)).getBoundsInRoot()
        assertTrue(row.bottom - row.top >= 48.dp)
        assertTrue(row.right <= 360.dp + 8.dp)
    }

    @Test
    fun givenFuturePlannedWhenShownThenRowReschedulesWithoutAStartButton() {
        var rescheduled = 0
        render(
            state = DaySheetState(
                date = today.plusDays(3),
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(scheduled(5, today.plusDays(3), "Pull A")),
                canRecordWeight = false
            ),
            onReschedule = { rescheduled += 1 }
        )
        composeRule.onNodeWithTag(daySheetCalendarIconTag(5), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.schedule_status_planned)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.action_start_workout)).assertCountEquals(0)
        composeRule.onNodeWithText("Pull A").performClick()
        assertEquals(1, rescheduled)
    }

    @Test
    fun givenPastUnfulfilledWhenShownThenRowStarts() {
        var starts = 0
        render(
            state = DaySheetState(
                date = today.minusDays(1),
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(scheduled(6, today.minusDays(1), "Láb"))
            ),
            onStart = { starts += 1 }
        )
        composeRule.onNodeWithTag(daySheetCalendarIconTag(6), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.schedule_status_missed)).assertCountEquals(0)
        composeRule.onNodeWithText("Láb").performClick()
        assertEquals(1, starts)
    }

    @Test
    fun givenInProgressWhenShownThenContinueUsesTheCalendarIcon() {
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
        composeRule.onNodeWithTag(daySheetCalendarIconTag(7), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.action_resume_workout)).assertCountEquals(0)
        composeRule.onNodeWithText("Push A").performClick()
        assertEquals(1, continues)
    }

    @Test
    fun givenCompletedSessionWhenShownThenRowOpensDetailsWithACheck() {
        var opened = 0L
        val workout = summary(80, today, "Push A")
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null,
                workouts = listOf(workout)
            ),
            onOpenWorkout = { opened = it }
        )
        composeRule.onNodeWithTag(daySheetCompletedIconTag(80), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.day_sheet_completed_heading)).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.day_sheet_scheduled_heading)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.action_open_details)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.action_view_in_journal)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.journal_duration, workout.durationLabel)).assertCountEquals(0)
        composeRule.onNodeWithText("Push A").performClick()
        assertEquals(80L, opened)
    }

    @Test
    fun givenArchivedTemplateWhenShownThenItCannotStart() {
        var starts = 0
        var removed = 0
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null,
                scheduledWorkouts = listOf(scheduled(9, today, "Régi", archived = true))
            ),
            onStart = { starts += 1 },
            onUnschedule = { removed += 1 }
        )
        composeRule.onNodeWithTag(daySheetCalendarIconTag(9), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.schedule_status_archived)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.action_start_workout)).assertCountEquals(0)
        composeRule.onNodeWithText("Régi").performClick()
        assertEquals(0, starts)
        assertEquals(1, removed)
    }

    @Test
    fun givenWeightErrorWhenShownThenTheMessageUsesTheExistingCopy() {
        render(
            state = DaySheetState(
                date = today,
                measurement = null,
                differenceFromPreviousKg = null
            ),
            weightError = WeightParseError.TooManyDecimals
        )
        composeRule.onNodeWithText(testString(R.string.error_weight_decimals)).assertIsDisplayed()
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
        weightInput: String = "",
        weightError: WeightParseError? = null,
        weightEditing: Boolean = false,
        onSaveWeight: () -> Unit = {},
        onBeginEdit: () -> Unit = {},
        onOpenWorkout: (Long) -> Unit = {},
        onStart: (Long) -> Unit = {},
        onContinue: (Long) -> Unit = {},
        onJournal: (Long) -> Unit = {},
        onReschedule: () -> Unit = {},
        onUnschedule: () -> Unit = {}
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
                            weightInput = weightInput,
                            weightError = weightError,
                            weightEditing = weightEditing,
                            weightSavedTick = 0,
                            onWeightChange = {},
                            onSaveWeight = onSaveWeight,
                            onBeginWeightEdit = onBeginEdit,
                            onOpenWorkout = onOpenWorkout,
                            onScheduleWorkout = {},
                            onStartScheduled = { id ->
                                if (!started) {
                                    started = true
                                    onStart(id)
                                }
                            },
                            onContinueScheduled = onContinue,
                            onOpenScheduledJournal = onJournal,
                            onReschedule = { onReschedule() },
                            onUnschedule = { onUnschedule() },
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

    private fun summary(id: Long, date: LocalDate, name: String): WorkoutSessionSummary {
        return WorkoutSessionSummary(
            session = WorkoutSession(
                id = id,
                templateId = 1L,
                templateName = name,
                status = SessionStatus.COMPLETED,
                workoutDate = date,
                startedAt = id,
                finishedAt = id + 1,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = id,
                updatedAt = id
            ),
            progress = SessionProgress(completed = 1, skipped = 2, pending = 0, total = 3),
            exerciseCount = 1,
            primaryMuscles = emptyList(),
            durationMillis = 90_000L
        )
    }
}
