package app.mymusclemap.domain.reports

import app.mymusclemap.dev.DemoTrainingHistoryGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportCatalogDemoTest {
    @Test
    fun eighteenMonthDemoCatalogMatchesClosedPartialHistory() {
        val snapshot = DemoTrainingHistoryGenerator.generate()
        val today = DemoTrainingHistoryGenerator.referenceDate
        val tables = snapshot.tables
        val evidence = ReportHistoryEvidence(
            sessionWorkoutDates = tables.workoutSessions.map { LocalDate.parse(it.workoutDate) },
            scheduledOccurrenceDates = tables.scheduledWorkouts.map { LocalDate.parse(it.scheduledDate) },
            bodyWeightDates = tables.weightMeasurements.map { LocalDate.parse(it.date) }
        )
        val historyStart = ReportHistory.earliestDate(evidence, today)
        assertEquals(LocalDate.of(2025, 3, 26), historyStart)

        val monthly = ReportCatalog.available(ReportKind.Monthly, today, historyStart)
        assertEquals(LocalDate.of(2026, 8, 1), monthly.first().period.startInclusive)
        assertEquals(ReportHistoryCoverage.Complete, monthly.first().coverage)
        assertEquals(LocalDate.of(2025, 3, 1), monthly.last().period.startInclusive)
        assertEquals(LocalDate.of(2025, 3, 31), monthly.last().period.endInclusive)
        assertEquals(ReportHistoryCoverage.Partial, monthly.last().coverage)
        assertTrue(monthly.none { it.period.contains(LocalDate.of(2026, 9, 1)) })

        val quarters = ReportCatalog.available(ReportKind.Quarterly, today, historyStart)
        assertEquals(LocalDate.of(2026, 4, 1), quarters.first().period.startInclusive)
        assertEquals(LocalDate.of(2026, 6, 30), quarters.first().period.endInclusive)
        assertEquals(LocalDate.of(2025, 1, 1), quarters.last().period.startInclusive)
        assertEquals(ReportHistoryCoverage.Partial, quarters.last().coverage)
        assertEquals(
            ReportHistoryCoverage.Complete,
            quarters.single { it.period.startInclusive == LocalDate.of(2025, 4, 1) }.coverage
        )
        assertTrue(quarters.none { it.period.startInclusive == LocalDate.of(2026, 7, 1) })
        assertTrue(quarters.none { it.period.startInclusive == LocalDate.of(2024, 10, 1) })

        val halves = ReportCatalog.available(ReportKind.HalfYear, today, historyStart)
        assertEquals(LocalDate.of(2026, 1, 1), halves.first().period.startInclusive)
        assertEquals(LocalDate.of(2026, 6, 30), halves.first().period.endInclusive)
        assertEquals(ReportHistoryCoverage.Partial, halves.last().coverage)
        assertEquals(LocalDate.of(2025, 1, 1), halves.last().period.startInclusive)
        assertEquals(
            ReportHistoryCoverage.Complete,
            halves.single { it.period.startInclusive == LocalDate.of(2025, 7, 1) }.coverage
        )
        assertTrue(halves.none { it.period.startInclusive == LocalDate.of(2026, 7, 1) })
        assertTrue(halves.none { it.period.startInclusive == LocalDate.of(2024, 7, 1) })

        val years = ReportCatalog.available(ReportKind.Yearly, today, historyStart)
        assertEquals(1, years.size)
        assertEquals(LocalDate.of(2025, 1, 1), years.single().period.startInclusive)
        assertEquals(LocalDate.of(2025, 12, 31), years.single().period.endInclusive)
        assertEquals(ReportHistoryCoverage.Partial, years.single().coverage)
    }
}
