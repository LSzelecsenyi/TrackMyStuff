package app.mymusclemap.ui.dashboard

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.mymusclemap.R
import app.mymusclemap.domain.body.BodyMeasurement
import app.mymusclemap.domain.body.BodyMeasurementSeries
import app.mymusclemap.domain.body.BodyMeasurementType
import app.mymusclemap.domain.model.ChartRange
import app.mymusclemap.testString
import app.mymusclemap.ui.navigation.AppNavigation
import app.mymusclemap.ui.navigation.AppRoutes
import app.mymusclemap.ui.pro.PRO_BADGE
import app.mymusclemap.ui.progress.PROGRESS_PHOTOS_ROW
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BodyProgressScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun overviewListsEveryMeasurementWithoutChartsOrRepeatedEmptyCopy() {
        var opened: String? = null
        var locked: BodyMeasurementType? = null
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                WeightDetailsScreen(
                    state = WeightDetailsUiState(
                        today = today,
                        rows = overviewRows()
                    ),
                    onBack = {},
                    onAddToday = {},
                    onChartRangeSelected = {},
                    onEditorDateChange = {},
                    onEditorWeightChange = {},
                    onSave = {},
                    onDismissEditor = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                    onMessageConsumed = {},
                    onOpenMeasurement = { opened = it },
                    onLockedMeasurement = { locked = it }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.body_progress_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.chart_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.range_30)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.body_measurements_section)).performScrollTo().assertIsDisplayed()
        BodyMeasurementType.entries.forEach { type ->
            composeRule.onNodeWithText(testString(type.labelRes())).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("50.0 cm").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sep 27 · +1.5 cm").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.body_measurement_none)).assertCountEquals(5)
        composeRule.onNodeWithText(testString(R.string.body_measurement_empty)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.body_measurement_add)).assertDoesNotExist()
        composeRule.onNodeWithTag(BODY_CHART_TAG).assertDoesNotExist()
        composeRule.onAllNodesWithTag(PRO_BADGE, useUnmergedTree = true).assertCountEquals(4)
        composeRule.onNodeWithTag(PROGRESS_PHOTOS_ROW).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.progress_photos_empty)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(BODY_ROW_TAG + "THIGH").performScrollTo().performClick()
        assertEquals("THIGH", opened)
        composeRule.onNodeWithTag(BODY_ROW_TAG + "CHEST").performScrollTo().performClick()
        assertEquals(BodyMeasurementType.CHEST, locked)
    }

    @Test
    fun emptyDetailOffersAddWithoutAChart() {
        var added = false
        setDetail(detail(emptyList()), onAdd = { added = true })
        composeRule.onNodeWithText(testString(BodyMeasurementType.THIGH.labelRes())).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.body_measurement_empty)).assertIsDisplayed()
        composeRule.onNodeWithTag(BODY_ADD_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(BODY_CHART_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(BODY_ADD_TAG).performClick()
        assertTrue(added)
    }

    @Test
    fun measurementTodayDisablesAddWhileHistoryStaysEditable() {
        val point = measurement(7, today, 50.0)
        var opened: BodyMeasurement? = null
        var added = false
        setDetail(detail(listOf(point)), onAdd = { added = true }, onOpenHistory = { opened = it })
        composeRule.onNodeWithTag(BODY_ADD_TAG).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag(BODY_HISTORY_TAG + point.id).performClick()
        assertEquals(point.id, opened?.id)
        assertTrue(!added)
    }

    @Test
    fun historicalMeasurementKeepsAddEnabled() {
        val point = measurement(8, today.minusDays(40), 48.0)
        setDetail(detail(listOf(point), ChartRange.Days30))
        composeRule.onNodeWithText(testString(R.string.body_measurement_empty)).assertIsDisplayed()
        composeRule.onNodeWithTag(BODY_ADD_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(BODY_CHART_TAG).assertDoesNotExist()
    }

    @Test
    fun oneMeasurementDetailShowsHistoryWithoutAChart() {
        val point = measurement(1, today, 50.0)
        setDetail(detail(listOf(point)))
        composeRule.onNodeWithText("50.0 cm").assertIsDisplayed()
        composeRule.onNodeWithText("Sep 27").assertIsDisplayed()
        composeRule.onNodeWithTag(BODY_HISTORY_TAG + point.id).assertIsDisplayed()
        composeRule.onNodeWithTag(BODY_CHART_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.body_measurement_empty)).assertDoesNotExist()
    }

    @Test
    fun twoMeasurementsRenderChartAndHistoryOpensEdit() {
        val older = measurement(1, today.minusDays(10), 48.5)
        val newer = measurement(2, today, 50.0)
        var opened: BodyMeasurement? = null
        var range: ChartRange? = null
        var backed = false
        setDetail(
            detail = detail(listOf(older, newer)),
            onOpenHistory = { opened = it },
            onRangeSelected = { range = it },
            onBack = { backed = true }
        )
        composeRule.onNodeWithTag(BODY_CHART_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.body_measurement_change, "+1.5 cm")).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.range_90)).performClick()
        assertEquals(ChartRange.Days90, range)
        composeRule.onNodeWithTag(BODY_HISTORY_TAG + newer.id).performScrollTo().performClick()
        assertEquals(newer.id, opened?.id)
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertTrue(backed)
    }

    @Test
    fun measurementDetailBackPopsToBodyProgress() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                MeasurementRouteHarness()
            }
        }
        composeRule.onNodeWithText("Body Progress").assertIsDisplayed()
        composeRule.onNodeWithTag("open-detail").performClick()
        composeRule.onNodeWithText("Thigh detail").assertIsDisplayed()
        composeRule.onNodeWithText("Body Progress").assertDoesNotExist()
        composeRule.onNodeWithTag("detail-back").performClick()
        composeRule.onNodeWithText("Body Progress").assertIsDisplayed()
        composeRule.onNodeWithText("Thigh detail").assertDoesNotExist()
    }

    @Composable
    private fun MeasurementRouteHarness() {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = AppRoutes.BODY_PROGRESS_GRAPH
        ) {
            navigation(
                route = AppRoutes.BODY_PROGRESS_GRAPH,
                startDestination = AppRoutes.WEIGHT_DETAILS
            ) {
                composable(AppRoutes.WEIGHT_DETAILS) {
                    Button(
                        onClick = { navController.navigate(AppNavigation.bodyMeasurementRoute("THIGH")) },
                        modifier = Modifier.testTag("open-detail")
                    ) {
                        Text("Body Progress")
                    }
                }
                composable(
                    route = AppRoutes.BODY_MEASUREMENT_PATTERN,
                    arguments = listOf(navArgument("type") { type = NavType.StringType })
                ) {
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("detail-back")
                    ) {
                        Text("Thigh detail")
                    }
                }
            }
        }
    }

    private fun setDetail(
        detail: BodyMeasurementDetail,
        onAdd: () -> Unit = {},
        onOpenHistory: (BodyMeasurement) -> Unit = {},
        onRangeSelected: (ChartRange) -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                BodyMeasurementDetailScreen(
                    detail = detail,
                    today = today,
                    editor = null,
                    lockedFeature = null,
                    userMessage = null,
                    onBack = onBack,
                    onRangeSelected = onRangeSelected,
                    onAdd = onAdd,
                    onOpenHistory = onOpenHistory,
                    onBodyDateChange = {},
                    onBodyValueChange = {},
                    onSaveBody = {},
                    onDismissBodyEditor = {},
                    onBodyDeleteRequest = {},
                    onBodyDeleteDismiss = {},
                    onBodyDeleteConfirm = {},
                    onDismissLocked = {},
                    onMessageConsumed = {}
                )
            }
        }
    }

    private fun overviewRows(): List<BodyProgressRow> {
        return BodyMeasurementType.entries.map { type ->
            if (type == BodyMeasurementType.THIGH) {
                BodyProgressRow(
                    type = type,
                    typeCode = type.code,
                    latest = measurement(4, today, 50.0),
                    change = 1.5,
                    lockedEmpty = false
                )
            } else {
                BodyProgressRow(
                    type = type,
                    typeCode = type.code,
                    latest = null,
                    change = null,
                    lockedEmpty = type.requiredFeature != null
                )
            }
        }
    }

    private fun detail(
        points: List<BodyMeasurement>,
        range: ChartRange = ChartRange.Days30
    ): BodyMeasurementDetail {
        val window = BodyMeasurementSeries.window(points, today, range)
        return BodyMeasurementDetail(
            type = BodyMeasurementType.THIGH,
            typeCode = BodyMeasurementType.THIGH.code,
            range = range,
            window = window,
            historyNewestFirst = window.points.asReversed(),
            canAdd = points.none { it.date == today },
            showChart = window.points.size >= 2
        )
    }

    private fun measurement(id: Long, date: LocalDate, value: Double): BodyMeasurement {
        return BodyMeasurement(id, BodyMeasurementType.THIGH.code, date, value, "MANUAL", null, 1, 1)
    }
}
