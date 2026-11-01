package app.mymusclemap.ui.health

import android.graphics.Insets
import android.view.WindowInsets
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.mymusclemap.R
import app.mymusclemap.domain.health.ExerciseHistoryEntry
import app.mymusclemap.domain.health.ExerciseHistorySnapshot
import app.mymusclemap.domain.health.DailyExercise
import app.mymusclemap.domain.health.DailyHrv
import app.mymusclemap.domain.health.DailySleep
import app.mymusclemap.domain.health.DailyStepTotal
import app.mymusclemap.domain.health.ExerciseTotal
import app.mymusclemap.domain.health.HealthAccess
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthExerciseKind
import app.mymusclemap.domain.health.HealthHistoryAccess
import app.mymusclemap.domain.health.HealthDetailPresentation
import app.mymusclemap.domain.health.HealthDetailState
import app.mymusclemap.domain.health.HealthMetric
import app.mymusclemap.domain.health.HealthQuietStatus
import app.mymusclemap.domain.health.HealthReadings
import app.mymusclemap.testQuantity
import app.mymusclemap.testString
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class HealthConnectDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 29)

    @Test
    fun headerClearsTheStatusBarAndContentClearsTheNavigationBar() {
        val statusPx = 90
        val navigationPx = 70
        lateinit var host: android.view.View
        composeRule.setContent {
            host = LocalView.current
            WeightTrackerThemeForPreview {
                HealthConnectDetailScreen(
                    state = HealthDetailState.Quiet(HealthQuietStatus.Unavailable),
                    onBack = {}
                )
            }
        }
        composeRule.runOnIdle {
            val insets = WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, statusPx, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, navigationPx))
                .build()
            ViewCompat.dispatchApplyWindowInsets(host, WindowInsetsCompat.toWindowInsetsCompat(insets))
        }
        composeRule.waitForIdle()
        val density = host.resources.displayMetrics.density
        val headerTop = composeRule.onNodeWithContentDescription(testString(R.string.action_back))
            .getUnclippedBoundsInRoot()
            .top
            .value
        val contentBottom = composeRule.onNodeWithTag(HEALTH_DETAIL)
            .getUnclippedBoundsInRoot()
            .bottom
            .value
        val screenBottom = host.height / density
        assertTrue("header top $headerTop should clear ${statusPx / density}dp", headerTop + 0.5f >= statusPx / density)
        assertTrue(
            "content bottom $contentBottom should stop ${navigationPx / density}dp above $screenBottom",
            contentBottom <= screenBottom - (navigationPx / density) + 0.5f
        )
    }

    @Test
    fun activityAndRecoveryShowIndependentMetrics() {
        render(sample())
        composeRule.onNodeWithTag(HEALTH_DETAIL_ACTIVITY).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_no_steps)).assertIsDisplayed()
        composeRule.onNodeWithText("1 session · 45m").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_no_cycling)).assertIsDisplayed()
        composeRule.onNodeWithText("1 session · 32m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(HEALTH_DETAIL_RECOVERY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_no_heart)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sep 28 · 47 ms").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sep 28 · 7h 32m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_sleep)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sep 23, 8,100 steps").assertExists()
        composeRule.onNodeWithContentDescription("Sep 28, 7,300 steps").assertExists()
        composeRule.onNodeWithContentDescription("Sep 24, 0 steps").assertDoesNotExist()
        repeat(7) { index ->
            composeRule.onNodeWithTag(HEALTH_DETAIL_STEPS_CHART + index, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun deniedHrvDoesNotHideSteps() {
        val state = HealthDetailPresentation.detail(
            access(steps = true, hrv = false),
            HealthReadings(steps = listOf(DailyStepTotal(today, 4_200))),
            today
        )
        render(state)
        composeRule.onNodeWithText("4,200").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_hrv_off)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_read_failed)).assertDoesNotExist()
    }

    @Test
    fun failedSleepLeavesTheActivitySectionIntact() {
        val state = HealthDetailPresentation.detail(
            access(steps = true, sleep = true),
            HealthReadings(
                steps = listOf(DailyStepTotal(today, 6_500)),
                failed = setOf(HealthMetric.SLEEP)
            ),
            today
        )
        render(state)
        composeRule.onNodeWithText("6,500").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_metric_failed)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unavailableDoesNotLookLikeABrokenMetricList() {
        render(HealthDetailState.Quiet(HealthQuietStatus.Unavailable))
        composeRule.onNodeWithText(testString(R.string.health_connect_overview_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithTag(HEALTH_DETAIL_ACTIVITY).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.health_connect_metric_failed)).assertDoesNotExist()
    }

    @Test
    fun hiddenHistoryDoesNotReplaceTheSevenDaySummary() {
        render(sample())
        composeRule.onNodeWithText(testString(R.string.health_connect_history_title)).assertDoesNotExist()
        composeRule.onNodeWithText("1 session · 45m").assertIsDisplayed()
        composeRule.onNodeWithText("1 session · 32m").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun historyEntryShowsALoadedCountAndOpensTheFullList() {
        val opens = intArrayOf(0)
        val allows = intArrayOf(0)
        render(
            state = sample(),
            exerciseHistory = ExerciseHistorySnapshot(
                visible = true,
                access = HealthHistoryAccess.NotGranted,
                entries = listOf(
                    historyEntry("new", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), LocalTime.of(15, 22), Duration.ofMinutes(39)),
                    historyEntry("old", HealthExerciseKind.CYCLING, LocalDate.of(2026, 9, 30), LocalTime.of(8, 8), Duration.ofSeconds(30))
                ),
                canLoadMore = true
            ),
            onOpenHistory = { opens[0] += 1 },
            onRequestHistory = { allows[0] += 1 }
        )
        composeRule.onNodeWithText("1 session · 32m").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_history_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testQuantity(R.plurals.health_connect_history_activities_loaded, 2))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(UiFormatters.monthTitle(java.time.YearMonth.of(2026, 10))).assertDoesNotExist()
        composeRule.onNodeWithTag(HEALTH_DETAIL_HISTORY_OPEN).performScrollTo().performClick()
        composeRule.onNodeWithTag(HEALTH_DETAIL_HISTORY_ALLOW).performScrollTo().performClick()
        assertEquals(1, opens[0])
        assertEquals(1, allows[0])
    }

    @Test
    fun backClosesTheDetailScreen() {
        val backs = intArrayOf(0)
        render(sample(), onBack = { backs[0] += 1 })
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertEquals(1, backs[0])
    }

    private fun sample(): HealthDetailState {
        return HealthDetailPresentation.detail(
            access(steps = true, exercise = true, heart = true, hrv = true, sleep = true),
            HealthReadings(
                steps = listOf(
                    DailyStepTotal(LocalDate.of(2026, 9, 23), 8_100),
                    DailyStepTotal(LocalDate.of(2026, 9, 28), 7_300)
                ),
                exercise = listOf(
                    DailyExercise(
                        LocalDate.of(2026, 9, 23),
                        strength = ExerciseTotal(1, Duration.ofMinutes(45))
                    ),
                    DailyExercise(
                        LocalDate.of(2026, 9, 28),
                        running = ExerciseTotal(1, Duration.ofMinutes(32))
                    )
                ),
                hrv = listOf(DailyHrv(LocalDate.of(2026, 9, 28), 47.0)),
                sleep = listOf(DailySleep(LocalDate.of(2026, 9, 28), Duration.ofMinutes(452)))
            ),
            today
        )
    }

    private fun access(
        steps: Boolean = false,
        exercise: Boolean = false,
        heart: Boolean = false,
        hrv: Boolean = false,
        sleep: Boolean = false
    ): HealthAccess {
        return HealthAccess(
            availability = HealthAvailability.Available,
            stepsGranted = steps,
            restingHeartRateGranted = heart,
            checked = true,
            exerciseGranted = exercise,
            hrvGranted = hrv,
            sleepGranted = sleep
        )
    }

    private fun historyEntry(
        id: String,
        kind: HealthExerciseKind,
        date: LocalDate,
        time: LocalTime,
        duration: Duration
    ): ExerciseHistoryEntry {
        val start = Instant.parse("2026-10-31T13:22:00Z")
        return ExerciseHistoryEntry(
            id = id,
            kind = kind,
            start = start,
            date = date,
            startTime = time,
            duration = duration,
            sourcePackage = null
        )
    }

    private fun render(
        state: HealthDetailState,
        onBack: () -> Unit = {},
        exerciseHistory: ExerciseHistorySnapshot = ExerciseHistorySnapshot(),
        onOpenHistory: () -> Unit = {},
        onRequestHistory: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                HealthConnectDetailScreen(
                    state = state,
                    onBack = onBack,
                    exerciseHistory = exerciseHistory,
                    onOpenHistory = onOpenHistory,
                    onRequestHistory = onRequestHistory
                )
            }
        }
    }
}
