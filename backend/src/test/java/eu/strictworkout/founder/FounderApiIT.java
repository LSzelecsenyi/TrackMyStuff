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
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FounderApplicationRepository applications;

    @BeforeEach
    void resetClock() {
        clock.set(FastFounderRulesConfig.START);
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

        completeTraining(token);
        ResponseEntity<String> earlyReport = post("/api/v1/founder/tester-report", report("1.0.0"), token);
        assertEquals(HttpStatus.CONFLICT, earlyReport.getStatusCode());
        assertEquals("REPORT_NOT_AVAILABLE", parse(earlyReport).get("errorCode"));

        Map<String, Object> saved = parse(ok(put("/api/v1/founder/feedback", "{\"text\":\"First note\"}", token)));
        assertFlag(true, requirement(saved, "feedback").get("submitted"));
        assertEquals("SUBMIT_TESTER_REPORT", saved.get("nextAction"));
        ok(put("/api/v1/founder/feedback", "{\"text\":\"Revised note\"}", token));

        Map<String, Object> pending = parse(ok(post(
                "/api/v1/founder/tester-report",
                "{\"appVersion\":\"1.2.3\",\"platform\":\"android\",\"qualifyingWorkouts\":99}",
                token
        )));
        assertEquals("PENDING_APPROVAL", pending.get("status"));
        assertEquals("WAIT_FOR_REVIEW", pending.get("nextAction"));
        assertFlag(true, pending.get("temporaryProActive"));

        ResponseEntity<String> frozen = put("/api/v1/founder/feedback", "{\"text\":\"Changed after review\"}", token);
        assertEquals(HttpStatus.CONFLICT, frozen.getStatusCode());
        assertEquals("FEEDBACK_FROZEN", parse(frozen).get("errorCode"));

        ResponseEntity<String> retry = post("/api/v1/founder/tester-report", report("1.2.3"), token);
        assertEquals(HttpStatus.OK, retry.getStatusCode());
        ResponseEntity<String> conflict = post("/api/v1/founder/tester-report", report("9.9.9"), token);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("REPORT_CONFLICT", parse(conflict).get("errorCode"));

        Map<String, Object> snapshot = snapshot(userId(token));
        assertEquals("1.2.3", snapshot.get("app_version"));
        assertEquals("android", snapshot.get("platform"));
        assertCount(2, snapshot.get("qualifying_workout_count"));
        assertCount(1, snapshot.get("distinct_workout_day_count"));
        assertEquals("Revised note", snapshot.get("feedback_text"));
        assertEquals(1, countSnapshots(userId(token)));
        assertFalse(conflict.getBody().contains("Revised note"));
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
                    feedback_text, created_at
                ) values (?::uuid, ?::uuid, now(), '0.0.1', 'android', 2, 1, now(), now(), 'seed', now())
                """,
                UUID.randomUUID().toString(),
                applicationId.toString()
        );

        ResponseEntity<String> failed = post("/api/v1/founder/tester-report", report("1.0.0"), token);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.getStatusCode());
        assertEquals("INTERNAL_ERROR", parse(failed).get("errorCode"));
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
        ok(put("/api/v1/founder/feedback", "{\"text\":\"Race\"}", token));
        List<Integer> statuses = race(
                () -> post("/api/v1/founder/tester-report", report("1.0.0"), token),
                () -> post("/api/v1/founder/tester-report", report("1.0.0"), token)
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
        return "{\"workoutId\":\"" + id + "\",\"completedAt\":\"" + completedAt + "\",\"localDate\":\"" + localDate + "\"}";
    }

    private static String report(String version) {
        return "{\"appVersion\":\"" + version + "\",\"platform\":\"android\"}";
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
                       snapshot.distinct_workout_day_count, snapshot.feedback_text
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
