package eu.strictworkout.founder;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import jakarta.persistence.EntityManager;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        properties = {
                "strict.auth.session-lifetime=P365D",
                "strict.admin.session-lifetime=P30D",
                "strict.admin.google-subjects=admin-subject"
        }
)
@Import({ScriptedIdentityVerifierConfig.class, FastFounderRulesConfig.class})
@Testcontainers
class FounderReviewIT {

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
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FounderReviewDecisionRepository decisions;

    @BeforeEach
    void resetClock() {
        clock.set(FastFounderRulesConfig.START);
    }

    @Test
    void normalUserCannotApproveOrGrantLifetime() {
        String user = pendingUser("self-approve");
        String applicationId = applicationId(userId(user));
        ResponseEntity<String> approval = post(
                "/api/v1/admin/founder/applications/" + applicationId + "/approval",
                "{\"approved\":true,\"status\":\"APPROVED\",\"lifetimePro\":true,\"entitlement\":\"PRO\",\"tier\":\"founderLifetime\",\"isPro\":true}",
                user
        );
        assertEquals(HttpStatus.UNAUTHORIZED, approval.getStatusCode());
        ResponseEntity<String> rejection = post(
                "/api/v1/admin/founder/applications/" + applicationId + "/rejection",
                "{\"reason\":\"no\"}",
                user
        );
        assertEquals(HttpStatus.UNAUTHORIZED, rejection.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, post("/api/v1/founder/approval", "{\"status\":\"APPROVED\"}", user).getStatusCode());

        ResponseEntity<String> workout = post(
                "/api/v1/founder/workouts",
                "{\"workoutId\":\"" + UUID.randomUUID() + "\",\"completedAt\":\"" + FastFounderRulesConfig.START
                        + "\",\"localDate\":\"2026-06-01\",\"status\":\"APPROVED\",\"founderLifetime\":true,\"isPro\":true}",
                user
        );
        assertEquals(HttpStatus.CONFLICT, workout.getStatusCode());
        assertEquals("APPLICATION_PENDING", parse(workout).get("errorCode"));
        assertEquals("PENDING_APPROVAL", parse(ok(get("/api/v1/founder", user))).get("status"));
        Map<String, Object> entitlements = parse(ok(get("/api/v1/entitlements", user)));
        assertEquals("PRO", entitlements.get("access"));
        assertFlag(false, entitlements.get("founderLifetime"));
        assertFlag(true, entitlements.get("temporaryFounderPro"));
        assertEquals(0, grantCount(userId(user)));
        assertEquals(0, grantCount(userId(user)));
    }

