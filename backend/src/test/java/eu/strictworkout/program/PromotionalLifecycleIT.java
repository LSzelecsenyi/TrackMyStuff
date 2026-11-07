package eu.strictworkout.program;

import eu.strictworkout.account.AccountDeletionStore;
import eu.strictworkout.account.DeletedAccountPolicy;
import eu.strictworkout.account.DeletionMarkerCleanup;
import eu.strictworkout.account.DeletionMarkerService;
import eu.strictworkout.account.EarlyAdopterAssignment;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class PromotionalLifecycleIT {

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
    private EarlyAdopterAssignment earlyAdopters;

    @Autowired
    private DeletionMarkerService markers;

    @Autowired
    private DeletionMarkerCleanup cleanup;

    @Autowired
    private DeletedAccountPolicy policy;

    private volatile String csrfToken;
    private final Set<String> adminSessions = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void resetPrograms() {
        jdbc.update("update founder_program_capacity set lifecycle = 'ACTIVE' where id = 1");
        jdbc.update("update early_adopter_cohort set lifecycle = 'ACTIVE', backfill_completed = true where id = 1");
        jdbc.update("update promotional_program set lifecycle = 'INACTIVE'");
        entityManager.clear();
    }

    @Test
    @DisplayName("Given Early Adopter is INACTIVE when a user registers, when the program later becomes ACTIVE and backfill runs, then the user does not receive Early Adopter")
    void registrationDuringPauseIsNotBackfilled() {
        jdbc.update("update early_adopter_cohort set lifecycle = 'INACTIVE' where id = 1");
        entityManager.clear();
        String subject = "paused-registration-" + UUID.randomUUID();
        login(subject);
        assertEquals(0, earlyAdopterGrants(subject));
        assertTrue(ineligible(subject));
        int assigned = count("select assigned_count from early_adopter_cohort where id = 1");
        jdbc.update("update early_adopter_cohort set lifecycle = 'ACTIVE', backfill_completed = false where id = 1");
        entityManager.clear();
        earlyAdopters.backfillExistingUsers();
        assertEquals(0, earlyAdopterGrants(subject));
        assertEquals(assigned, count("select assigned_count from early_adopter_cohort where id = 1"));
    }

    @Test
    @DisplayName("Given a historically eligible user exists before the Early Adopter lifecycle, when legitimate backfill runs, then the user can receive the status if capacity remains")
    void historicalUserCanBeBackfilled() {
        int assigned = count("select assigned_count from early_adopter_cohort where id = 1");
        int capacity = count("select capacity from early_adopter_cohort where id = 1");
        UUID historical = UUID.randomUUID();
        try {
            jdbc.update(
                    "insert into app_user (id, created_at, updated_at) values (?, '1970-01-01T00:00:00Z', '1970-01-01T00:00:00Z')",
                    historical
            );
            jdbc.update(
                    "update early_adopter_cohort set capacity = assigned_count + 1, backfill_completed = false, lifecycle = 'ACTIVE' where id = 1"
            );
            entityManager.clear();
            earlyAdopters.backfillExistingUsers();
            assertEquals(1, count(
                    "select count(*) from account_status_grant where user_id = ? and status = 'EARLY_ADOPTER'",
                    historical
            ));
            assertEquals(assigned + 1, count("select assigned_count from early_adopter_cohort where id = 1"));
        } finally {
            jdbc.update(
                    "update early_adopter_cohort set assigned_count = ?, capacity = ?, backfill_completed = true where id = 1",
                    assigned,
                    capacity
            );
        }
    }

    @Test
    @DisplayName("Given Founder is RETIRED, when the admin activates Pro Discovery, then Pro Discovery becomes available independently and Founder remains RETIRED")
    void proDiscoveryActivatesIndependentlyOfFounder() {
        String admin = adminLogin();
        ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin));
        Map<String, Object> discovery = parse(ok(put("/api/v1/admin/programs/pro-discovery", "{\"lifecycle\":\"ACTIVE\"}", admin)));
        assertEquals("ACTIVE", discovery.get("lifecycle"));
        assertEquals("true", String.valueOf(discovery.get("acceptingNewGrants")));
        Map<String, Object> programs = parse(ok(get("/api/v1/admin/programs", admin)));
        assertEquals("RETIRED", map(programs, "founder").get("lifecycle"));
        assertEquals("INACTIVE", map(programs, "welcomeBack").get("lifecycle"));
        Map<String, Object> availability = parse(ok(get("/api/v1/promotions/availability", null)));
        assertEquals("false", String.valueOf(availability.get("founderProgramActive")));
        assertEquals("true", String.valueOf(availability.get("proDiscoveryEnabled")));
        assertEquals("false", String.valueOf(availability.get("welcomeBackEnabled")));
        assertEquals("false", String.valueOf(availability.get("promotionsEnabled")));
    }

    @Test
    @DisplayName("Given Pro Discovery is INACTIVE, when a new user registers, then no new Pro Discovery benefit is granted")
    void inactiveProDiscoveryGrantsNothing() {
        String subject = "discovery-inactive-" + UUID.randomUUID();
        String token = login(subject);
        ResponseEntity<String> activation = post("/api/v1/promotions/pro-discovery/activate", "{}", token);
        assertEquals(HttpStatus.FORBIDDEN, activation.getStatusCode());
        assertEquals("CLOSED", parse(activation).get("status"));
        assertEquals(0, count(
                "select count(*) from promotional_trial where user_id = ?::uuid",
                userId(token)
        ));
    }

    @Test
    @DisplayName("Given Welcome Back is ACTIVE, when an eligible returning user qualifies, then Welcome Back may be granted independently of Founder and Pro Discovery")
    void welcomeBackIsIndependent() {
        String admin = adminLogin();
        ok(put("/api/v1/admin/programs/founder", "{\"lifecycle\":\"RETIRED\"}", admin));
        ok(put("/api/v1/admin/programs/welcome-back", "{\"lifecycle\":\"ACTIVE\"}", admin));
        String token = login("welcome-" + UUID.randomUUID());
        ResponseEntity<String> activation = post(
                "/api/v1/promotions/welcome-back/activate",
                "{\"qualifyingWorkoutId\":\"return-1\"}",
                token
        );
        assertEquals(HttpStatus.OK, activation.getStatusCode(), activation.getBody());
        assertEquals("ACTIVE", parse(activation).get("status"));
        assertEquals("RETIRED", lifecycle("founder_program_capacity"));
        assertEquals("INACTIVE", promotionLifecycle("PRO_DISCOVERY"));
        assertEquals(1, count(
                "select count(*) from welcome_back_grant where user_id = ?::uuid",
                userId(token)
        ));
    }

    @Test
    @DisplayName("Given Pro Discovery is RETIRED, when the admin attempts to reactivate it, then the backend rejects the transition")
    void retiredProDiscoveryCannotResume() {
        String admin = adminLogin();
        ok(put("/api/v1/admin/programs/pro-discovery", "{\"lifecycle\":\"RETIRED\"}", admin));
        ResponseEntity<String> resume = put("/api/v1/admin/programs/pro-discovery", "{\"lifecycle\":\"ACTIVE\"}", admin);
        assertEquals(HttpStatus.CONFLICT, resume.getStatusCode());
        assertEquals("PROGRAM_RETIRED", parse(resume).get("errorCode"));
        ResponseEntity<String> again = put("/api/v1/admin/programs/pro-discovery", "{\"lifecycle\":\"RETIRED\"}", admin);
        assertEquals(HttpStatus.OK, again.getStatusCode(), again.getBody());
        assertEquals("RETIRED", promotionLifecycle("PRO_DISCOVERY"));
    }

    @Test
    @DisplayName("Given Welcome Back is RETIRED, when the admin attempts to reactivate it, then the backend rejects the transition")
    void retiredWelcomeBackCannotResume() {
        String admin = adminLogin();
        ok(put("/api/v1/admin/programs/welcome-back", "{\"lifecycle\":\"RETIRED\"}", admin));
        ResponseEntity<String> resume = put("/api/v1/admin/programs/welcome-back", "{\"lifecycle\":\"INACTIVE\"}", admin);
        assertEquals(HttpStatus.CONFLICT, resume.getStatusCode());
        assertEquals("PROGRAM_RETIRED", parse(resume).get("errorCode"));
        assertEquals("RETIRED", promotionLifecycle("WELCOME_BACK"));
    }

    @Test
    @DisplayName("Given one promotion is RETIRED and another is ACTIVE, when marker cleanup runs, then only the retired promotion's obsolete protection is cleared")
    void cleanupClearsOnlyTheRetiredPromotion() {
        String subject = "marker-one-" + UUID.randomUUID();
        UUID userId = UUID.fromString(userId(login(subject)));
        markers.write(subject, Instant.parse("2026-06-01T00:00:00Z"), new AccountDeletionStore.Usage(true, true, true, true));
        jdbc.update("update promotional_program set lifecycle = 'RETIRED' where program = 'PRO_DISCOVERY'");
        cleanup.cleanup();
        cleanup.cleanup();
        assertFalse(policy.proDiscoveryUsed(userId));
        assertTrue(policy.founderUsed(userId));
        assertTrue(policy.welcomeBackUsed(userId));
        assertTrue(policy.skipEarlyAdopter(subject));
    }

    @Test
    @DisplayName("Given all four promotions are RETIRED, when marker cleanup runs, then obsolete marker rows are deleted without affecting grants or subscriptions")
    void cleanupDeletesOnlyEmptyMarkerRows() throws Exception {
        String subject = "marker-all-" + UUID.randomUUID();
        markers.write(subject, Instant.parse("2026-06-01T00:00:00Z"), new AccountDeletionStore.Usage(true, true, true, true));
        String hash = markers.find(subject).orElseThrow().subjectHash();
        jdbc.update("update founder_program_capacity set lifecycle = 'RETIRED' where id = 1");
        jdbc.update("update early_adopter_cohort set lifecycle = 'RETIRED' where id = 1");
        jdbc.update("update promotional_program set lifecycle = 'RETIRED'");
        int grants = count("select count(*) from entitlement_grant");
        int purchases = count("select count(*) from play_subscription");
        int enrolled = count("select enrolled_count from founder_program_capacity where id = 1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> cleanup.cleanup());
            Future<Integer> second = pool.submit(() -> cleanup.cleanup());
            first.get();
            second.get();
        } finally {
            pool.shutdown();
        }
        cleanup.cleanup();
        assertEquals(0, count("select count(*) from account_deletion_marker where subject_hash = ?", hash));
        assertEquals(grants, count("select count(*) from entitlement_grant"));
        assertEquals(purchases, count("select count(*) from play_subscription"));
        assertEquals(enrolled, count("select enrolled_count from founder_program_capacity where id = 1"));
    }

    private boolean ineligible(String subject) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                select early_adopter_ineligible from app_user user_row
                join external_identity identity on identity.user_id = user_row.id
                where identity.provider_subject = ?
                """,
                Boolean.class,
                subject
        ));
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

    private String promotionLifecycle(String program) {
        return jdbc.queryForObject(
                "select lifecycle from promotional_program where program = ?",
                String.class,
                program
        );
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
}
