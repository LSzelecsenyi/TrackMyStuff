package eu.strictworkout.admin;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "strict.admin.cookie-secure=false"
)
@Import({ScriptedIdentityVerifierConfig.class, AdminAllowlistEnforcementIT.AllowlistConfig.class})
@Testcontainers
class AdminAllowlistEnforcementIT {

    private static final String SUBJECT = "admin-subject";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private ScriptedIdentityVerifier identities;

    @Autowired
    private MutableAdminAllowlist allowlist;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void allowTheAdmin() {
        allowlist.replaceWith(SUBJECT);
        jdbc.update("delete from admin_session");
    }

    @Test
    void anAllowlistedSessionKeepsWorkingUntilTheSubjectIsRemoved() {
        String session = login(SUBJECT);
        assertEquals(HttpStatus.OK, adminGet(session).getStatusCode());
        assertEquals(1, activeAdminSessions());
    }

    @Test
    void removingTheSubjectRejectsSessionIntrospection() {
        String session = login(SUBJECT);
        allowlist.replaceWith();

        ResponseEntity<String> rejected = exchange(client().get()
                .uri("/api/v1/admin/session")
                .header(HttpHeaders.COOKIE, AdminSessionCookies.NAME + "=" + session));
        assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
        assertEquals("UNAUTHENTICATED", parse(rejected).get("errorCode"));
        assertFalse(rejected.getBody().contains(session));
        assertFalse(rejected.getBody().contains(SUBJECT));
        assertEquals(0, activeAdminSessions());
    }

    @Test
    void removingTheSubjectRevokesTheSessionWithAGenericResponse() {
        String session = login(SUBJECT);
        allowlist.replaceWith();

        ResponseEntity<String> rejected = adminGet(session);
        assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
        assertEquals("UNAUTHENTICATED", parse(rejected).get("errorCode"));
        assertFalse(rejected.getBody().contains(SUBJECT));
        assertFalse(rejected.getBody().toLowerCase().contains("allow"));
        assertFalse(rejected.getBody().contains("@"));
        assertEquals(0, activeAdminSessions());
        assertEquals(1, revokedAdminSessions());
    }

    @Test
    void reAddingTheSubjectDoesNotRestoreTheRevokedCookie() {
        String oldSession = login(SUBJECT);
        allowlist.replaceWith();
        assertEquals(HttpStatus.UNAUTHORIZED, adminGet(oldSession).getStatusCode());

        allowlist.replaceWith(SUBJECT);
        ResponseEntity<String> stillRejected = adminGet(oldSession);
        assertEquals(HttpStatus.UNAUTHORIZED, stillRejected.getStatusCode());
        assertEquals(0, activeAdminSessions());

        String fresh = login(SUBJECT);
        assertNotEquals(oldSession, fresh);
        assertEquals(HttpStatus.OK, adminGet(fresh).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, adminGet(oldSession).getStatusCode());
        assertEquals(1, activeAdminSessions());
    }

    @Test
    void removalDoesNotAffectTheOrdinaryUserSessionOrCrossTheSecurityDomains() {
        String admin = login(SUBJECT);
        String member = memberToken("member-subject");
        allowlist.replaceWith();

        ResponseEntity<String> me = client().get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + member)
                .exchange(this::capture);
        assertEquals(HttpStatus.OK, me.getStatusCode(), me.getBody());

        ResponseEntity<String> bearerOnAdmin = client().get()
                .uri("/api/v1/admin/founder/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + member)
                .exchange(this::capture);
        assertEquals(HttpStatus.UNAUTHORIZED, bearerOnAdmin.getStatusCode());

        ResponseEntity<String> cookieOnMe = client().get()
                .uri("/api/v1/me")
                .header(HttpHeaders.COOKIE, AdminSessionCookies.NAME + "=" + admin)
                .exchange(this::capture);
        assertEquals(HttpStatus.UNAUTHORIZED, cookieOnMe.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, adminGet(admin).getStatusCode());
    }

    @Test
    void concurrentRequestsAfterRemovalRevokeTheSessionOnce() throws Exception {
        String session = login(SUBJECT);
        allowlist.replaceWith();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return adminGet(session).getStatusCode().value();
                }));
            }
            ready.await();
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(401, result.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from admin_session", Integer.class));
        assertEquals(1, revokedAdminSessions());
        assertEquals(0, activeAdminSessions());
    }

    private String login(String subject) {
        identities.accept(subject, subject, subject + "@example.com", true);
        String csrf = csrfValue();
        ResponseEntity<String> response = exchange(client().post()
                .uri("/api/v1/admin/session")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .body("{\"idToken\":\"" + subject + "\"}"));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        String token = cookieValue(setCookie(response, AdminSessionCookies.NAME));
        assertNotNull(token);
        assertTrue(response.getBody() == null || !response.getBody().contains(token));
        return token;
    }

    private String memberToken(String subject) {
        identities.accept(subject, subject, subject + "@example.com", true);
        ResponseEntity<String> response = exchange(client().post()
                .uri("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"idToken\":\"" + subject + "\"}"));
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return String.valueOf(parse(response).get("accessToken"));
    }

    private ResponseEntity<String> adminGet(String session) {
        return exchange(client().get()
                .uri("/api/v1/admin/founder/applications")
                .header(HttpHeaders.COOKIE, AdminSessionCookies.NAME + "=" + session));
    }

    private String csrfValue() {
        ResponseEntity<String> response = exchange(client().get().uri("/api/v1/admin/csrf"));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        return cookieValue(setCookie(response, "XSRF-TOKEN"));
    }

    private int activeAdminSessions() {
        return jdbc.queryForObject(
                "select count(*) from admin_session where revoked_at is null",
                Integer.class
        );
    }

    private int revokedAdminSessions() {
        return jdbc.queryForObject(
                "select count(*) from admin_session where revoked_at is not null",
                Integer.class
        );
    }

    private ResponseEntity<String> exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange(this::capture);
    }

    private ResponseEntity<String> capture(
            org.springframework.http.HttpRequest request,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) throws java.io.IOException {
        HttpHeaders headers = new HttpHeaders();
        response.getHeaders().forEach(headers::addAll);
        return ResponseEntity.status(response.getStatusCode())
                .headers(headers)
                .body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private RestClient client() {
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    private static String setCookie(ResponseEntity<?> response, String name) {
        List<String> headers = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(headers);
        String prefix = name + "=";
        for (String header : headers) {
            if (header.startsWith(prefix)) {
                return header;
            }
        }
        return null;
    }

    private static String cookieValue(String header) {
        assertNotNull(header);
        int start = header.indexOf('=');
        int end = header.indexOf(';');
        return header.substring(start + 1, end);
    }

    private static Map<String, Object> parse(ResponseEntity<String> response) {
        return new BasicJsonParser().parseMap(response.getBody());
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
