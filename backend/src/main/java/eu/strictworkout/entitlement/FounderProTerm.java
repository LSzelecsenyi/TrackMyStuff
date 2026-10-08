package eu.strictworkout.entitlement;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Twelve calendar months in UTC, using {@link java.time.ZonedDateTime#plusMonths(long)}.
 * The time of day is kept. A day that does not exist in the target month clamps to the
 * last valid day, so 29 February becomes 28 February a year later. Pro is active only
 * while {@code now} is strictly before {@code expiresAt}.
 */
public final class FounderProTerm {

    private FounderProTerm() {
    }

    public static Instant expiresAt(Instant grantedAt) {
        return grantedAt.atZone(ZoneOffset.UTC).plusMonths(12).toInstant();
    }

    public static boolean active(Instant expiresAt, Instant now) {
        return expiresAt != null && now.isBefore(expiresAt);
    }
}
