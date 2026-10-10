package eu.strictworkout.account;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.boot.json.BasicJsonParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "strict.auth.session-lifetime=PT2H",
                "strict.google.client-id=test-client.apps.googleusercontent.com"
        }
)
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class AccountDeletionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private ScriptedIdentityVerifier identities;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void unauthenticatedDeletionIsRejected() {
        identities.accept("token-open", "subject-open", "open@example.com", true);
        String user = userId(login("token-open"));
        ResponseEntity<String> response = send(HttpMethod.DELETE, "/api/v1/account", body("token-open", true), null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(1, count("select count(*) from app_user where id = ?", UUID.fromString(user)));
    }

    @Test
    void emailAloneDoesNotDelete() {
        identities.accept("token-mail", "subject-mail", "mail@example.com", true);
        String user = userId(login("token-mail"));
        ResponseEntity<String> response = send(
                HttpMethod.POST,
                "/api/v1/account/deletion",
                "{\"email\":\"mail@example.com\",\"confirmed\":true}",
                null
        );
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(1, count("select count(*) from app_user where id = ?", UUID.fromString(user)));
    }

    @Test
    void staleAuthenticationAndMissingConfirmationDoNotDelete() {
        identities.accept("token-stale-login", "subject-stale", "stale@example.com", true);
        Map<String, Object> session = login("token-stale-login");
        identities.accept(
                "token-stale-delete",
                "subject-stale",
                "stale@example.com",
                true,
                Instant.now().minusSeconds(11 * 60)
        );
        ResponseEntity<String> stale = send(
                HttpMethod.DELETE,
                "/api/v1/account",
                body("token-stale-delete", true),
                token(session)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, stale.getStatusCode());
        assertTrue(stale.getBody().contains("REAUTHENTICATION_REQUIRED"));

        identities.accept("token-unconfirmed", "subject-stale", "stale@example.com", true);
        ResponseEntity<String> unconfirmed = send(
                HttpMethod.DELETE,
                "/api/v1/account",
                body("token-unconfirmed", false),
                token(session)
        );
        assertEquals(HttpStatus.BAD_REQUEST, unconfirmed.getStatusCode());
        assertTrue(unconfirmed.getBody().contains("CONFIRMATION_REQUIRED"));
        assertEquals(1, count("select count(*) from app_user where id = ?", UUID.fromString(userId(session))));
    }

    @Test
    void oneAccountCannotDeleteAnother() {
        identities.accept("token-owner", "subject-owner", "owner@example.com", true);
        identities.accept("token-other", "subject-other", "other@example.com", true);
        Map<String, Object> owner = login("token-owner");
        Map<String, Object> other = login("token-other");
        ResponseEntity<String> response = send(
                HttpMethod.DELETE,
                "/api/v1/account",
                body("token-other", true),
                token(owner)
        );
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().contains("ACCOUNT_MISMATCH"));
        assertEquals(HttpStatus.OK, me(token(owner)).getStatusCode());
        assertEquals(HttpStatus.OK, me(token(other)).getStatusCode());
    }

    @Test
    void deletionRemovesTheAccountAndALaterSignInDoesNotRestoreIt() throws Exception {
        identities.accept("token-delete", "subject-delete", "delete@example.com", true);
        identities.accept("token-bystander", "subject-bystander", "bystander@example.com", true);
        Map<String, Object> session = login("token-delete");
        Map<String, Object> bystander = login("token-bystander");
        String user = userId(session);
        seedPersonalData(user);
        int founders = count("select enrolled_count from founder_program_capacity where id = 1");
        int adopters = count("select assigned_count from early_adopter_cohort where id = 1");

        ResponseEntity<String> deleted = send(
                HttpMethod.DELETE,
                "/api/v1/account",
                body("token-delete", true),
                token(session)
        );
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        assertPersonalDataGone(user);
        assertEquals(founders, count("select enrolled_count from founder_program_capacity where id = 1"));
        assertEquals(adopters, count("select assigned_count from early_adopter_cohort where id = 1"));
        assertEquals(1, count("select count(*) from admin_user"));
        assertEquals(1, count("select count(*) from play_rtdn_message where message_id = 'kept-rtdn'"));
        assertEquals(HttpStatus.UNAUTHORIZED, me(token(session)).getStatusCode());
        assertEquals(HttpStatus.OK, me(token(bystander)).getStatusCode());

        ResponseEntity<String> again = send(HttpMethod.POST, "/api/v1/account/deletion", body("token-delete", true), null);
        assertEquals(HttpStatus.NO_CONTENT, again.getStatusCode());
        assertEquals(1, count("select count(*) from account_deletion_marker"));

        identities.accept("token-delete-2", "subject-delete", "delete@example.com", true);
        Map<String, Object> returned = login("token-delete-2");
        assertNotEquals(user, userId(returned));
        assertEquals(0, count(
                "select count(*) from account_status_grant where user_id = ? and status = 'EARLY_ADOPTER'",
                UUID.fromString(userId(returned))
        ));
        assertEquals(adopters, count("select assigned_count from early_adopter_cohort where id = 1"));

        ResponseEntity<String> founder = send(HttpMethod.POST, "/api/v1/founder/enrollment", null, token(returned));
        assertEquals(HttpStatus.CONFLICT, founder.getStatusCode());
        assertTrue(founder.getBody().contains("FOUNDER_ALREADY_USED"));
        ResponseEntity<String> discovery = send(
                HttpMethod.POST,
                "/api/v1/promotions/pro-discovery/activate",
                null,
                token(returned)
        );
        assertEquals(HttpStatus.CONFLICT, discovery.getStatusCode());
        assertTrue(discovery.getBody().contains("ALREADY_USED"));
        ResponseEntity<String> welcome = send(
                HttpMethod.POST,
                "/api/v1/promotions/welcome-back/activate",
                "{\"qualifyingWorkoutId\":\"workout-new\"}",
                token(returned)
        );
        assertEquals(HttpStatus.CONFLICT, welcome.getStatusCode());
        assertTrue(welcome.getBody().contains("CONSUMED"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> first = pool.submit(() -> repeatDelete(ready, go));
        Future<Integer> second = pool.submit(() -> repeatDelete(ready, go));
        ready.await();
        go.countDown();
        assertEquals(204, first.get());
        assertEquals(204, second.get());
        pool.shutdown();
        assertEquals(0, count("select count(*) from app_user where id = ?", UUID.fromString(userId(returned))));
    }

    @Test
    void thePublicPageExplainsDeletionAndRejectsAnUnknownGoogleToken() {
        ResponseEntity<String> page = send(HttpMethod.GET, "/account/delete", null, null);
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(page.getBody().contains("Delete account"));
        assertTrue(page.getBody().contains("test-client.apps.googleusercontent.com"));
        assertFalse(page.getBody().contains("/api/v1/admin"));
        ResponseEntity<String> rejected = send(
                HttpMethod.POST,
                "/api/v1/account/deletion",
                body("not-a-real-token", true),
                null
        );
        assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
        assertTrue(rejected.getBody().contains("INVALID_GOOGLE_TOKEN"));
    }

    @Test
    void privacyPagesArePublicInBothLanguages() {
        ResponseEntity<String> english = send(HttpMethod.GET, "/privacy", null, null);
        ResponseEntity<String> englishAlias = send(HttpMethod.GET, "/privacy/en", null, null);
        ResponseEntity<String> hungarian = send(HttpMethod.GET, "/privacy/hu", null, null);
        assertEquals(HttpStatus.OK, english.getStatusCode());
        assertEquals(HttpStatus.OK, englishAlias.getStatusCode());
        assertEquals(HttpStatus.OK, hungarian.getStatusCode());
        MediaType contentType = client().get().uri("/privacy").exchange((request, response) ->
                response.getHeaders().getContentType()
        );
        assertTrue(contentType != null && contentType.includes(MediaType.TEXT_HTML));
        assertPrivacyPage(english.getBody(), "Strict privacy policy", "/privacy/hu");
        assertPrivacyPage(englishAlias.getBody(), "Strict privacy policy", "/privacy/hu");
        assertPrivacyPage(hungarian.getBody(), "Strict adatvédelmi tájékoztató", "/privacy/en");
    }

    private static void assertPrivacyPage(String body, String title, String otherLanguage) {
        assertTrue(body.contains("<!DOCTYPE html>"));
        assertTrue(body.contains("<title>" + title));
        assertTrue(body.contains(otherLanguage));
        assertTrue(body.contains("href=\"/account/delete\""));
        assertFalse(body.contains("/api/v1/admin"));
        assertFalse(body.contains("purchase-token-sample"));
        assertFalse(body.contains("user@example.com"));
    }

    private int repeatDelete(CountDownLatch ready, CountDownLatch go) throws InterruptedException {
        ready.countDown();
        go.await();
        return send(HttpMethod.POST, "/api/v1/account/deletion", body("token-delete-2", true), null)
                .getStatusCode()
                .value();
    }

    private void seedPersonalData(String user) {
        UUID userId = UUID.fromString(user);
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update(
                """
                insert into founder_application (
                    id, user_id, status, enrolled_at, deadline_at, feedback_text, created_at, updated_at
                ) values (?, ?, 'ACTIVE_FREE', ?, ?, 'private feedback', ?, ?)
                """,
                applicationId, userId, at, at.plusSeconds(86_400), at, at
        );
        jdbc.update(
                """
                insert into founder_workout_event (
                    id, founder_application_id, client_workout_id, completed_at, workout_local_date, created_at, display_name
                ) values (?, ?, ?, ?, ?, ?, 'Secret Name')
                """,
                UUID.randomUUID(), applicationId, UUID.randomUUID(), at, LocalDate.now(ZoneOffset.UTC), at
        );
        jdbc.update(
                """
                insert into founder_review_snapshot (
                    id, founder_application_id, submitted_at, app_version, platform,
                    qualifying_workout_count, distinct_workout_day_count, enrolled_at, deadline_at,
                    feedback_text, created_at, client_submission_id
                ) values (?, ?, ?, '0.1.0', 'android', 1, 1, ?, ?, 'private feedback', ?, ?)
                """,
                UUID.randomUUID(), applicationId, at, at, at.plusSeconds(86_400), at, UUID.randomUUID()
        );
        jdbc.update(
                """
                insert into admin_user (id, google_subject, email, email_verified, created_at, updated_at)
                values (?, ?, 'admin@example.com', true, ?, ?)
                """,
                adminId, "admin-" + adminId, at, at
        );
        jdbc.update(
                """
                insert into founder_review_decision (
                    id, founder_application_id, admin_user_id, decision, reason, decided_at
                ) values (?, ?, ?, 'APPROVED', null, ?)
                """,
                UUID.randomUUID(), applicationId, adminId, at
        );
        jdbc.update(
                """
                insert into entitlement_grant (
                    id, user_id, source, founder_application_id, granted_at, created_at, expires_at
                ) values (?, ?, 'FOUNDER_LIFETIME', ?, ?, ?, null)
                """,
                UUID.randomUUID(), userId, applicationId, at, at
        );
        jdbc.update(
                """
                insert into promotional_trial (user_id, promotion_type, activated_at, expires_at)
                values (?, 'PRO_DISCOVERY', ?, ?)
                """,
                userId, at, at.plusSeconds(86_400)
        );
        jdbc.update(
                """
                insert into welcome_back_grant (
                    user_id, qualifying_workout_id, activated_at, expires_at, cooldown_until
                ) values (?, 'workout-1', ?, ?, ?)
                """,
                userId, at, at.plusSeconds(86_400), at.plusSeconds(86_400L * 180)
        );
        jdbc.update(
                """
                insert into play_subscription (
                    token_hash, purchase_token, package_name, product_id, subscription_state,
                    expiry_time, auto_renewing, entitled, linked_user_id, latest_order_id, updated_at, claim_blocked
                ) values (?, 'purchase-token-test', 'com.strictworkout.app', 'strict_pro', 'ACTIVE', ?, true, true, ?, 'GPA.1', ?, false)
                """,
                "ab".repeat(32), at.plusSeconds(86_400), userId, at
        );
        jdbc.update(
                """
                insert into play_rtdn_message (message_id, event_time, notification_type, received_at)
                values ('kept-rtdn', ?, 4, ?)
                """,
                at, at
        );
    }

    private void assertPersonalDataGone(String user) {
        UUID userId = UUID.fromString(user);
        assertEquals(0, count("select count(*) from app_user where id = ?", userId));
        assertEquals(0, count("select count(*) from external_identity where user_id = ?", userId));
        assertEquals(0, count("select count(*) from auth_session where user_id = ?", userId));
        assertEquals(0, count("select count(*) from founder_application where user_id = ?", userId));
        assertEquals(0, count("select count(*) from entitlement_grant where user_id = ?", userId));
        assertEquals(0, count("select count(*) from promotional_trial where user_id = ?", userId));
        assertEquals(0, count("select count(*) from welcome_back_grant where user_id = ?", userId));
        assertEquals(0, count("select count(*) from account_status_grant where user_id = ?", userId));
        assertEquals(0, count("select count(*) from founder_workout_event where display_name = 'Secret Name'"));
        assertEquals(0, count("select count(*) from founder_review_snapshot where feedback_text = 'private feedback'"));
        Map<String, Object> subscription = jdbc.queryForMap(
                "select linked_user_id, claim_blocked, purchase_token from play_subscription where token_hash = ?",
                "ab".repeat(32)
        );
        assertEquals(null, subscription.get("linked_user_id"));
        assertEquals(Boolean.TRUE, subscription.get("claim_blocked"));
        assertEquals("purchase-token-test", subscription.get("purchase_token"));
        assertEquals(1, count("select count(*) from account_deletion_marker where founder_used and pro_discovery_used and welcome_back_used"));
    }

    private Map<String, Object> login(String idToken) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/auth/google", "{\"idToken\":\"" + idToken + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return new BasicJsonParser().parseMap(response.getBody());
    }

    private ResponseEntity<String> me(String accessToken) {
        return send(HttpMethod.GET, "/api/v1/me", null, accessToken);
    }

    @SuppressWarnings("unchecked")
    private static String userId(Map<String, Object> session) {
        return String.valueOf(((Map<String, Object>) session.get("user")).get("id"));
    }

    private static String token(Map<String, Object> session) {
        return String.valueOf(session.get("accessToken"));
    }

    private static String body(String idToken, boolean confirmed) {
        return "{\"idToken\":\"" + idToken + "\",\"confirmed\":" + confirmed + "}";
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String json, String accessToken) {
        RestClient.RequestBodySpec request = client().method(method).uri(path);
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON);
        }
        if (accessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        RestClient.RequestHeadersSpec<?> spec = json == null ? request : request.body(json);
        return spec.exchange((httpRequest, response) -> {
            String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.status(response.getStatusCode()).body(responseBody);
        });
    }

    private RestClient client() {
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient))
                .build();
    }
}
