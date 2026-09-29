package app.mymusclemap.ui.health

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mymusclemap.R
import app.mymusclemap.domain.health.DailyRestingHeartRate
import app.mymusclemap.domain.health.DailyStepTotal
import app.mymusclemap.domain.health.HealthAccess
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthCardState
import app.mymusclemap.domain.health.HealthPresentation
import app.mymusclemap.domain.health.HealthQuietStatus
import app.mymusclemap.domain.health.HealthReadings
import app.mymusclemap.domain.health.StepSlot
import app.mymusclemap.testString
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
@Config(qualifiers = "w360dp-h800dp")
class HealthConnectCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 28)

    @Test
    fun unavailableStaysCompactAndOpensSettings() {
        val clicks = intArrayOf(0)
        render(HealthCardState.Quiet(HealthQuietStatus.Unavailable), onOpenSettings = { clicks[0] += 1 })
        composeRule.onNodeWithText(testString(R.string.health_connect_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_overview_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_read_failed)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_7_days)).assertDoesNotExist()
        composeRule.onNodeWithTag(OVERVIEW_HEALTH).performClick()
        assertEquals(1, clicks[0])
    }

    @Test
    fun notConnectedStaysCompactAndOpensSettings() {
        val clicks = intArrayOf(0)
        render(HealthCardState.Quiet(HealthQuietStatus.NotConnected), onOpenSettings = { clicks[0] += 1 })
        composeRule.onNodeWithText(testString(R.string.health_connect_overview_not_connected)).assertIsDisplayed()
        composeRule.onNodeWithTag(OVERVIEW_HEALTH).performClick()
        assertEquals(1, clicks[0])
    }

    @Test
    fun updateRequiredStaysAStatusNotAnErrorPanel() {
        render(HealthCardState.Quiet(HealthQuietStatus.UpdateRequired))
        composeRule.onNodeWithText(testString(R.string.health_connect_overview_update)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_read_failed)).assertDoesNotExist()
    }

    @Test
    fun stepsOnlyShowsStepsAndNotHeartRate() {
        render(
            HealthCardState.Readings(
                todaySteps = 7_300,
                todayHeartRate = null,
                stepsGranted = true,
                heartRateGranted = false,
                recentSteps = HealthPresentation.recentSteps(
                    listOf(
                        app.mymusclemap.domain.health.DailyStepTotal(today.minusDays(1), 10_200),
                        app.mymusclemap.domain.health.DailyStepTotal(today, 7_300)
                    ),
                    today
                ),
                readFailed = false
            )
        )
        composeRule.onNodeWithText("7,300").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_heart_off)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_from)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_7_days)).assertIsDisplayed()
        assertChartStaysInStepsColumn("Sep 28, 7,300 steps")
    }

    @Test
    fun stepsTodayWithNoHeartRateKeepsBarsInTheStepsColumn() {
        render(
            HealthCardState.Readings(
                todaySteps = 1_067,
                todayHeartRate = null,
                stepsGranted = true,
                heartRateGranted = true,
                recentSteps = HealthPresentation.recentSteps(
                    listOf(
                        DailyStepTotal(today.minusDays(1), 10_200),
                        DailyStepTotal(today, 1_067)
                    ),
                    today
                ),
                readFailed = false
            )
        )
        composeRule.onNodeWithText("1,067").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_no_heart)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_7_days)).assertIsDisplayed()
        assertChartStaysInStepsColumn("Sep 28, 1,067 steps")
    }

    @Test
    fun heartRateOnlyShowsHeartRateAndNotSteps() {
        render(
            HealthCardState.Readings(
                todaySteps = null,
                todayHeartRate = 59,
                stepsGranted = false,
                heartRateGranted = true,
                recentSteps = emptyList(),
                readFailed = false
            )
        )
        composeRule.onNodeWithText(testString(R.string.health_connect_bpm, 59)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_steps_off)).assertIsDisplayed()
        composeRule.onNodeWithTag(OVERVIEW_HEALTH_CHART).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_7_days)).assertDoesNotExist()
    }

    @Test
    fun missingRecentDayIsNotShownAsZero() {
        val slots = listOf(
            StepSlot(today.minusDays(6), 4_200),
            StepSlot(today.minusDays(5), 8_100),
            StepSlot(today.minusDays(4), 6_500),
            StepSlot(today.minusDays(3), 9_000),
            StepSlot(today.minusDays(2), 5_100),
            StepSlot(today.minusDays(1), null),
            StepSlot(today, 7_300)
        )
        render(
            HealthCardState.Readings(
                todaySteps = 7_300,
                todayHeartRate = 59,
                stepsGranted = true,
                heartRateGranted = true,
                recentSteps = slots,
                readFailed = false
            )
        )
        composeRule.onNodeWithText("7,300").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_bpm, 59)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_7_days)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sep 22, 4,200 steps").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sep 27, 0 steps").assertDoesNotExist()
        composeRule.onAllNodesWithText("0").assertCountEquals(0)
        assertChartStaysInStepsColumn("Sep 28, 7,300 steps")
    }

    @Test
    fun missingTodayKeepsYesterdayOnTheChartAndSaysToday() {
        val today = LocalDate.of(2026, 9, 29)
        val steps = listOf(
            DailyStepTotal(LocalDate.of(2026, 9, 23), 8_100),
            DailyStepTotal(LocalDate.of(2026, 9, 24), 6_500),
            DailyStepTotal(LocalDate.of(2026, 9, 25), 9_000),
            DailyStepTotal(LocalDate.of(2026, 9, 26), 5_100),
            DailyStepTotal(LocalDate.of(2026, 9, 27), 10_200),
            DailyStepTotal(LocalDate.of(2026, 9, 28), 7_300)
        )
        val card = HealthPresentation.card(
            HealthAccess(
                availability = HealthAvailability.Available,
                stepsGranted = true,
                restingHeartRateGranted = true,
                checked = true
            ),
            HealthReadings(
                steps = steps,
                restingHeartRate = listOf(
                    DailyRestingHeartRate(LocalDate.of(2026, 9, 28), 59)
                )
            ),
            today
        )
        render(card)
        composeRule.onNodeWithText(testString(R.string.health_connect_no_steps)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_no_heart)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_7_days)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sep 28, 7,300 steps").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sep 23, 8,100 steps").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sep 29, 0 steps").assertDoesNotExist()
        composeRule.onNodeWithText("7,300").assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.health_connect_bpm, 59)).assertDoesNotExist()
        composeRule.onAllNodesWithText("0").assertCountEquals(0)
        assertChartStaysInStepsColumn("Sep 28, 7,300 steps")
    }

    @Test
    fun connectedWithNoDataDoesNotDrawEmptyBars() {
        render(
            HealthCardState.Readings(
                todaySteps = null,
                todayHeartRate = null,
                stepsGranted = true,
                heartRateGranted = true,
                recentSteps = HealthPresentation.recentSteps(emptyList(), today),
                readFailed = false
            )
        )
        composeRule.onNodeWithText(testString(R.string.health_connect_no_steps)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_no_heart)).assertIsDisplayed()
        composeRule.onNodeWithTag(OVERVIEW_HEALTH_CHART).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_7_days)).assertDoesNotExist()
    }

    @Test
    fun readFailureStaysACaption() {
        render(
            HealthCardState.Readings(
                todaySteps = null,
                todayHeartRate = null,
                stepsGranted = true,
                heartRateGranted = true,
                recentSteps = emptyList(),
                readFailed = true
            )
        )
        composeRule.onNodeWithText(testString(R.string.health_connect_read_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.health_connect_no_steps)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.health_connect_last_7_days)).assertDoesNotExist()
    }

    @Test
    fun onlyOldestDayStaysInTheLeftmostSlot() {
        renderStepsChart(sevenDays(4_200, null, null, null, null, null, null), todaySteps = null)
        assertSevenFixedSlots()
        assertDescribedInSlot("Sep 22, 4,200 steps", 0)
        composeRule.onNodeWithContentDescription("Sep 23, 0 steps").assertDoesNotExist()
        assertChartStaysInStepsColumn("Sep 22, 4,200 steps")
    }

    @Test
    fun onlyTodayStaysInTheRightmostSlot() {
        renderStepsChart(sevenDays(null, null, null, null, null, null, 1_067))
        assertSevenFixedSlots()
        assertDescribedInSlot("Sep 28, 1,067 steps", 6)
        val chart = composeRule.onNodeWithTag(OVERVIEW_HEALTH_CHART).getBoundsInRoot()
        val todaySlot = chartSlot(6)
        assertTrue(todaySlot.left.value > chart.left.value + (chart.right.value - chart.left.value) * 0.7f)
        assertChartStaysInStepsColumn("Sep 28, 1,067 steps")
    }

    @Test
    fun onlyYesterdayStaysInTheSecondToLastSlot() {
        renderStepsChart(sevenDays(null, null, null, null, null, 10_200, null), todaySteps = null)
        assertSevenFixedSlots()
        assertDescribedInSlot("Sep 27, 10,200 steps", 5)
        assertChartStaysInStepsColumn("Sep 27, 10,200 steps")
    }

    @Test
    fun nonConsecutiveDaysKeepTheEmptySlotsBetweenThem() {
        renderStepsChart(sevenDays(4_200, null, null, 9_000, null, null, null), todaySteps = null)
        assertSevenFixedSlots()
        assertDescribedInSlot("Sep 22, 4,200 steps", 0)
        assertDescribedInSlot("Sep 25, 9,000 steps", 3)
        val first = chartSlot(0)
        val fourth = chartSlot(3)
        assertTrue(fourth.left.value - first.right.value > (first.right.value - first.left.value))
        composeRule.onNodeWithContentDescription("Sep 23, 0 steps").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Sep 24, 0 steps").assertDoesNotExist()
        assertChartStaysInStepsColumn("Sep 25, 9,000 steps")
    }

    @Test
    fun allSevenDaysKeepOneBarPerSlot() {
        val described = listOf(
            "Sep 22, 4,200 steps",
            "Sep 23, 8,100 steps",
            "Sep 24, 6,500 steps",
            "Sep 25, 9,000 steps",
            "Sep 26, 5,100 steps",
            "Sep 27, 10,200 steps",
            "Sep 28, 7,300 steps"
        )
        renderStepsChart(sevenDays(4_200, 8_100, 6_500, 9_000, 5_100, 10_200, 7_300))
        assertSevenFixedSlots()
        described.forEachIndexed { index, description ->
            assertDescribedInSlot(description, index)
        }
        assertChartStaysInStepsColumn("Sep 28, 7,300 steps")
    }

    private fun sevenDays(vararg steps: Long?): List<StepSlot> {
        check(steps.size == 7)
        return steps.mapIndexed { index, value ->
            StepSlot(today.minusDays((6 - index).toLong()), value)
        }
    }

    private fun renderStepsChart(steps: List<StepSlot>, todaySteps: Long? = steps.last().steps) {
        render(
            HealthCardState.Readings(
                todaySteps = todaySteps,
                todayHeartRate = null,
                stepsGranted = true,
                heartRateGranted = true,
                recentSteps = steps,
                readFailed = false
            )
        )
    }

    private fun chartSlot(index: Int) = composeRule.onNodeWithTag(
        OVERVIEW_HEALTH_CHART_SLOT + index,
        useUnmergedTree = true
    ).getBoundsInRoot()

    private fun assertSevenFixedSlots() {
        val chart = composeRule.onNodeWithTag(OVERVIEW_HEALTH_CHART).getBoundsInRoot()
        val slots = (0 until 7).map { chartSlot(it) }
        assertEquals(chart.left.value, slots.first().left.value, 1f)
        assertEquals(chart.right.value, slots.last().right.value, 1f)
        val chartWidth = chart.right.value - chart.left.value
        slots.forEach { bounds ->
            val slotWidthValue = bounds.right.value - bounds.left.value
            assertTrue(slotWidthValue > chartWidth / 10f)
            assertTrue(slotWidthValue < chartWidth / 5f)
        }
        for (index in 0 until 6) {
            assertTrue(slots[index].right.value <= slots[index + 1].left.value + 1f)
            assertTrue(slots[index + 1].left.value > slots[index].left.value)
        }
    }

    private fun assertDescribedInSlot(description: String, index: Int) {
        val described = composeRule.onNodeWithContentDescription(description).getBoundsInRoot()
        val bounds = chartSlot(index)
        assertEquals(bounds.left.value, described.left.value, 1f)
        assertEquals(bounds.right.value, described.right.value, 1f)
    }

    private fun assertChartStaysInStepsColumn(rightmostBar: String) {
        val steps = composeRule.onNodeWithTag(OVERVIEW_HEALTH_STEPS).getBoundsInRoot()
        val heart = composeRule.onNodeWithTag(OVERVIEW_HEALTH_HEART).getBoundsInRoot()
        val chart = composeRule.onNodeWithTag(OVERVIEW_HEALTH_CHART).getBoundsInRoot()
        val bar = composeRule.onNodeWithContentDescription(rightmostBar).getBoundsInRoot()
        assertTrue(steps.right <= heart.left)
        assertTrue(chart.left >= steps.left)
        assertTrue(chart.right <= steps.right)
        assertTrue(chart.right <= heart.left)
        assertTrue(bar.left >= steps.left)
        assertTrue(bar.right <= steps.right)
        assertTrue(bar.right <= heart.left)
    }

    private fun render(
        state: HealthCardState,
        onOpenSettings: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                HealthConnectOverviewCard(state = state, onOpenSettings = onOpenSettings)
            }
        }
    }
}
