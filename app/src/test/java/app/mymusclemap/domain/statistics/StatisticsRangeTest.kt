package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.entitlement.AppFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatisticsRangeTest {
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun thirtyDaysIsFreeAndOtherRangesRequireAdvancedStatistics() {
        assertNull(StatisticsRange.Days30.requiredFeature)
        assertTrue(StatisticsRange.Days30.isFree)
        listOf(
            StatisticsRange.Months3,
            StatisticsRange.Months6,
            StatisticsRange.Year1,
            StatisticsRange.All
        ).forEach { range ->
            assertEquals(AppFeature.AdvancedStatistics, range.requiredFeature)
            assertFalse(range.isFree)
        }
    }

    @Test
    fun thirtyDayWindowIsInclusiveOfTodayAndTwentyNinePriorDays() {
        val start = StatisticsRange.Days30.startInclusive(today)
        assertEquals(LocalDate.of(2026, 8, 18), start)
        assertTrue(StatisticsRange.Days30.contains(start!!, today))
        assertFalse(StatisticsRange.Days30.contains(start.minusDays(1), today))
        assertTrue(StatisticsRange.Days30.contains(today, today))
        assertFalse(StatisticsRange.Days30.contains(today.plusDays(1), today))
    }

    @Test
    fun threeMonthBoundaryUsesMinusMonthsInclusive() {
        val start = StatisticsRange.Months3.startInclusive(today)
        assertEquals(LocalDate.of(2026, 6, 16), start)
        assertTrue(StatisticsRange.Months3.contains(start!!, today))
        assertFalse(StatisticsRange.Months3.contains(start.minusDays(1), today))
    }

    @Test
    fun sixMonthAndYearBoundariesAreInclusive() {
        assertEquals(LocalDate.of(2026, 3, 16), StatisticsRange.Months6.startInclusive(today))
        assertEquals(LocalDate.of(2025, 9, 16), StatisticsRange.Year1.startInclusive(today))
        assertTrue(StatisticsRange.Year1.contains(LocalDate.of(2025, 9, 16), today))
        assertFalse(StatisticsRange.Year1.contains(LocalDate.of(2025, 9, 15), today))
    }

    @Test
    fun allTimeHasNoStartAndStillExcludesFutureDates() {
        assertNull(StatisticsRange.All.startInclusive(today))
        assertTrue(StatisticsRange.All.contains(LocalDate.of(2019, 1, 1), today))
        assertTrue(StatisticsRange.All.contains(today, today))
        assertFalse(StatisticsRange.All.contains(today.plusDays(1), today))
    }

    @Test
    fun fromNameFallsBackToThirtyDays() {
        assertEquals(StatisticsRange.Months6, StatisticsRange.fromName("Months6"))
        assertEquals(StatisticsRange.Days30, StatisticsRange.fromName("unknown"))
        assertEquals(StatisticsRange.Days30, StatisticsRange.fromName(null))
    }
}
