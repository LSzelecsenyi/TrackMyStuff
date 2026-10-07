package eu.strictworkout.account;

import eu.strictworkout.identity.AppUser;
import eu.strictworkout.identity.AppUserRepository;
import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class AccountStatusIT {

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
    private AppUserRepository users;

    @Autowired
    private EarlyAdopterAssignment earlyAdopters;

    @Autowired
    private PlatformTransactionManager transactions;

    @AfterAll
    static void close() {
    }

    @Test
    void memberApisCannotGrantAccountStatus() {
        identities.accept("status-ignore", "status-ignore", "status@example.com", true);
        String body = """
                {"idToken":"status-ignore","developer":true,"earlyAdopter":true,"specialAchievements":[{"key":"DEVELOPER"}]}
                """;
        Map<String, Object> session = parse(post("/api/v1/auth/google", body, null));
        String token = String.valueOf(session.get("accessToken"));
        String userId = userId(session);
        assertEquals(0, grants(userId, "DEVELOPER"));

        ResponseEntity<String> write = post(
                "/api/v1/entitlements",
                "{\"specialAchievements\":[{\"key\":\"DEVELOPER\"}],\"access\":\"PRO\"}",
                token
        );
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, write.getStatusCode());
        assertEquals(0, grants(userId, "DEVELOPER"));
    }

    @Test
    void earlyAdopterAndDeveloperAreReturnedWithoutGrantingPro() {
        openOneSlot();
        identities.accept("status-read", "status-read", "read@example.com", true);
        Map<String, Object> session = login("status-read");
        String userId = userId(session);
        String token = String.valueOf(session.get("accessToken"));
        OffsetDateTime registeredAt = jdbc.queryForObject(
                "select created_at from app_user where id = ?",
                OffsetDateTime.class,
                UUID.fromString(userId)
        );
        jdbc.update(
                "insert into account_status_grant (id, user_id, status, granted_at, created_at) values (?, ?, 'DEVELOPER', ?::timestamptz, now())",
                UUID.randomUUID(),
                UUID.fromString(userId),
                "2026-10-07T13:00:00Z"
        );

        Map<String, Object> entitlements = parse(ok(get("/api/v1/entitlements", token)));
        assertEquals("FREE", entitlements.get("access"));
        assertEquals("false", String.valueOf(entitlements.get("founderLifetime")));
        assertEquals("false", String.valueOf(entitlements.get("temporaryFounderPro")));
        assertEquals("null", String.valueOf(entitlements.get("founderGrantedAt")));
        List<Map<String, Object>> specials = specials(entitlements);
        Map<String, Object> early = specials.stream()
                .filter(item -> "EARLY_ADOPTER".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> developer = specials.stream()
                .filter(item -> "DEVELOPER".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        assertEquals(registeredAt.toInstant().toString(), early.get("grantedAt"));
        assertEquals("2026-10-07T13:00:00Z", developer.get("grantedAt"));
        assertTrue(specials.stream().noneMatch(item -> "FOUNDER".equals(item.get("key"))));
    }

    @Test
    void duplicateUserStatusIsRejectedAndFounderIsNotAnAccountStatus() {
        openOneSlot();
        identities.accept("status-dup", "status-dup", "dup@example.com", true);
        String userId = userId(login("status-dup"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into account_status_grant (id, user_id, status, granted_at, created_at) values (?, ?, 'EARLY_ADOPTER', now(), now())",
                UUID.randomUUID(),
                UUID.fromString(userId)
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into account_status_grant (id, user_id, status, granted_at, created_at) values (?, ?, 'FOUNDER', now(), now())",
                UUID.randomUUID(),
                UUID.fromString(userId)
        ));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from account_status_grant where status = 'FOUNDER'",
                Integer.class
        ));
    }

    @Test
    void repeatedLoginDoesNotConsumeAnotherSlot() {
        int before = assignedCount();
        if (before >= capacity()) {
            jdbc.update("update early_adopter_cohort set capacity = assigned_count + 1 where id = 1");
        }
        identities.accept("status-repeat", "status-repeat", "repeat@example.com", true);
        String first = userId(login("status-repeat"));
        int after = assignedCount();
        assertEquals(before + 1, after);
        identities.accept("status-repeat-2", "status-repeat", "repeat@example.com", true);
        assertEquals(first, userId(login("status-repeat-2")));
        assertEquals(after, assignedCount());
        assertEquals(1, grants(first, "EARLY_ADOPTER"));
    }

    @Test
    void backfillAssignsTheEarliestAccountsAndThenStaysClosed() {
        int existing = jdbc.queryForObject(
                "select count(*) from account_status_grant where status = 'EARLY_ADOPTER'",
                Integer.class
        );
        jdbc.update(
                "update early_adopter_cohort set assigned_count = ?, capacity = ?, backfill_completed = false where id = 1",
                existing,
                existing + 2
        );
        UUID earlier = UUID.fromString("00000000-0000-4000-8000-000000000010");
        UUID sameTimeLaterId = UUID.fromString("ffffffff-ffff-4000-8000-000000000011");
        UUID tooLate = UUID.fromString("00000000-0000-4000-8000-000000000012");
        insertUser(earlier, "1970-01-01T00:00:00Z");
        insertUser(sameTimeLaterId, "1970-01-01T00:00:00Z");
        insertUser(tooLate, "1970-01-02T00:00:00Z");
        entityManager.clear();

        earlyAdopters.backfillExistingUsers();

        assertEquals(1, grants(earlier.toString(), "EARLY_ADOPTER"));
        assertEquals(1, grants(sameTimeLaterId.toString(), "EARLY_ADOPTER"));
        assertEquals(0, grants(tooLate.toString(), "EARLY_ADOPTER"));
        assertEquals("1970-01-01T00:00:00Z", grantedAt(earlier));
        assertEquals(existing + 2, assignedCount());
        assertTrue(backfillCompleted());

        earlyAdopters.backfillExistingUsers();
        assertEquals(0, grants(tooLate.toString(), "EARLY_ADOPTER"));
        assertEquals(existing + 2, assignedCount());

        jdbc.update("delete from account_status_grant where user_id = ?", earlier);
        entityManager.clear();
        earlyAdopters.backfillExistingUsers();
        assertEquals(existing + 2, assignedCount());
        assertEquals(0, grants(tooLate.toString(), "EARLY_ADOPTER"));
    }

    @Test
    void user1000ReceivesEarlyAdopterAndUser1001DoesNot() {
        jdbc.update("update early_adopter_cohort set assigned_count = 999, capacity = 1000, backfill_completed = true where id = 1");
        entityManager.clear();
        UUID thousandth = insertAndAssign("1971-01-01T00:00:00Z");
        UUID next = insertAndAssign("1971-01-02T00:00:00Z");
        assertEquals(1, grants(thousandth.toString(), "EARLY_ADOPTER"));
        assertEquals(0, grants(next.toString(), "EARLY_ADOPTER"));
        assertEquals(1000, assignedCount());
    }

    @Test
    void concurrentRegistrationsAroundTheBoundaryAwardExactlyOneSlot() throws Exception {
        jdbc.update("update early_adopter_cohort set assigned_count = 999, capacity = 1000, backfill_completed = true where id = 1");
        entityManager.clear();
        TransactionTemplate template = newTransaction();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = pool.submit(() -> assignAfter(start, template, "1972-01-01T00:00:00Z"));
            Future<UUID> second = pool.submit(() -> assignAfter(start, template, "1972-01-01T00:00:01Z"));
            start.countDown();
            UUID left = first.get(30, TimeUnit.SECONDS);
            UUID right = second.get(30, TimeUnit.SECONDS);
            assertEquals(1, grants(left.toString(), "EARLY_ADOPTER") + grants(right.toString(), "EARLY_ADOPTER"));
            assertEquals(1000, assignedCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rollbackDoesNotBurnASlot() {
        jdbc.update("update early_adopter_cohort set assigned_count = 10, capacity = 1000, backfill_completed = true where id = 1");
        entityManager.clear();
        int before = assignedCount();
        UUID userId = UUID.randomUUID();
        TransactionTemplate template = newTransaction();
        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            AppUser user = users.saveAndFlush(new AppUser(userId, Instant.parse("1973-01-01T00:00:00Z"), Instant.parse("1973-01-01T00:00:00Z")));
            earlyAdopters.assignNewUser(user);
            throw new IllegalStateException("rollback");
        }));
        entityManager.clear();
        assertEquals(before, assignedCount());
        assertEquals(0, grants(userId.toString(), "EARLY_ADOPTER"));
        assertFalse(users.existsById(userId));
    }

    private UUID insertAndAssign(String createdAt) {
        UUID id = UUID.randomUUID();
        insertUser(id, createdAt);
        entityManager.clear();
        newTransaction().executeWithoutResult(status -> {
            AppUser user = users.findById(id).orElseThrow();
            earlyAdopters.assignNewUser(user);
        });
        entityManager.clear();
        return id;
    }

    private UUID assignAfter(CountDownLatch start, TransactionTemplate template, String createdAt) throws InterruptedException {
        start.await();
        UUID id = UUID.randomUUID();
        insertUser(id, createdAt);
        template.executeWithoutResult(status -> {
            AppUser user = users.findById(id).orElseThrow();
            earlyAdopters.assignNewUser(user);
        });
        return id;
    }

    private void insertUser(UUID id, String createdAt) {
        jdbc.update(
                "insert into app_user (id, created_at, updated_at) values (?, ?::timestamptz, ?::timestamptz)",
                id,
                createdAt,
                createdAt
        );
    }

    private void openOneSlot() {
        if (assignedCount() >= capacity()) {
            jdbc.update("update early_adopter_cohort set capacity = assigned_count + 1 where id = 1");
        }
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private int assignedCount() {
        return jdbc.queryForObject("select assigned_count from early_adopter_cohort where id = 1", Integer.class);
    }

    private int capacity() {
        return jdbc.queryForObject("select capacity from early_adopter_cohort where id = 1", Integer.class);
    }

    private boolean backfillCompleted() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select backfill_completed from early_adopter_cohort where id = 1",
                Boolean.class
        ));
    }

    private int grants(String userId, String status) {
        return jdbc.queryForObject(
                "select count(*) from account_status_grant where user_id = ? and status = ?",
                Integer.class,
                UUID.fromString(userId),
                status
        );
    }

    private String grantedAt(UUID userId) {
        return jdbc.queryForObject(
                "select granted_at from account_status_grant where user_id = ? and status = 'EARLY_ADOPTER'",
                OffsetDateTime.class,
                userId
        ).toInstant().toString();
    }

    private Map<String, Object> login(String token) {
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + token + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return parse(response);
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

    private ResponseEntity<String> exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((httpRequest, response) -> {
            byte[] bytes = response.getBody().readAllBytes();
            String body = new String(bytes, StandardCharsets.UTF_8);
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(body);
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
    private static Map<String, Object> user(Map<String, Object> session) {
        return (Map<String, Object>) session.get("user");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> specials(Map<String, Object> entitlements) {
        return (List<Map<String, Object>>) entitlements.get("specialAchievements");
    }

    private static String userId(Map<String, Object> session) {
        return String.valueOf(user(session).get("id"));
    }
}
