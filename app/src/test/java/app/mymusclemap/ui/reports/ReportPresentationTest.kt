package app.mymusclemap.ui.reports

import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.reports.ReportHistoryCoverage
import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.reports.ReportPeriod
import app.mymusclemap.domain.statistics.VolumeTrendResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportPresentationTest {
    @Test
    fun partialYearDropsMonthsThatEndBeforeHistoryAndKeepsLaterZeros() {
        val trend = (1..12).map { month ->
            val date = LocalDate.of(2025, month, 1)
            val value = if (month == 3) 40.0 else 0.0
            SeriesPoint(date, value)
        }
        val plotted = ReportChartSeries.plotted(
            trend = trend,
            resolution = VolumeTrendResolution.Monthly,
            coverage = ReportHistoryCoverage.Partial,
            historyStart = LocalDate.of(2025, 3, 26)
        )
        assertEquals(LocalDate.of(2025, 3, 1), plotted.first().date)
        assertFalse(plotted.any { it.date.monthValue < 3 })
        assertEquals(0.0, plotted.single { it.date == LocalDate.of(2025, 4, 1) }.value, 0.0)
        assertEquals(40.0, plotted.single { it.date == LocalDate.of(2025, 3, 1) }.value, 0.0)
        assertEquals(10, plotted.size)
    }

    @Test
    fun zerosAfterHistoryStartStayOnAPartialWeeklyChart() {
        val history = LocalDate.of(2026, 4, 2)
        val trend = listOf(
            SeriesPoint(LocalDate.of(2026, 3, 16), 0.0),
            SeriesPoint(LocalDate.of(2026, 3, 23), 0.0),
            SeriesPoint(LocalDate.of(2026, 3, 30), 12.0),
            SeriesPoint(LocalDate.of(2026, 4, 6), 0.0),
            SeriesPoint(LocalDate.of(2026, 4, 13), 0.0)
        )
        val plotted = ReportChartSeries.plotted(
            trend = trend,
            resolution = VolumeTrendResolution.Weekly,
            coverage = ReportHistoryCoverage.Partial,
            historyStart = history
        )
        assertEquals(LocalDate.of(2026, 3, 30), plotted.first().date)
        assertEquals(listOf(12.0, 0.0, 0.0), plotted.map { it.value })
    }

    @Test
    fun completeReportsKeepLeadingZeroBuckets() {
        val trend = (1..31).map { day ->
            SeriesPoint(LocalDate.of(2026, 8, day), 0.0)
        }
        val plotted = ReportChartSeries.plotted(
            trend = trend,
            resolution = VolumeTrendResolution.Daily,
            coverage = ReportHistoryCoverage.Complete,
            historyStart = LocalDate.of(2026, 8, 10)
        )
        assertEquals(trend, plotted)
    }

    @Test
    fun weeklyAxisLabelsStayInsideTheReportPeriod() {
        val halfYear = ReportPeriod(
            ReportKind.HalfYear,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30)
        )
        val halfSeries = listOf(
            SeriesPoint(LocalDate.of(2025, 12, 29), 0.0),
            SeriesPoint(LocalDate.of(2026, 6, 29), 0.0)
        )
        assertEquals(
            "Jan 1",
            ReportChartSeries.axisLabel(
                date = LocalDate.of(2025, 12, 29),
                resolution = VolumeTrendResolution.Weekly,
                period = halfYear,
                plotted = halfSeries
            )
        )
        val quarter = ReportPeriod(
            ReportKind.Quarterly,
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 6, 30)
        )
        val quarterSeries = listOf(
            SeriesPoint(LocalDate.of(2026, 3, 30), 0.0),
            SeriesPoint(LocalDate.of(2026, 6, 29), 0.0)
        )
        assertEquals(
            "Apr 1",
            ReportChartSeries.axisLabel(
                date = LocalDate.of(2026, 3, 30),
                resolution = VolumeTrendResolution.Weekly,
                period = quarter,
                plotted = quarterSeries
            )
        )
        assertFalse(
            ReportChartSeries.axisLabel(
                date = LocalDate.of(2025, 12, 29),
                resolution = VolumeTrendResolution.Weekly,
                period = halfYear,
                plotted = halfSeries
            ).contains("Dec")
        )
    }

    @Test
    fun durationOmitsZeroComponents() {
        assertEquals("9h 56m", reportDuration(9 * 3_600_000L + 56 * 60_000L))
        assertEquals("57h 52m", reportDuration(57 * 3_600_000L + 52 * 60_000L))
        assertEquals("144h 18m", reportDuration(144 * 3_600_000L + 18 * 60_000L))
        assertEquals("2h", reportDuration(2 * 3_600_000L))
        assertEquals("56m", reportDuration(56 * 60_000L))
        assertEquals("45s", reportDuration(45_000L))
    }

    @Test
    fun largeVolumesUseOneDecimalCompactForm() {
        assertEquals("804.0k kg", ReportVolumeFormat.kilograms(803_992.5))
        assertEquals("+356.7k kg", ReportVolumeFormat.signedKilograms(356_668.5))
        assertEquals("−356.7k kg", ReportVolumeFormat.signedKilograms(-356_668.5))
        assertEquals("121.5k", ReportVolumeFormat.axis(121_464.0))
        assertEquals("12.5 kg", ReportVolumeFormat.kilograms(12.5))
        assertEquals("0.0 kg", ReportVolumeFormat.signedKilograms(0.0))
        assertEquals("999.9 kg", ReportVolumeFormat.kilograms(999.9))
        assertEquals("1.0k kg", ReportVolumeFormat.kilograms(1_000.0))
    }
}
