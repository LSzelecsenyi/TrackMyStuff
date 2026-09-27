package app.mymusclemap.ui.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.reports.AvailableReport
import app.mymusclemap.domain.reports.ReportActivity
import app.mymusclemap.domain.reports.ReportAdherence
import app.mymusclemap.domain.reports.ReportBodyWeight
import app.mymusclemap.domain.reports.ReportExerciseHighlight
import app.mymusclemap.domain.reports.ReportHistoryCoverage
import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.reports.ReportMuscleCount
import app.mymusclemap.domain.reports.ReportPerformanceKind
import app.mymusclemap.domain.reports.ReportPeriod
import app.mymusclemap.domain.reports.ReportPeriodComparison
import app.mymusclemap.domain.reports.ReportSummary
import app.mymusclemap.domain.reports.ReportVolume
import app.mymusclemap.domain.statistics.VolumeTrendResolution
import app.mymusclemap.testString
import app.mymusclemap.ui.pro.PRO_BADGE
import app.mymusclemap.ui.pro.PRO_INFO_BODY
import app.mymusclemap.ui.pro.PRO_INFO_HIGHLIGHTS
import app.mymusclemap.ui.pro.PRO_INFO_TITLE
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
@Config(qualifiers = "w360dp-h2400dp")
class ReportsScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val august = ReportPeriod(
        ReportKind.Monthly,
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31)
    )
    private val september = ReportPeriod(
        ReportKind.Monthly,
        LocalDate.of(2026, 9, 1),
        LocalDate.of(2026, 9, 30)
    )

    @Test
    fun givenNoClosedReportsThenEmptyStateIsShown() {
        composeRule.setContent {
            var kind by remember { mutableStateOf(ReportKind.Monthly) }
            WeightTrackerThemeForPreview {
                ReportsScreen(
                    state = ReportsUiState(loading = false, kind = kind),
                    onBack = {},
                    onKindSelected = { kind = it },
                    onOpenReport = {}
                )
            }
        }
        composeRule.onNodeWithTag(REPORTS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(REPORTS_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.reports_empty_body)).assertIsDisplayed()
        composeRule.onNodeWithTag("reports-kind-quarterly").performClick()
        composeRule.onNodeWithTag("reports-kind-quarterly").assertIsSelected()
    }

    @Test
    fun givenCatalogThenNewestPeriodIsFirstAndPartialHistoryIsLabeled() {
        render(
            ReportsUiState(
                loading = false,
                reports = listOf(
                    AvailableReport(september, ReportHistoryCoverage.Complete),
                    AvailableReport(august, ReportHistoryCoverage.Partial)
                )
            )
        )
        composeRule.onNodeWithText(september.displayTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(august.displayTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.reports_period_partial, august.displayRange()))
            .assertIsDisplayed()
        val newer = composeRule.onNodeWithText(september.displayTitle()).fetchSemanticsNode().positionInRoot.y
        val older = composeRule.onNodeWithText(august.displayTitle()).fetchSemanticsNode().positionInRoot.y
        assertTrue(newer < older)
        composeRule.onNodeWithText("October 2026").assertDoesNotExist()
    }

    @Test
    fun givenSummaryThenDetailShowsDomainSectionsWithoutRecalculating() {
        renderDetail(sampleSummary())
        composeRule.onNodeWithTag(REPORT_DETAIL_ROOT).assertIsDisplayed()
        composeRule.onNodeWithText(august.displayRange()).assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_PARTIAL).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.reports_partial_notice)).assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_ACTIVITY).assertIsDisplayed()
        composeRule.onNodeWithText("1h").assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_ADHERENCE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_adherence_percent, 50)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_adherence_summary, 1, 2)).assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_VOLUME).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("500.0 kg").assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_COMPARISON).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.reports_comparison_with, "July 2026")).assertIsDisplayed()
        composeRule.onNodeWithText("+1 (+100%)").assertIsDisplayed()
        composeRule.onNodeWithText("−25 pp").assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_HIGHLIGHTS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bench press").assertIsDisplayed()
        composeRule.onNodeWithText("80.0 kg → 70.0 kg").assertIsDisplayed()
        composeRule.onNodeWithText("−10.0 kg").assertIsDisplayed()
        composeRule.onNodeWithText("Squat").assertDoesNotExist()
        composeRule.onNodeWithTag(REPORT_BODY_WEIGHT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+1.0 kg").assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_VOLUME_CHART).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_MUSCLES).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.statistics_muscles_scope)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.muscle_lats)).assertIsDisplayed()
    }

    @Test
    fun givenLowDataThenEmptyStatesStayOnTheReport() {
        renderDetail(
            ReportSummary(
                period = september,
                coverage = ReportHistoryCoverage.Complete,
                historyStart = LocalDate.of(2026, 1, 1),
                activity = ReportActivity(),
                adherence = ReportAdherence(),
                volume = ReportVolume(
                    resolution = VolumeTrendResolution.Daily,
                    trend = listOf(SeriesPoint(september.startInclusive, 0.0))
                ),
                muscles = emptyList(),
                exerciseHighlights = emptyList(),
                bodyWeight = null,
                previous = ReportPeriodComparison(
                    period = august,
                    workoutCountDelta = 0,
                    workoutCountPercent = null,
                    volumeDeltaKg = 0.0,
                    volumePercent = null,
                    adherencePointDelta = null
                )
            )
        )
        composeRule.onNodeWithTag(REPORT_PARTIAL).assertDoesNotExist()
        composeRule.onNodeWithTag(REPORT_BODY_WEIGHT).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.reports_adherence_empty)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.reports_volume_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.reports_highlights_empty)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.reports_muscles_empty)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("(+").assertDoesNotExist()
    }

    @Test
    fun givenLockedLongerKindThenPeriodsStayVisibleAndDoNotOpen() {
        var opened = false
        var lockedTaps = 0
        val quarter = ReportPeriod(ReportKind.Quarterly, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))
        render(
            ReportsUiState(
                loading = false,
                kind = ReportKind.Quarterly,
                reports = listOf(AvailableReport(quarter, ReportHistoryCoverage.Complete)),
                lockedKinds = setOf(ReportKind.Quarterly, ReportKind.HalfYear, ReportKind.Yearly)
            ),
            onOpenReport = { opened = true },
            onLockedReport = { lockedTaps += 1 }
        )
        composeRule.onNodeWithTag("reports-kind-quarterly").assertIsSelected()
        composeRule.onNodeWithTag("reports-kind-monthly").assertIsDisplayed()
        composeRule.onNodeWithText(quarter.displayTitle()).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_BADGE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(reportPeriodTag(quarter.kind, quarter.startInclusive)).performClick()
        assertFalse(opened)
        assertEquals(1, lockedTaps)
        composeRule.onNodeWithTag(REPORT_ACTIVITY).assertDoesNotExist()
    }

    @Test
    fun givenLockedReportsThenProInfoUsesReportsCopy() {
        render(
            ReportsUiState(
                loading = false,
                kind = ReportKind.Monthly,
                lockedFeature = AppFeature.AdvancedReports,
                lockedKinds = setOf(ReportKind.Quarterly, ReportKind.HalfYear, ReportKind.Yearly)
            )
        )
        composeRule.onNodeWithTag("reports-kind-monthly").assertIsSelected()
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_reports_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_BODY).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_reports_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_HIGHLIGHTS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_reports_quarterly)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_reports_half_year)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_reports_yearly)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertDoesNotExist()
    }

    @Test
    fun givenMissingSummaryThenUnavailableIsShown() {
        renderDetail(null)
        composeRule.onNodeWithTag(REPORT_UNAVAILABLE).assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_ACTIVITY).assertDoesNotExist()
    }

    private fun sampleSummary(): ReportSummary {
        val july = ReportPeriod(ReportKind.Monthly, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
        return ReportSummary(
            period = august,
            coverage = ReportHistoryCoverage.Partial,
            historyStart = LocalDate.of(2026, 8, 10),
            activity = ReportActivity(
                workoutCount = 2,
                trainingDayCount = 2,
                completedSetCount = 4,
                durationMillis = 3_600_000L
            ),
            adherence = ReportAdherence(plannedCount = 2, completedCount = 1),
            volume = ReportVolume(
                totalKg = 500.0,
                completedSetCount = 2,
                resolution = VolumeTrendResolution.Daily,
                trend = listOf(
                    SeriesPoint(LocalDate.of(2026, 8, 10), 200.0),
                    SeriesPoint(LocalDate.of(2026, 8, 20), 300.0)
                )
            ),
            muscles = listOf(
                ReportMuscleCount(MuscleGroup.LATS, 4, 2, LocalDate.of(2026, 8, 20)),
                ReportMuscleCount(MuscleGroup.CHEST, 1, 1, LocalDate.of(2026, 8, 10))
            ),
            exerciseHighlights = listOf(
                ReportExerciseHighlight(
                    exerciseId = 1L,
                    name = "Bench press",
                    measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                    kind = ReportPerformanceKind.EffectiveKg,
                    baselineDate = LocalDate.of(2026, 8, 10),
                    latestDate = LocalDate.of(2026, 8, 20),
                    baseline = 80.0,
                    latest = 70.0
                )
            ),
            bodyWeight = ReportBodyWeight(
                firstDate = LocalDate.of(2026, 8, 2),
                firstKg = 80.0,
                lastDate = LocalDate.of(2026, 8, 28),
                lastKg = 81.0,
                changeKg = 1.0,
                averageKg = 80.5
            ),
            previous = ReportPeriodComparison(
                period = july,
                workoutCountDelta = 1,
                workoutCountPercent = 100.0,
                volumeDeltaKg = 500.0,
                volumePercent = null,
                adherencePointDelta = -25
            )
        )
    }

    private fun render(
        state: ReportsUiState,
        onKindSelected: (ReportKind) -> Unit = {},
        onOpenReport: (ReportPeriod) -> Unit = {},
        onLockedReport: () -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ReportsScreen(
                    state = state,
                    onBack = {},
                    onKindSelected = onKindSelected,
                    onOpenReport = onOpenReport,
                    onLockedReport = onLockedReport
                )
            }
        }
    }

    private fun renderDetail(summary: ReportSummary?) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ReportDetailScreen(loading = false, summary = summary, onBack = {})
            }
        }
    }
}
