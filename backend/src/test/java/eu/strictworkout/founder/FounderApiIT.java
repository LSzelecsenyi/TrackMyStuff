package eu.strictworkout.founder;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "strict.auth.session-lifetime=P365D"
)
@Import({ScriptedIdentityVerifierConfig.class, FastFounderRulesConfig.class})
@Testcontainers
class FounderApiIT {

    private static final TimeZone ORIGINAL_ZONE = TimeZone.getDefault();

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private ScriptedIdentityVerifier identities;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private FounderRules rules;

    @Autowired
    private FounderRulesBinding rulesBinding;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FounderApplicationRepository applications;

    @BeforeEach
    void resetClock() {
        clock.set(FastFounderRulesConfig.START);
        rulesBinding.replace("fast", new FounderRules(1, 2, 1, 45, true, true));
    }

    @AfterAll
    static void restoreJvmZone() {
        TimeZone.setDefault(ORIGINAL_ZONE);
    }

    @Test
    void fastRulesExistOnlyInThisTestContext() {
        assertEquals(1, rules.temporaryProWorkoutCount());
        assertEquals(2, rules.founderWorkoutCount());
        assertEquals(1, rules.requiredDistinctWorkoutDays());
        assertNotEquals(FounderRules.PRODUCTION, rules);
    }

    @Test
    void enrollmentIsAuthenticatedIdempotentAndDoesNotReset() {
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/v1/founder/enrollment", "{}", null).getStatusCode());

        String token = login("enroll-user");
        Map<String, Object> first = parse(ok(post("/api/v1/founder/enrollment", "{\"userId\":\"00000000-0000-0000-0000-000000000099\"}", token)));
        assertEquals("ACTIVE_FREE", first.get("status"));
        assertEquals("COMPLETE_WORKOUTS", first.get("nextAction"));
        assertFlag(false, first.get("temporaryProActive"));
        assertEquals(FastFounderRulesConfig.START.toString(), first.get("enrolledAt"));
        assertEquals(FastFounderRulesConfig.START.plus(Duration.ofHours(45 * 24)).toString(), first.get("deadlineAt"));

        clock.set(FastFounderRulesConfig.START.plus(Duration.ofDays(3)));
        Map<String, Object> second = parse(ok(post("/api/v1/founder/enrollment", "{}", token)));
        assertEquals(first.get("enrolledAt"), second.get("enrolledAt"));
        assertEquals(first.get("deadlineAt"), second.get("deadlineAt"));
        assertEquals("ACTIVE_FREE", second.get("status"));
        assertEquals(1, countApplications(userId(token)));
    }

    @Test
    void enrollmentStopsAtCapacityAndStaysIdempotentWhenFullOrClosed() {
        assertEquals(2000, jdbc.queryForObject(
                "select capacity from founder_program_capacity where id = 1",
                Integer.class
        ));
        String existing = login("capacity-existing");
        ok(post("/api/v1/founder/enrollment", "{}", existing));
        try {
            jdbc.update("update founder_program_capacity set capacity = enrolled_count where id = 1");
            String blocked = login("capacity-blocked");
            ResponseEntity<String> full = post("/api/v1/founder/enrollment", "{}", blocked);
            assertEquals(HttpStatus.CONFLICT, full.getStatusCode());
            assertEquals("ENROLLMENT_FULL", parse(full).get("errorCode"));
            assertEquals(0, countApplications(userId(blocked)));
            Map<String, Object> again = parse(ok(post("/api/v1/founder/enrollment", "{}", existing)));
            assertEquals("ACTIVE_FREE", again.get("status"));

            jdbc.update("update founder_program_capacity set enrollment_open = false where id = 1");
            String afterClose = login("capacity-closed");
            ResponseEntity<String> closed = post("/api/v1/founder/enrollment", "{}", afterClose);
            assertEquals(HttpStatus.CONFLICT, closed.getStatusCode());
            assertEquals("ENROLLMENT_CLOSED", parse(closed).get("errorCode"));
            assertEquals("ACTIVE_FREE", parse(ok(post("/api/v1/founder/enrollment", "{}", existing))).get("status"));
        } finally {
            jdbc.update("update founder_program_capacity set capacity = 2000, enrollment_open = true where id = 1");
        }
    }

    @Test
    void concurrentEnrollmentCannotExceedTheRemainingSlot() throws Exception {
        jdbc.update("update founder_program_capacity set capacity = enrolled_count + 1 where id = 1");
        try {
            String first = login("capacity-race-a");
            String second = login("capacity-race-b");
            List<Integer> statuses = race(
                    () -> post("/api/v1/founder/enrollment", "{}", first),
                    () -> post("/api/v1/founder/enrollment", "{}", second)
            );
            assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
            Integer enrolled = jdbc.queryForObject(
                    "select enrolled_count from founder_program_capacity where id = 1",
                    Integer.class
            );
            Integer capacity = jdbc.queryForObject(
                    "select capacity from founder_program_capacity where id = 1",
                    Integer.class
            );
            assertEquals(capacity, enrolled);
            int applications = countApplications(userId(first)) + countApplications(userId(second));
            assertEquals(1, applications);
        } finally {
            jdbc.update("update founder_program_capacity set capacity = 2000 where id = 1");
        }
    }

