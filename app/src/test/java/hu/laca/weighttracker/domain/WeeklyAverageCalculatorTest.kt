package hu.laca.weighttracker.domain

import hu.laca.weighttracker.measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyAverageCalculatorTest {
    private val todayComplete = LocalDate.of(2026, 3, 18)
    private val todayPartial = LocalDate.of(2026, 3, 11)

    @Test
    fun weeklyAverageWithEveryDayMeasured() {
        val week = listOf(
            measurement("2026-03-09", 80.0),
            measurement("2026-03-10", 81.0),
            measurement("2026-03-11", 82.0),
            measurement("2026-03-12", 83.0),
            measurement("2026-03-13", 84.0),
            measurement("2026-03-14", 85.0),
            measurement("2026-03-15", 86.0)
        )
        val result = WeeklyAverageCalculator.calculate(week, todayComplete).single()
        assertEquals(7, result.sampleCount)
        assertEquals(83.0, result.averageKg, 0.0001)
        assertEquals(LocalDate.of(2026, 3, 9), result.weekStart)
        assertEquals(LocalDate.of(2026, 3, 15), result.weekEnd)
        assertEquals(LocalDate.of(2026, 3, 15), result.coveredEnd)
        assertFalse(result.isCurrentWeek)
    }

    @Test
    fun weeklyAverageWithMissingDaysDoesNotCountThemAsZero() {
        val week = listOf(
            measurement("2026-03-09", 80.0),
            measurement("2026-03-15", 90.0)
        )
        val result = WeeklyAverageCalculator.calculate(week, todayComplete).single()
        assertEquals(2, result.sampleCount)
        assertEquals(85.0, result.averageKg, 0.0001)
    }

    @Test
    fun currentPartialWeekUsesMeasurementsRecordedSoFar() {
        val measurements = listOf(
            measurement("2026-03-09", 80.0),
            measurement("2026-03-10", 82.0),
            measurement("2026-03-11", 84.0)
        )
        val result = WeeklyAverageCalculator.calculate(measurements, todayPartial).single()
        assertTrue(result.isCurrentWeek)
        assertEquals(3, result.sampleCount)
        assertEquals(82.0, result.averageKg, 0.0001)
        assertEquals(LocalDate.of(2026, 3, 9), result.weekStart)
        assertEquals(LocalDate.of(2026, 3, 15), result.weekEnd)
        assertEquals(todayPartial, result.coveredEnd)
    }

    @Test
    fun measurementsSpanningTwoIsoWeeksCreateTwoAverages() {
        val measurements = listOf(
            measurement("2026-03-08", 80.0),
            measurement("2026-03-09", 90.0)
        )
        val result = WeeklyAverageCalculator.calculate(measurements, todayComplete)
        assertEquals(2, result.size)
        assertEquals(LocalDate.of(2026, 3, 9), result[0].weekStart)
        assertEquals(90.0, result[0].averageKg, 0.0001)
        assertEquals(LocalDate.of(2026, 3, 2), result[1].weekStart)
        assertEquals(80.0, result[1].averageKg, 0.0001)
    }

    @Test
    fun yearBoundaryIsoWeekKeepsDecemberAndJanuaryTogether() {
        val measurements = listOf(
            measurement("2020-12-28", 80.0),
            measurement("2021-01-03", 90.0)
        )
        val result = WeeklyAverageCalculator.calculate(
            measurements,
            LocalDate.of(2021, 1, 10)
        )
        assertEquals(1, result.size)
        assertEquals(2020, result.single().weekBasedYear)
        assertEquals(53, result.single().weekOfYear)
        assertEquals(85.0, result.single().averageKg, 0.0001)
        assertEquals(LocalDate.of(2020, 12, 28), result.single().weekStart)
        assertEquals(LocalDate.of(2021, 1, 3), result.single().weekEnd)
    }

    @Test
    fun yearBoundarySplitsAdjacentIsoWeeks() {
        val measurements = listOf(
            measurement("2024-12-29", 70.0),
            measurement("2024-12-30", 80.0)
        )
        val result = WeeklyAverageCalculator.calculate(
            measurements,
            LocalDate.of(2025, 1, 6)
        )
        assertEquals(2, result.size)
        assertEquals(2025, result[0].weekBasedYear)
        assertEquals(1, result[0].weekOfYear)
        assertEquals(80.0, result[0].averageKg, 0.0001)
        assertEquals(2024, result[1].weekBasedYear)
        assertEquals(52, result[1].weekOfYear)
        assertEquals(70.0, result[1].averageKg, 0.0001)
    }
}
