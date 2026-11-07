package eu.strictworkout.billing;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Decides when a Play subscription has ended for the 24-month history window.
 * Entitled, paused, pending, and account-hold subscriptions stay recoverable.
 * Cancellation does not start the window while access continues through expiry.
 */
public final class SubscriptionEndedAt {

    private SubscriptionEndedAt() {
    }

    public static Instant advance(Instant previous, String state, Instant expiry, boolean entitled, Instant now) {
        String normalized = normalize(state);
        if (entitled || recoverableWithoutEntitlement(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "EXPIRED", "ACTIVE", "CANCELED", "IN_GRACE_PERIOD" ->
                    expiry != null && !expiry.isAfter(now) ? expiry : null;
            case "REVOKED", "PENDING_PURCHASE_CANCELED" ->
                    previous == null || previous.isAfter(now) ? now : previous;
            default -> null;
        };
    }

    public static boolean retentionElapsed(Instant endedAt, Instant now) {
        if (endedAt == null || now == null) {
            return false;
        }
        ZonedDateTime deadline = endedAt.atZone(ZoneOffset.UTC).plusMonths(24);
        return !deadline.toInstant().isAfter(now);
    }

    /**
     * A row may be deleted only when the stored end is still a terminal state
     * and the 24-calendar-month window has elapsed. Unknown, paused, pending,
     * and held rows are kept.
     */
    public static boolean removable(
            String state,
            Instant expiry,
            boolean entitled,
            Instant endedAt,
            boolean retentionHold,
            Instant now
    ) {
        if (retentionHold || entitled || endedAt == null || !retentionElapsed(endedAt, now)) {
            return false;
        }
        String normalized = normalize(state);
        return switch (normalized) {
            case "EXPIRED", "ACTIVE", "CANCELED", "IN_GRACE_PERIOD" -> expiry != null && !expiry.isAfter(now);
            case "REVOKED", "PENDING_PURCHASE_CANCELED" -> true;
            default -> false;
        };
    }

    private static boolean recoverableWithoutEntitlement(String state) {
        return switch (state) {
            case "PAUSED", "ON_HOLD", "PENDING" -> true;
            default -> false;
        };
    }

    private static String normalize(String state) {
        if (state == null) {
            return "";
        }
        String value = state.trim();
        String prefix = "SUBSCRIPTION_STATE_";
        if (value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        return value;
    }
}
