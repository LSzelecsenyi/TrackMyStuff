package eu.strictworkout.billing;

import java.time.Instant;

public final class PlayEntitlement {

    private PlayEntitlement() {
    }

    /**
     * Active, canceled-but-paid-through, and grace period keep access through [expiry].
     * Pending, paused, account hold, expired, and revoked do not.
     * Access ends at [expiry]. The instant itself is not entitled.
     */
    public static boolean entitled(String state, Instant expiry, Instant now) {
        if (state == null || expiry == null || !now.isBefore(expiry)) {
            return false;
        }
        return switch (state) {
            case "ACTIVE", "CANCELED", "IN_GRACE_PERIOD" -> true;
            default -> false;
        };
    }
}
