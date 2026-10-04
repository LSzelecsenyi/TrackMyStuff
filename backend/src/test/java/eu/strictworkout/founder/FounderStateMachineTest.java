package eu.strictworkout.founder;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FounderStateMachineTest {

    private static final Instant ENROLLED = Instant.parse("2026-06-01T00:00:00Z");
    private static final FounderRules PRODUCTION = FounderRules.PRODUCTION;
    private static final FounderRules FAST = new FounderRules(1, 2, 1, 45, true, true);
    private final FounderStateMachine machine = new FounderStateMachine();

    @Test
    void productionRulesUseThePublishedThresholds() {
        assertEquals(5, PRODUCTION.temporaryProWorkoutCount());
        assertEquals(10, PRODUCTION.founderWorkoutCount());
        assertEquals(6, PRODUCTION.requiredDistinctWorkoutDays());
        assertEquals(45, PRODUCTION.qualificationWindowDays());
        assertTrue(PRODUCTION.feedbackRequired());
        assertTrue(PRODUCTION.testerAnalyticsReportRequired());
        assertEquals(Duration.ofHours(45 * 24), Duration.between(ENROLLED, FounderWindow.deadline(ENROLLED, PRODUCTION)));
    }

    @Test
    void fifthProductionWorkoutUnlocksTemporaryProAndFourthDoesNot() {
        FounderEvaluation fourth = evaluate(PRODUCTION, 4, 4, false, false, ENROLLED.plusSeconds(1));
        FounderEvaluation fifth = evaluate(PRODUCTION, 5, 5, false, false, ENROLLED.plusSeconds(1));
        assertEquals(FounderStatus.ACTIVE_FREE, fourth.status());
        assertFalse(fourth.temporaryProActive());
        assertEquals(FounderStatus.ACTIVE_PRO, fifth.status());
        assertTrue(fifth.temporaryProActive());
        assertFalse(fifth.trainingRequirementsComplete());
        assertEquals(FounderNextAction.COMPLETE_WORKOUTS, fifth.nextAction());
    }

    @Test
    void tenWorkoutsAndSixDaysMeetTrainingButNotPendingApproval() {
        FounderEvaluation training = evaluate(PRODUCTION, 10, 6, false, false, ENROLLED.plusSeconds(1));
        assertTrue(training.trainingRequirementsComplete());
        assertEquals(FounderStatus.ACTIVE_PRO, training.status());
        assertEquals(FounderNextAction.SUBMIT_FEEDBACK, training.nextAction());

        FounderEvaluation feedbackOnly = evaluate(PRODUCTION, 10, 6, true, false, ENROLLED.plusSeconds(1));
        assertEquals(FounderStatus.ACTIVE_PRO, feedbackOnly.status());
        assertEquals(FounderNextAction.SUBMIT_TESTER_REPORT, feedbackOnly.nextAction());
        assertFalse(TemporaryPro.active(FounderStatus.ACTIVE_FREE));
        assertTrue(TemporaryPro.active(FounderStatus.ACTIVE_PRO));
    }

    @Test
    void reportWithTrainingAndFeedbackBecomesPendingAndStaysPendingAfterTheDeadline() {
        Instant deadline = FounderWindow.deadline(ENROLLED, PRODUCTION);
        FounderEvaluation pending = evaluate(PRODUCTION, 10, 6, true, true, deadline);
        assertEquals(FounderStatus.PENDING_APPROVAL, pending.status());
        assertTrue(pending.temporaryProActive());
        assertEquals(FounderNextAction.WAIT_FOR_REVIEW, pending.nextAction());

        FounderFacts stillPending = new FounderFacts(
                FounderStatus.PENDING_APPROVAL,
                ENROLLED,
                deadline,
                workouts(10, 6),
                true,
                true
        );
        FounderEvaluation later = machine.evaluate(stillPending, PRODUCTION, deadline.plus(Duration.ofDays(30)));
        assertEquals(FounderStatus.PENDING_APPROVAL, later.status());
        assertTrue(later.temporaryProActive());
    }

    @Test
    void incompleteApplicationExpiresAfterTheDeadlineButNotAtTheDeadline() {
        Instant deadline = FounderWindow.deadline(ENROLLED, PRODUCTION);
        FounderEvaluation atDeadline = evaluate(PRODUCTION, 9, 6, true, true, deadline);
        assertEquals(FounderStatus.ACTIVE_PRO, atDeadline.status());

        FounderEvaluation afterDeadline = evaluate(PRODUCTION, 9, 6, true, true, deadline.plusNanos(1));
        assertEquals(FounderStatus.EXPIRED, afterDeadline.status());
        assertFalse(afterDeadline.temporaryProActive());
        assertEquals(FounderNextAction.EXPIRED, afterDeadline.nextAction());
    }

    @Test
    void qualificationAfterTheDeadlineExpiresInsteadOfBecomingPending() {
        Instant deadline = FounderWindow.deadline(ENROLLED, FAST);
        FounderEvaluation late = evaluate(FAST, 2, 1, true, true, deadline.plusNanos(1));
        assertEquals(FounderStatus.EXPIRED, late.status());
    }

    @Test
    void fastRulesDoNotChangeProductionThresholds() {
        FounderEvaluation one = evaluate(FAST, 1, 1, false, false, ENROLLED.plusSeconds(1));
        assertEquals(FounderStatus.ACTIVE_PRO, one.status());
        assertFalse(one.trainingRequirementsComplete());
        FounderEvaluation two = evaluate(FAST, 2, 1, false, false, ENROLLED.plusSeconds(1));
        assertTrue(two.trainingRequirementsComplete());
        assertEquals(FounderNextAction.SUBMIT_FEEDBACK, two.nextAction());
        assertEquals(5, FounderRules.PRODUCTION.temporaryProWorkoutCount());
    }

    @Test
    void duplicateWorkoutIdDoesNotIncreaseTheCountAndSameDayCountsOnce() {
        UUID id = UUID.randomUUID();
        List<FounderWorkoutFact> duplicated = List.of(
                new FounderWorkoutFact(id, ENROLLED, LocalDate.of(2026, 6, 1)),
                new FounderWorkoutFact(id, ENROLLED, LocalDate.of(2026, 6, 2))
        );
        FounderEvaluation sameId = machine.evaluate(facts(PRODUCTION, FounderStatus.ACTIVE_FREE, duplicated, false, false), PRODUCTION, ENROLLED.plusSeconds(1));
        assertEquals(1, sameId.qualifyingWorkouts());
        assertEquals(1, sameId.distinctWorkoutDays());

        List<FounderWorkoutFact> sameDay = List.of(
                new FounderWorkoutFact(UUID.randomUUID(), ENROLLED, LocalDate.of(2026, 6, 1)),
                new FounderWorkoutFact(UUID.randomUUID(), ENROLLED, LocalDate.of(2026, 6, 1))
        );
        FounderEvaluation oneDay = machine.evaluate(facts(PRODUCTION, FounderStatus.ACTIVE_FREE, sameDay, false, false), PRODUCTION, ENROLLED.plusSeconds(1));
        assertEquals(2, oneDay.qualifyingWorkouts());
        assertEquals(1, oneDay.distinctWorkoutDays());

        List<FounderWorkoutFact> twoDays = List.of(
                new FounderWorkoutFact(UUID.randomUUID(), ENROLLED, LocalDate.of(2026, 6, 1)),
                new FounderWorkoutFact(UUID.randomUUID(), ENROLLED, LocalDate.of(2026, 6, 2))
        );
        FounderEvaluation split = machine.evaluate(facts(PRODUCTION, FounderStatus.ACTIVE_FREE, twoDays, false, false), PRODUCTION, ENROLLED.plusSeconds(1));
        assertEquals(2, split.distinctWorkoutDays());
    }

    @Test
    void localDateCheckDoesNotUseTheJvmZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            Instant completed = Instant.parse("2026-06-01T12:00:00Z");
            for (String zone : List.of("UTC", "Pacific/Kiritimati", "Pacific/Pago_Pago")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertTrue(WorkoutEventRules.localDateMatchesInstant(completed, LocalDate.of(2026, 6, 1)));
                assertTrue(WorkoutEventRules.localDateMatchesInstant(completed, LocalDate.of(2026, 6, 2)));
                assertFalse(WorkoutEventRules.localDateMatchesInstant(completed, LocalDate.of(2026, 6, 3)));
                assertEquals(ZoneOffset.UTC, ZoneOffset.ofHours(0));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void temporaryProFollowsStatusRatherThanAClientFlag() {
        assertFalse(TemporaryPro.active(FounderStatus.ACTIVE_FREE));
        assertTrue(TemporaryPro.active(FounderStatus.ACTIVE_PRO));
        assertTrue(TemporaryPro.active(FounderStatus.PENDING_APPROVAL));
        assertFalse(TemporaryPro.active(FounderStatus.EXPIRED));
    }

    private FounderEvaluation evaluate(
            FounderRules rules,
            int workouts,
            int days,
            boolean feedback,
            boolean report,
            Instant now
    ) {
        return machine.evaluate(
                facts(rules, FounderStatus.ACTIVE_FREE, workouts(workouts, days), feedback, report),
                rules,
                now
        );
    }

    private static FounderFacts facts(
            FounderRules rules,
            FounderStatus status,
            List<FounderWorkoutFact> workouts,
            boolean feedback,
            boolean report
    ) {
        return new FounderFacts(
                status,
                ENROLLED,
                FounderWindow.deadline(ENROLLED, rules),
                workouts,
                feedback,
                report
        );
    }

    private static List<FounderWorkoutFact> workouts(int count, int distinctDays) {
        List<FounderWorkoutFact> workouts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            workouts.add(new FounderWorkoutFact(
                    UUID.randomUUID(),
                    ENROLLED,
                    LocalDate.of(2026, 6, 1).plusDays(Math.min(i, distinctDays - 1))
            ));
        }
        return workouts;
    }
}
