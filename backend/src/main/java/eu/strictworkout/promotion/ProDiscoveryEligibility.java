package eu.strictworkout.promotion;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Pro Discovery V3 schedule. The anchor is the account's original registration
 * until an offer has been shown, then the time that offer was shown.
 * Two calendar months are added in UTC. A device clock is not an input.
 */
public final class ProDiscoveryEligibility {

    private ProDiscoveryEligibility() {
    }

    public static Instant offerDueAt(Instant anchor) {
        return anchor.atZone(ZoneOffset.UTC).plusMonths(2).toInstant();
    }

    public static boolean offerDue(Instant anchor, Instant now) {
        return anchor != null && now != null && !now.isBefore(offerDueAt(anchor));
    }
}
