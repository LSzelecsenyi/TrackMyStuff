package eu.strictworkout.founder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class FounderStateMachine {

    public FounderEvaluation evaluate(FounderFacts facts, FounderRules rules, Instant now) {
        Count count = count(facts.workouts());
        boolean trainingComplete = count.workouts >= rules.founderWorkoutCount()
                && count.days >= rules.requiredDistinctWorkoutDays();
        boolean feedbackMet = !rules.feedbackRequired() || facts.feedbackSubmitted();
        boolean reportMet = !rules.testerAnalyticsReportRequired() || facts.reportSubmitted();
        boolean qualified = trainingComplete && feedbackMet && reportMet;
        FounderStatus status = nextStatus(facts, rules, now, count.workouts, qualified);
        return new FounderEvaluation(
                status,
                count.workouts,
                count.days,
                TemporaryPro.active(status),
                trainingComplete,
                feedbackMet,
                reportMet,
                nextAction(status, rules, count, facts.feedbackSubmitted(), facts.reportSubmitted(), trainingComplete)
        );
    }

    private static FounderStatus nextStatus(
            FounderFacts facts,
            FounderRules rules,
            Instant now,
            int workouts,
            boolean qualified
    ) {
        return switch (facts.status()) {
            case PENDING_APPROVAL -> FounderStatus.PENDING_APPROVAL;
            case EXPIRED -> FounderStatus.EXPIRED;
            case APPROVED -> FounderStatus.APPROVED;
            case REJECTED -> FounderStatus.REJECTED;
            case ACTIVE_FREE, ACTIVE_PRO -> {
                if (qualified && !now.isAfter(facts.deadlineAt())) {
                    yield FounderStatus.PENDING_APPROVAL;
                }
                if (now.isAfter(facts.deadlineAt())) {
                    yield FounderStatus.EXPIRED;
                }
                if (workouts >= rules.temporaryProWorkoutCount()) {
                    yield FounderStatus.ACTIVE_PRO;
                }
                yield FounderStatus.ACTIVE_FREE;
            }
        };
    }

    private static FounderNextAction nextAction(
            FounderStatus status,
            FounderRules rules,
            Count count,
            boolean feedbackSubmitted,
            boolean reportSubmitted,
            boolean trainingComplete
    ) {
        if (status == FounderStatus.EXPIRED) {
            return FounderNextAction.EXPIRED;
        }
        if (status == FounderStatus.APPROVED) {
            return FounderNextAction.APPROVED;
        }
        if (status == FounderStatus.REJECTED) {
            return FounderNextAction.REJECTED;
        }
        if (status == FounderStatus.PENDING_APPROVAL) {
            return FounderNextAction.WAIT_FOR_REVIEW;
        }
        if (count.workouts < rules.founderWorkoutCount()) {
            return FounderNextAction.COMPLETE_WORKOUTS;
        }
        if (count.days < rules.requiredDistinctWorkoutDays()) {
            return FounderNextAction.COMPLETE_TRAINING_DAYS;
        }
        if (rules.feedbackRequired() && !feedbackSubmitted) {
            return FounderNextAction.SUBMIT_FEEDBACK;
        }
        if (rules.testerAnalyticsReportRequired() && !reportSubmitted) {
            return FounderNextAction.SUBMIT_TESTER_REPORT;
        }
        if (trainingComplete) {
            return FounderNextAction.WAIT_FOR_REVIEW;
        }
        return FounderNextAction.COMPLETE_WORKOUTS;
    }

    private static Count count(List<FounderWorkoutFact> workouts) {
        Set<UUID> ids = new HashSet<>();
        Set<LocalDate> days = new HashSet<>();
        if (workouts != null) {
            for (FounderWorkoutFact workout : workouts) {
                if (ids.add(workout.clientWorkoutId())) {
                    days.add(workout.localDate());
                }
            }
        }
        return new Count(ids.size(), days.size());
    }

    private record Count(int workouts, int days) {
    }
}