    @Test
    void usersCannotSeeOrAdvanceEachOther() {
        String first = login("user-a");
        String second = login("user-b");
        ok(post("/api/v1/founder/enrollment", "{}", first));
        UUID sharedWorkout = UUID.randomUUID();
        ok(post("/api/v1/founder/workouts", workout(sharedWorkout, FastFounderRulesConfig.START, "2026-06-01"), first));

        assertEquals(HttpStatus.NOT_FOUND, get("/api/v1/founder", second).getStatusCode());
        assertEquals("FOUNDER_NOT_ENROLLED", parse(get("/api/v1/founder", second)).get("errorCode"));
        ok(post("/api/v1/founder/enrollment", "{}", second));
        ok(post("/api/v1/founder/workouts", workout(sharedWorkout, FastFounderRulesConfig.START, "2026-06-01"), second));

        Map<String, Object> firstState = parse(ok(get("/api/v1/founder", first)));
        Map<String, Object> secondState = parse(ok(get("/api/v1/founder", second)));
        assertCount(1, progress(firstState).get("qualifyingWorkouts"));
        assertCount(1, progress(secondState).get("qualifyingWorkouts"));
        assertEquals(1, countEvents(userId(first)));
        assertEquals(1, countEvents(userId(second)));
    }

    @Test
    void workoutRetryIsIdempotentAndAConflictingReplayDoesNotChangeTheFirstEvent() {
        String token = enrolled("workout-retry");
        UUID workoutId = UUID.randomUUID();
        Map<String, Object> first = parse(ok(post(
                "/api/v1/founder/workouts",
                workout(workoutId, FastFounderRulesConfig.START, "2026-06-01"),
                token
        )));
        assertEquals("ACTIVE_PRO", first.get("status"));
        assertFlag(true, first.get("temporaryProActive"));
        assertCount(1, progress(first).get("qualifyingWorkouts"));
        assertCount(1, progress(first).get("temporaryProRequiredWorkouts"));

        Map<String, Object> retry = parse(ok(post(
                "/api/v1/founder/workouts",
                workout(workoutId, FastFounderRulesConfig.START, "2026-06-01"),
                token
        )));
        assertCount(1, progress(retry).get("qualifyingWorkouts"));

        ResponseEntity<String> conflict = post(
                "/api/v1/founder/workouts",
                workout(workoutId, FastFounderRulesConfig.START.plusSeconds(60), "2026-05-31"),
                token
        );
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("WORKOUT_CONFLICT", parse(conflict).get("errorCode"));
        assertEquals(1, countEvents(userId(token)));
        assertEquals("2026-06-01", localDate(userId(token)));
    }

    @Test
    void distinctLocalDatesCountOncePerDay() {
        String token = enrolled("days");
        ok(post("/api/v1/founder/workouts", workout(UUID.randomUUID(), FastFounderRulesConfig.START, "2026-06-01"), token));
        Map<String, Object> sameDay = parse(ok(post(
                "/api/v1/founder/workouts",
                workout(UUID.randomUUID(), FastFounderRulesConfig.START.plusSeconds(30), "2026-06-01"),
                token
        )));
        assertCount(2, progress(sameDay).get("qualifyingWorkouts"));
        assertCount(1, progress(sameDay).get("distinctWorkoutDays"));
        assertFlag(true, sameDay.get("trainingRequirementsComplete"));
    }

    @Test
    void concurrentDuplicateCountsOnceAndDistinctConcurrentWorkoutsBothSurvive() throws Exception {
        String duplicateUser = enrolled("concurrent-duplicate");
        UUID workoutId = UUID.randomUUID();
        List<Integer> duplicateStatuses = race(() -> post(
                "/api/v1/founder/workouts",
                workout(workoutId, FastFounderRulesConfig.START, "2026-06-01"),
                duplicateUser
        ));
        assertEquals(List.of(200, 200), duplicateStatuses.stream().sorted().toList());
        assertEquals(1, countEvents(userId(duplicateUser)));

        String both = enrolled("concurrent-both");
        List<Integer> bothStatuses = race(
                () -> post("/api/v1/founder/workouts", workout(UUID.randomUUID(), FastFounderRulesConfig.START, "2026-06-01"), both),
                () -> post("/api/v1/founder/workouts", workout(UUID.randomUUID(), FastFounderRulesConfig.START, "2026-06-01"), both)
        );
        assertEquals(List.of(200, 200), bothStatuses.stream().sorted().toList());
        assertEquals(2, countEvents(userId(both)));
        assertEquals("ACTIVE_PRO", parse(ok(get("/api/v1/founder", both))).get("status"));
    }

