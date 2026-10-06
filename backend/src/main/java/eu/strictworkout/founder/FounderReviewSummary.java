package eu.strictworkout.founder;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact queue row for choosing an application to review.
 * Feedback and workout observations stay on the detail resource.
 */
public record FounderReviewSummary(
        UUID id,
        FounderStatus status,
        String testerEmail,
        Instant enrolledAt,
        Instant deadlineAt,
        Instant pendingAt,
        Qualification qualification
) {
    public record Qualification(
            int qualifyingWorkoutCount,
            int distinctWorkoutDayCount,
            Integer requiredWorkoutCount,
            Integer requiredDistinctDayCount
    ) {
    }

    static FounderReviewSummary from(FounderApplication application, String testerEmail, FounderReviewSnapshot snapshot) {
        return new FounderReviewSummary(
                application.getId(),
                application.getStatus(),
                testerEmail,
                application.getEnrolledAt(),
                application.getDeadlineAt(),
                application.getPendingAt(),
                snapshot == null ? null : new Qualification(
                        snapshot.getQualifyingWorkoutCount(),
                        snapshot.getDistinctWorkoutDayCount(),
                        snapshot.getRequiredWorkoutCount(),
                        snapshot.getRequiredDistinctDayCount()
                )
        );
    }
}