    @Test
    void adminAndMobileSessionsAreNotInterchangeable() {
        String user = login("mobile-user", "mobile-user");
        String admin = adminLogin();
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me", admin).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/founder", admin).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/entitlements", admin).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/admin/founder/applications", user).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/admin/founder/applications", null).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/v1/health", null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/v1/admin/session", "{\"idToken\":\"stranger\"}", null).getStatusCode());
        identities.accept("other-admin", "not-allowlisted", "other@example.com", true);
        ResponseEntity<String> denied = post("/api/v1/admin/session", "{\"idToken\":\"other-admin\"}", null);
        assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
        assertEquals("ADMIN_NOT_ALLOWED", parse(denied).get("errorCode"));
        assertFalse(denied.getBody().contains("not-allowlisted"));
    }

    @Test
    void approvalGrantsLifetimeAtomicallyAndSurvivesANewLogin() {
        String user = pendingUser("lifetime-user");
        String userId = userId(user);
        Map<String, Object> before = parse(ok(get("/api/v1/entitlements", user)));
        assertFlag(false, before.get("founderLifetime"));
        String applicationId = applicationId(userId);
        String admin = adminLogin();
        Map<String, Object> approved = parse(ok(post(
                "/api/v1/admin/founder/applications/" + applicationId + "/approval",
                "{\"status\":\"IGNORED\"}",
                admin
        )));
        assertEquals("APPROVED", approved.get("status"));
        assertEquals("lifetime-user@example.com", approved.get("testerEmail"));
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) approved.get("snapshot");
        assertCount(2, snapshot.get("qualifyingWorkoutCount"));
        assertEquals("Ready for review", snapshot.get("feedbackText"));
        @SuppressWarnings("unchecked")
        Map<String, Object> decision = (Map<String, Object>) approved.get("decision");
        assertEquals("APPROVED", decision.get("decision"));
        assertEquals("null", String.valueOf(decision.get("reason")));

        Map<String, Object> again = parse(ok(post(
                "/api/v1/admin/founder/applications/" + applicationId + "/approval",
                "{}",
                admin
        )));
        assertEquals("APPROVED", again.get("status"));
        assertEquals(1, grantCount(userId));
        assertEquals(1, decisionCount(applicationId));

        Map<String, Object> entitlements = parse(ok(get("/api/v1/entitlements", user)));
        assertEquals("PRO", entitlements.get("access"));
        assertFlag(true, entitlements.get("founderLifetime"));
        assertFlag(false, entitlements.get("temporaryFounderPro"));

        String reinstalled = login("lifetime-user", "lifetime-user");
        assertEquals(userId, userId(reinstalled));
        assertNotEquals(user, reinstalled);
        Map<String, Object> restored = parse(ok(get("/api/v1/entitlements", reinstalled)));
        assertFlag(true, restored.get("founderLifetime"));
        entityManager.clear();
        FounderReviewDecision reloaded = decisions.findByApplication_Id(UUID.fromString(applicationId)).orElseThrow();
        assertEquals("APPROVED", reloaded.getDecision());
        assertEquals(decision.get("reviewedBy"), reloaded.getAdminId().toString());
    }

    @Test
    void rejectionRequiresAReasonAndDoesNotGrantLifetime() {
        String user = pendingUser("rejected-user");
        String applicationId = applicationId(userId(user));
        String admin = adminLogin();
        ResponseEntity<String> blank = post(
                "/api/v1/admin/founder/applications/" + applicationId + "/rejection",
                "{\"reason\":\" \"}",
                admin
        );
        assertEquals(HttpStatus.BAD_REQUEST, blank.getStatusCode());
        Map<String, Object> rejected = parse(ok(post(
                "/api/v1/admin/founder/applications/" + applicationId + "/rejection",
                "{\"reason\":\"Not enough detail\"}",
                admin
        )));
        assertEquals("REJECTED", rejected.get("status"));
        parse(ok(post(
                "/api/v1/admin/founder/applications/" + applicationId + "/rejection",
                "{\"reason\":\"Not enough detail\"}",
                admin
        )));
        ResponseEntity<String> changed = post(
                "/api/v1/admin/founder/applications/" + applicationId + "/rejection",
                "{\"reason\":\"A different reason\"}",
                admin
        );
        assertEquals(HttpStatus.CONFLICT, changed.getStatusCode());
        assertEquals("REVIEW_CONFLICT", parse(changed).get("errorCode"));
        ResponseEntity<String> approveAfter = post(
                "/api/v1/admin/founder/applications/" + applicationId + "/approval",
                "{}",
                admin
        );
        assertEquals(HttpStatus.CONFLICT, approveAfter.getStatusCode());
        Map<String, Object> entitlements = parse(ok(get("/api/v1/entitlements", user)));
        assertEquals("FREE", entitlements.get("access"));
        assertFlag(false, entitlements.get("founderLifetime"));
        assertFlag(false, entitlements.get("temporaryFounderPro"));
        assertEquals(0, grantCount(userId(user)));
        assertEquals("REJECTED", parse(ok(get("/api/v1/founder", user))).get("status"));
    }

    @Test
    void approvingANonPendingApplicationConflictsAndMissingApplicationIsNotFound() {
        String user = login("not-ready", "not-ready");
        ok(post("/api/v1/founder/enrollment", "{}", user));
        String applicationId = applicationId(userId(user));
        String admin = adminLogin();
        ResponseEntity<String> tooEarly = post("/api/v1/admin/founder/applications/" + applicationId + "/approval", "{}", admin);
        assertEquals(HttpStatus.CONFLICT, tooEarly.getStatusCode());
        assertEquals("REVIEW_CONFLICT", parse(tooEarly).get("errorCode"));
        ResponseEntity<String> missing = post(
                "/api/v1/admin/founder/applications/" + UUID.randomUUID() + "/approval",
                "{}",
                admin
        );
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("FOUNDER_NOT_FOUND", parse(missing).get("errorCode"));
        assertEquals(0, grantCount(userId(user)));
    }

    @Test
    void aFailedApprovalRollsBackBothStatusAndGrant() {
        String user = pendingUser("rollback-user");
        String applicationId = applicationId(userId(user));
        adminLogin();
        jdbc.update(
                """
                insert into founder_review_decision (
                    id, founder_application_id, admin_user_id, decision, reason, decided_at
                )
                select ?::uuid, ?::uuid, id, 'APPROVED', null, now() from admin_user
                """,
                UUID.randomUUID().toString(),
                applicationId
        );
        String admin = adminLogin();
        ResponseEntity<String> failed = post("/api/v1/admin/founder/applications/" + applicationId + "/approval", "{}", admin);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.getStatusCode());
        assertEquals("INTERNAL_ERROR", parse(failed).get("errorCode"));
        assertFalse(failed.getBody().contains("Ready for review"));
        assertEquals("PENDING_APPROVAL", jdbc.queryForObject(
                "select status from founder_application where id = ?::uuid",
                String.class,
                applicationId
        ));
        assertEquals(0, grantCount(userId(user)));
    }

    @Test
    void concurrentApprovalCreatesOneGrant() throws Exception {
        String user = pendingUser("concurrent-review");
        String applicationId = applicationId(userId(user));
        String admin = adminLogin();
        List<Integer> statuses = race(
                () -> post("/api/v1/admin/founder/applications/" + applicationId + "/approval", "{}", admin),
                () -> post("/api/v1/admin/founder/applications/" + applicationId + "/rejection", "{\"reason\":\"Race\"}", admin)
        );
        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        String status = jdbc.queryForObject(
                "select status from founder_application where id = ?::uuid",
                String.class,
                applicationId
        );
        int grants = grantCount(userId(user));
        if ("APPROVED".equals(status)) {
            assertEquals(1, grants);
        } else {
            assertEquals("REJECTED", status);
            assertEquals(0, grants);
        }
        assertEquals(1, decisionCount(applicationId));
    }

    @Test
    void usersCannotReviewEachOther() {
        String first = pendingUser("queue-a");
        String second = pendingUser("queue-b");
        String admin = adminLogin();
        List<Map<String, Object>> queue = parseList(ok(get("/api/v1/admin/founder/applications", admin)));
        String firstId = applicationId(userId(first));
        String secondId = applicationId(userId(second));
        List<String> ids = queue.stream().map(row -> String.valueOf(row.get("id"))).toList();
        assertTrue(ids.contains(firstId));
        assertTrue(ids.contains(secondId));
        ok(post("/api/v1/admin/founder/applications/" + firstId + "/approval", "{}", admin));
        assertEquals("PENDING_APPROVAL", parse(ok(get("/api/v1/founder", second))).get("status"));
        assertEquals(0, grantCount(userId(second)));
        assertFlag(false, parse(ok(get("/api/v1/entitlements", second))).get("founderLifetime"));
    }

    @Test
    void approvedAndRejectedApplicationsCannotResubmitTheTesterReport() {
        String admin = adminLogin();
        String approved = pendingUser("closed-approved");
        String approvedId = applicationId(userId(approved));
        Object approvedPendingAt = jdbc.queryForObject(
                "select pending_at from founder_application where id = ?::uuid",
                Object.class,
                approvedId
        );
        ok(post("/api/v1/admin/founder/applications/" + approvedId + "/approval", "{}", admin));
        ResponseEntity<String> approvedAgain = post(
                "/api/v1/founder/tester-report",
                "{\"submissionId\":\"" + UUID.randomUUID() + "\",\"appVersion\":\"9.9.9\",\"feedback\":\"Changed\",\"status\":\"APPROVED\",\"founderLifetime\":true}",
                approved
        );
        assertEquals(HttpStatus.CONFLICT, approvedAgain.getStatusCode());
        assertEquals("APPROVED", parse(ok(get("/api/v1/founder", approved))).get("status"));
        assertEquals(approvedPendingAt, jdbc.queryForObject(
                "select pending_at from founder_application where id = ?::uuid",
                Object.class,
                approvedId
        ));
        assertEquals(1, snapshotCount(approvedId));

        String rejected = pendingUser("closed-rejected");
        String rejectedId = applicationId(userId(rejected));
        ok(post(
                "/api/v1/admin/founder/applications/" + rejectedId + "/rejection",
                "{\"reason\":\"Not this time\"}",
                admin
        ));
        ResponseEntity<String> rejectedAgain = post(
                "/api/v1/founder/tester-report",
                "{\"submissionId\":\"" + UUID.randomUUID() + "\",\"appVersion\":\"9.9.9\",\"feedback\":\"Changed\"}",
                rejected
        );
        assertEquals(HttpStatus.CONFLICT, rejectedAgain.getStatusCode());
        assertEquals("REJECTED", parse(ok(get("/api/v1/founder", rejected))).get("status"));
        assertEquals(1, snapshotCount(rejectedId));
        assertEquals(0, grantCount(userId(rejected)));
    }

    private int snapshotCount(String applicationId) {
        return jdbc.queryForObject(
                "select count(*) from founder_review_snapshot where founder_application_id = ?::uuid",
                Integer.class,
                applicationId
        );
    }

    private String pendingUser(String subject) {
        String token = login(subject, subject);
        ok(post("/api/v1/founder/enrollment", "{}", token));
        ok(post("/api/v1/founder/workouts", workout(UUID.randomUUID(), FastFounderRulesConfig.START, "2026-06-01"), token));
        ok(post("/api/v1/founder/workouts", workout(UUID.randomUUID(), FastFounderRulesConfig.START.plusSeconds(5), "2026-06-01"), token));
        ok(post(
                "/api/v1/founder/tester-report",
                "{\"submissionId\":\"" + UUID.randomUUID() + "\",\"appVersion\":\"1.0.0\",\"feedback\":\"Ready for review\"}",
                token
        ));
        return token;
    }

    private String adminLogin() {
        identities.accept("admin-token", "admin-subject", "admin@example.com", true);
        ResponseEntity<String> response = post("/api/v1/admin/session", "{\"idToken\":\"admin-token\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private String login(String tokenName, String subject) {
        identities.accept(tokenName, subject, subject + "@example.com", true);
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + tokenName + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private String userId(String token) {
        return String.valueOf(parse(ok(get("/api/v1/me", token))).get("id"));
    }

    private String applicationId(String userId) {
        return jdbc.queryForObject(
                "select id::text from founder_application where user_id = ?::uuid",
                String.class,
                userId
        );
    }

    private int grantCount(String userId) {
        return jdbc.queryForObject(
                "select count(*) from entitlement_grant where user_id = ?::uuid",
                Integer.class,
                userId
        );
    }

    private int decisionCount(String applicationId) {
        return jdbc.queryForObject(
                "select count(*) from founder_review_decision where founder_application_id = ?::uuid",
                Integer.class,
                applicationId
        );
    }

    private static String workout(UUID id, Instant completedAt, String localDate) {
        return "{\"workoutId\":\"" + id + "\",\"completedAt\":\"" + completedAt + "\",\"localDate\":\"" + localDate + "\"}";
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseList(ResponseEntity<String> response) {
        return (List<Map<String, Object>>) (List<?>) new BasicJsonParser().parseList(response.getBody());
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
