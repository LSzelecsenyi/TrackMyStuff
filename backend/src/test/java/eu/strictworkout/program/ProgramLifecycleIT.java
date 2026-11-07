package eu.strictworkout.program;

import eu.strictworkout.account.AccountDeletionStore;
import eu.strictworkout.account.DeletedAccountPolicy;
import eu.strictworkout.account.DeletionMarkerCleanup;
import eu.strictworkout.account.DeletionMarkerService;
import eu.strictworkout.account.EarlyAdopterAssignment;
import eu.strictworkout.founder.FastFounderRulesConfig;
import eu.strictworkout.account.GoogleSubjectHash;
import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class ProgramLifecycleIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private ScriptedIdentityVerifier identities;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DeletionMarkerService markers;

    @Autowired
    private DeletionMarkerCleanup cleanup;

    @Autowired
    private DeletedAccountPolicy policy;

    @Autowired
    private EarlyAdopterAssignment earlyAdopters;

    private volatile String csrfToken;

    private final Set<String> adminSessions = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void programsStartActive() {
        jdbc.update("update founder_program_capacity set lifecycle = 'ACTIVE' where id = 1");
        jdbc.update("update early_adopter_cohort set lifecycle = 'ACTIVE' where id = 1");
        entityManager.clear();
    }

    @Test
    @DisplayName("Given Founder enrollment was open, when the database is migrated, then it is ACTIVE and the old flag is gone")
    void founderOpenMigratesToActiveAndClosedWouldBecomeInactive() {
        assertEquals("ACTIVE", lifecycle("founder_program_capacity"));
        assertEquals("ACTIVE", lifecycle("early_adopter_cohort"));
        assertEquals(0, jdbc.queryForObject(
                """
                select count(*) from information_schema.columns
                where table_name = 'founder_program_capacity' and column_name = 'enrollment_open'
                """,
                Integer.class
        ));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "select case when true then 'ACTIVE' else 'INACTIVE' end",
                String.class
        ));
        assertEquals("INACTIVE", jdbc.queryForObject(
                "select case when false then 'ACTIVE' else 'INACTIVE' end",
                String.class
        ));
    }

    @Test
    @DisplayName("Given Founder is ACTIVE, when admin pauses it, then new enrollment is rejected and an existing participant stays eligible")
    void pauseFounderRejectsNewEnrollmentAndKeepsExistingParticipants() {
        String existing = login("pause-existing");
        ok(post("/api/v1/founder/enrollment", "{}", existing));
        String admin = adminLogin();
        try {
            Map<String, Object> paused = parse(ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"INACTIVE\"}", admin)));
            assertEquals("INACTIVE", paused.get("lifecycle"));
            assertFalse(Boolean.parseBoolean(String.valueOf(paused.get("acceptingNewParticipants"))));
            String blocked = login("pause-new");
            ResponseEntity<String> rejected = post("/api/v1/founder/enrollment", "{}", blocked);
            assertEquals(HttpStatus.CONFLICT, rejected.getStatusCode());
            assertEquals("ENROLLMENT_CLOSED", parse(rejected).get("errorCode"));
            assertEquals("ACTIVE_FREE", parse(ok(post("/api/v1/founder/enrollment", "{}", existing))).get("status"));
            assertEquals(HttpStatus.OK, post("/api/v1/founder/workouts", workout(), existing).getStatusCode());
        } finally {
            programsStartActive();
        }
    }

    @Test
    @DisplayName("Given Founder is INACTIVE, when admin resumes it, then a new eligible participant can enroll")
    void resumeFounderAllowsNewEnrollment() {
        String admin = adminLogin();
        ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"INACTIVE\"}", admin));
        Map<String, Object> resumed = parse(ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"ACTIVE\"}", admin)));
        assertEquals("ACTIVE", resumed.get("lifecycle"));
        String joiner = login("resume-new");
        assertEquals(HttpStatus.OK, post("/api/v1/founder/enrollment", "{}", joiner).getStatusCode());
    }

    @Test
    @DisplayName("Given Founder is RETIRED, when admin tries to reactivate it, then the backend rejects the transition")
    void retiredFounderCannotBeReactivated() {
        String admin = adminLogin();
        try {
            Map<String, Object> retired = parse(ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin)));
            assertEquals("RETIRED", retired.get("lifecycle"));
            Map<String, Object> again = parse(ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin)));
            assertEquals("RETIRED", again.get("lifecycle"));
            ResponseEntity<String> resume = put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"ACTIVE\"}", admin);
            assertEquals(HttpStatus.CONFLICT, resume.getStatusCode());
            assertEquals("PROGRAM_RETIRED", parse(resume).get("errorCode"));
            ResponseEntity<String> pause = put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"INACTIVE\"}", admin);
            assertEquals("PROGRAM_RETIRED", parse(pause).get("errorCode"));
            ResponseEntity<String> oldSwitch = put("/api/v1/admin/founder/enrollment", "{\"open\":true}", admin);
            assertEquals(HttpStatus.CONFLICT, oldSwitch.getStatusCode());
            assertEquals("PROGRAM_RETIRED", parse(oldSwitch).get("errorCode"));
            assertEquals("RETIRED", lifecycle("founder_program_capacity"));
            String member = login("retired-member");
            assertEquals(HttpStatus.UNAUTHORIZED, put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"ACTIVE\"}", member).getStatusCode());
        } finally {
            programsStartActive();
        }
    }

    @Test
    @DisplayName("Given Founder is RETIRED and a participant enrolled before retirement, when they qualify and are approved, then the Founder entitlement is granted")
    void retiredFounderStillCompletesAnExistingParticipant() {
        String token = login("retire-complete");
        ok(post("/api/v1/founder/enrollment", "{}", token));
        ok(post("/api/v1/founder/workouts", workout(), token));
        ok(post("/api/v1/founder/workouts", workout(), token));
        ok(post(
                "/api/v1/founder/tester-report",
                "{\"submissionId\":\"" + UUID.randomUUID() + "\",\"appVersion\":\"1.0.0\",\"feedback\":\"Ready for review\"}",
                token
        ));
        String userId = userId(token);
        String applicationId = applicationId(userId);
        String admin = adminLogin();
        try {
            ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin));
            assertEquals(HttpStatus.CONFLICT, post("/api/v1/founder/enrollment", "{}", login("retire-complete-new")).getStatusCode());
            ResponseEntity<String> approval = post(
                    "/api/v1/admin/founder/applications/" + applicationId + "/approval",
                    "{}",
                    admin
            );
            assertEquals(HttpStatus.OK, approval.getStatusCode(), approval.getBody());
            assertEquals(1, count(
                    "select count(*) from entitlement_grant where user_id = ?::uuid",
                    userId
            ));
        } finally {
            programsStartActive();
        }
    }

    @Test
    @DisplayName("Given Early Adopter is ACTIVE and the cohort is full, when another account registers, then it gets no Early Adopter status and the program stays ACTIVE")
    void fullEarlyAdopterCohortStaysActive() {
        int assigned = count("select assigned_count from early_adopter_cohort where id = 1");
        int capacity = count("select capacity from early_adopter_cohort where id = 1");
        try {
            jdbc.update("update early_adopter_cohort set assigned_count = capacity where id = 1");
            entityManager.clear();
            String subject = "full-cohort-" + UUID.randomUUID();
            login(subject);
            assertEquals(0, earlyAdopterGrants(subject));
            assertEquals("ACTIVE", lifecycle("early_adopter_cohort"));
            Map<String, Object> view = parse(ok(get("/api/v1/admin/programs", adminLogin())));
            assertEquals("ACTIVE", map(view, "earlyAdopter").get("lifecycle"));
            assertFalse(Boolean.parseBoolean(String.valueOf(map(view, "earlyAdopter").get("acceptingNewParticipants"))));
        } finally {
            jdbc.update(
                    "update early_adopter_cohort set assigned_count = ?, capacity = ? where id = 1",
                    assigned,
                    capacity
            );
            entityManager.clear();
        }
    }

    @Test
    @DisplayName("Given Early Adopter is INACTIVE, when a new account registers, then no Early Adopter status is assigned")
    void inactiveEarlyAdopterAssignsNothing() {
        jdbc.update("update early_adopter_cohort set lifecycle = 'INACTIVE' where id = 1");
        entityManager.clear();
        int assigned = count("select assigned_count from early_adopter_cohort where id = 1");
        String subject = "inactive-adopter-" + UUID.randomUUID();
        login(subject);
        assertEquals(0, earlyAdopterGrants(subject));
        assertEquals(assigned, count("select assigned_count from early_adopter_cohort where id = 1"));
    }

    @Test
    @DisplayName("Given Early Adopter is RETIRED, when a new account registers or startup backfill runs, then no new Early Adopter status is granted")
    void retiredEarlyAdopterGrantsNothing() {
        jdbc.update(
                "update early_adopter_cohort set lifecycle = 'RETIRED', backfill_completed = false where id = 1"
        );
        entityManager.clear();
        int assigned = count("select assigned_count from early_adopter_cohort where id = 1");
        UUID waiting = UUID.randomUUID();
        jdbc.update(
                "insert into app_user (id, created_at, updated_at) values (?, now(), now())",
                waiting
        );
        try {
            earlyAdopters.backfillExistingUsers();
            entityManager.clear();
            assertEquals(0, count(
                    "select count(*) from account_status_grant where user_id = ? and status = 'EARLY_ADOPTER'",
                    waiting
            ));
            assertEquals(Boolean.FALSE, jdbc.queryForObject(
                    "select backfill_completed from early_adopter_cohort where id = 1",
                    Boolean.class
            ));
            String subject = "retired-adopter-" + UUID.randomUUID();
            login(subject);
            assertEquals(0, earlyAdopterGrants(subject));
            assertEquals(assigned, count("select assigned_count from early_adopter_cohort where id = 1"));
            assertEquals("RETIRED", lifecycle("early_adopter_cohort"));
        } finally {
            jdbc.update("update early_adopter_cohort set backfill_completed = true where id = 1");
            programsStartActive();
        }
    }

    @Test
    @DisplayName("Given Early Adopter is RETIRED, when admin tries to resume it, then the backend rejects the transition")
    void retiredEarlyAdopterCannotResume() {
        String admin = adminLogin();
        try {
            ok(put("/api/v1/admin/programs/early-adopter", "{\"lifecycle\":\"RETIRED\"}", admin));
            int grants = count("select count(*) from account_status_grant where status = 'EARLY_ADOPTER'");
            int assigned = count("select assigned_count from early_adopter_cohort where id = 1");
            ResponseEntity<String> resume = put("/api/v1/admin/programs/early-adopter", "{\"lifecycle\":\"ACTIVE\"}", admin);
            assertEquals(HttpStatus.CONFLICT, resume.getStatusCode());
            assertEquals("PROGRAM_RETIRED", parse(resume).get("errorCode"));
            assertEquals("RETIRED", lifecycle("early_adopter_cohort"));
            assertEquals(grants, count("select count(*) from account_status_grant where status = 'EARLY_ADOPTER'"));
            assertEquals(assigned, count("select assigned_count from early_adopter_cohort where id = 1"));
        } finally {
            programsStartActive();
        }
    }

    @Test
    @DisplayName("Given Founder is RETIRED and another one-time promotion still needs protection, when marker cleanup runs, then only the Founder flag is removed")
    void founderRetirementCleansOnlyTheFounderMarker() {
        String subject = "marker-founder-" + UUID.randomUUID();
        UUID userId = UUID.fromString(userId(login(subject)));
        markers.write(subject, Instant.parse("2026-06-01T00:00:00Z"), new AccountDeletionStore.Usage(true, true, true, true));
        int entitlements = count("select count(*) from entitlement_grant");
        int purchases = count("select count(*) from play_subscription");
        int enrolled = count("select enrolled_count from founder_program_capacity where id = 1");
        try {
            ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", adminLogin()));
            cleanup.cleanup();
            cleanup.cleanup();
            assertFalse(policy.founderUsed(userId));
            assertTrue(policy.proDiscoveryUsed(userId));
            assertTrue(policy.welcomeBackUsed(userId));
            assertTrue(policy.skipEarlyAdopter(subject));
            assertEquals(entitlements, count("select count(*) from entitlement_grant"));
            assertEquals(purchases, count("select count(*) from play_subscription"));
            assertEquals(enrolled, count("select enrolled_count from founder_program_capacity where id = 1"));
        } finally {
            programsStartActive();
        }
    }

    @Test
    @DisplayName("Given Early Adopter is RETIRED and Pro Discovery or Welcome Back protection remains, when marker cleanup runs, then those protections stay effective")
    void earlyAdopterRetirementLeavesOtherPromotions() throws Exception {
        String subject = "marker-early-" + UUID.randomUUID();
        UUID userId = UUID.fromString(userId(login(subject)));
        markers.write(subject, Instant.parse("2026-06-01T00:00:00Z"), new AccountDeletionStore.Usage(true, true, true, true));
        String obsoleteSubject = "marker-obsolete-" + UUID.randomUUID();
        markers.write(obsoleteSubject, Instant.parse("2026-06-01T00:00:00Z"), new AccountDeletionStore.Usage(true, false, false, true));
        String obsoleteHash = markers.find(obsoleteSubject).orElseThrow().subjectHash();
        try {
            String admin = adminLogin();
            ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin));
            ok(put("/api/v1/admin/programs/early-adopter", "{\"lifecycle\":\"RETIRED\"}", admin));
            List<Integer> passes = race(() -> cleanup.cleanup(), () -> cleanup.cleanup());
            assertEquals(2, passes.size());
            cleanup.cleanup();
            assertTrue(policy.proDiscoveryUsed(userId));
            assertTrue(policy.welcomeBackUsed(userId));
            assertFalse(policy.founderUsed(userId));
            assertFalse(policy.skipEarlyAdopter(subject));
            assertFalse(policy.skipEarlyAdopter(obsoleteSubject));
            assertEquals(0, count(
                    "select count(*) from account_deletion_marker where subject_hash = ?",
                    obsoleteHash
            ));
            assertTrue(policy.proDiscoveryUsed(userId));
        } finally {
            programsStartActive();
        }
    }

    @Test
    @DisplayName("Given a legacy marker records Founder use only, when Early Adopter eligibility is checked, then that marker does not block Early Adopter")
    void earlyAdopterUsesItsOwnLegacyFlag() {
        String founderOnly = "legacy-founder-" + UUID.randomUUID();
        jdbc.update(
                """
                insert into account_deletion_marker (
                    subject_hash, deleted_at, founder_used, pro_discovery_used, welcome_back_used, early_adopter_used
                ) values (?, now(), true, false, false, false)
                """,
                GoogleSubjectHash.of(founderOnly)
        );
        String adopterOnly = "legacy-adopter-" + UUID.randomUUID();
        jdbc.update(
                """
                insert into account_deletion_marker (
                    subject_hash, deleted_at, founder_used, pro_discovery_used, welcome_back_used, early_adopter_used
                ) values (?, now(), false, false, false, true)
                """,
                GoogleSubjectHash.of(adopterOnly)
        );
        assertFalse(policy.skipEarlyAdopter(founderOnly));
        assertTrue(policy.skipEarlyAdopter(adopterOnly));
    }

    @Test
    @DisplayName("Given Founder is ACTIVE, when retirement and a new enrollment race, then the program ends retired and a later enrollment is rejected")
    void concurrentRetirementAndEnrollmentStayConsistent() throws Exception {
        String existing = login("race-existing");
        ok(post("/api/v1/founder/enrollment", "{}", existing));
        String admin = adminLogin();
        String contender = login("race-contender");
        try {
            race(
                    () -> put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin).getStatusCode().value(),
                    () -> post("/api/v1/founder/enrollment", "{}", contender).getStatusCode().value()
            );
            assertEquals("RETIRED", lifecycle("founder_program_capacity"));
            assertEquals("ACTIVE_FREE", parse(ok(post("/api/v1/founder/enrollment", "{}", existing))).get("status"));
            ResponseEntity<String> later = post("/api/v1/founder/enrollment", "{}", login("race-later"));
            assertEquals(HttpStatus.CONFLICT, later.getStatusCode());
            assertEquals("ENROLLMENT_CLOSED", parse(later).get("errorCode"));
            List<Integer> retirements = race(
                    () -> put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin).getStatusCode().value(),
                    () -> put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin).getStatusCode().value()
            );
            assertEquals(List.of(200, 200), retirements.stream().sorted().toList());
            assertEquals("RETIRED", lifecycle("founder_program_capacity"));
        } finally {
            programsStartActive();
        }
    }

    private int earlyAdopterGrants(String subject) {
        return count(
                """
                select count(*) from account_status_grant grant_row
                join external_identity identity on identity.user_id = grant_row.user_id
                where identity.provider_subject = ? and grant_row.status = 'EARLY_ADOPTER'
                """,
                subject
        );
    }

    private String lifecycle(String table) {
        return jdbc.queryForObject("select lifecycle from " + table + " where id = 1", String.class);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String login(String subject) {
        identities.accept(subject, subject, subject + "@example.com", true);
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + subject + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private String adminLogin() {
        identities.accept("admin-token", "admin-subject", "admin@example.com", true);
        ResponseEntity<String> response = post("/api/v1/admin/session", "{\"idToken\":\"admin-token\"}", null);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        String token = cookie(response, "STRICT_ADMIN_SESSION");
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

    private static String workout() {
        return "{\"workoutId\":\"" + UUID.randomUUID()
                + "\",\"completedAt\":\"2026-06-01T00:00:00Z\",\"localDate\":\"2026-06-01\"}";
    }

    private ResponseEntity<String> ok(ResponseEntity<String> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return response;
    }

    private ResponseEntity<String> get(String path, String accessToken) {
        RestClient.RequestHeadersSpec<?> request = client().get().uri(path);
        apply(request, path, accessToken, false);
        return exchange(request);
    }

    private ResponseEntity<String> post(String path, String json, String accessToken) {
        RestClient.RequestBodySpec request = client().post().uri(path).contentType(MediaType.APPLICATION_JSON);
        apply(request, path, accessToken, true);
        return exchange(request.body(json));
    }

    private ResponseEntity<String> put(String path, String json, String accessToken) {
        RestClient.RequestBodySpec request = client().put().uri(path).contentType(MediaType.APPLICATION_JSON);
        apply(request, path, accessToken, true);
        return exchange(request.body(json));
    }

    private void apply(RestClient.RequestHeadersSpec<?> request, String path, String accessToken, boolean mutating) {
        if (path.startsWith("/api/v1/admin")) {
            String csrf = csrf();
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

    private String csrf() {
        if (csrfToken != null) {
            return csrfToken;
        }
        ResponseEntity<String> response = exchange(client().get().uri("/api/v1/admin/csrf"));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        csrfToken = cookie(response, "XSRF-TOKEN");
        assertNotNull(csrfToken);
        return csrfToken;
    }

    private static String cookie(ResponseEntity<?> response, String name) {
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
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    private List<Integer> race(IntCall first, IntCall second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (IntCall call : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return call.run();
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

    @FunctionalInterface
    private interface IntCall {
        int run() throws Exception;
    }
}
