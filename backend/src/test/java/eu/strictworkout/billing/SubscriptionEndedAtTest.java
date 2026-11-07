package eu.strictworkout.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionEndedAtTest {

    private static final Instant ENDED = Instant.parse("2024-01-31T00:00:00Z");
    private static final Instant DAY_BEFORE = Instant.parse("2026-01-30T23:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-01-31T00:00:00Z");

    @Test
    @DisplayName("Given a subscription is entitled or paused, when its end is calculated, then no retention clock starts")
    void recoverableStatesDoNotEnd() {
        assertNull(SubscriptionEndedAt.advance(ENDED, "ACTIVE", DEADLINE, true, DEADLINE));
        assertNull(SubscriptionEndedAt.advance(ENDED, "CANCELED", DEADLINE, true, ENDED));
        assertNull(SubscriptionEndedAt.advance(ENDED, "IN_GRACE_PERIOD", DEADLINE, true, ENDED));
        assertNull(SubscriptionEndedAt.advance(ENDED, "PAUSED", ENDED, false, DEADLINE));
        assertNull(SubscriptionEndedAt.advance(ENDED, "ON_HOLD", ENDED, false, DEADLINE));
        assertNull(SubscriptionEndedAt.advance(ENDED, "PENDING", ENDED, false, DEADLINE));
    }

    @Test
    @DisplayName("Given a canceled subscription is still inside its paid period, when its end is calculated, then cancellation time is not used")
    void cancellationDoesNotStartRetention() {
        Instant expiry = Instant.parse("2026-12-01T00:00:00Z");
        Instant canceledAt = Instant.parse("2026-06-01T00:00:00Z");
        assertNull(SubscriptionEndedAt.advance(null, "CANCELED", expiry, true, canceledAt));
    }

    @Test
    @DisplayName("Given a subscription has expired, when 24 calendar months have not elapsed, then it is kept")
    void twentyFourCalendarMonths() {
        assertFalse(SubscriptionEndedAt.retentionElapsed(ENDED, DAY_BEFORE));
        assertTrue(SubscriptionEndedAt.retentionElapsed(ENDED, DEADLINE));
        assertFalse(SubscriptionEndedAt.removable("EXPIRED", ENDED, false, ENDED, false, DAY_BEFORE));
        assertTrue(SubscriptionEndedAt.removable("EXPIRED", ENDED, false, ENDED, false, DEADLINE));
    }

    @Test
    @DisplayName("Given the end time or the Play state cannot be verified, when retention is evaluated, then the purchase is kept")
    void ambiguousRowsAreKept() {
        assertNull(SubscriptionEndedAt.advance(null, "EXPIRED", null, false, DEADLINE));
        assertNull(SubscriptionEndedAt.advance(null, "SOMETHING_NEW", ENDED, false, DEADLINE));
        assertFalse(SubscriptionEndedAt.removable("EXPIRED", null, false, ENDED, false, DEADLINE));
        assertFalse(SubscriptionEndedAt.removable("PAUSED", ENDED, false, ENDED, false, DEADLINE));
        assertFalse(SubscriptionEndedAt.removable("EXPIRED", ENDED, false, ENDED, true, DEADLINE));
        assertFalse(SubscriptionEndedAt.removable("ACTIVE", DEADLINE, true, ENDED, false, DEADLINE));
    }

    @Test
    void revokedUsesTheFirstConfirmedInstantAndDoesNotMoveLater() {
        Instant first = Instant.parse("2026-03-01T00:00:00Z");
        Instant later = first.plusSeconds(86_400);
        assertEquals(first, SubscriptionEndedAt.advance(null, "REVOKED", DEADLINE, false, first));
        assertEquals(first, SubscriptionEndedAt.advance(first, "REVOKED", DEADLINE, false, later));
    }
}
