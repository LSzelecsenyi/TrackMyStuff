package eu.strictworkout.founder;

import java.time.Duration;
import java.time.Instant;

public final class FounderWindow {

    private FounderWindow() {
    }

    public static Instant deadline(Instant enrolledAt, FounderRules rules) {
        return enrolledAt.plus(Duration.ofHours(24L * rules.qualificationWindowDays()));
    }
}
