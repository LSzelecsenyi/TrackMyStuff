package eu.strictworkout.founder;

public final class FounderReviewRules {

    private FounderReviewRules() {
    }

    public static ReviewOutcome approve(FounderStatus status) {
        return switch (status) {
            case PENDING_APPROVAL -> ReviewOutcome.APPLY;
            case APPROVED -> ReviewOutcome.IDEMPOTENT;
            case REJECTED, ACTIVE_FREE, ACTIVE_PRO, EXPIRED -> ReviewOutcome.CONFLICT;
        };
    }

    public static ReviewOutcome reject(FounderStatus status, String recordedReason, String submittedReason) {
        return switch (status) {
            case PENDING_APPROVAL -> ReviewOutcome.APPLY;
            case REJECTED -> recordedReason.equals(submittedReason) ? ReviewOutcome.IDEMPOTENT : ReviewOutcome.CONFLICT;
            case APPROVED, ACTIVE_FREE, ACTIVE_PRO, EXPIRED -> ReviewOutcome.CONFLICT;
        };
    }
}
