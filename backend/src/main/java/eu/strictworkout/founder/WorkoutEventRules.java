package eu.strictworkout.founder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class WorkoutEventRules {

    public static final Duration FUTURE_SKEW = Duration.ofHours(36);
    private static final int MIN_OFFSET_HOURS = -12;
    private static final int MAX_OFFSET_HOURS = 14;

    private WorkoutEventRules() {
    }

    public static boolean localDateMatchesInstant(Instant completedAt, LocalDate localDate) {
        LocalDate earliest = completedAt.atOffset(ZoneOffset.ofHours(MIN_OFFSET_HOURS)).toLocalDate();
        LocalDate latest = completedAt.atOffset(ZoneOffset.ofHours(MAX_OFFSET_HOURS)).toLocalDate();
        return !localDate.isBefore(earliest) && !localDate.isAfter(latest);
    }

    public static boolean completedTooFarInTheFuture(Instant completedAt, Instant now) {
        return completedAt.isAfter(now.plus(FUTURE_SKEW));
    }

    public static boolean insideQualificationWindow(Instant enrolledAt, Instant deadlineAt, Instant completedAt) {
        return !completedAt.isBefore(enrolledAt) && !completedAt.isAfter(deadlineAt);
    }
}
