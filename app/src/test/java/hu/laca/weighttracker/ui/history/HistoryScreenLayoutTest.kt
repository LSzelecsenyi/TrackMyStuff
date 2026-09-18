package hu.laca.weighttracker.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.journal.JournalAssembler
import hu.laca.weighttracker.domain.journal.JournalEmptyKind
import hu.laca.weighttracker.domain.journal.JournalFilter
import hu.laca.weighttracker.domain.journal.JournalTimeline
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.SessionProgress
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.WorkoutSession
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import hu.laca.weighttracker.ui.components.UiFormatters
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
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
class HistoryScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val newer = LocalDate.parse("2026-09-17")
    private val older = LocalDate.parse("2026-09-16")

    @Test
    fun givenMixedEntriesWhenAllIsActiveThenItemsGroupByDateInExistingOrder() {
        renderInteractive(
            measurements = listOf(weight(1, newer, 87.9), weight(2, older, 87.6)),
            summaries = listOf(
                workout(10, newer, "Esti húzó", startedAt = 9_000L),
                workout(11, newer, "Reggeli toló", startedAt = 1_000L),
                workout(12, older, "Push A", startedAt = 5_000L)
            )
        )
        composeRule.onNodeWithTag(JOURNAL_FILTER_ALL).performClick()
        composeRule.onNodeWithTag(JOURNAL_FILTER_ALL).assertIsSelected()
        val newerHeader = composeRule.onNodeWithTag(journalGroupTag(newer)).getBoundsInRoot()
        val olderHeader = composeRule.onNodeWithTag(journalGroupTag(older)).getBoundsInRoot()
        assertTrue(comesBefore(newerHeader, olderHeader))
        val weight = composeRule.onNodeWithTag(journalWeightRowTag(1)).getBoundsInRoot()
        val evening = composeRule.onNodeWithTag(journalWorkoutRowTag(10)).getBoundsInRoot()
        val morning = composeRule.onNodeWithTag(journalWorkoutRowTag(11)).getBoundsInRoot()
        assertTrue(comesBefore(weight, evening))
        assertTrue(comesBefore(evening, morning))
        composeRule.onNodeWithText(UiFormatters.weightKg(87.9)).assertIsDisplayed()
        composeRule.onNodeWithText("Esti húzó").assertIsDisplayed()
        composeRule.onNodeWithText("Reggeli toló").assertIsDisplayed()
    }

    @Test
    fun givenFreshJournalWhenOpenedThenWorkoutTabIsSelectedInWorkoutWeightAllOrder() {
        renderInteractive(
            measurements = listOf(weight(1, newer, 87.9)),
            summaries = listOf(workout(10, newer, "Push A"))
        )
        composeRule.onNodeWithTag(JOURNAL_FILTER_WORKOUT).assertIsSelected()
        composeRule.onNodeWithTag(JOURNAL_FILTER_WEIGHT).assertIsNotSelected()
        composeRule.onNodeWithTag(JOURNAL_FILTER_ALL).assertIsNotSelected()
        val workouts = composeRule.onNodeWithTag(JOURNAL_FILTER_WORKOUT).getBoundsInRoot()
        val weight = composeRule.onNodeWithTag(JOURNAL_FILTER_WEIGHT).getBoundsInRoot()
        val all = composeRule.onNodeWithTag(JOURNAL_FILTER_ALL).getBoundsInRoot()
        assertTrue("Edzések should precede Testsúly: $workouts $weight", workouts.left < weight.left)
        assertTrue("Testsúly should precede Összes: $weight $all", weight.left < all.left)
        val workoutNode = composeRule.onNodeWithTag(JOURNAL_FILTER_WORKOUT).fetchSemanticsNode()
        val weightNode = composeRule.onNodeWithTag(JOURNAL_FILTER_WEIGHT).fetchSemanticsNode()
        val allNode = composeRule.onNodeWithTag(JOURNAL_FILTER_ALL).fetchSemanticsNode()
        val workoutIndex = workoutNode.config[androidx.compose.ui.semantics.SemanticsProperties.TraversalIndex]
        val weightIndex = weightNode.config[androidx.compose.ui.semantics.SemanticsProperties.TraversalIndex]
        val allIndex = allNode.config[androidx.compose.ui.semantics.SemanticsProperties.TraversalIndex]
        assertTrue("a11y Edzések before Testsúly: $workoutIndex $weightIndex", workoutIndex < weightIndex)
        assertTrue("a11y Testsúly before Összes: $weightIndex $allIndex", weightIndex < allIndex)
        composeRule.onNodeWithTag(journalWorkoutRowTag(10)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(journalWeightRowTag(1)).assertCountEquals(0)
    }

    @Test
    fun givenWeightTabThenOnlyWeightRowsAreVisible() {
        renderInteractive(
            measurements = listOf(weight(1, newer, 87.9)),
            summaries = listOf(workout(10, newer, "Push A"))
        )
        composeRule.onNodeWithTag(JOURNAL_FILTER_WEIGHT).performClick()
        composeRule.onNodeWithTag(JOURNAL_FILTER_WEIGHT).assertIsSelected()
        composeRule.onNodeWithTag(journalWeightRowTag(1)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(journalWorkoutRowTag(10)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Push A").assertCountEquals(0)
    }

    @Test
    fun givenWorkoutTabThenOnlyWorkoutRowsAreVisible() {
        renderInteractive(
            measurements = listOf(weight(1, newer, 87.9)),
            summaries = listOf(workout(10, newer, "Push A"))
        )
        composeRule.onNodeWithTag(JOURNAL_FILTER_WORKOUT).performClick()
        composeRule.onNodeWithTag(JOURNAL_FILTER_WORKOUT).assertIsSelected()
        composeRule.onNodeWithTag(JOURNAL_FILTER_ALL).assertIsNotSelected()
        composeRule.onNodeWithTag(journalWorkoutRowTag(10)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(journalWeightRowTag(1)).assertCountEquals(0)
        composeRule.onAllNodesWithText(UiFormatters.weightKg(87.9)).assertCountEquals(0)
    }

    @Test
    fun givenWorkoutRowWhenTappedThenDetailOpensOnce() {
        val opened = intArrayOf(0)
        render(
            timeline = mixedTimeline(),
            onOpenWorkout = { opened[0] += 1 }
        )
        composeRule.onNodeWithTag(journalWorkoutRowTag(10)).performClick()
        composeRule.onNodeWithTag(journalWorkoutRowTag(10)).performClick()
        assertEquals(1, opened[0])
    }

    @Test
    fun givenWorkoutOverflowWhenTappedThenMenuOpensBesideIconWithoutOpeningDetail() {
        val opened = intArrayOf(0)
        render(timeline = mixedTimeline(), onOpenWorkout = { opened[0] += 1 })
        composeRule.onNodeWithTag(journalWorkoutOverflowButtonTag(10)).performClick()
        composeRule.onNodeWithText("Edzés törlése").assertIsDisplayed()
        composeRule.onNodeWithTag(journalWorkoutOverflowMenuTag(10)).assertIsDisplayed()
        assertEquals(0, opened[0])
        val row = composeRule.onNodeWithTag(journalWorkoutRowTag(10)).getBoundsInRoot()
        val popupRight = popupWindowLayoutParams().maxOf { params ->
            (params.x + params.width).toFloat()
        }
        assertTrue(
            "menu should stay on the right near the overflow icon: popupRight=$popupRight row=$row",
            popupRight > (row.left + row.right).value / 2
        )
    }

    @Test
    fun givenWorkoutDeleteConfirmThenExistingActionRunsOnce() {
        val confirmed = intArrayOf(0)
        val summary = workout(10, newer, "Push A")
        render(
            timeline = JournalAssembler.assemble(
                measurements = emptyList(),
                summaries = listOf(summary),
                filter = JournalFilter.ALL,
                includeAbandoned = false
            ),
            pendingDelete = summary,
            onConfirmDeleteWorkout = { confirmed[0] += 1 }
        )
        composeRule.onNodeWithTag(WORKOUT_DELETE_CONFIRM).performClick()
        assertEquals(1, confirmed[0])
    }

    @Test
    fun givenHeaderPlusWhenTappedThenWeightFlowOpensWithoutFab() {
        val adds = intArrayOf(0)
        render(timeline = mixedTimeline(), onAdd = { adds[0] += 1 })
        composeRule.onNodeWithContentDescription("Testsúly rögzítése").performClick()
        composeRule.onNodeWithContentDescription("Testsúly rögzítése").performClick()
        assertEquals(1, adds[0])
        composeRule.onAllNodesWithContentDescription("Mérés hozzáadása").assertCountEquals(0)
    }

    @Test
    fun givenOverflowImportWhenTappedThenExistingImportRouteStartsOnce() {
        val imports = intArrayOf(0)
        render(timeline = mixedTimeline(), onOpenImport = { imports[0] += 1 })
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_BUTTON).performClick()
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_IMPORT).performClick()
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_BUTTON).performClick()
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_IMPORT).performClick()
        assertEquals(1, imports[0])
    }

    @Test
    fun givenNoJournalDataWhenAllIsActiveThenEmptyCopyAppears() {
        render(
            timeline = JournalTimeline(
                groups = emptyList(),
                filter = JournalFilter.ALL,
                includeAbandoned = false,
                emptyKind = JournalEmptyKind.NoEntries
            )
        )
        composeRule.onNodeWithText("Még nincs naplóbejegyzés.").assertIsDisplayed()
    }

    @Test
    fun givenOnlyWeightsWhenWorkoutTabIsSelectedThenWorkoutEmptyCopyAppears() {
        renderInteractive(
            measurements = listOf(weight(1, newer, 87.9)),
            summaries = emptyList()
        )
        composeRule.onNodeWithTag(JOURNAL_FILTER_WORKOUT).performClick()
        composeRule.onNodeWithText("Még nincs befejezett edzés.").assertIsDisplayed()
    }

    @Test
    fun givenOnlyWorkoutsWhenWeightTabIsSelectedThenWeightEmptyCopyAndRecordActionAppear() {
        renderInteractive(
            measurements = emptyList(),
            summaries = listOf(workout(10, newer, "Push A"))
        )
        composeRule.onNodeWithTag(JOURNAL_FILTER_WEIGHT).performClick()
        composeRule.onNodeWithText("Még nincs testsúlymérés.").assertIsDisplayed()
        composeRule.onNodeWithTag(JOURNAL_EMPTY_RECORD).assertIsDisplayed()
    }

    @Test
    fun givenTwoWorkoutsOnTheSameDayThenTwoRowsShareOneDateGroup() {
        render(
            timeline = JournalAssembler.assemble(
                measurements = emptyList(),
                summaries = listOf(
                    workout(10, newer, "Esti húzó", startedAt = 9_000L),
                    workout(11, newer, "Reggeli toló", startedAt = 1_000L)
                ),
                filter = JournalFilter.ALL,
                includeAbandoned = false
            )
        )
        composeRule.onAllNodesWithTag(journalGroupTag(newer)).assertCountEquals(1)
        composeRule.onNodeWithTag(journalWorkoutRowTag(10)).assertIsDisplayed()
        composeRule.onNodeWithTag(journalWorkoutRowTag(11)).assertIsDisplayed()
        val first = composeRule.onNodeWithTag(journalWorkoutRowTag(10)).getBoundsInRoot()
        val second = composeRule.onNodeWithTag(journalWorkoutRowTag(11)).getBoundsInRoot()
        assertTrue(comesBefore(first, second))
    }

    @Test
    fun givenImportedSessionWithNullTemplateIdThenItOpensAndDeletesLikeTemplateSessions() {
        val opened = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        val imported = workout(20, newer, "Importált edzés", templateId = null)
        val templated = workout(21, newer, "Sablon edzés", templateId = 4L)
        render(
            timeline = JournalAssembler.assemble(
                measurements = emptyList(),
                summaries = listOf(imported, templated),
                filter = JournalFilter.ALL,
                includeAbandoned = false
            ),
            onOpenWorkout = { opened += it },
            onRequestDeleteWorkout = { deleted += it }
        )
        composeRule.onNodeWithTag(journalWorkoutRowTag(20)).performClick()
        composeRule.onNodeWithTag(journalWorkoutOverflowButtonTag(20)).performClick()
        composeRule.onNodeWithText("Edzés törlése").assertIsDisplayed()
        composeRule.onNodeWithTag(WORKOUT_DELETE_ACTION).performClick()
        assertEquals(listOf(20L), opened)
        assertEquals(listOf(20L), deleted)
        composeRule.onNodeWithTag(journalWorkoutRowTag(21)).assertIsDisplayed()
        composeRule.onNodeWithTag(journalWorkoutOverflowButtonTag(21)).assertIsDisplayed()
    }

    @Test
    fun givenNarrowWidthAndLargeFontThenTouchTargetsStayUsable() {
        render(
            timeline = mixedTimeline(),
            width = 360.dp,
            fontScale = 1.3f
        )
        val add = composeRule.onNodeWithTag(JOURNAL_ADD).getBoundsInRoot()
        val overflow = composeRule.onNodeWithTag(JOURNAL_OVERFLOW_BUTTON).getBoundsInRoot()
        val all = composeRule.onNodeWithTag(JOURNAL_FILTER_ALL).getBoundsInRoot()
        val row = composeRule.onNodeWithTag(journalWorkoutRowTag(10)).getBoundsInRoot()
        val workoutOverflow = composeRule.onNodeWithTag(journalWorkoutOverflowButtonTag(10)).getBoundsInRoot()
        assertMinTouch(add)
        assertMinTouch(overflow)
        assertMinTouch(all)
        assertTrue("row height ${row.bottom - row.top}", row.bottom - row.top >= 48.dp)
        assertTrue("row should stay in 360dp: $row", row.right <= 360.dp + 8.dp)
        assertMinTouch(workoutOverflow)
        composeRule.onNodeWithText("Napló").assertIsDisplayed()
    }

    @Test
    fun givenListScrolledToEndThenLastRowStaysFullyVisible() {
        val days = (0..8).map { older.minusDays(it.toLong()) }
        render(
            timeline = JournalAssembler.assemble(
                measurements = days.mapIndexed { index, date -> weight(index.toLong() + 1, date, 80.0 + index) },
                summaries = emptyList(),
                filter = JournalFilter.ALL,
                includeAbandoned = false
            ),
            height = 640.dp
        )
        composeRule.onNodeWithTag(JOURNAL_LIST).performTouchInput {
            repeat(10) { swipeUp() }
        }
        composeRule.waitForIdle()
        val lastId = days.size.toLong()
        composeRule.onNodeWithTag(journalWeightRowTag(lastId)).assertIsDisplayed()
        val last = composeRule.onNodeWithTag(journalWeightRowTag(lastId)).getBoundsInRoot()
        assertTrue("last row should stay on screen: $last", last.bottom <= 640.dp + 1.dp)
        assertTrue("last row should be fully visible: $last", last.top >= 0.dp - 1.dp)
    }

    @Test
    fun givenVisibleScreenThenLegacyMaterialChromeIsGone() {
        render(timeline = mixedTimeline())
        composeRule.onNodeWithText("Napló").assertIsDisplayed()
        composeRule.onNodeWithTag(JOURNAL_ADD).assertIsDisplayed()
        composeRule.onNodeWithTag(JOURNAL_FILTER_ALL).assertIsDisplayed()
        composeRule.onAllNodesWithText("Részletek").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Beállítások megnyitása").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Mérés hozzáadása").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Edzések importálása").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Testsúly rögzítése").assertIsDisplayed()
    }

    @Test
    fun givenFastDoubleTapsThenEachTargetFlowStartsOnce() {
        val adds = intArrayOf(0)
        val imports = intArrayOf(0)
        val opened = intArrayOf(0)
        render(
            timeline = mixedTimeline(),
            onAdd = { adds[0] += 1 },
            onOpenImport = { imports[0] += 1 },
            onOpenWorkout = { opened[0] += 1 }
        )
        composeRule.onNodeWithTag(JOURNAL_ADD).performClick()
        composeRule.onNodeWithTag(JOURNAL_ADD).performClick()
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_BUTTON).performClick()
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_IMPORT).performClick()
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_BUTTON).performClick()
        composeRule.onNodeWithTag(JOURNAL_OVERFLOW_IMPORT).performClick()
        composeRule.onNodeWithTag(journalWorkoutRowTag(10)).performClick()
        composeRule.onNodeWithTag(journalWorkoutRowTag(10)).performClick()
        assertEquals(1, adds[0])
        assertEquals(1, imports[0])
        assertEquals(1, opened[0])
    }

    private fun mixedTimeline(): JournalTimeline {
        return JournalAssembler.assemble(
            measurements = listOf(weight(1, newer, 87.9)),
            summaries = listOf(workout(10, newer, "Push A")),
            filter = JournalFilter.ALL,
            includeAbandoned = false
        )
    }

    private fun weight(id: Long, date: LocalDate, kg: Double): WeightMeasurement {
        return WeightMeasurement(id, date, kg, 1L, 1L)
    }

    private fun workout(
        id: Long,
        date: LocalDate,
        name: String,
        startedAt: Long = 1_000L,
        templateId: Long? = 1L,
        muscles: List<MuscleGroup> = listOf(MuscleGroup.CHEST, MuscleGroup.ABS)
    ): WorkoutSessionSummary {
        return WorkoutSessionSummary(
            session = WorkoutSession(
                id = id,
                templateId = templateId,
                templateName = name,
                status = SessionStatus.COMPLETED,
                workoutDate = date,
                startedAt = startedAt,
                finishedAt = startedAt + 7_000L * 60,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = startedAt,
                updatedAt = startedAt
            ),
            progress = SessionProgress(1, 0, 0, 1),
            exerciseCount = 1,
            primaryMuscles = muscles,
            durationMillis = 7_000L * 60
        )
    }

    private fun comesBefore(first: DpRect, second: DpRect): Boolean {
        return first.top < second.top - 1.dp
    }

    private fun assertMinTouch(bounds: DpRect) {
        assertTrue("width ${bounds.right - bounds.left}", bounds.right - bounds.left >= 48.dp)
        assertTrue("height ${bounds.bottom - bounds.top}", bounds.bottom - bounds.top >= 48.dp)
    }

    private fun popupWindowLayoutParams(): List<android.view.WindowManager.LayoutParams> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val shadow = org.robolectric.Shadows.shadowOf(wm)
        val viewsMethod = generateSequence(shadow.javaClass as Class<*>?) { type -> type.superclass }
            .mapNotNull { type ->
                type.methods.firstOrNull { method ->
                    method.name == "getViews" && method.parameterCount == 0
                }
            }
            .firstOrNull()
        val views = viewsMethod?.invoke(shadow) as? List<*> ?: emptyList<Any>()
        return views.mapNotNull { view ->
            (view as? android.view.View)?.layoutParams as? android.view.WindowManager.LayoutParams
        }
    }

    private fun renderInteractive(
        measurements: List<WeightMeasurement>,
        summaries: List<WorkoutSessionSummary>
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            var filter by remember { mutableStateOf(JournalFilter.WORKOUT) }
            val timeline = JournalAssembler.assemble(
                measurements = measurements,
                summaries = summaries,
                filter = filter,
                includeAbandoned = false
            )
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                        HistoryScreen(
                            state = HistoryUiState(loading = false, timeline = timeline),
                            today = newer,
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
                            onFilterSelected = { filter = it },
                            onIncludeAbandoned = {},
                            onOpenWorkout = {},
                            onRequestDeleteWorkout = {},
                            onDismissDeleteWorkout = {},
                            onConfirmDeleteWorkout = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun render(
        timeline: JournalTimeline,
        width: Dp = 360.dp,
        height: Dp = 2000.dp,
        fontScale: Float = 1f,
        pendingDelete: WorkoutSessionSummary? = null,
        onAdd: () -> Unit = {},
        onOpenImport: () -> Unit = {},
        onOpenWorkout: (Long) -> Unit = {},
        onRequestDeleteWorkout: (Long) -> Unit = {},
        onConfirmDeleteWorkout: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(height)
                            .fillMaxSize()
                    ) {
                        HistoryScreen(
                            state = HistoryUiState(
                                loading = false,
                                timeline = timeline,
                                pendingWorkoutDelete = pendingDelete
                            ),
                            today = newer,
                            onAdd = onAdd,
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
                            onOpenImport = onOpenImport,
                            onFilterSelected = {},
                            onIncludeAbandoned = {},
                            onOpenWorkout = onOpenWorkout,
                            onRequestDeleteWorkout = onRequestDeleteWorkout,
                            onDismissDeleteWorkout = {},
                            onConfirmDeleteWorkout = onConfirmDeleteWorkout
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
