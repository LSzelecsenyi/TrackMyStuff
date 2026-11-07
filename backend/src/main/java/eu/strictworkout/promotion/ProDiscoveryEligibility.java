package eu.strictworkout.promotion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.TreeSet;

/**
 * Authoritative Pro Discovery activity rules.
 * Evaluation uses the UTC calendar date of the server clock.
 * A device clock or zone is not an input.
 */
public final class ProDiscoveryEligibility {

    public static final int REQUIRED_DAYS = 24;
    public static final int REQUIRED_RECENT_DAYS = 2;
    public static final int RECENT_WINDOW_DAYS = 30;
    public static final int MAX_DATES = 4_000;
    /** One UTC day of grace so a zone ahead of UTC can store its local today. */
    public static final int FUTURE_GRACE_DAYS = 1;
    public static final LocalDate EARLIEST_DATE = LocalDate.of(2010, 1, 1);

    private ProDiscoveryEligibility() {
    }

    public static LocalDate evaluationDate(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate();
    }

    public static LocalDate latestAcceptedDate(LocalDate evaluationDate) {
        return evaluationDate.plusDays(FUTURE_GRACE_DAYS);
    }

    public static Progress evaluate(Collection<LocalDate> dates, LocalDate evaluationDate) {
        TreeSet<LocalDate> unique = new TreeSet<>();
        if (dates != null) {
            unique.addAll(dates);
        }
        LocalDate first = unique.isEmpty() ? null : unique.first();
        LocalDate matureOn = first == null ? null : first.plusMonths(6);
        LocalDate recentStart = evaluationDate.minusDays(RECENT_WINDOW_DAYS - 1L);
        int recent = 0;
        for (LocalDate date : unique) {
            if (!date.isBefore(recentStart) && !date.isAfter(evaluationDate)) {
                recent++;
            }
        }
        boolean mature = matureOn != null && !evaluationDate.isBefore(matureOn);
        boolean eligible = mature && unique.size() >= REQUIRED_DAYS && recent >= REQUIRED_RECENT_DAYS;
        return new Progress(
                unique.size(),
                Math.max(0, REQUIRED_DAYS - unique.size()),
                first,
                matureOn,
                recent,
                Math.max(0, REQUIRED_RECENT_DAYS - recent),
                eligible
        );
    }

    public record Progress(
            int qualifyingDays,
            int remainingDays,
            LocalDate firstWorkoutDate,
            LocalDate matureOn,
            int recentDays,
            int remainingRecentDays,
            boolean eligible
    ) {
    }
}
