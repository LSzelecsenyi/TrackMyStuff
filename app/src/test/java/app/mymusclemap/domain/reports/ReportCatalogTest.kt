package app.mymusclemap.domain.reports

import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportCatalogTest {
    private val historyStart = LocalDate.of(2025, 3, 26)
    private val today = LocalDate.of(2026, 9, 26)

    @Test
    fun latestClosedPeriodsOn26September2026() {
        assertEquals(
            LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31),
            span(ReportKind.Monthly)
        )
        assertEquals(
            LocalDate.of(2026, 4, 1) to LocalDate.of(2026, 6, 30),
            span(ReportKind.Quarterly)
        )
        assertEquals(
            LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 6, 30),
            span(ReportKind.HalfYear)
        )
        assertEquals(
            LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 12, 31),
            span(ReportKind.Yearly)
        )
        assertTrue(closed(ReportKind.Monthly).none { it.period.contains(today) })
        assertTrue(closed(ReportKind.Quarterly).none { it.period.startInclusive == LocalDate.of(2026, 7, 1) })
        assertTrue(closed(ReportKind.HalfYear).none { it.period.startInclusive == LocalDate.of(2026, 7, 1) })
        assertTrue(closed(ReportKind.Yearly).none { it.period.startInclusive == LocalDate.of(2026, 1, 1) })
    }

    @Test
    fun aPeriodBecomesAvailableOnlyAfterItHasFullyEnded() {
        val longHistory = LocalDate.of(2020, 1, 1)

        val lastDayOfAugust = catalog(ReportKind.Monthly, LocalDate.of(2026, 8, 31), longHistory).first()
        assertEquals(LocalDate.of(2026, 7, 1), lastDayOfAugust.period.startInclusive)

        val firstOfSeptember = catalog(ReportKind.Monthly, LocalDate.of(2026, 9, 1), longHistory).first()
        assertEquals(LocalDate.of(2026, 8, 1), firstOfSeptember.period.startInclusive)

        val lastDayOfSeptember = catalog(ReportKind.Monthly, LocalDate.of(2026, 9, 30), longHistory).first()
        assertEquals(LocalDate.of(2026, 8, 1), lastDayOfSeptember.period.startInclusive)

        val firstOfOctober = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 9, 1), catalog(ReportKind.Monthly, firstOfOctober, longHistory).first().period.startInclusive)
        assertEquals(LocalDate.of(2026, 7, 1), catalog(ReportKind.Quarterly, firstOfOctober, longHistory).first().period.startInclusive)
        assertEquals(LocalDate.of(2026, 1, 1), catalog(ReportKind.HalfYear, firstOfOctober, longHistory).first().period.startInclusive)
        assertEquals(LocalDate.of(2025, 1, 1), catalog(ReportKind.Yearly, firstOfOctober, longHistory).first().period.startInclusive)

        val newYear = LocalDate.of(2027, 1, 1)
        assertEquals(LocalDate.of(2026, 12, 1), catalog(ReportKind.Monthly, newYear, longHistory).first().period.startInclusive)
        assertEquals(LocalDate.of(2026, 10, 1), catalog(ReportKind.Quarterly, newYear, longHistory).first().period.startInclusive)
        assertEquals(LocalDate.of(2026, 7, 1), catalog(ReportKind.HalfYear, newYear, longHistory).first().period.startInclusive)
        val year2026 = catalog(ReportKind.Yearly, newYear, longHistory).first()
        assertEquals(LocalDate.of(2026, 1, 1), year2026.period.startInclusive)
        assertEquals(LocalDate.of(2026, 12, 31), year2026.period.endInclusive)
        assertEquals(ReportHistoryCoverage.Complete, year2026.coverage)
        assertTrue(catalog(ReportKind.Yearly, LocalDate.of(2026, 12, 31), longHistory).none {
            it.period.startInclusive == LocalDate.of(2026, 1, 1)
        })
    }

    @Test
    fun partialHistoryKeepsTheCalendarPeriodAndDropsEarlierEmptyOnes() {
        val monthly = closed(ReportKind.Monthly)
        val march = monthly.last()
        val april = monthly[monthly.lastIndex - 1]
        assertEquals(LocalDate.of(2025, 3, 1), march.period.startInclusive)
        assertEquals(LocalDate.of(2025, 3, 31), march.period.endInclusive)
        assertEquals(ReportHistoryCoverage.Partial, march.coverage)
        assertEquals(LocalDate.of(2025, 4, 1), april.period.startInclusive)
        assertEquals(ReportHistoryCoverage.Complete, april.coverage)
        assertTrue(monthly.none { it.period.endInclusive.isBefore(historyStart) })

        val quarters = closed(ReportKind.Quarterly)
        assertEquals(LocalDate.of(2025, 1, 1), quarters.last().period.startInclusive)
        assertEquals(LocalDate.of(2025, 3, 31), quarters.last().period.endInclusive)
        assertEquals(ReportHistoryCoverage.Partial, quarters.last().coverage)
        val secondQuarter = quarters.single { it.period.startInclusive == LocalDate.of(2025, 4, 1) }
        assertEquals(ReportHistoryCoverage.Complete, secondQuarter.coverage)
        assertTrue(quarters.none { it.period.startInclusive == LocalDate.of(2024, 10, 1) })

        val halves = closed(ReportKind.HalfYear)
        assertEquals(LocalDate.of(2025, 1, 1), halves.last().period.startInclusive)
        assertEquals(ReportHistoryCoverage.Partial, halves.last().coverage)
        val secondHalf = halves.single { it.period.startInclusive == LocalDate.of(2025, 7, 1) }
        assertEquals(LocalDate.of(2025, 12, 31), secondHalf.period.endInclusive)
        assertEquals(ReportHistoryCoverage.Complete, secondHalf.coverage)
        assertTrue(halves.none { it.period.startInclusive == LocalDate.of(2024, 7, 1) })

        val years = closed(ReportKind.Yearly)
        assertEquals(1, years.size)
        assertEquals(LocalDate.of(2025, 1, 1), years.single().period.startInclusive)
        assertEquals(LocalDate.of(2025, 12, 31), years.single().period.endInclusive)
        assertEquals(ReportHistoryCoverage.Partial, years.single().coverage)

        val year2026 = catalog(ReportKind.Yearly, LocalDate.of(2027, 1, 1), historyStart).first()
        assertEquals(LocalDate.of(2026, 1, 1), year2026.period.startInclusive)
        assertEquals(ReportHistoryCoverage.Complete, year2026.coverage)
    }

    @Test
    fun catalogIsNewestFirstAndStopsAtTheFirstIntersectingPeriod() {
        val monthly = closed(ReportKind.Monthly)
        assertEquals(LocalDate.of(2026, 8, 1), monthly.first().period.startInclusive)
        assertEquals(LocalDate.of(2025, 3, 1), monthly.last().period.startInclusive)
        assertTrue(monthly.zipWithNext().all { (newer, older) -> older.period == newer.period.previous() })
        assertEquals(18, monthly.size)
    }

    @Test
    fun noPersistedHistoryProducesAnEmptyCatalog() {
        assertNull(ReportHistory.earliestDate(ReportHistoryEvidence(), today))
        ReportKind.entries.forEach { kind ->
            assertTrue(catalog(kind, today, historyStart = null).isEmpty())
        }
        val futureOnly = ReportHistoryEvidence(
            sessionWorkoutDates = listOf(today.plusDays(2)),
            scheduledOccurrenceDates = listOf(today.plusDays(9)),
            bodyWeightDates = listOf(today.plusDays(1))
        )
        assertNull(ReportHistory.earliestDate(futureOnly, today))
        assertTrue(catalog(ReportKind.Monthly, today, ReportHistory.earliestDate(futureOnly, today)).isEmpty())
    }

    @Test
    fun historyStartsAtTheEarliestRecordOnOrBeforeToday() {
        val evidence = ReportHistoryEvidence(
            sessionWorkoutDates = listOf(LocalDate.of(2025, 4, 2), today.plusDays(1)),
            scheduledOccurrenceDates = listOf(LocalDate.of(2025, 3, 30), today.plusMonths(1)),
            bodyWeightDates = listOf(LocalDate.of(2025, 3, 26))
        )
        assertEquals(historyStart, ReportHistory.earliestDate(evidence, today))
    }

    @Test
    fun persistedSourcesIncludeCancelledSchedulesUnplannedWorkoutsAndWeightsButNotFutureOrOriginalDates() {
        val original = LocalDate.of(2025, 2, 10)
        val scheduledDate = LocalDate.of(2025, 4, 3)
        val evidence = ReportHistoryEvidence.fromPersisted(
            sessions = listOf(
                session(LocalDate.of(2025, 4, 8), scheduledWorkoutId = null),
                session(today.plusDays(3), scheduledWorkoutId = null)
            ),
            scheduled = listOf(
                planned(
                    scheduledDate = scheduledDate,
                    originalScheduledDate = original,
                    cancelledAt = 10L
                )
            ),
            bodyWeights = listOf(weight(LocalDate.of(2025, 4, 1)))
        )
        assertEquals(LocalDate.of(2025, 4, 1), ReportHistory.earliestDate(evidence, today))
        assertTrue(evidence.scheduledOccurrenceDates.none { it == original })
        val april = catalog(ReportKind.Monthly, today, ReportHistory.earliestDate(evidence, today))
        assertEquals(LocalDate.of(2025, 4, 1), april.last().period.startInclusive)
        assertEquals(ReportHistoryCoverage.Complete, april.last().coverage)
        assertTrue(april.none { it.period.startInclusive == LocalDate.of(2025, 2, 1) })
    }

    @Test
    fun aRecordOnTodayDoesNotOpenTheStillOpenPeriod() {
        val start = ReportHistory.earliestDate(
            ReportHistoryEvidence(bodyWeightDates = listOf(today)),
            today
        )
        assertEquals(today, start)
        assertTrue(catalog(ReportKind.Monthly, today, start).isEmpty())
    }

    private fun closed(kind: ReportKind): List<AvailableReport> {
        return catalog(kind, today, historyStart)
    }

    private fun catalog(
        kind: ReportKind,
        today: LocalDate,
        historyStart: LocalDate?
    ): List<AvailableReport> {
        return ReportCatalog.available(kind, today, historyStart)
    }

    private fun span(kind: ReportKind): Pair<LocalDate, LocalDate> {
        val period = closed(kind).first().period
        return period.startInclusive to period.endInclusive
    }

    private fun session(date: LocalDate, scheduledWorkoutId: Long?): WorkoutSession {
        return WorkoutSession(
            id = 1L,
            templateId = null,
            templateName = "Hub",
            status = SessionStatus.COMPLETED,
            workoutDate = date,
            startedAt = 1L,
            finishedAt = 2L,
            abandonedAt = null,
            notes = null,
            bodyWeightKg = null,
            bodyWeightSource = BodyWeightSource.MANUAL,
            bodyWeightSourceDate = null,
            createdAt = 1L,
            updatedAt = 1L,
            scheduledWorkoutId = scheduledWorkoutId
        )
    }

    private fun planned(
        scheduledDate: LocalDate,
        originalScheduledDate: LocalDate,
        cancelledAt: Long?
    ): ScheduledWorkout {
        return ScheduledWorkout(
            id = 1L,
            scheduledDate = scheduledDate,
            templateId = null,
            templateName = "Push",
            exerciseCount = 1,
            plannedSetCount = 1,
            templateArchived = false,
            sessionId = null,
            sessionStatus = null,
            createdAt = 1L,
            originalScheduledDate = originalScheduledDate,
            cancelledAt = cancelledAt
        )
    }

    private fun weight(date: LocalDate): WeightMeasurement {
        return WeightMeasurement(
            id = 1L,
            date = date,
            weightKg = 80.0,
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
