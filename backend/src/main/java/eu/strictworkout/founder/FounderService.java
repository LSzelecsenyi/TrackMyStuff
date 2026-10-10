package eu.strictworkout.founder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.strictworkout.account.DeletedAccountPolicy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FounderService {

    public static final int FEEDBACK_MAX_LENGTH = 8000;

    private static final Logger log = LoggerFactory.getLogger(FounderService.class);

    private final FounderApplicationRepository applications;
    private final FounderWorkoutEventRepository events;
    private final FounderReviewSnapshotRepository snapshots;
    private final FounderEnrollment enrollment;
    private final FounderStateMachine machine;
    private final FounderRulesBinding rulesBinding;
    private final DeletedAccountPolicy deletedAccounts;
    private final Clock clock;

    public FounderService(
            FounderApplicationRepository applications,
            FounderWorkoutEventRepository events,
            FounderReviewSnapshotRepository snapshots,
            FounderEnrollment enrollment,
            FounderStateMachine machine,
            FounderRulesBinding rulesBinding,
            DeletedAccountPolicy deletedAccounts,
            Clock clock
    ) {
        this.applications = applications;
        this.events = events;
        this.snapshots = snapshots;
        this.enrollment = enrollment;
        this.machine = machine;
        this.rulesBinding = rulesBinding;
        this.deletedAccounts = deletedAccounts;
        this.clock = clock;
    }

    private FounderRules rules() {
        return rulesBinding.rules();
    }

    @Transactional
    public FounderView enroll(UUID userId) {
        if (deletedAccounts.founderUsed(userId)) {
            throw new FounderCommandException(
                    HttpStatus.CONFLICT,
                    "FOUNDER_ALREADY_USED",
                    "Founder enrollment was already used by a deleted account for this Google sign-in."
            );
        }
        if (applications.findIdByUserId(userId).isEmpty()) {
            try {
                enrollment.insert(userId);
            } catch (DataIntegrityViolationException ex) {
                log.info("Concurrent Founder enrollment resolved to the existing application");
            }
        }
        FounderApplication application = lock(userId);
        return view(sync(application), application);
    }

    @Transactional
    public FounderView current(UUID userId) {
        FounderApplication application = lock(userId);
        return view(sync(application), application);
    }

    @Transactional
    public FounderCommandResult recordWorkout(
            UUID userId,
            UUID workoutId,
            Instant completedAt,
            LocalDate localDate,
            WorkoutObservation observation
    ) {
        FounderApplication application = lock(userId);
        List<FounderWorkoutEvent> stored = events.findByApplicationIdOrderByCreatedAtAsc(application.getId());
        FounderEvaluation evaluation = sync(application, stored);
        FounderCommandResult closed = closed(application, evaluation);
        if (closed != null) {
            return closed;
        }
        Instant now = clock.instant();
        if (WorkoutEventRules.completedTooFarInTheFuture(completedAt, now)
                || !WorkoutEventRules.localDateMatchesInstant(completedAt, localDate)) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WORKOUT",
                    "The workout event is not valid."
            );
        }
        if (!WorkoutEventRules.insideQualificationWindow(application.getEnrolledAt(), application.getDeadlineAt(), completedAt)) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "INVALID_WORKOUT",
                    "The workout is outside the qualification window."
            );
        }
        FounderWorkoutEvent existing = events.findByApplicationIdAndClientWorkoutId(application.getId(), workoutId).orElse(null);
        if (existing != null) {
            if (existing.samePayload(completedAt, localDate)) {
                return FounderCommandResult.ok(view(evaluation, application));
            }
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "WORKOUT_CONFLICT",
                    "This workout was already recorded with different details."
            );
        }
        WorkoutObservation accepted = observation == null
                ? new WorkoutObservation(null, null, null, null, null, null)
                : observation;
        if (accepted.invalid()) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WORKOUT",
                    "The workout observation is not valid."
            );
        }
        events.saveAndFlush(new FounderWorkoutEvent(
                UUID.randomUUID(),
                application,
                workoutId,
                completedAt,
                localDate,
                now,
                accepted.normalized()
        ));
        stored = events.findByApplicationIdOrderByCreatedAtAsc(application.getId());
        return FounderCommandResult.ok(view(sync(application, stored), application));
    }

    @Transactional
    public FounderCommandResult saveFeedback(UUID userId, String text) {
        FounderApplication application = lock(userId);
        List<FounderWorkoutEvent> stored = events.findByApplicationIdOrderByCreatedAtAsc(application.getId());
        FounderEvaluation evaluation = sync(application, stored);
        if (application.reportSubmitted()) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "FEEDBACK_FROZEN",
                    "Feedback is frozen after the Tester Report is submitted."
            );
        }
        FounderCommandResult closed = closed(application, evaluation);
        if (closed != null) {
            return closed;
        }
        if (!evaluation.trainingRequirementsComplete()) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "FEEDBACK_NOT_AVAILABLE",
                    "Feedback is not available until training requirements are complete."
            );
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > FEEDBACK_MAX_LENGTH) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.BAD_REQUEST,
                    "FEEDBACK_INVALID",
                    "Feedback must be between 1 and 8000 characters."
            );
        }
        application.replaceFeedback(trimmed, clock.instant());
        return FounderCommandResult.ok(view(sync(application, stored), application));
    }

    /**
     * One submission stores the user's feedback, records the review package, and moves the
     * application to pending approval. A repeated {@code submissionId} returns the existing
     * result and does not change {@code pending_at} or create another snapshot.
     * Qualification counts, status, and entitlement are taken from the server, not the body.
     * The platform stored for review is always {@code android}.
     */
    @Transactional
    public FounderCommandResult submitReport(UUID userId, UUID submissionId, String feedback, String appVersion) {
        FounderApplication application = lock(userId);
        List<FounderWorkoutEvent> stored = events.findByApplicationIdOrderByCreatedAtAsc(application.getId());
        FounderEvaluation evaluation = sync(application, stored);
        FounderReviewSnapshot existing = snapshots.findByApplicationId(application.getId()).orElse(null);
        if (existing != null || application.reportSubmitted() || evaluation.status() == FounderStatus.PENDING_APPROVAL) {
            if (existing != null && existing.sameSubmission(submissionId)) {
                return FounderCommandResult.ok(view(evaluation, application));
            }
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "REPORT_CONFLICT",
                    "The Tester Report was already submitted."
            );
        }
        FounderCommandResult closed = closed(application, evaluation);
        if (closed != null) {
            return closed;
        }
        if (!evaluation.trainingRequirementsComplete()) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "REPORT_NOT_AVAILABLE",
                    "The Tester Report is not available until training requirements are complete."
            );
        }
        String trimmed = feedback == null ? "" : feedback.trim();
        if (trimmed.isEmpty() || trimmed.length() > FEEDBACK_MAX_LENGTH) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.BAD_REQUEST,
                    "FEEDBACK_INVALID",
                    "Feedback must be between 1 and 8000 characters."
            );
        }
        Instant now = clock.instant();
        application.replaceFeedback(trimmed, now);
        application.markReportSubmitted(now);
        FounderEvaluation submitted = sync(application, stored);
        snapshots.saveAndFlush(new FounderReviewSnapshot(
                UUID.randomUUID(),
                application,
                now,
                appVersion,
                "android",
                submitted.qualifyingWorkouts(),
                submitted.distinctWorkoutDays(),
                trimmed,
                submissionId,
                rulesBinding.profile(),
                rules().temporaryProWorkoutCount(),
                rules().founderWorkoutCount(),
                rules().requiredDistinctWorkoutDays(),
                rules().qualificationWindowDays()
        ));
        return FounderCommandResult.ok(view(submitted, application));
    }

    private FounderApplication lock(UUID userId) {
        return applications.lockByUserId(userId).orElseThrow(FounderCommandException::notEnrolled);
    }

    private FounderEvaluation sync(FounderApplication application) {
        return sync(application, events.findByApplicationIdOrderByCreatedAtAsc(application.getId()));
    }

    private FounderEvaluation sync(FounderApplication application, List<FounderWorkoutEvent> stored) {
        FounderEvaluation evaluation = machine.evaluate(facts(application, stored), rules(), clock.instant());
        application.applyStatus(evaluation.status(), clock.instant());
        return evaluation;
    }

    private FounderCommandResult closed(FounderApplication application, FounderEvaluation evaluation) {
        if (evaluation.status() == FounderStatus.EXPIRED) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "APPLICATION_EXPIRED",
                    "The Founder qualification window has ended."
            );
        }
        if (evaluation.status() == FounderStatus.PENDING_APPROVAL) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "APPLICATION_PENDING",
                    "The Founder application is already awaiting review."
            );
        }
        if (evaluation.status() == FounderStatus.APPROVED || evaluation.status() == FounderStatus.REJECTED) {
            return FounderCommandResult.reject(
                    view(evaluation, application),
                    HttpStatus.CONFLICT,
                    "APPLICATION_CLOSED",
                    "The Founder application is no longer open."
            );
        }
        return null;
    }

    private FounderFacts facts(FounderApplication application, List<FounderWorkoutEvent> stored) {
        return new FounderFacts(
                application.getStatus(),
                application.getEnrolledAt(),
                application.getDeadlineAt(),
                stored.stream().map(FounderWorkoutEvent::fact).toList(),
                application.feedbackSubmitted(),
                application.reportSubmitted()
        );
    }

    private FounderView view(FounderEvaluation evaluation, FounderApplication application) {
        return new FounderView(
                evaluation.status(),
                application.getEnrolledAt(),
                application.getDeadlineAt(),
                new FounderView.Progress(
                        evaluation.qualifyingWorkouts(),
                        rules().founderWorkoutCount(),
                        evaluation.distinctWorkoutDays(),
                        rules().requiredDistinctWorkoutDays(),
                        rules().temporaryProWorkoutCount()
                ),
                evaluation.temporaryProActive(),
                evaluation.trainingRequirementsComplete(),
                new FounderView.Requirement(rules().feedbackRequired(), application.feedbackSubmitted()),
                new FounderView.Requirement(rules().testerAnalyticsReportRequired(), application.reportSubmitted()),
                evaluation.nextAction()
        );
    }
}
