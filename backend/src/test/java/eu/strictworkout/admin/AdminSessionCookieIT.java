package eu.strictworkout.admin;

import eu.strictworkout.auth.OpaqueTokenGenerator;
import eu.strictworkout.founder.AdjustableClock;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "strict.admin.session-lifetime=PT2H",
                "strict.admin.google-subjects=admin-subject",
                "strict.admin.cookie-secure=false"
        }
)
@Import({ScriptedIdentityVerifierConfig.class, AdminSessionCookieIT.ClockConfig.class})
@Testcontainers
class AdminSessionCookieIT {

    private static final Instant START = Instant.parse("2026-06-01T00:00:00Z");

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

    @BeforeEach
    void resetSessions() {
        clock.set(START);
        jdbc.update("delete from admin_session");
    }

    @Test
    void allowlistedLoginSetsAnHttpOnlyHostOnlySessionCookieAndNoJsonToken() {
        clock.set(START);
        ResponseEntity<String> response = login("admin-token", "admin-subject", null);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        String header = setCookieHeader(response, AdminSessionCookies.NAME);
        String token = setCookieValue(header);
        assertSessionCookie(header, false);
        assertTrue(header.contains("Max-Age=7200"));
        assertTrue(response.getBody() == null || response.getBody().isBlank());
        assertBodyAndNonCookieHeadersDoNotContain(response, token);
        assertEquals(1, activeAdminSessions());

        String csrfHeader = setCookieHeader(response, "XSRF-TOKEN");
        if (csrfHeader == null) {
            csrfHeader = setCookieHeader(csrf(), "XSRF-TOKEN");
        }
        assertCsrfCookie(csrfHeader, false);
    }

    @Test
    void loginRequiresCsrfAndRejectsUnknownOrDisallowedGoogleIdentities() {
        clock.set(START);
        int before = activeAdminSessions();
        ResponseEntity<String> missingCsrf = post(
                "/api/v1/admin/session",
                "{\"idToken\":\"admin-token\"}",
                null,
                null,
                false
        );
        assertEquals(HttpStatus.FORBIDDEN, missingCsrf.getStatusCode());
        assertEquals("FORBIDDEN", parse(missingCsrf).get("errorCode"));
        assertNull(setCookieHeader(missingCsrf, AdminSessionCookies.NAME));
        assertEquals(before, activeAdminSessions());

        String csrf = csrfValue();
        ResponseEntity<String> invalidCsrf = post(
                "/api/v1/admin/session",
                "{\"idToken\":\"admin-token\"}",
                null,
                "not-the-csrf-token",
                true
        );
        assertEquals(HttpStatus.FORBIDDEN, invalidCsrf.getStatusCode());
        assertFalse(invalidCsrf.getBody().contains("not-the-csrf-token"));
        assertNull(setCookieHeader(invalidCsrf, AdminSessionCookies.NAME));

        identities.accept("admin-token", "admin-subject", "admin@example.com", true);
        ResponseEntity<String> invalidGoogle = post(
                "/api/v1/admin/session",
                "{\"idToken\":\"unknown-google-token\"}",
                csrf,
                csrf,
                true
        );
        assertEquals(HttpStatus.UNAUTHORIZED, invalidGoogle.getStatusCode());
        assertEquals("INVALID_GOOGLE_TOKEN", parse(invalidGoogle).get("errorCode"));
        assertNull(setCookieHeader(invalidGoogle, AdminSessionCookies.NAME));
        assertFalse(invalidGoogle.getBody().contains("unknown-google-token"));

        identities.accept("other-admin", "not-allowlisted", "other@example.com", true);
        ResponseEntity<String> denied = post(
                "/api/v1/admin/session",
                "{\"idToken\":\"other-admin\"}",
                csrf,
                csrf,
                true
        );
        assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
        assertEquals("ADMIN_NOT_ALLOWED", parse(denied).get("errorCode"));
        assertFalse(denied.getBody().contains("not-allowlisted"));
        assertNull(setCookieHeader(denied, AdminSessionCookies.NAME));
        assertEquals(before, activeAdminSessions());
    }

