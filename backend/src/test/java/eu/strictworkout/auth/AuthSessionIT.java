package eu.strictworkout.auth;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "strict.auth.session-lifetime=PT2H"
)
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class AuthSessionIT {

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
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext applicationContext;

    @AfterAll
    static void restoreJvmZone() {
        TimeZone.setDefault(ORIGINAL_ZONE);
    }

    @Test
    void firstLoginCreatesOneUserAndRepeatedLoginKeepsThatUser() {
        identities.accept("token-repeat-a", "sub-repeat", "first@example.com", true);
        Map<String, Object> first = login("token-repeat-a");
        identities.accept("token-repeat-b", "sub-repeat", "first@example.com", true);
        Map<String, Object> second = login("token-repeat-b");

        assertEquals(userId(first), userId(second));
        assertNotEquals(first.get("accessToken"), second.get("accessToken"));
        assertEquals(1, countIdentities("sub-repeat"));
        assertEquals(2, countSessions(userId(first)));
        assertEquals("Bearer", first.get("tokenType"));
        assertEquals(Set.of("accessToken", "tokenType", "expiresAt", "user"), first.keySet());
        assertEquals(Set.of("id"), user(first).keySet());
    }

    @Test
    void changedEmailKeepsTheUserAndSameEmailWithAnotherSubjectDoesNot() {
        identities.accept("token-email-a", "sub-email", "old@example.com", true);
        String user = userId(login("token-email-a"));
        identities.accept("token-email-b", "sub-email", "new@example.com", true);
        assertEquals(user, userId(login("token-email-b")));
        assertEquals("new@example.com", jdbc.queryForObject(
                "select email from external_identity where provider = 'GOOGLE' and provider_subject = 'sub-email'",
                String.class
        ));

        identities.accept("token-other-sub", "sub-other", "new@example.com", true);
        String other = userId(login("token-other-sub"));
        assertNotEquals(user, other);
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from external_identity where email = 'new@example.com'",
                Integer.class
        ));
    }

    @Test
    void unverifiedEmailIsNotStoredUntilALaterVerifiedClaim() {
        identities.accept("token-unverified", "sub-unverified", "hidden@example.com", false);
        login("token-unverified");
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from external_identity where provider_subject = 'sub-unverified' and email is not null",
                Integer.class
        ));
        identities.accept("token-verified-later", "sub-unverified", "hidden@example.com", true);
        login("token-verified-later");
        assertEquals("hidden@example.com", jdbc.queryForObject(
                "select email from external_identity where provider_subject = 'sub-unverified'",
                String.class
        ));
    }

    @Test
    void clientSuppliedIdentityFieldsAreIgnored() {
        identities.accept("token-ignore-client", "sub-ignore", "real@example.com", true);
        String body = """
                {"idToken":"token-ignore-client","userId":"00000000-0000-0000-0000-000000000001","email":"attacker@example.com","emailVerified":true}
                """;
        Map<String, Object> session = parse(post("/api/v1/auth/google", body, null));
        assertNotEquals("00000000-0000-0000-0000-000000000001", userId(session));
        assertEquals("real@example.com", jdbc.queryForObject(
                "select email from external_identity where provider_subject = 'sub-ignore'",
                String.class
        ));
    }

    @Test
    void rejectedGoogleTokensCreateNothing() {
        int usersBefore = jdbc.queryForObject("select count(*) from app_user", Integer.class);
        for (String token : List.of("invalid-token", "wrong-audience", "expired-google-token")) {
            ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + token + "\"}", null);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            Map<String, Object> json = new BasicJsonParser().parseMap(response.getBody());
            assertEquals("INVALID_GOOGLE_TOKEN", json.get("errorCode"));
            assertFalse(response.getBody().contains(token));
        }
        assertEquals(usersBefore, jdbc.queryForObject("select count(*) from app_user", Integer.class));
    }

    @Test
    void malformedGoogleRequestDoesNotEchoTheToken() {
        String token = "SHOULD-NOT-LEAK-INTO-THE-ERROR-BODY";
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"\"}", null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().toLowerCase().contains("exception"));

        ResponseEntity<String> broken = post("/api/v1/auth/google", "{\"idToken\":\"" + token, null);
        assertEquals(HttpStatus.BAD_REQUEST, broken.getStatusCode());
        assertFalse(broken.getBody().contains(token));
    }

    @Test
    void databaseStoresOnlyTheTokenDigest() {
        identities.accept("token-digest", "sub-digest", "digest@example.com", true);
        String raw = (String) login("token-digest").get("accessToken");
        assertTrue(OpaqueTokenGenerator.matchesFormat(raw));
        byte[] stored = jdbc.queryForObject(
                "select token_hash from auth_session where token_hash = ?",
                byte[].class,
                TokenDigests.sha256(raw)
        );
        assertArrayEquals(TokenDigests.sha256(raw), stored);
        assertEquals(32, stored.length);
        assertFalse(new String(stored, StandardCharsets.UTF_8).contains(raw));
    }

    @Test
    void sessionExpiryIsAnInstantAndDoesNotFollowTheJvmZone() {
        assertEquals("Pacific/Kiritimati", TimeZone.getDefault().getID());
        identities.accept("token-zone", "sub-zone", null, false);
        String raw = (String) login("token-zone").get("accessToken");
        OffsetDateTime created = jdbc.queryForObject(
                "select created_at from auth_session where token_hash = ?",
                (rs, row) -> rs.getObject(1, OffsetDateTime.class),
                TokenDigests.sha256(raw)
        );
        OffsetDateTime expires = jdbc.queryForObject(
                "select expires_at from auth_session where token_hash = ?",
                (rs, row) -> rs.getObject(1, OffsetDateTime.class),
                TokenDigests.sha256(raw)
        );
        assertEquals(Duration.ofHours(2), Duration.between(created.toInstant(), expires.toInstant()));
        assertTrue(Math.abs(Duration.between(Instant.now().plus(Duration.ofHours(2)), expires.toInstant()).getSeconds()) < 15);
        String zone = jdbc.queryForObject("show time zone", String.class);
        assertTrue(zone.equals("UTC") || zone.equals("Etc/UTC") || zone.equals("GMT"));
    }

    @Test
    void meReturnsTheSameStrictUserAcrossSessions() {
        identities.accept("token-me-a", "sub-me", "me@example.com", true);
        identities.accept("token-me-b", "sub-me", "me@example.com", true);
        Map<String, Object> first = login("token-me-a");
        Map<String, Object> second = login("token-me-b");
        assertEquals(userId(first), me((String) first.get("accessToken")));
        assertEquals(userId(first), me((String) second.get("accessToken")));
    }

    @Test
    void logoutRevokesOnlyTheCurrentSession() {
        identities.accept("token-logout-a", "sub-logout", "logout@example.com", true);
        identities.accept("token-logout-b", "sub-logout", "logout@example.com", true);
        String first = (String) login("token-logout-a").get("accessToken");
        String secondToken = (String) login("token-logout-b").get("accessToken");
        String user = me(secondToken);

        ResponseEntity<String> deleted = exchange(client().delete().uri("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + first));
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(first));
        assertEquals(user, me(secondToken));
    }

    @Test
    void expiredRevokedAndUnknownTokensDoNotAuthenticate() {
        identities.accept("token-expiry", "sub-expiry", "expiry@example.com", true);
        String raw = (String) login("token-expiry").get("accessToken");
        jdbc.update(
                """
                update auth_session
                set created_at = now() - interval '2 minutes',
                    expires_at = now() - interval '1 minute'
                where token_hash = ?
                """,
                TokenDigests.sha256(raw)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(raw));

        identities.accept("token-revoked", "sub-revoked", "revoked@example.com", true);
        String revoked = (String) login("token-revoked").get("accessToken");
        jdbc.update(
                "update auth_session set revoked_at = ? where token_hash = ?",
                ps -> {
                    ps.setObject(1, OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
                    ps.setBytes(2, TokenDigests.sha256(revoked));
                }
        );
        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(revoked));
        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(OpaqueTokenGenerator.generate()));
        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(null));
    }

    @Test
    void malformedAuthorizationHeadersAreRejected() {
        identities.accept("token-malformed", "sub-malformed", "malformed@example.com", true);
        String raw = (String) login("token-malformed").get("accessToken");
        for (String header : List.of("Bearer", "Bearer ", "Basic " + raw, "bearer " + raw, "Bearer " + raw + " extra")) {
            ResponseEntity<String> response = exchange(client().get()
                    .uri("/api/v1/me")
                    .header(HttpHeaders.AUTHORIZATION, header));
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), header);
            assertFalse(response.getBody().contains(raw));
        }
        ResponseEntity<String> logout = exchange(client().delete().uri("/api/v1/auth/session"));
        assertEquals(HttpStatus.UNAUTHORIZED, logout.getStatusCode());
    }

    @Test
    void protectedUnknownPathDoesNotLeakAndAuthenticatedUnknownPathIsNotFound() {
        ResponseEntity<String> anonymous = exchange(client().get().uri("/api/v1/not-a-real-route"));
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
        assertFalse(anonymous.getBody().toLowerCase().contains("exception"));

        identities.accept("token-missing-route", "sub-missing-route", "route@example.com", true);
        String raw = (String) login("token-missing-route").get("accessToken");
        ResponseEntity<String> missing = exchange(client().get()
                .uri("/api/v1/not-a-real-route")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + raw));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertFalse(missing.getBody().contains(raw));
        Map<String, Object> json = new BasicJsonParser().parseMap(missing.getBody());
        assertEquals("NOT_FOUND", json.get("errorCode"));
    }

    @Test
    void providerSubjectRemainsUniqueUnderConcurrentFirstLogin() throws Exception {
        identities.accept("token-race", "sub-race", "race@example.com", true);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return post("/api/v1/auth/google", "{\"idToken\":\"token-race\"}", null);
            }));
        }
        ready.await();
        start.countDown();
        Set<String> users = new java.util.HashSet<>();
        for (Future<ResponseEntity<String>> result : results) {
            ResponseEntity<String> response = result.get();
            assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
            users.add(userId(parse(response)));
        }
        pool.shutdown();
        assertEquals(Set.of(users.iterator().next()), users);
        assertEquals(1, countIdentities("sub-race"));
        assertEquals(threads, countSessions(UUID.fromString(users.iterator().next())));

        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                """
                insert into external_identity
                    (id, user_id, provider, provider_subject, email, email_verified, created_at, updated_at)
                values (?::uuid, ?::uuid, 'GOOGLE', 'sub-race', null, false, now(), now())
                """,
                UUID.randomUUID().toString(),
                users.iterator().next()
        ));
    }

    @Test
    void healthStaysPublic() {
        ResponseEntity<String> response = exchange(client().get().uri("/api/v1/health"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"status\":\"UP\"}", response.getBody());
        assertTrue(response.getHeaders().getOrEmpty(HttpHeaders.WWW_AUTHENTICATE).isEmpty());
    }

    @Test
    void applicationDoesNotCreateAGeneratedUser() {
        assertEquals(0, applicationContext.getBeanNamesForType(UserDetailsService.class).length);
    }

    private Map<String, Object> login(String idToken) {
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + idToken + "\"}", null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return parse(response);
    }

    private String me(String accessToken) {
        ResponseEntity<String> response = exchange(client().get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("id"));
    }

    private HttpStatus meStatus(String accessToken) {
        RestClient.RequestHeadersSpec<?> request = client().get().uri("/api/v1/me");
        if (accessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return HttpStatus.valueOf(exchange(request).getStatusCode().value());
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

    private static String userId(Map<String, Object> session) {
        return String.valueOf(user(session).get("id"));
    }

    private int countIdentities(String subject) {
        return jdbc.queryForObject(
                "select count(*) from external_identity where provider = 'GOOGLE' and provider_subject = ?",
                Integer.class,
                subject
        );
    }

    private int countSessions(String userId) {
        return countSessions(UUID.fromString(userId));
    }

    private int countSessions(UUID userId) {
        return jdbc.queryForObject(
                "select count(*) from auth_session where user_id = ?",
                Integer.class,
                userId
        );
    }
}
