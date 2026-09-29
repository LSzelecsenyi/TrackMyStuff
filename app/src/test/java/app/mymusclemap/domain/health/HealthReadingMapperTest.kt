package app.mymusclemap.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthReadingMapperTest {
    private val today = LocalDate.of(2024, 6, 15)

    @Test
    fun windowIsThirtyDaysEndingOnTheInjectedDate() {
        val start = HealthWindow.start(today)
        assertEquals(LocalDate.of(2024, 5, 17), start)
        assertEquals(30, ChronoUnit.DAYS.between(start, today) + 1)
        val (queryStart, queryEnd) = HealthWindow.queryRange(today)
        assertEquals(start.atStartOfDay(), queryStart)
        assertEquals(today.plusDays(1).atStartOfDay(), queryEnd)
    }

    @Test
    fun dailyStepsKeepReturnedTotalsAndOmitMissingDays() {
        val mapped = HealthReadingMapper.steps(
            listOf(
                HealthMetricBucket(today.minusDays(2), 4_200),
                HealthMetricBucket(today.minusDays(1), null),
                HealthMetricBucket(today, 7_300)
            ),
            today
        )
        assertEquals(
            listOf(
                DailyStepTotal(today.minusDays(2), 4_200),
                DailyStepTotal(today, 7_300)
            ),
            mapped
        )
        assertTrue(mapped.none { it.date == today.minusDays(1) })
        assertTrue(mapped.none { it.steps == 0L })
    }

    @Test
    fun explicitZeroFromAggregationStaysAReading() {
        val mapped = HealthReadingMapper.steps(
            listOf(HealthMetricBucket(today, 0)),
            today
        )
        assertEquals(listOf(DailyStepTotal(today, 0)), mapped)
    }

    @Test
    fun restingHeartRateMapsBeatsPerMinuteAndSkipsMissingDays() {
        val mapped = HealthReadingMapper.restingHeartRate(
            listOf(
                HealthMetricBucket(today.minusDays(2), 62),
                HealthMetricBucket(today, 59)
            ),
            today
        )
        assertEquals(
            listOf(
                DailyRestingHeartRate(today.minusDays(2), 62),
                DailyRestingHeartRate(today, 59)
            ),
            mapped
        )
    }

    @Test
    fun futureAndOlderThanThirtyDaysAreDropped() {
        val mapped = HealthReadingMapper.steps(
            listOf(
                HealthMetricBucket(HealthWindow.start(today).minusDays(1), 1_000),
                HealthMetricBucket(HealthWindow.start(today), 2_000),
                HealthMetricBucket(today.plusDays(1), 9_999)
            ),
            today
        )
        assertEquals(listOf(DailyStepTotal(HealthWindow.start(today), 2_000)), mapped)
    }

    @Test
    fun yesterdayRemainsWhenTodayHasNoAggregate() {
        val today = LocalDate.of(2026, 9, 29)
        val steps = HealthReadingMapper.steps(
            listOf(
                HealthMetricBucket(LocalDate.of(2026, 9, 22), 4_200),
                HealthMetricBucket(LocalDate.of(2026, 9, 28), 7_300),
                HealthMetricBucket(today, null),
                HealthMetricBucket(today.plusDays(1), 9_999)
            ),
            today
        )
        assertEquals(
            listOf(
                DailyStepTotal(LocalDate.of(2026, 9, 22), 4_200),
                DailyStepTotal(LocalDate.of(2026, 9, 28), 7_300)
            ),
            steps
        )
        val heart = HealthReadingMapper.restingHeartRate(
            listOf(
                HealthMetricBucket(LocalDate.of(2026, 9, 28), 59),
                HealthMetricBucket(today, null)
            ),
            today
        )
        assertEquals(listOf(DailyRestingHeartRate(LocalDate.of(2026, 9, 28), 59)), heart)
    }

    @Test
    fun localMidnightKeepsThePreviousEveningOnThePreviousDate() {
        val zone = ZoneId.of("GMT")
        val today = LocalDate.of(2026, 9, 29)
        val beforeMidnight = LocalDate.of(2026, 9, 28).atTime(23, 30).atZone(zone).toLocalDate()
        val afterMidnight = today.atTime(0, 30).atZone(zone).toLocalDate()
        val morningHeart = LocalDate.of(2026, 9, 28).atTime(8, 0).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 28), beforeMidnight)
        assertEquals(today, afterMidnight)
        assertEquals(LocalDate.of(2026, 9, 28), morningHeart)
        val mapped = HealthReadingMapper.steps(
            listOf(
                HealthMetricBucket(beforeMidnight, 7_300),
                HealthMetricBucket(afterMidnight, null)
            ),
            today
        )
        assertEquals(listOf(DailyStepTotal(LocalDate.of(2026, 9, 28), 7_300)), mapped)
        val heart = HealthReadingMapper.restingHeartRate(
            listOf(HealthMetricBucket(morningHeart, 59)),
            today
        )
        assertEquals(listOf(DailyRestingHeartRate(LocalDate.of(2026, 9, 28), 59)), heart)
    }

    @Test
    fun dateBoundaryUsesTheInjectedLocalDateRatherThanAnotherZone() {
        val instant = LocalDate.of(2024, 6, 15).atTime(20, 0)
            .atZone(ZoneId.of("America/Los_Angeles"))
            .toInstant()
        val pacific = instant.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate()
        val auckland = instant.atZone(ZoneId.of("Pacific/Auckland")).toLocalDate()
        assertEquals(LocalDate.of(2024, 6, 15), pacific)
        assertEquals(LocalDate.of(2024, 6, 16), auckland)
        val mapped = HealthReadingMapper.steps(
            listOf(
                HealthMetricBucket(auckland, 100),
                HealthMetricBucket(pacific, 50)
            ),
            pacific
        )
        assertEquals(listOf(DailyStepTotal(pacific, 50)), mapped)
    }
}
