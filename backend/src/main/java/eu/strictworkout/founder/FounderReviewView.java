package eu.strictworkout.founder;

import java.time.Instant;
import java.util.UUID;

public record FounderReviewView(
        UUID id,
        FounderStatus status,
        String testerEmail,
        Instant enrolledAt,
        Instant deadlineAt,
        Instant pendingAt,
        Instant reportSubmittedAt,
        Snapshot snapshot,
        Decision decision
) {
    public record Snapshot(
            Instant submittedAt,
            String appVersion,
            String platform,
            int qualifyingWorkoutCount,
            int distinctWorkoutDays,
            Instant enrolledAt,
            Instant deadlineAt,
            String feedbackText
    ) {
    }

    public record Decision(
            String decision,
            Instant decidedAt,
            String reason,
            UUID reviewedBy
    ) {
    }

    static FounderReviewView from(
            FounderApplication application,
            String testerEmail,
            FounderReviewSnapshot snapshot,
            FounderReviewDecision decision
    ) {
        return new FounderReviewView(
                application.getId(),
                application.getStatus(),
                testerEmail,
                application.getEnrolledAt(),
                application.getDeadlineAt(),
                application.getPendingAt(),
                application.getTesterReportSubmittedAt(),
                snapshot == null ? null : new Snapshot(
                        snapshot.getSubmittedAt(),
                        snapshot.getAppVersion(),
                        snapshot.getPlatform(),
                        snapshot.getQualifyingWorkoutCount(),
                        snapshot.getDistinctWorkoutDayCount(),
                        snapshot.getEnrolledAt(),
                        snapshot.getDeadlineAt(),
                        snapshot.getFeedbackText()
                ),
                decision == null ? null : new Decision(
                        decision.getDecision(),
                        decision.getDecidedAt(),
                        decision.getReason(),
                        decision.getAdminId()
                )
        );
    }
}