    @Test
    void adminCookieAndMemberBearerStayIsolated() {
        clock.set(START);
        String csrf = csrfValue();
        String admin = sessionOf(login("admin-token", "admin-subject", null));
        String member = memberToken("member-subject");

        ResponseEntity<String> adminRead = get("/api/v1/admin/founder/applications", admin, csrf);
        assertEquals(HttpStatus.OK, adminRead.getStatusCode(), adminRead.getBody());
        assertEquals("[]", adminRead.getBody());

        ResponseEntity<String> bearerOnAdmin = get("/api/v1/admin/founder/applications", null, null);
        ResponseEntity<String> bearerAttempt = client().get()
                .uri("/api/v1/admin/founder/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + member)
                .exchange(this::capture);
        assertEquals(HttpStatus.UNAUTHORIZED, bearerAttempt.getStatusCode());
        assertEquals("UNAUTHENTICATED", parse(bearerAttempt).get("errorCode"));
        assertFalse(bearerAttempt.getHeaders().containsHeader(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals(HttpStatus.UNAUTHORIZED, bearerOnAdmin.getStatusCode());

        ResponseEntity<String> cookieOnMe = client().get()
                .uri("/api/v1/me")
                .header(HttpHeaders.COOKIE, AdminSessionCookies.NAME + "=" + admin)
                .exchange(this::capture);
        assertEquals(HttpStatus.UNAUTHORIZED, cookieOnMe.getStatusCode());
        assertEquals("Bearer", cookieOnMe.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));

        ResponseEntity<String> adminBearerOnMe = client().get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .exchange(this::capture);
        assertEquals(HttpStatus.UNAUTHORIZED, adminBearerOnMe.getStatusCode());

        ResponseEntity<String> malformed = client().get()
                .uri("/api/v1/admin/founder/applications")
                .header(HttpHeaders.COOKIE, AdminSessionCookies.NAME + "=not-a-session")
                .exchange(this::capture);
        assertEquals(HttpStatus.UNAUTHORIZED, malformed.getStatusCode());

        ResponseEntity<String> unknown = client().get()
                .uri("/api/v1/admin/founder/applications")
                .header(HttpHeaders.COOKIE, AdminSessionCookies.NAME + "=" + OpaqueTokenGenerator.generate())
                .exchange(this::capture);
        assertEquals(HttpStatus.UNAUTHORIZED, unknown.getStatusCode());
        assertFalse(unknown.getBody().contains("token"));
    }

    @Test
    void stateChangingAdminRequestsRequireCsrfAndMemberRequestsDoNot() {
        clock.set(START);
        String csrf = csrfValue();
        String admin = sessionOf(login("admin-token", "admin-subject", null));
        String member = memberToken("csrf-member");

        ResponseEntity<String> missing = deleteSession(admin, csrf, null);
        assertEquals(HttpStatus.FORBIDDEN, missing.getStatusCode());
        assertEquals("FORBIDDEN", parse(missing).get("errorCode"));
        assertEquals(1, activeAdminSessions());

        ResponseEntity<String> invalid = deleteSession(admin, csrf, "wrong-csrf-token");
        assertEquals(HttpStatus.FORBIDDEN, invalid.getStatusCode());
        assertFalse(invalid.getBody().contains(admin));
        assertFalse(invalid.getBody().contains("wrong-csrf-token"));
        assertEquals(1, activeAdminSessions());

        ResponseEntity<String> enrollment = post(
                "/api/v1/founder/enrollment",
                "{}",
                null,
                null,
                false,
                member
        );
        assertEquals(HttpStatus.OK, enrollment.getStatusCode(), enrollment.getBody());
        ResponseEntity<String> logoutMember = client().delete()
                .uri("/api/v1/auth/session")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + member)
                .exchange(this::capture);
        assertEquals(HttpStatus.NO_CONTENT, logoutMember.getStatusCode(), logoutMember.getBody());
    }

    @Test
    void logoutRevokesTheServerSessionAndClearsTheCookie() {
        clock.set(START);
        String csrf = csrfValue();
        String admin = sessionOf(login("admin-token", "admin-subject", null));
        ResponseEntity<String> logout = deleteSession(admin, csrf, csrf);
        assertEquals(HttpStatus.NO_CONTENT, logout.getStatusCode(), logout.getBody());
        String cleared = setCookieHeader(logout, AdminSessionCookies.NAME);
        assertNotNull(cleared);
        assertTrue(cleared.contains("Max-Age=0"));
        assertSessionCookie(cleared, false);
        assertEquals(0, activeAdminSessions());

        ResponseEntity<String> again = get("/api/v1/admin/founder/applications", admin, csrf);
        assertEquals(HttpStatus.UNAUTHORIZED, again.getStatusCode());
        ResponseEntity<String> repeated = deleteSession(admin, csrf, csrf);
        assertEquals(HttpStatus.UNAUTHORIZED, repeated.getStatusCode());
        assertEquals(0, activeAdminSessions());
    }

    @Test
    void expiredAndRevokedSessionsAreRejected() {
        clock.set(START);
        String csrf = csrfValue();
        String admin = sessionOf(login("admin-token", "admin-subject", null));
        jdbc.update("update admin_session set revoked_at = created_at");
        ResponseEntity<String> revoked = get("/api/v1/admin/founder/applications", admin, csrf);
        assertEquals(HttpStatus.UNAUTHORIZED, revoked.getStatusCode());

        jdbc.update("update admin_session set revoked_at = null");
        clock.set(START.plusSeconds(7201));
        ResponseEntity<String> expired = get("/api/v1/admin/founder/applications", admin, csrf);
        assertEquals(HttpStatus.UNAUTHORIZED, expired.getStatusCode());
        assertFalse(expired.getBody().contains(admin));
    }

    @Test
    void aSecondLoginReplacesThePresentedSession() {
        clock.set(START);
        String first = sessionOf(login("admin-token", "admin-subject", null));
        String csrf = csrfValue();
        ResponseEntity<String> secondLogin = login("admin-token", "admin-subject", first);
        String second = sessionOf(secondLogin);
        assertNotEquals(first, second);
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/admin/founder/applications", first, csrf).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/v1/admin/founder/applications", second, csrf).getStatusCode());
        assertEquals(1, activeAdminSessions());
    }

    private String memberToken(String subject) {
        identities.accept(subject, subject, subject + "@example.com", true);
        ResponseEntity<String> response = post(
                "/api/v1/auth/google",
                "{\"idToken\":\"" + subject + "\"}",
                null,
                null,
                false
        );
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        Map<String, Object> body = parse(response);
        assertEquals("Bearer", body.get("tokenType"));
        return String.valueOf(body.get("accessToken"));
    }

    private ResponseEntity<String> login(String idToken, String subject, String presentedSession) {
        identities.accept(idToken, subject, subject + "@example.com", true);
        String csrf = csrfValue();
        return post(
                "/api/v1/admin/session",
                "{\"idToken\":\"" + idToken + "\"}",
                presentedSession == null ? csrf : csrf + "; " + AdminSessionCookies.NAME + "=" + presentedSession,
                csrf,
                true
        );
    }

    private String csrfValue() {
        return setCookieValue(setCookieHeader(csrf(), "XSRF-TOKEN"));
    }

    private ResponseEntity<String> csrf() {
        ResponseEntity<String> response = exchange(client().get().uri("/api/v1/admin/csrf"));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());
        return response;
    }

    private ResponseEntity<String> get(String path, String session, String csrf) {
        RestClient.RequestHeadersSpec<?> request = client().get().uri(path);
        if (session != null) {
            request = request.header(HttpHeaders.COOKIE, cookieHeader(session, csrf));
        }
        return exchange(request);
    }

    private ResponseEntity<String> deleteSession(String session, String csrfCookie, String csrfHeader) {
        RestClient.RequestHeadersSpec<?> request = client().delete()
                .uri("/api/v1/admin/session")
                .header(HttpHeaders.COOKIE, cookieHeader(session, csrfCookie));
        if (csrfHeader != null) {
            request = request.header("X-XSRF-TOKEN", csrfHeader);
        }
        return exchange(request);
    }

    private ResponseEntity<String> post(
            String path,
            String json,
            String cookie,
            String csrfHeader,
            boolean sendCsrfHeader
    ) {
        return post(path, json, cookie, csrfHeader, sendCsrfHeader, null);
    }

    private ResponseEntity<String> post(
            String path,
            String json,
            String cookie,
            String csrfHeader,
            boolean sendCsrfHeader,
            String bearer
    ) {
        RestClient.RequestBodySpec request = client().post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            request = request.header(HttpHeaders.COOKIE, cookie.startsWith("XSRF-TOKEN=") ? cookie : "XSRF-TOKEN=" + cookie);
        }
        if (sendCsrfHeader) {
            request = request.header("X-XSRF-TOKEN", csrfHeader);
        }
        if (bearer != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return exchange(request.body(json));
    }

    private static String cookieHeader(String session, String csrf) {
        return "XSRF-TOKEN=" + csrf + "; " + AdminSessionCookies.NAME + "=" + session;
    }

    private static String sessionOf(ResponseEntity<String> response) {
        String token = setCookieValue(setCookieHeader(response, AdminSessionCookies.NAME));
        assertNotNull(token);
        return token;
    }

    private int activeAdminSessions() {
        return jdbc.queryForObject(
                "select count(*) from admin_session where revoked_at is null",
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

    private static void assertSessionCookie(String header, boolean secure) {
        assertNotNull(header);
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
        assertTrue(header.contains("Path=/api/v1/admin"));
        assertFalse(header.toLowerCase().contains("domain="));
        assertEquals(secure, header.contains("Secure"));
    }

    private static void assertCsrfCookie(String header, boolean secure) {
        assertNotNull(header);
        assertFalse(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
        assertTrue(header.contains("Path=/;") || header.endsWith("Path=/"));
        assertFalse(header.contains("Path=/api"));
        assertFalse(header.toLowerCase().contains("domain="));
        assertEquals(secure, header.contains("Secure"));
    }

    private static void assertBodyAndNonCookieHeadersDoNotContain(ResponseEntity<String> response, String secret) {
        if (response.getBody() != null) {
            assertFalse(response.getBody().contains(secret));
        }
        response.getHeaders().forEach((name, values) -> {
            if (!HttpHeaders.SET_COOKIE.equalsIgnoreCase(name)) {
                for (String value : values) {
                    assertFalse(value.contains(secret));
                }
            }
        });
    }

    private static String setCookieHeader(ResponseEntity<?> response, String name) {
        List<String> headers = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (headers == null) {
            return null;
        }
        String prefix = name + "=";
        for (String header : headers) {
            if (header.startsWith(prefix)) {
                return header;
            }
        }
        return null;
    }

    private static String setCookieValue(String header) {
        assertNotNull(header);
        int start = header.indexOf('=');
        int end = header.indexOf(';');
        return end < 0 ? header.substring(start + 1) : header.substring(start + 1, end);
    }

    private static Map<String, Object> parse(ResponseEntity<String> response) {
        return new BasicJsonParser().parseMap(response.getBody());
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        AdjustableClock adjustableClock() {
            return new AdjustableClock(START);
        }
    }
}
