package eu.strictworkout.promotion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProDiscoveryEligibilityTest {

    @Test
    void twoCalendarMonthsUseUtcMonthEnds() {
        Instant january = instant(2026, 1, 31, 23, 30);
        assertEquals(instant(2026, 3, 31, 23, 30), ProDiscoveryEligibility.offerDueAt(january));
        assertFalse(ProDiscoveryEligibility.offerDue(january, instant(2026, 3, 31, 23, 29)));
        assertTrue(ProDiscoveryEligibility.offerDue(january, instant(2026, 3, 31, 23, 30)));
    }

    @Test
    void aLeapDayMovesTwoCalendarMonthsAhead() {
        Instant leap = instant(2024, 2, 29, 8, 15);
        assertEquals(instant(2024, 4, 29, 8, 15), ProDiscoveryEligibility.offerDueAt(leap));
        Instant ordinary = instant(2023, 2, 28, 8, 15);
        assertEquals(instant(2023, 4, 28, 8, 15), ProDiscoveryEligibility.offerDueAt(ordinary));
    }

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return LocalDate.of(year, month, day).atTime(hour, minute).toInstant(ZoneOffset.UTC);
    }
}
