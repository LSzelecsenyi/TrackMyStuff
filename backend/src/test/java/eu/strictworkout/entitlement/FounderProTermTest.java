package eu.strictworkout.entitlement;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FounderProTermTest {

    @Test
    void twelveCalendarMonthsKeepTheUtcTimeOfDay() {
        Instant grantedAt = Instant.parse("2026-03-15T18:45:01Z");
        assertEquals(Instant.parse("2027-03-15T18:45:01Z"), FounderProTerm.expiresAt(grantedAt));
    }

    @Test
    void monthEndAndLeapDayClampToARealUtcDay() {
        assertEquals(
                Instant.parse("2027-01-31T00:00:00Z"),
                FounderProTerm.expiresAt(Instant.parse("2026-01-31T00:00:00Z"))
        );
        assertEquals(
                Instant.parse("2025-02-28T23:30:00Z"),
                FounderProTerm.expiresAt(Instant.parse("2024-02-29T23:30:00Z"))
        );
        assertEquals(
                Instant.parse("2027-02-28T12:00:00Z"),
                FounderProTerm.expiresAt(Instant.parse("2026-02-28T12:00:00Z"))
        );
    }

    @Test
    void proIsActiveOnlyBeforeTheExpirationInstant() {
        Instant expiresAt = Instant.parse("2027-03-15T18:45:01Z");
        assertTrue(FounderProTerm.active(expiresAt, expiresAt.minusNanos(1)));
        assertFalse(FounderProTerm.active(expiresAt, expiresAt));
        assertFalse(FounderProTerm.active(expiresAt, expiresAt.plusSeconds(1)));
        assertFalse(FounderProTerm.active(null, expiresAt));
    }
}
