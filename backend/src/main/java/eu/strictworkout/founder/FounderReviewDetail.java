package eu.strictworkout.founder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only review evidence for one Founder application.
 * Qualification counts and rules come from the immutable submission snapshot.
 * Workout rows are the observations stored when each qualifying workout was accepted.
 * Current application status can move to approved or rejected without rewriting that evidence.
 */
public record FounderReviewDetail(
        Application application,
        Tester tester,
        Qualification qualification,
        Report report,
        List<Workout> workouts,
        Decision decision
) {
    public record Application(
            UUID id,
            FounderStatus status,
            Instant enrolledAt,
            Instant deadlineAt,
            Instant pendingAt,
            Instant expiredAt
    ) {
    }

    public record Tester(String email) {
    }

    public record Qualification(
            int qualifyingWorkoutCount,
            int distinctWorkoutDayCount,
            Boolean trainingRequirementsComplete,
            Boolean temporaryProReached,
            Rules rules
    ) {
    }

    /**
     * Thresholds copied when the Tester Report was submitted.
     * They are not the rules configured on the server today.
     */
    public record Rules(
            String profile,
            Integer requiredWorkoutCount,
            Integer requiredDistinctDayCount,
            Integer temporaryProWorkoutCount,
            Integer qualificationWindowDays,
            Instant enrolledAt,
            Instant deadlineAt
    ) {
    }

    public record Report(
            Instant submittedAt,
            String appVersion,
            String platform,
            String feedback
    ) {
    }

    /**
     * One accepted qualifying workout.
     * {@code clientWorkoutId} is the client-generated id stored with the event, not a device database id.
     * The other fields are observations recorded at ingestion and do not decide whether the workout counted.
     */
    public record Workout(
            UUID clientWorkoutId,
            LocalDate localDate,
            Instant completedAt,
            String displayName,
            Integer durationSeconds,
            Integer exerciseCount,
            Integer completedSetCount,
            Boolean fromTemplate,
            Boolean usedExternalLoad
    ) {
    }

    public record Decision(
            String decision,
            Instant decidedAt,
            String reason,
            UUID reviewedBy
    ) {
    }

    static FounderReviewDetail from(
            FounderApplication application,
            String testerEmail,
            FounderReviewSnapshot snapshot,
            List<FounderWorkoutEvent> workouts,
            FounderReviewDecision decision
    ) {
        return new FounderReviewDetail(
                new Application(
                        application.getId(),
                        application.getStatus(),
                        application.getEnrolledAt(),
                        application.getDeadlineAt(),
                        application.getPendingAt(),
                        application.getExpiredAt()
                ),
                new Tester(testerEmail),
                qualification(snapshot),
                report(snapshot),
                workouts.stream().map(FounderReviewDetail::workout).toList(),
                decision == null ? null : new Decision(
                        decision.getDecision(),
                        decision.getDecidedAt(),
                        decision.getReason(),
                        decision.getAdminId()
                )
        );
    }

    private static Qualification qualification(FounderReviewSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new Qualification(
                snapshot.getQualifyingWorkoutCount(),
                snapshot.getDistinctWorkoutDayCount(),
                FounderStateMachine.recordedTrainingComplete(
                        snapshot.getQualifyingWorkoutCount(),
                        snapshot.getDistinctWorkoutDayCount(),
                        snapshot.getRequiredWorkoutCount(),
                        snapshot.getRequiredDistinctDayCount()
                ),
                FounderStateMachine.recordedTemporaryPro(
                        snapshot.getQualifyingWorkoutCount(),
                        snapshot.getTemporaryProWorkoutCount()
                ),
                new Rules(
                        snapshot.getRulesProfile(),
                        snapshot.getRequiredWorkoutCount(),
                        snapshot.getRequiredDistinctDayCount(),
                        snapshot.getTemporaryProWorkoutCount(),
                        snapshot.getQualificationWindowDays(),
                        snapshot.getEnrolledAt(),
                        snapshot.getDeadlineAt()
                )
        );
    }

    private static Report report(FounderReviewSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new Report(
                snapshot.getSubmittedAt(),
                snapshot.getAppVersion(),
                snapshot.getPlatform(),
                snapshot.getFeedbackText()
        );
    }

    private static Workout workout(FounderWorkoutEvent event) {
        return new Workout(
                event.getClientWorkoutId(),
                event.getWorkoutLocalDate(),
                event.getCompletedAt(),
                event.getDisplayName(),
                event.getDurationSeconds(),
                event.getExerciseCount(),
                event.getCompletedSetCount(),
                event.getFromTemplate(),
                event.getUsedExternalLoad()
        );
    }
}
