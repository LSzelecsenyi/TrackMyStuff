package eu.strictworkout.founder;

import eu.strictworkout.admin.MutableAdminAllowlist;
import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "strict.auth.session-lifetime=P365D",
                "strict.admin.session-lifetime=PT2H",
                "strict.admin.google-subjects=admin-subject"
        }
)
@Import({ScriptedIdentityVerifierConfig.class, FastFounderRulesConfig.class, FounderReviewReadIT.AllowlistConfig.class})
@Testcontainers
class FounderReviewReadIT {

    private static final String FEEDBACK = "The rest timer was easy to miss.";
    private static final String SUBJECT = "founder-reader-subject";

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
    private FounderRulesBinding rules;

    @Autowired
    private MutableAdminAllowlist allowlist;

    private volatile String csrfToken;

    private final Set<String> adminSessions = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void reset() {
        clock.set(FastFounderRulesConfig.START);
        allowlist.replaceWith("admin-subject");
        rules.replace("fast", new FounderRules(1, 2, 1, 45, true, true));
    }

    @Test
    void pendingListIsCompactAndDetailShowsTheSubmittedReview() {
        Submitted submitted = submitReview(SUBJECT, "ada.review@example.com");
        String admin = adminLogin();
        ResponseEntity<String> listResponse = ok(get("/api/v1/admin/founder/applications", admin));
        assertFalse(listResponse.getBody().contains(FEEDBACK));
        assertFalse(listResponse.getBody().contains("Early session"));
        assertFalse(listResponse.getBody().contains("workouts"));
        Map<String, Object> row = row(parseList(listResponse), submitted.applicationId);
        assertEquals("PENDING_APPROVAL", row.get("status"));
        assertEquals("ada.review@example.com", row.get("testerEmail"));
        assertEquals("2026-06-01T00:00:00Z", row.get("enrolledAt"));
        assertEquals("2026-07-16T00:00:00Z", row.get("deadlineAt"));
        assertNotNull(row.get("pendingAt"));
        assertFalse(row.containsKey("report"));
        Map<String, Object> summary = map(row, "qualification");
        assertCount(3, summary.get("qualifyingWorkoutCount"));
        assertCount(2, summary.get("distinctWorkoutDayCount"));
        assertCount(2, summary.get("requiredWorkoutCount"));
        assertCount(1, summary.get("requiredDistinctDayCount"));

        ResponseEntity<String> detailResponse = ok(get(
                "/api/v1/admin/founder/applications/" + submitted.applicationId,
                admin
        ));
        Map<String, Object> detail = parse(detailResponse);
        assertEquals("PENDING_APPROVAL", map(detail, "application").get("status"));
        assertEquals("2026-06-01T00:00:00Z", map(detail, "application").get("enrolledAt"));
        assertEquals("2026-07-16T00:00:00Z", map(detail, "application").get("deadlineAt"));
        assertEquals(row.get("pendingAt"), map(detail, "application").get("pendingAt"));
        assertEquals("ada.review@example.com", map(detail, "tester").get("email"));
        Map<String, Object> qualification = map(detail, "qualification");
        assertCount(3, qualification.get("qualifyingWorkoutCount"));
        assertCount(2, qualification.get("distinctWorkoutDayCount"));
        assertEquals("true", String.valueOf(qualification.get("trainingRequirementsComplete")));
        assertEquals("true", String.valueOf(qualification.get("temporaryProReached")));
        Map<String, Object> storedRules = map(qualification, "rules");
        assertEquals("fast", storedRules.get("profile"));
        assertCount(2, storedRules.get("requiredWorkoutCount"));
        assertCount(1, storedRules.get("requiredDistinctDayCount"));
        assertCount(1, storedRules.get("temporaryProWorkoutCount"));
        assertCount(45, storedRules.get("qualificationWindowDays"));
        assertEquals("2026-06-01T00:00:00Z", storedRules.get("enrolledAt"));
        assertEquals("2026-07-16T00:00:00Z", storedRules.get("deadlineAt"));
        Map<String, Object> report = map(detail, "report");
        assertEquals("1.4.2", report.get("appVersion"));
        assertEquals("android", report.get("platform"));
        assertEquals(FEEDBACK, report.get("feedback"));
        assertNotNull(report.get("submittedAt"));
        assertTrue(jsonNull(detail.get("decision")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> workouts = (List<Map<String, Object>>) detail.get("workouts");
        assertEquals(List.of(submitted.early, submitted.later, submitted.nextDay), workouts.stream()
                .map(workout -> String.valueOf(workout.get("clientWorkoutId")))
                .toList());
        assertEquals("2026-06-01", workouts.get(0).get("localDate"));
        assertEquals("Early session", workouts.get(0).get("displayName"));
        assertCount(1800, workouts.get(0).get("durationSeconds"));
        assertCount(4, workouts.get(0).get("exerciseCount"));
        assertCount(12, workouts.get(0).get("completedSetCount"));
        assertEquals("true", String.valueOf(workouts.get(0).get("fromTemplate")));
        assertEquals("false", String.valueOf(workouts.get(0).get("usedExternalLoad")));
        assertEquals("Later session", workouts.get(1).get("displayName"));
        assertEquals("Next day", workouts.get(2).get("displayName"));
        assertEquals("2026-06-02", workouts.get(2).get("localDate"));
        assertSecretsAbsent(detailResponse.getBody(), submitted, admin);
    }

    @Test
    void laterRuleChangesLeaveTheSubmittedEvidenceAlone() {
        Submitted submitted = submitReview("later-rules-subject", "later.rules@example.com");
        String admin = adminLogin();
        jdbc.update(
                "update founder_application set deadline_at = enrolled_at + interval '1 day' where id = ?::uuid",
                submitted.applicationId
        );
        rules.replace("production", FounderRules.PRODUCTION);
        try {
            Map<String, Object> detail = parse(ok(get(
                    "/api/v1/admin/founder/applications/" + submitted.applicationId,
                    admin
            )));
            assertEquals("2026-06-02T00:00:00Z", map(detail, "application").get("deadlineAt"));
            Map<String, Object> storedRules = map(map(detail, "qualification"), "rules");
            assertEquals("fast", storedRules.get("profile"));
            assertCount(2, storedRules.get("requiredWorkoutCount"));
            assertCount(1, storedRules.get("requiredDistinctDayCount"));
            assertCount(1, storedRules.get("temporaryProWorkoutCount"));
            assertCount(45, storedRules.get("qualificationWindowDays"));
            assertEquals("2026-07-16T00:00:00Z", storedRules.get("deadlineAt"));
            assertEquals(FEEDBACK, map(detail, "report").get("feedback"));
            assertEquals(2, jdbc.queryForObject(
                    "select required_workout_count from founder_review_snapshot where founder_application_id = ?::uuid",
                    Integer.class,
                    submitted.applicationId
            ));
        } finally {
            rules.replace("fast", new FounderRules(1, 2, 1, 45, true, true));
        }
    }

    @Test
    void missingHistoricalThresholdsStayNull() {
        Submitted submitted = submitReview("missing-rules-subject", "missing.rules@example.com");
        String admin = adminLogin();
        jdbc.update(
                """
                update founder_review_snapshot
                set rules_profile = null,
                    temporary_pro_workout_count = null,
                    required_workout_count = null,
                    required_distinct_day_count = null,
                    qualification_window_days = null
                where founder_application_id = ?::uuid
                """,
                submitted.applicationId
        );
        Map<String, Object> detail = parse(ok(get(
                "/api/v1/admin/founder/applications/" + submitted.applicationId,
                admin
        )));
        Map<String, Object> qualification = map(detail, "qualification");
        assertCount(3, qualification.get("qualifyingWorkoutCount"));
        assertCount(2, qualification.get("distinctWorkoutDayCount"));
        assertTrue(jsonNull(qualification.get("trainingRequirementsComplete")));
        assertTrue(jsonNull(qualification.get("temporaryProReached")));
        Map<String, Object> storedRules = map(qualification, "rules");
        assertTrue(jsonNull(storedRules.get("profile")));
        assertTrue(jsonNull(storedRules.get("requiredWorkoutCount")));
        assertTrue(jsonNull(storedRules.get("requiredDistinctDayCount")));
        assertTrue(jsonNull(storedRules.get("temporaryProWorkoutCount")));
        assertTrue(jsonNull(storedRules.get("qualificationWindowDays")));
        assertFalse(String.valueOf(storedRules.get("requiredWorkoutCount")).equals("10"));
    }

    @Test
    void anApplicationWithoutAReportDoesNotInventSnapshotData() {
        String user = login("open-user", "open-user@example.com");
        ok(post("/api/v1/founder/enrollment", "{}", user));
        ok(post("/api/v1/founder/workouts", observed(
                UUID.randomUUID(),
                FastFounderRulesConfig.START,
                "2026-06-01",
                "Not submitted",
                60,
                1,
                1,
                false,
                false
        ), user));
        String applicationId = applicationId(userId(user));
        String admin = adminLogin();
        List<Map<String, Object>> queue = parseList(ok(get("/api/v1/admin/founder/applications", admin)));
        assertTrue(queue.stream().noneMatch(row -> applicationId.equals(row.get("id"))));
        Map<String, Object> detail = parse(ok(get("/api/v1/admin/founder/applications/" + applicationId, admin)));
        assertEquals("ACTIVE_PRO", map(detail, "application").get("status"));
        assertTrue(jsonNull(detail.get("qualification")));
        assertTrue(jsonNull(detail.get("report")));
        assertTrue(jsonNull(detail.get("decision")));
        assertEquals(1, ((List<?>) detail.get("workouts")).size());
    }

    @Test
    void onlyACurrentAdminCookieCanReadTheReview() {
        Submitted submitted = submitReview("auth-reader-subject", "auth.reader@example.com");
        String user = login("member-reader", "member-reader@example.com");
        String path = "/api/v1/admin/founder/applications/" + submitted.applicationId;
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/admin/founder/applications", user).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get(path, user).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/admin/founder/applications", null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get(path, null).getStatusCode());

        String admin = adminLogin();
        assertEquals(HttpStatus.OK, get(path, admin).getStatusCode());

        jdbc.update("update admin_session set revoked_at = created_at");
        assertEquals(HttpStatus.UNAUTHORIZED, get(path, admin).getStatusCode());

        String renewed = adminLogin();
        assertEquals(HttpStatus.OK, get(path, renewed).getStatusCode());
        clock.set(FastFounderRulesConfig.START.plusSeconds(7201));
        assertEquals(HttpStatus.UNAUTHORIZED, get(path, renewed).getStatusCode());

        clock.set(FastFounderRulesConfig.START);
        String current = adminLogin();
        int activeBeforeRemoval = jdbc.queryForObject(
                "select count(*) from admin_session where revoked_at is null",
                Integer.class
        );
        allowlist.replaceWith();
        ResponseEntity<String> removed = get(path, current);
        assertEquals(HttpStatus.UNAUTHORIZED, removed.getStatusCode());
        assertFalse(removed.getBody().contains(current));
        assertFalse(removed.getBody().contains("admin-subject"));
        assertEquals(activeBeforeRemoval - 1, jdbc.queryForObject(
                "select count(*) from admin_session where revoked_at is null",
                Integer.class
        ));
    }

    private Submitted submitReview(String subject, String email) {
        String user = login(subject, email);
        ok(post("/api/v1/founder/enrollment", "{}", user));
        UUID nextDay = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        UUID early = UUID.randomUUID();
        ok(post("/api/v1/founder/workouts", observed(
                nextDay, FastFounderRulesConfig.START.plusSeconds(14 * 3600), "2026-06-02", "Next day", 900, 2, 6, true, true
        ), user));
        ok(post("/api/v1/founder/workouts", observed(
                later, FastFounderRulesConfig.START.plusSeconds(50), "2026-06-01", "Later session", 2400, 5, 15, false, true
        ), user));
        ok(post("/api/v1/founder/workouts", observed(
                early, FastFounderRulesConfig.START, "2026-06-01", "Early session", 1800, 4, 12, true, false
        ), user));
        ok(post(
                "/api/v1/founder/tester-report",
                "{\"submissionId\":\"" + UUID.randomUUID() + "\",\"appVersion\":\"1.4.2\",\"feedback\":\"" + FEEDBACK + "\"}",
                user
        ));
        return new Submitted(applicationId(userId(user)), user, early.toString(), later.toString(), nextDay.toString());
    }

    private void assertSecretsAbsent(String body, Submitted submitted, String adminSession) {
        assertFalse(body.contains(SUBJECT));
        assertFalse(body.contains(submitted.bearer));
        assertFalse(body.contains(adminSession));
        assertFalse(body.contains("googleSubject"));
        assertFalse(body.contains("tokenHash"));
        assertFalse(body.contains("clientSubmissionId"));
        String userId = userId(submitted.bearer);
        assertFalse(body.contains(userId));
        String snapshotId = jdbc.queryForObject(
                "select id::text from founder_review_snapshot where founder_application_id = ?::uuid",
                String.class,
                submitted.applicationId
        );
        String submissionId = jdbc.queryForObject(
                "select client_submission_id::text from founder_review_snapshot where founder_application_id = ?::uuid",
                String.class,
                submitted.applicationId
        );
        assertFalse(body.contains(snapshotId));
        assertFalse(body.contains(submissionId));
        List<String> eventIds = jdbc.queryForList(
                "select id::text from founder_workout_event where founder_application_id = ?::uuid",
                String.class,
                submitted.applicationId
        );
        for (String eventId : eventIds) {
            assertFalse(body.contains(eventId));
        }
    }

    private String login(String subject, String email) {
        identities.accept(subject, subject, email, true);
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + subject + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private String adminLogin() {
        identities.accept("admin-token", "admin-subject", "admin@example.com", true);
        ResponseEntity<String> response = post("/api/v1/admin/session", "{\"idToken\":\"admin-token\"}", null);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        String token = setCookie(response, "STRICT_ADMIN_SESSION");
        assertNotNull(token);
        adminSessions.add(token);
        return token;
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

    private static String observed(
            UUID id,
            Instant completedAt,
            String localDate,
            String name,
            int duration,
            int exercises,
            int sets,
            boolean fromTemplate,
            boolean usedExternalLoad
    ) {
        return "{\"workoutId\":\"" + id + "\",\"completedAt\":\"" + completedAt + "\",\"localDate\":\"" + localDate
                + "\",\"displayName\":\"" + name + "\",\"durationSeconds\":" + duration
                + ",\"exerciseCount\":" + exercises + ",\"completedSetCount\":" + sets
                + ",\"fromTemplate\":" + fromTemplate + ",\"usedExternalLoad\":" + usedExternalLoad + "}";
    }

    private ResponseEntity<String> ok(ResponseEntity<String> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return response;
    }

    private ResponseEntity<String> get(String path, String accessToken) {
        RestClient.RequestHeadersSpec<?> request = client().get().uri(path);
        applyCredential(request, path, accessToken, false);
        return exchange(request);
    }

    private ResponseEntity<String> post(String path, String json, String accessToken) {
        RestClient.RequestBodySpec request = client().post().uri(path).contentType(MediaType.APPLICATION_JSON);
        applyCredential(request, path, accessToken, true);
        return exchange(request.body(json));
    }

    private void applyCredential(
            RestClient.RequestHeadersSpec<?> request,
            String path,
            String accessToken,
            boolean mutating
    ) {
        if (path.startsWith("/api/v1/admin")) {
            String csrf = ensureCsrf();
            StringBuilder cookie = new StringBuilder("XSRF-TOKEN=").append(csrf);
            if (accessToken != null && adminSessions.contains(accessToken)) {
                cookie.append("; STRICT_ADMIN_SESSION=").append(accessToken);
            } else if (accessToken != null) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            }
            request.header(HttpHeaders.COOKIE, cookie.toString());
            if (mutating) {
                request.header("X-XSRF-TOKEN", csrf);
            }
            return;
        }
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
    }

    private String ensureCsrf() {
        if (csrfToken != null) {
            return csrfToken;
        }
        ResponseEntity<String> response = exchange(client().get().uri("/api/v1/admin/csrf"));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        String token = setCookie(response, "XSRF-TOKEN");
        assertNotNull(token);
        csrfToken = token;
        return token;
    }

    private static String setCookie(ResponseEntity<?> response, String name) {
        List<String> headers = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (headers == null) {
            return null;
        }
        String prefix = name + "=";
        for (String header : headers) {
            if (header.startsWith(prefix)) {
                String value = header.substring(prefix.length());
                int end = value.indexOf(';');
                return end < 0 ? value : value.substring(0, end);
            }
        }
        return null;
    }

    private ResponseEntity<String> exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((httpRequest, response) -> {
            HttpHeaders headers = new HttpHeaders();
            response.getHeaders().forEach(headers::addAll);
            byte[] body = response.getBody().readAllBytes();
            return ResponseEntity.status(response.getStatusCode())
                    .headers(headers)
                    .body(new String(body, StandardCharsets.UTF_8));
        });
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, String id) {
        return rows.stream()
                .filter(item -> id.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
    }

    private static void assertCount(int expected, Object actual) {
        assertEquals(expected, ((Number) actual).intValue());
    }

    private static boolean jsonNull(Object value) {
        return value == null || "null".equals(value);
    }

    private record Submitted(String applicationId, String bearer, String early, String later, String nextDay) {
    }

    @TestConfiguration
    static class AllowlistConfig {
        @Bean
        @Primary
        MutableAdminAllowlist mutableAdminAllowlist() {
            return new MutableAdminAllowlist();
        }
    }
}
