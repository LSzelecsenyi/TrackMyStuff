package eu.strictworkout.admin;

import eu.strictworkout.identity.ScriptedIdentityVerifier;
import eu.strictworkout.identity.ScriptedIdentityVerifierConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=prod",
                "strict.admin.cookie-secure=false",
                "strict.admin.google-subjects=admin-subject",
                "strict.founder.rules=production"
        }
)
@Import(ScriptedIdentityVerifierConfig.class)
@Testcontainers
class AdminSessionCookieProdIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void productionPlaceholders(DynamicPropertyRegistry registry) {
        registry.add("SPRING_DATASOURCE_URL", postgres::getJdbcUrl);
        registry.add("SPRING_DATASOURCE_USERNAME", postgres::getUsername);
        registry.add("SPRING_DATASOURCE_PASSWORD", postgres::getPassword);
        registry.add("STRICT_GOOGLE_CLIENT_ID", () -> "prod-admin-cookie-test");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ScriptedIdentityVerifier identities;

    @Test
    void productionEmitsASecureAdminCookieWhenThePropertyRequestsOtherwise() {
        identities.accept("admin-token", "admin-subject", "admin@example.com", true);
        ResponseEntity<String> csrf = exchange(client().get().uri("/api/v1/admin/csrf"));
        assertEquals(HttpStatus.NO_CONTENT, csrf.getStatusCode(), csrf.getBody());
        String csrfHeader = setCookieHeader(csrf, "XSRF-TOKEN");
        String csrfToken = cookieValue(csrfHeader);
        assertTrue(csrfHeader.contains("Secure"));
        assertFalse(csrfHeader.contains("HttpOnly"));
        assertFalse(csrfHeader.toLowerCase().contains("domain="));

        ResponseEntity<String> login = exchange(client().post()
                .uri("/api/v1/admin/session")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .body("{\"idToken\":\"admin-token\"}"));
        assertEquals(HttpStatus.NO_CONTENT, login.getStatusCode(), login.getBody());
        String session = setCookieHeader(login, AdminSessionCookies.NAME);
        assertNotNull(session);
        assertTrue(session.contains("Secure"));
        assertTrue(session.contains("HttpOnly"));
        assertTrue(session.contains("SameSite=Strict"));
        assertTrue(session.contains("Path=/api/v1/admin"));
        assertFalse(session.toLowerCase().contains("domain="));
        String token = cookieValue(session);
        assertTrue(login.getBody() == null || !login.getBody().contains(token));
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

    private static String setCookieHeader(ResponseEntity<?> response, String name) {
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
}
