package hu.laca.weighttracker.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

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
}
