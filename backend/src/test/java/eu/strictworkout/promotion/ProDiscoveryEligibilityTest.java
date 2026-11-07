package eu.strictworkout.promotion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProDiscoveryEligibilityTest {

    @Test
    void sixCalendarMonthsAreNotOneHundredEightyDays() {
        LocalDate first = LocalDate.of(2024, 8, 31);
        assertEquals(LocalDate.of(2025, 2, 28), first.plusMonths(6));
        LocalDate january = LocalDate.of(2024, 1, 31);
        assertEquals(LocalDate.of(2024, 7, 31), january.plusMonths(6));
        ProDiscoveryEligibility.Progress early = ProDiscoveryEligibility.evaluate(
                qualifying(january, 22, LocalDate.of(2024, 7, 30), LocalDate.of(2024, 7, 31)),
                LocalDate.of(2024, 7, 30)
        );
        ProDiscoveryEligibility.Progress ready = ProDiscoveryEligibility.evaluate(
                qualifying(january, 22, LocalDate.of(2024, 7, 30), LocalDate.of(2024, 7, 31)),
                LocalDate.of(2024, 7, 31)
        );
        assertFalse(early.eligible());
        assertTrue(ready.eligible());
        assertEquals(2, ready.recentDays());
    }

    @Test
    void theSameDateCountsOnceAndAFutureDateIsRejected() {
        LocalDate today = LocalDate.of(2026, 11, 6);
        List<LocalDate> parsed = ProDiscoveryActivity.parse(List.of("2020-01-01", "2020-01-01"), today);
        assertEquals(List.of(LocalDate.of(2020, 1, 1)), parsed);
        assertThrows(ProDiscoverySyncException.class, () -> ProDiscoveryActivity.parse(List.of("2026-11-08"), today));
        assertThrows(ProDiscoverySyncException.class, () -> ProDiscoveryActivity.parse(List.of("2009-12-31"), today));
    }

    @Test
    void theServerUtcDateIsTheOnlyClock() {
        Instant utcEvening = LocalDate.of(2026, 11, 6).atStartOfDay(ZoneOffset.UTC).plusHours(22).toInstant();
        assertEquals(LocalDate.of(2026, 11, 6), ProDiscoveryEligibility.evaluationDate(utcEvening));
        ProDiscoveryEligibility.Progress progress = ProDiscoveryEligibility.evaluate(
                List.of(LocalDate.of(2026, 11, 6)),
                ProDiscoveryEligibility.evaluationDate(utcEvening)
        );
        assertFalse(progress.eligible());
        assertEquals(LocalDate.of(2027, 5, 6), progress.matureOn());
    }

    private static List<LocalDate> qualifying(LocalDate first, int earlyDays, LocalDate recentA, LocalDate recentB) {
        List<LocalDate> dates = new ArrayList<>();
        for (int index = 0; index < earlyDays; index++) {
            dates.add(first.plusDays(index));
        }
        dates.add(recentA);
        dates.add(recentB);
        return dates;
    }
}
