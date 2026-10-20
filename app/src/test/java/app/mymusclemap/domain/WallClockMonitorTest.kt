package app.mymusclemap.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WallClockMonitorTest {
    private val morning = Instant.parse("2026-03-11T09:00:00Z")
    private val boundary = Instant.parse("2026-03-11T10:00:00Z")

    @Test
    fun minuteSamplesDoNotRepeatDayOrBoundaryEffects() = runTest {
        var days = 0
        var boundaries = 0
        val monitor = WallClockMonitor { now ->
            boundary.takeIf { now.isBefore(it) }
        }
        fun sample(instant: Instant, day: LocalDate) {
            val effect = monitor.onSample(instant, day)
            if (effect.dayChanged) days += 1
            if (effect.boundaryReached) boundaries += 1
        }

        sample(morning, LocalDate.of(2026, 3, 11))
        sample(morning.plusSeconds(60), LocalDate.of(2026, 3, 11))
        sample(morning.plusSeconds(120), LocalDate.of(2026, 3, 11))
        assertEquals(0, days)
        assertEquals(0, boundaries)

        sample(boundary, LocalDate.of(2026, 3, 11))
        sample(boundary.plusSeconds(60), LocalDate.of(2026, 3, 11))
        assertEquals(0, days)
        assertEquals(1, boundaries)

        sample(Instant.parse("2026-03-12T00:05:00Z"), LocalDate.of(2026, 3, 12))
        sample(Instant.parse("2026-03-12T00:06:00Z"), LocalDate.of(2026, 3, 12))
        assertEquals(1, days)
        assertEquals(1, boundaries)
    }

    @Test
    fun clockRollbackDoesNotFireTheSameBoundaryTwice() {
        val monitor = WallClockMonitor { now ->
            boundary.takeIf { now.isBefore(it) }
        }
        assertFalse(monitor.onSample(morning, LocalDate.of(2026, 3, 11)).boundaryReached)
        assertTrue(monitor.onSample(boundary, LocalDate.of(2026, 3, 11)).boundaryReached)
        assertFalse(monitor.onSample(morning, LocalDate.of(2026, 3, 11)).boundaryReached)
        assertFalse(monitor.onSample(boundary.plusSeconds(30), LocalDate.of(2026, 3, 11)).boundaryReached)
    }

    @Test
    fun sameInstantAcrossZonesReportsOneDayChange() {
        val monitor = WallClockMonitor { null }
        val instant = Instant.parse("2026-03-28T23:30:00Z")
        assertFalse(monitor.onSample(instant, LocalDate.of(2026, 3, 28)).dayChanged)
        assertTrue(monitor.onSample(instant, LocalDate.of(2026, 3, 29)).dayChanged)
        assertFalse(monitor.onSample(instant, LocalDate.of(2026, 3, 29)).dayChanged)
    }

}
