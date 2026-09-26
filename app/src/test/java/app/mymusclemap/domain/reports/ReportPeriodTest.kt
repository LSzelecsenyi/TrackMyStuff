package app.mymusclemap.domain.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportPeriodTest {
    @Test
    fun monthlyUsesCalendarMonthLengths() {
        val january = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 1, 15))
        val april = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 4, 1))
        val february = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2025, 2, 10))
        val leapFebruary = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2024, 2, 29))

        assertEquals(LocalDate.of(2026, 1, 1), january.startInclusive)
        assertEquals(LocalDate.of(2026, 1, 31), january.endInclusive)
        assertEquals(LocalDate.of(2026, 4, 30), april.endInclusive)
        assertEquals(LocalDate.of(2025, 2, 1), february.startInclusive)
        assertEquals(LocalDate.of(2025, 2, 28), february.endInclusive)
        assertEquals(LocalDate.of(2024, 2, 1), leapFebruary.startInclusive)
        assertEquals(LocalDate.of(2024, 2, 29), leapFebruary.endInclusive)
        assertTrue(leapFebruary.contains(LocalDate.of(2024, 2, 29)))
        assertFalse(february.contains(LocalDate.of(2025, 3, 1)))
    }

    @Test
    fun quartersAndHalvesUseFixedCalendarBlocks() {
        assertSpan(ReportKind.Quarterly, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))
        assertSpan(ReportKind.Quarterly, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))
        assertSpan(ReportKind.Quarterly, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30))
        assertSpan(ReportKind.Quarterly, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30))
        assertSpan(ReportKind.Quarterly, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))
        assertSpan(ReportKind.Quarterly, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))
        assertSpan(ReportKind.Quarterly, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31))
        assertSpan(ReportKind.Quarterly, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31))

        assertSpan(ReportKind.HalfYear, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))
        assertSpan(ReportKind.HalfYear, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))
        assertSpan(ReportKind.HalfYear, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31))
        assertSpan(ReportKind.HalfYear, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31))
    }

    @Test
    fun yearlyIsTheCalendarYear() {
        val year = ReportPeriod.containing(ReportKind.Yearly, LocalDate.of(2025, 3, 26))
        assertEquals(LocalDate.of(2025, 1, 1), year.startInclusive)
        assertEquals(LocalDate.of(2025, 12, 31), year.endInclusive)
        assertTrue(year.contains(LocalDate.of(2025, 1, 1)))
        assertTrue(year.contains(LocalDate.of(2025, 12, 31)))
        assertFalse(year.contains(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun previousAndNextCrossMonthQuarterHalfAndYearBoundaries() {
        val december = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2025, 12, 31))
        assertEquals(LocalDate.of(2026, 1, 1), december.next().startInclusive)
        assertEquals(LocalDate.of(2026, 1, 31), december.next().endInclusive)
        assertEquals(december, december.next().previous())

        val january = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2025, 12, 1), january.previous().startInclusive)
        assertEquals(LocalDate.of(2025, 12, 31), january.previous().endInclusive)

        val fourthQuarter = ReportPeriod.containing(ReportKind.Quarterly, LocalDate.of(2025, 12, 1))
        assertEquals(LocalDate.of(2026, 1, 1), fourthQuarter.next().startInclusive)
        assertEquals(LocalDate.of(2026, 3, 31), fourthQuarter.next().endInclusive)
        assertEquals(LocalDate.of(2025, 7, 1), fourthQuarter.previous().startInclusive)

        val secondHalf = ReportPeriod.containing(ReportKind.HalfYear, LocalDate.of(2025, 7, 1))
        assertEquals(LocalDate.of(2026, 1, 1), secondHalf.next().startInclusive)
        assertEquals(LocalDate.of(2026, 6, 30), secondHalf.next().endInclusive)
        assertEquals(LocalDate.of(2025, 1, 1), secondHalf.previous().startInclusive)
        assertEquals(LocalDate.of(2025, 6, 30), secondHalf.previous().endInclusive)

        val year = ReportPeriod.containing(ReportKind.Yearly, LocalDate.of(2025, 6, 1))
        assertEquals(LocalDate.of(2026, 1, 1), year.next().startInclusive)
        assertEquals(LocalDate.of(2024, 1, 1), year.previous().startInclusive)
        assertEquals(year, year.next().previous())
    }

    @Test
    fun identityIsKindPlusStart() {
        val january = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 1, 20))
        val again = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 1, 1))
        val quarter = ReportPeriod.containing(ReportKind.Quarterly, LocalDate.of(2026, 1, 1))
        assertEquals(january, again)
        assertFalse(january == quarter)
    }

    @Test
    fun aPeriodThatContainsTodayIsOpen() {
        val today = LocalDate.of(2026, 9, 26)
        val september = ReportPeriod.containing(ReportKind.Monthly, today)
        assertFalse(september.isClosed(today))
        assertTrue(september.previous().isClosed(today))
        assertFalse(september.isClosed(september.endInclusive))
        assertTrue(september.isClosed(september.endInclusive.plusDays(1)))
    }

    private fun assertSpan(
        kind: ReportKind,
        probe: LocalDate,
        start: LocalDate,
        end: LocalDate
    ) {
        val period = ReportPeriod.containing(kind, probe)
        assertEquals(start, period.startInclusive)
        assertEquals(end, period.endInclusive)
    }
}
