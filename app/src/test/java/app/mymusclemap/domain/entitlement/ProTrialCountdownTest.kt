package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ProTrialCountdownTest {
    private val expiresAt = Instant.parse("2026-08-10T12:00:00Z")

    @Test
    fun moreThanADayIncludesDaysAndHours() {
        val now = expiresAt.minusSeconds(2 * 86_400L + 5 * 3_600L)
        assertEquals("2 days 5 hours", formatTrialRemaining(now, expiresAt))
    }

    @Test
    fun lessThanADayIncludesHoursAndMinutes() {
        val now = expiresAt.minusSeconds(3 * 3_600L + 10 * 60L)
        assertEquals("3 hours 10 minutes", formatTrialRemaining(now, expiresAt))
    }

    @Test
    fun lessThanAnHourIncludesMinutesAndSeconds() {
        val now = expiresAt.minusSeconds(2 * 60L + 5)
        assertEquals("2 minutes 5 seconds", formatTrialRemaining(now, expiresAt))
    }

    @Test
    fun underAMinuteIsSeconds() {
        assertEquals("45 seconds", formatTrialRemaining(expiresAt.minusSeconds(45), expiresAt))
    }

    @Test
    fun theExpirationInstantIsZeroSeconds() {
        assertEquals("0 seconds", formatTrialRemaining(expiresAt, expiresAt))
    }
}
