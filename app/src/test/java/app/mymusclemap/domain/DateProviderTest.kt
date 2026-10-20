package app.mymusclemap.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.suspendCancellableCoroutine

class DateProviderTest {
    @Test
    fun givenBudapestAfterUtcMidnightWhenTodayThenLocalCalendarDayIsUsed() {
        val budapest = ZoneId.of("Europe/Budapest")
        val instant = Instant.parse("2026-03-28T23:30:00Z")
        val local = SystemDateProvider(Clock.fixed(instant, budapest))
        val utc = SystemDateProvider(Clock.fixed(instant, ZoneOffset.UTC))
        assertEquals(LocalDate.of(2026, 3, 29), local.today())
        assertEquals(LocalDate.of(2026, 3, 28), utc.today())
        assertNotEquals(utc.today(), local.today())
    }

    @Test
    fun givenBudapestBeforeLocalMidnightWhenTodayThenPreviousCalendarDayIsKept() {
        val budapest = ZoneId.of("Europe/Budapest")
        val instant = Instant.parse("2026-03-28T21:30:00Z")
        val provider = SystemDateProvider(Clock.fixed(instant, budapest))
        assertEquals(LocalDate.of(2026, 3, 28), provider.today())
        assertEquals(LocalTime.of(22, 30), provider.now().toLocalTime())
    }

    @Test
    fun givenSpringDstGapWhenTodayThenCalendarDayIsNeitherCreatedNorSkipped() {
        val budapest = ZoneId.of("Europe/Budapest")
        val beforeGap = SystemDateProvider(
            Clock.fixed(Instant.parse("2026-03-28T23:30:00Z"), budapest)
        )
        val afterGap = SystemDateProvider(
            Clock.fixed(Instant.parse("2026-03-29T01:30:00Z"), budapest)
        )
        assertEquals(LocalDate.of(2026, 3, 29), beforeGap.today())
        assertEquals(LocalDate.of(2026, 3, 29), afterGap.today())
        assertEquals(LocalTime.of(3, 30), afterGap.now().toLocalTime())
    }

    @Test
    fun givenFallDstOverlapWhenTodayThenSingleCalendarDayIsKept() {
        val budapest = ZoneId.of("Europe/Budapest")
        val firstPass = SystemDateProvider(
            Clock.fixed(Instant.parse("2026-10-25T00:30:00Z"), budapest)
        )
        val secondPass = SystemDateProvider(
            Clock.fixed(Instant.parse("2026-10-25T01:30:00Z"), budapest)
        )
        assertEquals(LocalDate.of(2026, 10, 25), firstPass.today())
        assertEquals(LocalDate.of(2026, 10, 25), secondPass.today())
    }

    @Test
    fun givenMutableDateProviderWhenDateChangesThenObserveTodayEmitsNewDay() = runTest {
        val provider = MutableDateProvider(LocalDate.of(2026, 9, 15), LocalTime.of(23, 50))
        assertEquals(LocalDate.of(2026, 9, 15), provider.observeToday().first())
        provider.setNow(LocalDate.of(2026, 9, 16), LocalTime.of(0, 5))
        assertEquals(LocalDate.of(2026, 9, 16), provider.today())
        assertEquals(LocalDate.of(2026, 9, 16), provider.observeToday().first())
    }

    @Test
    fun directReadsStayFreshWhileTheUiSnapshotWaitsForASample() = runTest {
        val clock = AdjustableClock(Instant.parse("2026-10-20T08:00:00Z"), ZoneOffset.UTC)
        val provider = SystemDateProvider(clock)
        assertEquals(LocalDateTime.of(2026, 10, 20, 8, 0), provider.observeNow().first())
        clock.setInstant(Instant.parse("2026-10-20T11:00:00Z"))
        assertEquals(LocalDateTime.of(2026, 10, 20, 11, 0), provider.now())
        assertEquals(LocalDate.of(2026, 10, 20), provider.today())
        assertEquals(LocalDateTime.of(2026, 10, 20, 8, 0), provider.observeNow().first())
        provider.onWallClockChanged()
        assertEquals(LocalDateTime.of(2026, 10, 20, 11, 0), provider.observeNow().first())
    }

    @Test
    fun foregroundResumeAfterHoursRefreshesObservedTimeWithoutATickerInBackground() = runTest {
        val clock = AdjustableClock(Instant.parse("2026-10-20T08:01:00Z"), ZoneOffset.UTC)
        var waits = 0
        val provider = SystemDateProvider(clock) {
            waits += 1
            suspendCancellableCoroutine { }
        }
        provider.bind(backgroundScope)
        provider.onForeground()
        testScheduler.runCurrent()
        assertTrue(provider.isUiTickerRunning())
        assertEquals(1, waits)

        provider.onBackground()
        testScheduler.runCurrent()
        assertFalse(provider.isUiTickerRunning())
        val waitsWhileHidden = waits
        clock.setInstant(Instant.parse("2026-10-20T14:01:00Z"))
        assertEquals(LocalDateTime.of(2026, 10, 20, 8, 1), provider.observeNow().first())
        assertEquals(waitsWhileHidden, waits)
        assertFalse(provider.isUiTickerRunning())

        provider.onForeground()
        assertEquals(LocalDateTime.of(2026, 10, 20, 14, 1), provider.observeNow().first())
        assertEquals(LocalDate.of(2026, 10, 20), provider.observeToday().first())
        provider.onBackground()
        testScheduler.runCurrent()
    }

    @Test
    fun foregroundSampleCrossesMidnightMonthAndYear() = runTest {
        val clock = AdjustableClock(Instant.parse("2026-12-31T23:50:00Z"), ZoneOffset.UTC)
        val provider = SystemDateProvider(clock)
        assertEquals(LocalDate.of(2026, 12, 31), provider.observeToday().first())
        clock.setInstant(Instant.parse("2027-01-01T00:05:00Z"))
        provider.onForeground()
        assertEquals(LocalDate.of(2027, 1, 1), provider.observeToday().first())
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 5), provider.observeNow().first())
    }

    @Test
    fun timezoneChangeUpdatesTheObservedLocalDate() = runTest {
        val instant = Instant.parse("2026-03-28T23:30:00Z")
        val clock = AdjustableClock(instant, ZoneOffset.UTC)
        val provider = SystemDateProvider(clock)
        assertEquals(LocalDate.of(2026, 3, 28), provider.observeToday().first())
        clock.setZone(ZoneId.of("Europe/Budapest"))
        provider.onWallClockChanged()
        assertEquals(LocalDate.of(2026, 3, 29), provider.today())
        assertEquals(LocalDate.of(2026, 3, 29), provider.observeToday().first())
    }

    @Test
    fun manualClockAdjustmentIsVisibleOnTheNextSample() = runTest {
        val clock = AdjustableClock(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC)
        val provider = SystemDateProvider(clock)
        clock.setInstant(Instant.parse("2026-06-15T09:00:00Z"))
        provider.onWallClockChanged()
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 0), provider.observeNow().first())
        clock.setInstant(Instant.parse("2026-06-16T09:00:00Z"))
        provider.onWallClockChanged()
        assertEquals(LocalDate.of(2026, 6, 16), provider.observeToday().first())
    }

    @Test
    fun systemWallClockRereadsTheDefaultZone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val clock = SystemWallClock()
            assertEquals(ZoneId.of("UTC"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(ZoneId.of("America/Los_Angeles"), clock.zone)
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
