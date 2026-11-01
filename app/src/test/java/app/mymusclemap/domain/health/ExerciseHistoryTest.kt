package app.mymusclemap.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ExerciseHistoryTest {
    private val utc: ZoneId = ZoneOffset.UTC

    @Test
    fun firstGrantBoundaryKeepsThirtyDaysBeforeTheGrantAndTheSafeStartNeverPrecedesIt() {
        val grant = Instant.parse("2026-10-12T08:00:00Z")
        val now = Instant.parse("2026-11-01T11:00:00Z")
        val earliest = ExerciseHistoryBoundary.earliestWithoutHistory(grant)
        assertEquals(Instant.parse("2026-09-12T08:00:00Z"), earliest)
        assertTrue(Instant.parse("2026-09-30T06:08:00Z").isAfter(earliest))
        assertFalse(ExerciseHistoryBoundary.safeRecentStart(now).isBefore(earliest))
        assertEquals(now.minus(Duration.ofDays(30)), ExerciseHistoryBoundary.safeRecentStart(now))
    }

    @Test
    fun safeRecentStartMatchesTheBoundaryWhenPermissionWasJustGranted() {
        val now = Instant.parse("2026-11-01T11:00:00Z")
        assertEquals(
            ExerciseHistoryBoundary.earliestWithoutHistory(now),
            ExerciseHistoryBoundary.safeRecentStart(now)
        )
    }

    @Test
    fun sessionsOlderThanThirtyDaysStayInTheHistoryList() {
        val today = LocalDate.of(2026, 11, 1)
        val older = session(today.minusDays(40).atTime(8, 0).toInstant(ZoneOffset.UTC), 50, HealthExerciseKind.CYCLING, "old")
        val recent = session(today.atTime(15, 22).toInstant(ZoneOffset.UTC), 39, HealthExerciseKind.RUNNING, "new")
        val entries = ExerciseHistory.entries(listOf(older, recent))
        assertEquals(listOf(today, today.minusDays(40)), entries.map { it.date })
    }

    @Test
    fun shortSessionKeepsItsSecondsAndIsNotDropped() {
        val start = Instant.parse("2026-10-27T14:37:00Z")
        val entry = ExerciseHistory.entries(
            listOf(
                HealthExerciseSession(
                    start = start,
                    end = start.plusSeconds(30),
                    zone = utc,
                    kind = HealthExerciseKind.RUNNING,
                    id = "short"
                )
            )
        ).single()
        assertEquals(Duration.ofSeconds(30), entry.duration)
        assertEquals(LocalTime.of(14, 37), entry.startTime)
    }

    @Test
    fun crossMidnightSessionStaysOnTheStartDate() {
        val start = LocalDate.of(2026, 10, 31).atTime(23, 40).toInstant(ZoneOffset.ofHours(1))
        val entry = ExerciseHistory.entries(
            listOf(
                HealthExerciseSession(
                    start = start,
                    end = start.plus(Duration.ofMinutes(50)),
                    zone = ZoneOffset.ofHours(1),
                    kind = HealthExerciseKind.RUNNING,
                    id = "night"
                )
            )
        ).single()
        assertEquals(LocalDate.of(2026, 10, 31), entry.date)
        assertEquals(LocalTime.of(23, 40), entry.startTime)
        assertEquals(Duration.ofMinutes(50), entry.duration)
    }

    @Test
    fun daylightSavingOffsetKeepsTheLocalStartAndTheInstantDuration() {
        val zone = ZoneId.of("America/New_York")
        val start = java.time.ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, zone).toInstant()
        val end = java.time.ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, zone).toInstant()
        val entry = ExerciseHistory.entries(
            listOf(
                HealthExerciseSession(
                    start = start,
                    end = end,
                    zone = zone,
                    kind = HealthExerciseKind.CYCLING,
                    id = "dst"
                )
            )
        ).single()
        assertEquals(Duration.ofHours(1), Duration.between(start, end))
        assertEquals(LocalDate.of(2026, 3, 8), entry.date)
        assertEquals(LocalTime.of(1, 30), entry.startTime)
        assertEquals(Duration.ofHours(1), entry.duration)
    }

    @Test
    fun duplicateIdsCollapseAndDistinctSessionsOnTheSameDayStaySeparate() {
        val start = Instant.parse("2026-10-11T13:40:00Z")
        val first = session(start, 39, HealthExerciseKind.RUNNING, "same")
        val copy = first.copy()
        val other = session(start.plusSeconds(5), 20, HealthExerciseKind.RUNNING, "other")
        val entries = ExerciseHistory.entries(listOf(first, copy, other))
        assertEquals(listOf("id:other", "id:same"), entries.map { it.id })
    }

    @Test
    fun blankIdsWithTheSameSignatureCollapse() {
        val start = Instant.parse("2026-10-04T14:35:00Z")
        val a = session(start, 10, HealthExerciseKind.RUNNING, "").copy(id = "")
        val b = a.copy()
        assertEquals(1, ExerciseHistory.entries(listOf(a, b)).size)
    }

    @Test
    fun zeroDurationAndOtherKindsAreOmitted() {
        val start = Instant.parse("2026-10-03T09:00:00Z")
        val entries = ExerciseHistory.entries(
            listOf(
                HealthExerciseSession(start, start, utc, HealthExerciseKind.CYCLING, id = "zero"),
                session(start, 11, HealthExerciseKind.OTHER, "other")
            )
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun laterPageDoesNotReintroduceAnExistingSession() {
        val older = entry("old", Instant.parse("2026-09-30T06:08:00Z"))
        val newer = entry("new", Instant.parse("2026-10-31T13:22:00Z"))
        val merged = ExerciseHistory.merge(listOf(newer), listOf(newer, older))
        assertEquals(listOf("id:new", "id:old"), merged.map { it.id })
    }

    private fun session(
        start: Instant,
        minutes: Long,
        kind: HealthExerciseKind,
        id: String
    ): HealthExerciseSession {
        return HealthExerciseSession(
            start = start,
            end = start.plus(Duration.ofMinutes(minutes)),
            zone = utc,
            kind = kind,
            id = id,
            sourcePackage = "com.strava"
        )
    }

    private fun entry(id: String, start: Instant): ExerciseHistoryEntry {
        return ExerciseHistory.entries(
            listOf(session(start, 10, HealthExerciseKind.CYCLING, id))
        ).single()
    }
}
