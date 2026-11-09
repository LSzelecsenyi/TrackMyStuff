package eu.strictworkout.entitlement;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class PlayReviewEntitlementIT {

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
    void verifiedReviewerReceivesProWithoutFounderOrSubscriptionRows() {
        String reviewer = login("play-reviewer", "U0652739323@ID.GLE", true);
        Map<String, Object> entitlements = entitlements(reviewer, "/api/v1/entitlements");
        assertEquals("PRO", entitlements.get("access"));
        assertFlag(true, entitlements.get("temporaryFounderPro"));
        assertFlag(false, entitlements.get("founderLifetime"));
        assertFlag(false, entitlements.get("founderRecognized"));
        assertEquals("null", String.valueOf(entitlements.get("founderGrantedAt")));
        assertEquals("null", String.valueOf(entitlements.get("founderProExpiresAt")));
        UUID reviewerId = userId(reviewer);
        assertEquals(0, rows("founder_application", reviewerId));
        assertEquals(0, rows("entitlement_grant", reviewerId));
        assertEquals(0, subscriptions(reviewerId));
    }

    @Test
    void otherVerifiedUsersStayFree() {
        String other = login("other-user", "other@example.com", true);
        Map<String, Object> entitlements = entitlements(
                other,
                "/api/v1/entitlements?email=" + PlayReviewAccess.EMAIL
        );
        assertEquals("FREE", entitlements.get("access"));
        assertFlag(false, entitlements.get("temporaryFounderPro"));
        assertFlag(false, entitlements.get("founderLifetime"));
        assertEquals(0, rows("entitlement_grant", userId(other)));
    }

    @Test
    void unverifiedReviewerEmailDoesNotGrantPro() {
        String token = login("unverified-reviewer", PlayReviewAccess.EMAIL, false);
        UUID userId = userId(token);
        assertEquals("FREE", entitlements(token, "/api/v1/entitlements").get("access"));

        jdbc.update(
                "update external_identity set email = ?, email_verified = false where user_id = ?",
                PlayReviewAccess.EMAIL,
                userId
        );
        Map<String, Object> stillFree = entitlements(token, "/api/v1/entitlements");
        assertEquals("FREE", stillFree.get("access"));
        assertFlag(false, stillFree.get("temporaryFounderPro"));

        jdbc.update(
                "update external_identity set email_verified = true where user_id = ?",
                userId
        );
        Map<String, Object> verified = entitlements(token, "/api/v1/entitlements");
        assertEquals("PRO", verified.get("access"));
        assertFlag(true, verified.get("temporaryFounderPro"));
        assertEquals(0, rows("founder_application", userId));
        assertEquals(0, rows("entitlement_grant", userId));
    }

    private String login(String subject, String email, boolean emailVerified) {
        identities.accept(subject, subject, email, emailVerified);
        ResponseEntity<String> response = post("/api/v1/auth/google", "{\"idToken\":\"" + subject + "\"}");
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private Map<String, Object> entitlements(String accessToken, String path) {
        ResponseEntity<String> response = get(path, accessToken);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return parse(response);
    }

    private UUID userId(String accessToken) {
        ResponseEntity<String> response = get("/api/v1/me", accessToken);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return UUID.fromString(String.valueOf(parse(response).get("id")));
    }

    private int rows(String table, UUID userId) {
        return count("select count(*) from " + table + " where user_id = ?", userId);
    }

    private int subscriptions(UUID userId) {
        return count("select count(*) from play_subscription where linked_user_id = ?", userId);
    }

    private int count(String sql, UUID userId) {
        Integer count = jdbc.queryForObject(sql, Integer.class, userId);
        return count == null ? 0 : count;
    }

    private ResponseEntity<String> get(String path, String accessToken) {
        return exchange(client().get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResponseEntity<String> post(String path, String json) {
        return exchange(client().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json));
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

    private static void assertFlag(boolean expected, Object actual) {
        assertEquals(Boolean.toString(expected), String.valueOf(actual));
    }
}
