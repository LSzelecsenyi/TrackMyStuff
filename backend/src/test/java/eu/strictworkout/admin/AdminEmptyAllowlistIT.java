package eu.strictworkout.admin;

import eu.strictworkout.auth.OpaqueTokenGenerator;
import eu.strictworkout.auth.TokenDigests;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "strict.admin.google-subjects=",
                "strict.admin.cookie-secure=false"
        }
)
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class AdminEmptyAllowlistIT {

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
    void anEmptyAllowlistRejectsNewLoginAndRevokesAnExistingSession() {
        String token = OpaqueTokenGenerator.generate();
        UUID adminId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        jdbc.update(
                "insert into admin_user (id, google_subject, email, email_verified, created_at, updated_at) "
                        + "values (?::uuid, 'stored-subject', 'stored@example.com', true, now(), now())",
                adminId.toString()
        );
        jdbc.update(
                "insert into admin_session (id, admin_user_id, token_hash, created_at, expires_at) "
                        + "values (?::uuid, ?::uuid, ?, now(), now() + interval '2 hours')",
                sessionId.toString(),
                adminId.toString(),
                TokenDigests.sha256(token)
        );

        ResponseEntity<String> rejected = exchange(client().get()
                .uri("/api/v1/admin/founder/applications")
                .header(HttpHeaders.COOKIE, AdminSessionCookies.NAME + "=" + token));
        assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
        assertEquals("UNAUTHENTICATED", parse(rejected).get("errorCode"));
        assertFalse(rejected.getBody().contains("stored-subject"));
        assertFalse(rejected.getBody().contains("stored@example.com"));
        assertEquals(0, activeAdminSessions());

        identities.accept("admin-token", "stored-subject", "stored@example.com", true);
        String csrf = csrfValue();
        ResponseEntity<String> login = exchange(client().post()
                .uri("/api/v1/admin/session")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .body("{\"idToken\":\"admin-token\"}"));
        assertEquals(HttpStatus.UNAUTHORIZED, login.getStatusCode());
        assertEquals("ADMIN_NOT_ALLOWED", parse(login).get("errorCode"));
        assertFalse(login.getBody().contains("stored-subject"));
        assertEquals(0, activeAdminSessions());

        identities.accept("member-token", "member-subject", "member@example.com", true);
        ResponseEntity<String> memberLogin = exchange(client().post()
                .uri("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"idToken\":\"member-token\"}"));
        assertEquals(HttpStatus.OK, memberLogin.getStatusCode(), memberLogin.getBody());
        String member = String.valueOf(parse(memberLogin).get("accessToken"));
        ResponseEntity<String> me = exchange(client().get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + member));
        assertEquals(HttpStatus.OK, me.getStatusCode(), me.getBody());
    }

    private String csrfValue() {
        ResponseEntity<String> response = exchange(client().get().uri("/api/v1/admin/csrf"));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        String header = response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow();
        int end = header.indexOf(';');
        return header.substring("XSRF-TOKEN=".length(), end);
    }

    private int activeAdminSessions() {
        return jdbc.queryForObject(
                "select count(*) from admin_session where revoked_at is null",
                Integer.class
        );
    }

    private ResponseEntity<String> exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((httpRequest, response) -> {
            HttpHeaders headers = new HttpHeaders();
            response.getHeaders().forEach(headers::addAll);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(headers)
                    .body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
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
}