    @Test
    void feedbackAndReportFollowTheRequiredOrderAndFreezeTheReviewPackage() {
        String token = enrolled("feedback");
        ResponseEntity<String> early = put("/api/v1/founder/feedback", "{\"text\":\"Too soon\"}", token);
        assertEquals(HttpStatus.CONFLICT, early.getStatusCode());
        assertEquals("FEEDBACK_NOT_AVAILABLE", parse(early).get("errorCode"));

        ResponseEntity<String> earlyReport = post("/api/v1/founder/tester-report", report("1.0.0"), token);
        assertEquals(HttpStatus.CONFLICT, earlyReport.getStatusCode());
        assertEquals("REPORT_NOT_AVAILABLE", parse(earlyReport).get("errorCode"));

        completeTraining(token);
        UUID submissionId = UUID.randomUUID();
        String body = report(submissionId, "1.2.3", "Revised note", ",\"qualifyingWorkouts\":99,\"status\":\"APPROVED\",\"founderLifetime\":true,\"userId\":\"" + userId(token) + "\",\"pendingAt\":\"2000-01-01T00:00:00Z\"");
        Map<String, Object> pending = parse(ok(post("/api/v1/founder/tester-report", body, token)));
        assertEquals("PENDING_APPROVAL", pending.get("status"));
        assertEquals("WAIT_FOR_REVIEW", pending.get("nextAction"));
        assertFlag(true, pending.get("temporaryProActive"));
        assertFlag(true, requirement(pending, "feedback").get("submitted"));
        assertFlag(true, requirement(pending, "testerReport").get("submitted"));
        assertFalse(pending.toString().contains("Revised note"));
        assertFalse(pending.toString().contains("@"));

        ResponseEntity<String> frozen = put("/api/v1/founder/feedback", "{\"text\":\"Changed after review\"}", token);
        assertEquals(HttpStatus.CONFLICT, frozen.getStatusCode());
        assertEquals("FEEDBACK_FROZEN", parse(frozen).get("errorCode"));

        Object pendingAt = jdbc.queryForObject(
                "select pending_at from founder_application where user_id = ?::uuid",
                Object.class,
                userId(token)
        );
        ResponseEntity<String> retry = post("/api/v1/founder/tester-report", body, token);
        assertEquals(HttpStatus.OK, retry.getStatusCode());
        assertEquals("PENDING_APPROVAL", parse(retry).get("status"));
        assertEquals(pendingAt, jdbc.queryForObject(
                "select pending_at from founder_application where user_id = ?::uuid",
                Object.class,
                userId(token)
        ));
        ResponseEntity<String> conflict = post("/api/v1/founder/tester-report", report("9.9.9"), token);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("REPORT_CONFLICT", parse(conflict).get("errorCode"));
        assertEquals(pendingAt, jdbc.queryForObject(
                "select pending_at from founder_application where user_id = ?::uuid",
                Object.class,
                userId(token)
        ));

        Map<String, Object> snapshot = snapshot(userId(token));
        assertEquals("1.2.3", snapshot.get("app_version"));
        assertEquals("android", snapshot.get("platform"));
        assertCount(2, snapshot.get("qualifying_workout_count"));
        assertCount(1, snapshot.get("distinct_workout_day_count"));
        assertEquals("Revised note", snapshot.get("feedback_text"));
        assertEquals(submissionId.toString(), snapshot.get("client_submission_id").toString());
        assertEquals(1, countSnapshots(userId(token)));
        assertFalse(conflict.getBody().contains("Revised note"));
        Map<String, Object> entitlements = parse(ok(get("/api/v1/entitlements", token)));
        assertFlag(true, entitlements.get("temporaryFounderPro"));
        assertFlag(false, entitlements.get("founderLifetime"));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from entitlement_grant where user_id = ?::uuid",
                Integer.class,
                userId(token)
        ));
    }

    @Test
    void aFailedReportTransactionDoesNotMarkTheReportSubmitted() {
        String token = enrolled("failed-report");
        completeTraining(token);
        ok(put("/api/v1/founder/feedback", "{\"text\":\"Keep this private\"}", token));
        UUID applicationId = applicationId(userId(token));
        jdbc.update(
                """
                insert into founder_review_snapshot (
                    id, founder_application_id, submitted_at, app_version, platform,
                    qualifying_workout_count, distinct_workout_day_count, enrolled_at, deadline_at,
                    feedback_text, created_at, client_submission_id
                ) values (?::uuid, ?::uuid, now(), '0.0.1', 'android', 2, 1, now(), now(), 'seed', now(), ?::uuid)
                """,
                UUID.randomUUID().toString(),
                applicationId.toString(),
                UUID.randomUUID().toString()
        );

        ResponseEntity<String> failed = post("/api/v1/founder/tester-report", report("1.0.0"), token);
        assertEquals(HttpStatus.CONFLICT, failed.getStatusCode());
        assertEquals("REPORT_CONFLICT", parse(failed).get("errorCode"));
        assertFalse(failed.getBody().contains("Keep this private"));
        assertEquals("ACTIVE_PRO", jdbc.queryForObject(
                "select status from founder_application where id = ?::uuid",
                String.class,
                applicationId.toString()
        ));
        assertEquals(null, jdbc.queryForObject(
                "select tester_report_submitted_at from founder_application where id = ?::uuid",
                Object.class,
                applicationId.toString()
        ));
    }

    @Test
    void overdueActiveApplicationsExpireAndPendingApprovalDoesNot() {
        String expiredUser = enrolled("expiry");
        Instant deadline = FastFounderRulesConfig.START.plus(Duration.ofHours(45 * 24));
        clock.set(deadline);
        Map<String, Object> atDeadline = parse(ok(get("/api/v1/founder", expiredUser)));
        assertEquals("ACTIVE_FREE", atDeadline.get("status"));

        clock.set(deadline.plusNanos(1));
        Map<String, Object> expired = parse(ok(get("/api/v1/founder", expiredUser)));
        assertEquals("EXPIRED", expired.get("status"));
        assertFlag(false, expired.get("temporaryProActive"));
        ResponseEntity<String> rejected = post(
                "/api/v1/founder/workouts",
                workout(UUID.randomUUID(), deadline, "2026-07-16"),
                expiredUser
        );
        assertEquals(HttpStatus.CONFLICT, rejected.getStatusCode());
        assertEquals("APPLICATION_EXPIRED", parse(rejected).get("errorCode"));
        Map<String, Object> stillExpired = parse(ok(post("/api/v1/founder/enrollment", "{}", expiredUser)));
        assertEquals("EXPIRED", stillExpired.get("status"));
        assertEquals(1, countApplications(userId(expiredUser)));

        clock.set(FastFounderRulesConfig.START);
        String pendingUser = enrolled("pending-survives");
        qualify(pendingUser);
        clock.set(FastFounderRulesConfig.START.plus(Duration.ofHours(45 * 24)).plus(Duration.ofDays(10)));
        Map<String, Object> pending = parse(ok(get("/api/v1/founder", pendingUser)));
        assertEquals("PENDING_APPROVAL", pending.get("status"));
        assertFlag(true, pending.get("temporaryProActive"));
    }

    @Test
    void exactDeadlineStillAcceptsTheTesterReport() {
        String token = enrolled("deadline-boundary");
        completeTraining(token);
        ok(put("/api/v1/founder/feedback", "{\"text\":\"On time\"}", token));
        clock.set(FastFounderRulesConfig.START.plus(Duration.ofHours(45 * 24)));
        Map<String, Object> pending = parse(ok(post("/api/v1/founder/tester-report", report("1.0.0"), token)));
        assertEquals("PENDING_APPROVAL", pending.get("status"));
    }

    @Test
    void testerReportRejectsBlankFeedbackAndALateDeadline() {
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/v1/founder/tester-report", report("1.0.0"), null).getStatusCode());

        String token = enrolled("report-validation");
        completeTraining(token);
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/v1/founder/tester-report", report(UUID.randomUUID(), "1.0.0", ""), token).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/v1/founder/tester-report", report(UUID.randomUUID(), "1.0.0", "   "), token).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post(
                "/api/v1/founder/tester-report",
                report(UUID.randomUUID(), "1.0.0", "x".repeat(8001)),
                token
        ).getStatusCode());
        assertEquals("ACTIVE_PRO", parse(ok(get("/api/v1/founder", token))).get("status"));
        assertEquals(0, countSnapshots(userId(token)));

        String other = enrolled("report-other");
        ResponseEntity<String> foreign = post(
                "/api/v1/founder/tester-report",
                report(UUID.randomUUID(), "1.0.0", "For someone else", ",\"applicationId\":\"" + applicationId(userId(token)) + "\""),
                other
        );
        assertEquals(HttpStatus.CONFLICT, foreign.getStatusCode());
        assertEquals("ACTIVE_PRO", parse(ok(get("/api/v1/founder", token))).get("status"));
        assertEquals(0, countSnapshots(userId(token)));

        clock.set(FastFounderRulesConfig.START.plus(Duration.ofHours(45 * 24)).plusNanos(1));
        ResponseEntity<String> late = post("/api/v1/founder/tester-report", report("1.0.0"), token);
        assertEquals(HttpStatus.CONFLICT, late.getStatusCode());
        assertEquals("APPLICATION_EXPIRED", parse(late).get("errorCode"));
        assertEquals("EXPIRED", parse(ok(get("/api/v1/founder", token))).get("status"));
        assertEquals(0, countSnapshots(userId(token)));
    }

    @Test
    void founderEndpointsRequireTheStrictSessionAndIgnoreABodyUserId() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/founder", null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/v1/founder/workouts", workout(UUID.randomUUID(), FastFounderRulesConfig.START, "2026-06-01"), null).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/v1/health", null).getStatusCode());
        ResponseEntity<String> google = post("/api/v1/auth/google", "{\"idToken\":\"public-google\"}", null);
        assertEquals(HttpStatus.UNAUTHORIZED, google.getStatusCode());

        String token = login("owner");
        String other = login("other");
        ok(post("/api/v1/founder/enrollment", "{\"userId\":\"" + userId(other) + "\"}", token));
        assertEquals(HttpStatus.NOT_FOUND, get("/api/v1/founder", other).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/v1/founder", token).getStatusCode());
    }

    @Test
    void completeJourneySurvivesARepositoryReload() {
        String token = enrolled("journey");
        Map<String, Object> first = parse(ok(post(
                "/api/v1/founder/workouts",
                workout(UUID.randomUUID(), FastFounderRulesConfig.START, "2026-06-01"),
                token
        )));
        assertEquals("ACTIVE_PRO", first.get("status"));
        qualify(token);

        entityManager.clear();
        FounderApplication reloaded = applications.findById(applicationId(userId(token))).orElseThrow();
        assertEquals(FounderStatus.PENDING_APPROVAL, reloaded.getStatus());
        assertEquals("PENDING_APPROVAL", parse(ok(get("/api/v1/founder", token))).get("status"));
        assertEquals(1, countSnapshots(userId(token)));
    }

    @Test
    void concurrentReportSubmissionCreatesOneSnapshot() throws Exception {
        String token = enrolled("concurrent-report");
        completeTraining(token);
        String body = report(UUID.randomUUID(), "1.0.0", "Race");
        List<Integer> statuses = race(
                () -> post("/api/v1/founder/tester-report", body, token),
                () -> post("/api/v1/founder/tester-report", body, token)
        );
        assertEquals(List.of(200, 200), statuses.stream().sorted().toList());
        assertEquals(1, countSnapshots(userId(token)));
        assertEquals("PENDING_APPROVAL", parse(ok(get("/api/v1/founder", token))).get("status"));
    }

    @Test
    void workoutSubmissionRejectsMalformedIdsAndCannotGrantLifetime() {
        String token = enrolled("workout-authority");
        ResponseEntity<String> malformed = post(
                "/api/v1/founder/workouts",
                "{\"workoutId\":\"not-a-uuid\",\"completedAt\":\"2026-06-01T00:00:00Z\",\"localDate\":\"2026-06-01\"}",
                token
        );
        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
        assertEquals(0, countEvents(userId(token)));

        String desired = "{"
                + "\"workoutId\":\"" + UUID.randomUUID() + "\","
                + "\"completedAt\":\"2026-06-01T00:00:00Z\","
                + "\"localDate\":\"2026-06-01\","
                + "\"userId\":\"" + UUID.randomUUID() + "\","
                + "\"applicationId\":\"" + UUID.randomUUID() + "\","
                + "\"status\":\"APPROVED\","
                + "\"founderLifetime\":true,"
                + "\"tier\":\"PRO\","
                + "\"origin\":\"HEALTH_CONNECT\","
                + "\"sets\":[{\"reps\":5,\"weightKg\":100}]"
                + "}";
        Map<String, Object> recorded = parse(ok(post("/api/v1/founder/workouts", desired, token)));
        assertEquals("ACTIVE_PRO", recorded.get("status"));
        assertCount(1, progress(recorded).get("qualifyingWorkouts"));
        assertEquals(1, countEvents(userId(token)));

        Map<String, Object> entitlements = parse(ok(get("/api/v1/entitlements", token)));
        assertFlag(true, entitlements.get("temporaryFounderPro"));
        assertFlag(false, entitlements.get("founderLifetime"));
        assertEquals(HttpStatus.NOT_FOUND, post("/api/v1/founder/approval", "{\"status\":\"APPROVED\",\"founderLifetime\":true}", token).getStatusCode());

        String again = login("workout-authority");
        Map<String, Object> restored = parse(ok(get("/api/v1/founder", again)));
        assertCount(1, progress(restored).get("qualifyingWorkouts"));
        assertEquals("ACTIVE_PRO", restored.get("status"));
        Map<String, Object> restoredEntitlements = parse(ok(get("/api/v1/entitlements", again)));
        assertFlag(true, restoredEntitlements.get("temporaryFounderPro"));
        assertFlag(false, restoredEntitlements.get("founderLifetime"));
    }

    @Test
    void workoutObservationsStayOptionalAndCannotChangeQualification() {
        assertEquals(6, columnCount("founder_workout_event",
                "display_name", "duration_seconds", "exercise_count", "completed_set_count", "from_template", "used_external_load"));
        assertEquals(5, columnCount("founder_review_snapshot",
                "rules_profile", "temporary_pro_workout_count", "required_workout_count",
                "required_distinct_day_count", "qualification_window_days"));

        String token = enrolled("workout-observations");
        String userId = userId(token);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<String, Object> sparse = parse(ok(post(
                "/api/v1/founder/workouts",
                workout(first, FastFounderRulesConfig.START, "2026-06-01", ",\"displayName\":\"   \""),
                token
        )));
        assertEquals("ACTIVE_PRO", sparse.get("status"));
        assertCount(1, progress(sparse).get("qualifyingWorkouts"));
        Map<String, Object> sparseRow = eventRow(userId, first);
        assertNull(sparseRow.get("display_name"));
        assertNull(sparseRow.get("duration_seconds"));
        assertNull(sparseRow.get("exercise_count"));
        assertNull(sparseRow.get("completed_set_count"));
        assertNull(sparseRow.get("from_template"));
        assertNull(sparseRow.get("used_external_load"));

        String observed = workout(second, FastFounderRulesConfig.START.plusSeconds(10), "2026-06-01",
                ",\"displayName\":\"Push\",\"durationSeconds\":1800,\"exerciseCount\":500,\"completedSetCount\":12,"
                        + "\"fromTemplate\":true,\"usedExternalLoad\":false,\"requiredWorkoutCount\":99,"
                        + "\"rulesProfile\":\"production\",\"temporaryProUnlocked\":true,\"trainingComplete\":true,"
                        + "\"status\":\"APPROVED\",\"pendingApproval\":true");
        Map<String, Object> recorded = parse(ok(post("/api/v1/founder/workouts", observed, token)));
        assertEquals("ACTIVE_PRO", recorded.get("status"));
        assertCount(2, progress(recorded).get("qualifyingWorkouts"));
        assertCount(2, progress(recorded).get("requiredWorkouts"));
        assertCount(1, progress(recorded).get("distinctWorkoutDays"));
        Map<String, Object> observedRow = eventRow(userId, second);
        assertEquals("Push", observedRow.get("display_name"));
        assertCount(1800, observedRow.get("duration_seconds"));
        assertCount(500, observedRow.get("exercise_count"));
        assertCount(12, observedRow.get("completed_set_count"));
        assertEquals(Boolean.TRUE, observedRow.get("from_template"));
        assertEquals(Boolean.FALSE, observedRow.get("used_external_load"));
        assertEquals(2, countEvents(userId));

        Map<String, Object> retried = parse(ok(post(
                "/api/v1/founder/workouts",
                workout(second, FastFounderRulesConfig.START.plusSeconds(10), "2026-06-01",
                        ",\"displayName\":\"Changed\",\"durationSeconds\":1,\"fromTemplate\":false,\"usedExternalLoad\":true"),
                token
        )));
        assertCount(2, progress(retried).get("qualifyingWorkouts"));
        Map<String, Object> unchanged = eventRow(userId, second);
        assertEquals("Push", unchanged.get("display_name"));
        assertCount(1800, unchanged.get("duration_seconds"));
        assertEquals(Boolean.TRUE, unchanged.get("from_template"));
        assertEquals(Boolean.FALSE, unchanged.get("used_external_load"));

        assertEquals(HttpStatus.BAD_REQUEST, post(
                "/api/v1/founder/workouts",
                workout(second, FastFounderRulesConfig.START.plusSeconds(10), "2026-06-01", ",\"durationSeconds\":-1"),
                token
        ).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post(
                "/api/v1/founder/workouts",
                workout(UUID.randomUUID(), FastFounderRulesConfig.START.plusSeconds(20), "2026-06-01",
                        ",\"durationSeconds\":-1,\"exerciseCount\":-1,\"completedSetCount\":-5"),
                token
        ).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post(
                "/api/v1/founder/workouts",
                workout(UUID.randomUUID(), FastFounderRulesConfig.START.plusSeconds(30), "2026-06-01",
                        ",\"displayName\":\"" + "x".repeat(81) + "\""),
                token
        ).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, post(
                "/api/v1/founder/workouts",
                workout(second, FastFounderRulesConfig.START.plusSeconds(40), "2026-06-01", ",\"displayName\":\"Other\""),
                token
        ).getStatusCode());
        assertEquals(2, countEvents(userId));
        assertEquals("Push", eventRow(userId, second).get("display_name"));

        UUID application = applicationId(userId);
        UUID rejected = UUID.randomUUID();
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                """
                insert into founder_workout_event (
                    id, founder_application_id, client_workout_id, completed_at, workout_local_date, created_at, duration_seconds
                ) values (?::uuid, ?::uuid, ?::uuid, ?::timestamptz, ?::date, ?::timestamptz, -1)
                """,
                rejected.toString(),
                application.toString(),
                rejected.toString(),
                FastFounderRulesConfig.START.plusSeconds(50).toString(),
                "2026-06-01",
                FastFounderRulesConfig.START.plusSeconds(50).toString()
        ));
        assertEquals(2, countEvents(userId));
    }

    @Test
    void reviewSnapshotFreezesRulesAndReportSubmissionDoesNotGrantLifetime() {
        String token = enrolled("frozen-rules");
        String userId = userId(token);
        completeTraining(token);
        UUID submissionId = UUID.randomUUID();
        String body = report(submissionId, "1.4.0", "The timer is easy to miss.",
                ",\"requiredWorkoutCount\":99,\"rulesProfile\":\"production\",\"temporaryProWorkoutCount\":7,"
                        + "\"qualificationWindowDays\":1,\"status\":\"APPROVED\",\"founderLifetime\":true");
        Map<String, Object> pending = parse(ok(post("/api/v1/founder/tester-report", body, token)));
        assertEquals("PENDING_APPROVAL", pending.get("status"));
        assertCount(2, progress(pending).get("requiredWorkouts"));
        Map<String, Object> frozen = jdbc.queryForMap(
                """
                select snapshot.rules_profile, snapshot.temporary_pro_workout_count, snapshot.required_workout_count,
                       snapshot.required_distinct_day_count, snapshot.qualification_window_days,
                       snapshot.client_submission_id, snapshot.feedback_text
                from founder_review_snapshot snapshot
                join founder_application application on application.id = snapshot.founder_application_id
                where application.user_id = ?::uuid
                """,
                userId
        );
        assertEquals("fast", frozen.get("rules_profile"));
        assertCount(1, frozen.get("temporary_pro_workout_count"));
        assertCount(2, frozen.get("required_workout_count"));
        assertCount(1, frozen.get("required_distinct_day_count"));
        assertCount(45, frozen.get("qualification_window_days"));
        assertEquals(submissionId.toString(), frozen.get("client_submission_id").toString());
        assertEquals("The timer is easy to miss.", frozen.get("feedback_text"));

        ResponseEntity<String> retry = post("/api/v1/founder/tester-report", body, token);
        assertEquals(HttpStatus.OK, retry.getStatusCode());
        assertEquals(1, countSnapshots(userId));
        Map<String, Object> entitlements = parse(ok(get("/api/v1/entitlements", token)));
        assertFlag(true, entitlements.get("temporaryFounderPro"));
        assertFlag(false, entitlements.get("founderLifetime"));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from entitlement_grant where user_id = ?::uuid and source = 'FOUNDER_LIFETIME'",
                Integer.class,
                userId
        ));

        rulesBinding.replace("production", FounderRules.PRODUCTION);
        Map<String, Object> after = parse(ok(get("/api/v1/founder", token)));
        assertEquals("PENDING_APPROVAL", after.get("status"));
        assertCount(10, progress(after).get("requiredWorkouts"));
        Map<String, Object> stillFrozen = jdbc.queryForMap(
                """
                select snapshot.rules_profile, snapshot.temporary_pro_workout_count, snapshot.required_workout_count,
                       snapshot.required_distinct_day_count, snapshot.qualification_window_days
                from founder_review_snapshot snapshot
                join founder_application application on application.id = snapshot.founder_application_id
                where application.user_id = ?::uuid
                """,
                userId
        );
        assertEquals("fast", stillFrozen.get("rules_profile"));
        assertCount(1, stillFrozen.get("temporary_pro_workout_count"));
        assertCount(2, stillFrozen.get("required_workout_count"));
        assertCount(1, stillFrozen.get("required_distinct_day_count"));
        assertCount(45, stillFrozen.get("qualification_window_days"));
    }

    private void qualify(String token) {
        completeTraining(token);
        ok(put("/api/v1/founder/feedback", "{\"text\":\"Ready\"}", token));
        ok(post("/api/v1/founder/tester-report", report("1.0.0"), token));
    }

    private void completeTraining(String token) {
        ok(post("/api/v1/founder/workouts", workout(UUID.randomUUID(), FastFounderRulesConfig.START, "2026-06-01"), token));
        ok(post("/api/v1/founder/workouts", workout(UUID.randomUUID(), FastFounderRulesConfig.START.plusSeconds(10), "2026-06-01"), token));
    }

    private String enrolled(String subject) {
        String token = login(subject);
        ok(post("/api/v1/founder/enrollment", "{}", token));
        return token;
    }

    private String login(String subject) {
        identities.accept(subject, subject, subject + "@example.com", true);
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + subject + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private String userId(String token) {
        return String.valueOf(parse(ok(get("/api/v1/me", token))).get("id"));
    }

    private static String workout(UUID id, Instant completedAt, String localDate) {
        return workout(id, completedAt, localDate, "");
    }

    private static String workout(UUID id, Instant completedAt, String localDate, String extra) {
        return "{\"workoutId\":\"" + id + "\",\"completedAt\":\"" + completedAt + "\",\"localDate\":\"" + localDate + "\"" + extra + "}";
    }

    private static String report(String version) {
        return report(UUID.randomUUID(), version, "Ready");
    }

    private static String report(UUID submissionId, String version, String feedback) {
        return report(submissionId, version, feedback, "");
    }

    private static String report(UUID submissionId, String version, String feedback, String extra) {
        return "{\"submissionId\":\"" + submissionId + "\",\"appVersion\":\"" + version
                + "\",\"feedback\":\"" + feedback + "\"" + extra + "}";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> progress(Map<String, Object> state) {
        return (Map<String, Object>) state.get("progress");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requirement(Map<String, Object> state, String name) {
        return (Map<String, Object>) state.get(name);
    }

    private int countApplications(String userId) {
        return jdbc.queryForObject(
                "select count(*) from founder_application where user_id = ?::uuid",
                Integer.class,
                userId
        );
    }

    private int columnCount(String table, String... columns) {
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.length, "?"));
        Object[] args = new Object[columns.length + 1];
        args[0] = table;
        System.arraycopy(columns, 0, args, 1, columns.length);
        return jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = ? and column_name in (" + placeholders + ")",
                Integer.class,
                args
        );
    }

    private Map<String, Object> eventRow(String userId, UUID clientWorkoutId) {
        return jdbc.queryForMap(
                """
                select event.display_name, event.duration_seconds, event.exercise_count,
                       event.completed_set_count, event.from_template, event.used_external_load
                from founder_workout_event event
                join founder_application application on application.id = event.founder_application_id
                where application.user_id = ?::uuid and event.client_workout_id = ?::uuid
                """,
                userId,
                clientWorkoutId.toString()
        );
    }

    private int countEvents(String userId) {
        return jdbc.queryForObject(
                """
                select count(*) from founder_workout_event event
                join founder_application application on application.id = event.founder_application_id
                where application.user_id = ?::uuid
                """,
                Integer.class,
                userId
        );
    }

    private int countSnapshots(String userId) {
        return jdbc.queryForObject(
                """
                select count(*) from founder_review_snapshot snapshot
                join founder_application application on application.id = snapshot.founder_application_id
                where application.user_id = ?::uuid
                """,
                Integer.class,
                userId
        );
    }

    private String localDate(String userId) {
        return jdbc.queryForObject(
                """
                select event.workout_local_date::text from founder_workout_event event
                join founder_application application on application.id = event.founder_application_id
                where application.user_id = ?::uuid
                """,
                String.class,
                userId
        );
    }

    private UUID applicationId(String userId) {
        return jdbc.queryForObject(
                "select id from founder_application where user_id = ?::uuid",
                UUID.class,
                userId
        );
    }

    private Map<String, Object> snapshot(String userId) {
        return jdbc.queryForMap(
                """
                select snapshot.app_version, snapshot.platform, snapshot.qualifying_workout_count,
                       snapshot.distinct_workout_day_count, snapshot.feedback_text,
                       snapshot.client_submission_id
                from founder_review_snapshot snapshot
                join founder_application application on application.id = snapshot.founder_application_id
                where application.user_id = ?::uuid
                """,
                userId
        );
    }

    private List<Integer> race(ThrowingCall first, ThrowingCall second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (ThrowingCall call : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return call.run().getStatusCode().value();
                }));
            }
            ready.await();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            return statuses;
        } finally {
            pool.shutdown();
        }
    }

    private List<Integer> race(ThrowingCall call) throws Exception {
        return race(call, call);
    }

    private ResponseEntity<String> ok(ResponseEntity<String> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return response;
    }

    private ResponseEntity<String> get(String path, String accessToken) {
        RestClient.RequestHeadersSpec<?> request = client().get().uri(path);
        if (accessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return exchange(request);
    }

    private ResponseEntity<String> post(String path, String json, String accessToken) {
        RestClient.RequestBodySpec request = client().post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return exchange(request.body(json));
    }

    private ResponseEntity<String> put(String path, String json, String accessToken) {
        RestClient.RequestBodySpec request = client().put().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return exchange(request.body(json));
    }

    private ResponseEntity<String> exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((httpRequest, response) -> ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private RestClient client() {
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    private static Map<String, Object> parse(ResponseEntity<String> response) {
        return new BasicJsonParser().parseMap(response.getBody());
    }

    private static void assertFlag(boolean expected, Object actual) {
        assertEquals(Boolean.toString(expected), String.valueOf(actual));
    }

    private static void assertCount(int expected, Object actual) {
        assertEquals(expected, ((Number) actual).intValue());
    }

    @FunctionalInterface
    private interface ThrowingCall {
        ResponseEntity<String> run() throws Exception;
    }
}
