package eu.strictworkout.promotion;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "strict.auth.session-lifetime=P365D")
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class ProDiscoveryEligibilityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private ScriptedIdentityVerifier identities;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void proDiscoveryIsOpen() {
        jdbc.update("update promotional_program set lifecycle = 'ACTIVE' where program = 'PRO_DISCOVERY'");
    }

    @Test
    @DisplayName("Given an account younger than two months, when eligibility is checked, then no offer is available")
    void aYoungAccountHasNoOffer() {
        String token = login("young-" + UUID.randomUUID());
        Map<String, Object> body = status(token);
        assertEquals("NOT_ELIGIBLE", body.get("status"));
        assertEquals("NONE", body.get("offer"));
        assertEquals(0, offers(token));
    }

    @Test
    @DisplayName("Given an account exactly two calendar months old with no Pro, when eligibility is checked, then a trial offer is available")
    void twoMonthsOpensTheTrialOffer() {
        String token = login("mature-" + UUID.randomUUID());
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(2).minusSeconds(1).toInstant());
        Map<String, Object> body = status(token);
        assertEquals("ELIGIBLE", body.get("status"));
        assertEquals("TRIAL", body.get("offer"));
        assertEquals(0, offers(token));
    }

    @Test
    @DisplayName("Given an eligible account with zero workouts, when eligibility is checked, then the offer does not depend on workout history")
    void zeroWorkoutsStillQualify() {
        String token = login("empty-" + UUID.randomUUID());
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(2).minusSeconds(1).toInstant());
        assertEquals("TRIAL", status(token).get("offer"));
        assertEquals(0, count(
                "select count(*) from information_schema.tables where table_name = 'pro_discovery_activity_day'"
        ));
    }

    @Test
    @DisplayName("Given an eligible account that never used Pro Discovery, when the offer is acknowledged, then the trial variant was the one available")
    void theFirstOfferIsTheFreeTrial() {
        String token = login("first-" + UUID.randomUUID());
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        assertEquals("TRIAL", status(token).get("offer"));
        Map<String, Object> shown = presented(token);
        assertTrue(Boolean.parseBoolean(String.valueOf(shown.get("recorded"))));
        assertEquals("NONE", shown.get("offer"));
        assertEquals(1, offers(token));
    }

    @Test
    @DisplayName("Given the free trial was consumed and two months have passed since it was shown, when eligibility is checked, then the subscription variant is shown and a second trial is refused")
    void aUsedTrialBecomesASubscriptionOffer() {
        String token = login("used-" + UUID.randomUUID());
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(4).toInstant());
        assertEquals(HttpStatus.OK, post("/api/v1/promotions/pro-discovery/activate", "{}", token).getStatusCode());
        jdbc.update(
                "update promotional_trial set expires_at = now() - interval '1 hour' where user_id = ?::uuid",
                userId(token)
        );
        jdbc.update(
                """
                insert into pro_discovery_offer (user_id, presented_at, updated_at)
                values (?::uuid, now() - interval '2 months' - interval '1 minute', now())
                """,
                userId(token)
        );
        Map<String, Object> body = status(token);
        assertEquals("ALREADY_USED", body.get("status"));
        assertEquals("SUBSCRIPTION", body.get("offer"));
        ResponseEntity<String> again = post("/api/v1/promotions/pro-discovery/activate", "{}", token);
        assertEquals(HttpStatus.CONFLICT, again.getStatusCode(), again.getBody());
        assertEquals("ALREADY_USED", parse(again).get("status"));
        assertEquals(1, trials(token));
    }

    @Test
    @DisplayName("Given an offer was shown, when another acknowledgement arrives before two months, then the schedule does not move")
    void aSecondAcknowledgementDoesNotMoveTheSchedule() throws Exception {
        String token = login("once-" + UUID.randomUUID());
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        assertTrue(Boolean.parseBoolean(String.valueOf(presented(token).get("recorded"))));
        String first = presentedAt(token);
        Thread.sleep(1100);
        Map<String, Object> second = presented(token);
        assertFalse(Boolean.parseBoolean(String.valueOf(second.get("recorded"))));
        assertEquals("NONE", second.get("offer"));
        assertEquals(first, presentedAt(token));
    }

    @Test
    @DisplayName("Given two calendar months have passed since the previous offer, when eligibility is checked, then the next variant is available")
    void theNextCycleOpensAfterTwoMonths() {
        String token = login("cycle-" + UUID.randomUUID());
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(6).toInstant());
        jdbc.update(
                """
                insert into pro_discovery_offer (user_id, presented_at, updated_at)
                values (?::uuid, now() - interval '2 months' - interval '1 minute', now())
                """,
                userId(token)
        );
        assertEquals("TRIAL", status(token).get("offer"));
    }

    @Test
    @DisplayName("Given active Pro from Play, Founder, Welcome Back, or a running trial, when an offer would otherwise be due, then no offer is displayed")
    void activeProSuppressesTheOffer() {
        String play = login("play-" + UUID.randomUUID());
        registered(play, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        jdbc.update(
                """
                insert into play_subscription (
                    token_hash, purchase_token, package_name, product_id, subscription_state,
                    expiry_time, auto_renewing, entitled, linked_user_id, updated_at
                ) values (?, 'token', 'com.strictworkout.app', 'strict_pro', 'ACTIVE',
                          now() + interval '10 days', true, true, ?::uuid, now())
                """,
                "a".repeat(64),
                userId(play)
        );
        assertEquals("NONE", status(play).get("offer"));

        String founder = login("founder-" + UUID.randomUUID());
        registered(founder, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        jdbc.update(
                """
                insert into founder_application (
                    id, user_id, status, enrolled_at, deadline_at, created_at, updated_at
                ) values (?, ?::uuid, 'PENDING_APPROVAL', now(), now() + interval '30 days', now(), now())
                """,
                UUID.randomUUID(),
                userId(founder)
        );
        assertEquals("INCOMPATIBLE", status(founder).get("status"));
        assertEquals("NONE", status(founder).get("offer"));

        String welcome = login("welcome-" + UUID.randomUUID());
        registered(welcome, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        jdbc.update(
                """
                insert into welcome_back_grant (
                    user_id, qualifying_workout_id, activated_at, expires_at, cooldown_until
                ) values (?::uuid, 'workout-1', now(), now() + interval '3 days', now() + interval '180 days')
                """,
                userId(welcome)
        );
        assertEquals("NONE", status(welcome).get("offer"));
        assertEquals(1, count("select count(*) from welcome_back_grant where user_id = ?::uuid", userId(welcome)));

        String running = login("running-" + UUID.randomUUID());
        registered(running, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        assertEquals(HttpStatus.OK, post("/api/v1/promotions/pro-discovery/activate", "{}", running).getStatusCode());
        assertEquals("ACTIVE_TRIAL", status(running).get("status"));
        assertEquals("NONE", status(running).get("offer"));
    }

    @Test
    @DisplayName("Given the program is INACTIVE or RETIRED, when eligibility is checked, then no offer is displayed")
    void aClosedProgramShowsNoOffer() {
        String inactive = login("inactive-" + UUID.randomUUID());
        registered(inactive, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        jdbc.update("update promotional_program set lifecycle = 'INACTIVE' where program = 'PRO_DISCOVERY'");
        assertEquals("PROGRAM_CLOSED", status(inactive).get("status"));
        assertEquals("NONE", status(inactive).get("offer"));
        jdbc.update("update promotional_program set lifecycle = 'RETIRED' where program = 'PRO_DISCOVERY'");
        String retired = login("retired-" + UUID.randomUUID());
        registered(retired, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        assertEquals("NONE", status(retired).get("offer"));
        assertEquals(HttpStatus.FORBIDDEN, post("/api/v1/promotions/pro-discovery/activate", "{}", retired).getStatusCode());
    }

    @Test
    @DisplayName("Given the account signs in again after the offer was recorded, when status is read, then the reminder schedule is unchanged")
    void reinstallKeepsTheServerSchedule() {
        String subject = "reinstall-" + UUID.randomUUID();
        String token = login(subject);
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        presented(token);
        String stamp = presentedAt(token);
        String again = login(subject + "-phone", subject);
        Map<String, Object> body = status(again);
        assertEquals("NONE", body.get("offer"));
        assertEquals(stamp, presentedAt(again));
    }

    @Test
    @DisplayName("Given two accounts, when one is eligible, then the other account is unaffected")
    void accountsStayIsolated() {
        String older = login("older-" + UUID.randomUUID());
        String younger = login("younger-" + UUID.randomUUID());
        registered(older, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        assertEquals("TRIAL", status(older).get("offer"));
        assertEquals("NONE", status(younger).get("offer"));
        presented(older);
        assertEquals(0, offers(younger));
    }

    @Test
    @DisplayName("Given two devices acknowledge the same due offer, when both finish, then one presentation timestamp remains")
    void twoDevicesDoNotCreateTwoCycles() throws Exception {
        String subject = "devices-" + UUID.randomUUID();
        String first = login(subject);
        String second = login(subject + "-other", subject);
        registered(first, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> left = pool.submit(() ->
                    post("/api/v1/promotions/pro-discovery/presented", "{}", first));
            Future<ResponseEntity<String>> right = pool.submit(() ->
                    post("/api/v1/promotions/pro-discovery/presented", "{}", second));
            assertEquals(HttpStatus.OK, left.get().getStatusCode());
            assertEquals(HttpStatus.OK, right.get().getStatusCode());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, offers(first));
        assertNotEquals("", presentedAt(first));
        String stamp = presentedAt(second);
        Thread.sleep(1100);
        presented(first);
        assertEquals(stamp, presentedAt(second));
    }

    @Test
    @DisplayName("Given two activation attempts, when they race, then one trial is stored")
    void concurrentActivationDoesNotExtend() throws Exception {
        String token = login("race-" + UUID.randomUUID());
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> left = pool.submit(() ->
                    post("/api/v1/promotions/pro-discovery/activate", "{}", token));
            Future<ResponseEntity<String>> right = pool.submit(() ->
                    post("/api/v1/promotions/pro-discovery/activate", "{}", token));
            ResponseEntity<String> leftBody = left.get();
            ResponseEntity<String> rightBody = right.get();
            assertTrue(leftBody.getStatusCode().is2xxSuccessful() || rightBody.getStatusCode().is2xxSuccessful());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, trials(token));
    }

    @Test
    @DisplayName("Given a used trial and account deletion, when the same Google account returns, then another free trial is refused")
    void deletionDoesNotAllowAnotherTrial() {
        String subject = "reuse-" + UUID.randomUUID();
        String token = login(subject);
        registered(token, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        assertEquals(HttpStatus.OK, post("/api/v1/promotions/pro-discovery/activate", "{}", token).getStatusCode());
        identities.accept("delete-" + subject, subject, subject + "@example.com", true);
        ResponseEntity<String> deleted = send(
                HttpMethod.DELETE,
                "/api/v1/account",
                "{\"idToken\":\"delete-" + subject + "\",\"confirmed\":true}",
                token
        );
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode(), deleted.getBody());
        String returned = login(subject + "-again", subject);
        registered(returned, Instant.now().atZone(ZoneOffset.UTC).minusMonths(3).toInstant());
        ResponseEntity<String> activation = post("/api/v1/promotions/pro-discovery/activate", "{}", returned);
        assertEquals(HttpStatus.CONFLICT, activation.getStatusCode(), activation.getBody());
        assertEquals("ALREADY_USED", parse(activation).get("status"));
        assertEquals("SUBSCRIPTION", status(returned).get("offer"));
        assertEquals(0, trials(returned));
    }

    @Test
    @DisplayName("Given an installed client still calls the V2 activity endpoints, when the server answers, then nothing is stored")
    void obsoleteActivityEndpointsStoreNothing() {
        String token = login("legacy-" + UUID.randomUUID());
        ResponseEntity<String> permission = post(
                "/api/v1/promotions/pro-discovery/activity/permission",
                "{\"enabled\":true,\"scopeVersion\":1}",
                token
        );
        assertEquals(HttpStatus.OK, permission.getStatusCode(), permission.getBody());
        assertFalse(Boolean.parseBoolean(String.valueOf(parse(permission).get("enabled"))));
        ResponseEntity<String> upload = send(
                HttpMethod.PUT,
                "/api/v1/promotions/pro-discovery/activity",
                "{\"installationId\":\"" + UUID.randomUUID() + "\",\"baseRevision\":0,\"generation\":1,\"dates\":[\"2020-01-01\"]}",
                token
        );
        assertEquals(HttpStatus.FORBIDDEN, upload.getStatusCode(), upload.getBody());
        assertEquals("ACTIVITY_NOT_PERMITTED", parse(upload).get("errorCode"));
        assertEquals(0, count(
                "select count(*) from information_schema.tables where table_name in ('pro_discovery_device', 'pro_discovery_activity_day')"
        ));
    }

    @Test
    @DisplayName("Given a legacy migration of a still-running trial, when it is submitted, then it does not mint Pro")
    void migrateCannotMintAFreshGrant() {
        String token = login("migrate-" + UUID.randomUUID());
        Instant now = Instant.now();
        String body = "{\"activatedAt\":\"" + now.minusSeconds(60) + "\",\"expiresAt\":\"" + now.plusSeconds(86_400) + "\"}";
        ResponseEntity<String> fresh = post("/api/v1/promotions/pro-discovery/migrate", body, token);
        assertEquals(HttpStatus.CONFLICT, fresh.getStatusCode(), fresh.getBody());
        assertEquals("INVALID", parse(fresh).get("status"));
        assertEquals(0, trials(token));
    }

    private Map<String, Object> presented(String token) {
        ResponseEntity<String> response = post("/api/v1/promotions/pro-discovery/presented", "{}", token);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return parse(response);
    }

    private void registered(String token, Instant createdAt) {
        jdbc.update(
                "update app_user set created_at = ?::timestamptz where id = ?::uuid",
                createdAt.toString(),
                userId(token)
        );
    }

    private String presentedAt(String token) {
        return jdbc.queryForObject(
                """
                select to_char(presented_at at time zone 'utc', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
                from pro_discovery_offer where user_id = ?::uuid
                """,
                String.class,
                userId(token)
        );
    }

    private Map<String, Object> status(String token) {
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/v1/promotions/pro-discovery", null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return parse(response);
    }

    private int trials(String token) {
        return count("select count(*) from promotional_trial where user_id = ?::uuid", userId(token));
    }

    private int offers(String token) {
        return count("select count(*) from pro_discovery_offer where user_id = ?::uuid", userId(token));
    }

    private String login(String subject) {
        return login(subject, subject);
    }

    private String login(String idToken, String subject) {
        identities.accept(idToken, subject, subject + "@example.com", true);
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + idToken + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private String userId(String token) {
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/v1/me", null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("id"));
    }

    private ResponseEntity<String> post(String path, String json, String token) {
        return send(HttpMethod.POST, path, json, token);
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String json, String token) {
        RestClient.RequestBodySpec request = client().method(method).uri(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RestClient.RequestHeadersSpec<?> spec = json == null ? request : request.body(json);
        return spec.exchange((httpRequest, response) -> {
            byte[] payload = response.getBody().readAllBytes();
            return ResponseEntity.status(response.getStatusCode()).body(new String(payload, StandardCharsets.UTF_8));
        });
    }

    private RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static Map<String, Object> parse(ResponseEntity<String> response) {
        return new BasicJsonParser().parseMap(response.getBody());
    }
}
