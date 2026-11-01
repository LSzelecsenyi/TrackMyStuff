package app.mymusclemap.ui.health

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.R
import app.mymusclemap.domain.health.ExerciseHistoryEntry
import app.mymusclemap.domain.health.ExerciseHistorySnapshot
import app.mymusclemap.domain.health.HealthExerciseKind
import app.mymusclemap.domain.health.HealthHistoryAccess
import app.mymusclemap.testString
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class ExerciseHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backLeavesTheScreen() {
        val backs = intArrayOf(0)
        render(sample(canLoadMore = false), onBack = { backs[0] += 1 })
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertEquals(1, backs[0])
    }

    @Test
    fun completeSummaryCountsEveryLoadedKind() {
        render(sample(canLoadMore = false))
        composeRule.onNodeWithTag(EXERCISE_HISTORY_SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_history_total)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_history_loaded_note)).assertDoesNotExist()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText(UiFormatters.monthTitle(YearMonth.of(2026, 10))).assertIsDisplayed()
        composeRule.onNodeWithText(UiFormatters.monthTitle(YearMonth.of(2026, 9))).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("58s").assertIsDisplayed()
        composeRule.onNodeWithText("50m 25s").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Strava").assertCountEquals(4)
    }

    @Test
    fun partialPagesAreLabeledAsLoaded() {
        render(sample(canLoadMore = true))
        composeRule.onNodeWithText(testString(R.string.health_connect_history_loaded_note)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_history_loaded_label)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_history_total)).assertDoesNotExist()
    }

    @Test
    fun filtersHideOtherKindsAndKeepTheSelection() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            WeightTrackerThemeForPreview {
                ExerciseHistoryScreen(
                    history = sample(canLoadMore = false),
                    onBack = {},
                    onLoadMore = {},
                    onRetryInitial = {},
                    onRetryPage = {},
                    onRequestHistory = {},
                    sourceName = { "Strava" }
                )
            }
        }
        composeRule.onNodeWithTag(EXERCISE_HISTORY_FILTER_CYCLING).performClick()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_FILTER_CYCLING).assertIsSelected()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ROW + "run-short").assertDoesNotExist()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ROW + "ride").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_FILTER_CYCLING).assertIsSelected()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ROW + "ride").assertIsDisplayed()
    }

    @Test
    fun strengthFilterShowsOnlyStrength() {
        render(sample(canLoadMore = false))
        composeRule.onNodeWithTag(EXERCISE_HISTORY_FILTER_STRENGTH).performClick()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ROW + "lift").assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ROW + "ride").assertDoesNotExist()
    }

    @Test
    fun aRepeatedSessionIsShownOnce() {
        val duplicate = sample(canLoadMore = false).let { history ->
            history.copy(entries = listOf(history.entries.first(), history.entries.first()))
        }
        render(duplicate)
        composeRule.onAllNodesWithTag(EXERCISE_HISTORY_ROW + "run-new", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun anEmptyFilterWithMorePagesDoesNotClaimTheHistoryIsEmpty() {
        val loads = intArrayOf(0)
        render(
            history = ExerciseHistorySnapshot(
                visible = true,
                access = HealthHistoryAccess.Granted,
                entries = listOf(entry("run-new", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), Duration.ofMinutes(39))),
                canLoadMore = true
            ),
            onLoadMore = { loads[0] += 1 }
        )
        composeRule.onNodeWithTag(EXERCISE_HISTORY_FILTER_CYCLING).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(testString(R.string.health_connect_history_filter_none_cycling)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.health_connect_history_filter_older)).assertIsDisplayed()
        assertEquals(1, loads[0])
    }

    @Test
    fun aFinishedFilterCanBeEmpty() {
        val loads = intArrayOf(0)
        render(
            history = ExerciseHistorySnapshot(
                visible = true,
                access = HealthHistoryAccess.Granted,
                entries = listOf(
                    entry("lift", HealthExerciseKind.STRENGTH, LocalDate.of(2026, 10, 11), Duration.ofMinutes(20))
                )
            ),
            onLoadMore = { loads[0] += 1 }
        )
        composeRule.onNodeWithTag(EXERCISE_HISTORY_FILTER_CYCLING).performClick()
        composeRule.onNodeWithText(testString(R.string.health_connect_history_filter_none_cycling)).assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(0, loads[0])
    }

    @Test
    fun showEarlierSessionsRequestsTheNextPage() {
        val loads = intArrayOf(0)
        render(oneSession(canLoadMore = true), onLoadMore = { loads[0] += 1 })
        composeRule.onNodeWithTag(EXERCISE_HISTORY_MORE).assertIsDisplayed().performClick()
        assertEquals(1, loads[0])
    }

    @Test
    fun aFailedLaterPageKeepsTheRowsAndRetries() {
        val retries = intArrayOf(0)
        render(oneSession(canLoadMore = true).copy(failed = true), onRetryPage = { retries[0] += 1 })
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ROW + "run-new").assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_RETRY).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ROW + "run-new").assertIsDisplayed()
        assertEquals(1, retries[0])
    }

    @Test
    fun deniedHistoryCanBeRequested() {
        val requests = intArrayOf(0)
        render(
            oneSession(canLoadMore = false).copy(access = HealthHistoryAccess.NotGranted),
            onRequestHistory = { requests[0] += 1 }
        )
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ALLOW).performScrollTo().performClick()
        assertEquals(1, requests[0])
    }

    @Test
    fun unsupportedHistoryExplainsTheLimit() {
        render(oneSession(canLoadMore = false).copy(access = HealthHistoryAccess.Unsupported))
        composeRule.onNodeWithText(testString(R.string.health_connect_history_unsupported)).assertIsDisplayed()
        composeRule.onNodeWithTag(EXERCISE_HISTORY_ALLOW).assertDoesNotExist()
    }

    @Test
    fun initialFailureRetriesTheFirstPage() {
        val retries = intArrayOf(0)
        render(
            ExerciseHistorySnapshot(visible = true, failed = true),
            onRetryInitial = { retries[0] += 1 }
        )
        composeRule.onNodeWithTag(EXERCISE_HISTORY_RETRY).performClick()
        assertEquals(1, retries[0])
    }

    private fun oneSession(canLoadMore: Boolean): ExerciseHistorySnapshot {
        return ExerciseHistorySnapshot(
            visible = true,
            access = HealthHistoryAccess.Granted,
            canLoadMore = canLoadMore,
            entries = listOf(
                entry("run-new", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), Duration.ofMinutes(39))
            )
        )
    }

    private fun sample(canLoadMore: Boolean): ExerciseHistorySnapshot {
        return ExerciseHistorySnapshot(
            visible = true,
            access = HealthHistoryAccess.Granted,
            canLoadMore = canLoadMore,
            entries = listOf(
                entry("run-new", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), Duration.ofMinutes(39)),
                entry("run-short", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 27), Duration.ofSeconds(58)),
                entry("lift", HealthExerciseKind.STRENGTH, LocalDate.of(2026, 10, 11), Duration.ofMinutes(20)),
                entry("ride", HealthExerciseKind.CYCLING, LocalDate.of(2026, 9, 30), Duration.ofSeconds(50 * 60 + 25))
            )
        )
    }

    private fun entry(
        id: String,
        kind: HealthExerciseKind,
        date: LocalDate,
        duration: Duration
    ): ExerciseHistoryEntry {
        return ExerciseHistoryEntry(
            id = id,
            kind = kind,
            start = Instant.parse("2026-10-31T13:22:00Z"),
            date = date,
            startTime = LocalTime.of(15, 22),
            duration = duration,
            sourcePackage = "com.strava"
        )
    }

    private fun render(
        history: ExerciseHistorySnapshot,
        onBack: () -> Unit = {},
        onLoadMore: () -> Unit = {},
        onRetryInitial: () -> Unit = {},
        onRetryPage: () -> Unit = {},
        onRequestHistory: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ExerciseHistoryScreen(
                    history = history,
                    onBack = onBack,
                    onLoadMore = onLoadMore,
                    onRetryInitial = onRetryInitial,
                    onRetryPage = onRetryPage,
                    onRequestHistory = onRequestHistory,
                    sourceName = { "Strava" }
                )
            }
        }
    }
}
