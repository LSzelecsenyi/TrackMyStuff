package eu.strictworkout;

import eu.strictworkout.founder.FounderRules;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StrictFoundationIT {

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
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FounderRules founderRules;

    @AfterAll
    static void restoreJvmZone() {
        TimeZone.setDefault(ORIGINAL_ZONE);
    }

    @Test
    void contextStartsAndFlywayAppliesTheBaselineOnly() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version = '1'",
                Integer.class
        );
        assertEquals(1, applied);

        List<String> tables = jdbc.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """,
                String.class
        );
        assertEquals(List.of(
                "admin_session",
                "admin_user",
                "app_user",
                "auth_session",
                "entitlement_grant",
                "external_identity",
                "flyway_schema_history",
                "founder_application",
                "founder_review_decision",
                "founder_review_snapshot",
                "founder_workout_event"
        ), tables);
        Integer founderMigration = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version = '3'",
                Integer.class
        );
        assertEquals(1, founderMigration);
        Integer reviewMigration = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version = '4'",
                Integer.class
        );
        assertEquals(1, reviewMigration);
        Integer baseline = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version = '2'",
                Integer.class
        );
        assertEquals(1, baseline);
    }

    @Test
    void productionFounderRulesStayAtThePublishedThresholds() {
        assertEquals(FounderRules.PRODUCTION, founderRules);
    }

    @Test
    void healthIsUnauthenticatedAndReturnsTheContract() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/health")
                .exchange((request, httpResponse) -> {
                    String body = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    return ResponseEntity.status(httpResponse.getStatusCode())
                            .headers(httpResponse.getHeaders())
                            .body(body);
                });

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getHeaders().getLocation());
        assertTrue(response.getHeaders().getOrEmpty("WWW-Authenticate").isEmpty());
        assertTrue(response.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON));

        Map<String, Object> json = new BasicJsonParser().parseMap(response.getBody());
        assertEquals(Set.of("status"), json.keySet());
        assertEquals("UP", json.get("status"));
        assertEquals("{\"status\":\"UP\"}", response.getBody());
    }

    @Test
    void unknownApiPathWithoutAuthenticationIsRejected() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/missing")
                .exchange((request, httpResponse) -> {
                    String body = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    return ResponseEntity.status(httpResponse.getStatusCode())
                            .headers(httpResponse.getHeaders())
                            .body(body);
                });

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<String, Object> json = new BasicJsonParser().parseMap(response.getBody());
        assertEquals(Set.of("errorCode", "message"), json.keySet());
        assertEquals("UNAUTHENTICATED", json.get("errorCode"));
        assertEquals("Authentication is required.", json.get("message"));
        String body = response.getBody().toLowerCase();
        assertFalse(body.contains("exception"));
        assertFalse(body.contains("stack"));
        assertFalse(body.contains("timezone"));
        assertFalse(body.contains("password"));
    }

    @Test
    void timestamptzRoundTripDoesNotFollowTheJvmZone() throws Exception {
        assertEquals("Pacific/Kiritimati", TimeZone.getDefault().getID());
        Instant written = Instant.parse("2026-06-15T23:45:30Z");

        try (Connection connection = dataSource.getConnection()) {
            String sessionZone = queryString(connection, "SHOW TIME ZONE");
            assertTrue(
                    sessionZone.equals("UTC") || sessionZone.equals("Etc/UTC") || sessionZone.equals("GMT"),
                    "session TimeZone was " + sessionZone
            );

            try (PreparedStatement insert = connection.prepareStatement(
                    "CREATE TEMP TABLE tz_probe (occurred_at TIMESTAMPTZ NOT NULL)"
            )) {
                insert.execute();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO tz_probe (occurred_at) VALUES (?)"
            )) {
                insert.setObject(1, OffsetDateTime.ofInstant(written, ZoneOffset.UTC));
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT occurred_at FROM tz_probe"
            );
                 ResultSet rows = select.executeQuery()) {
                assertTrue(rows.next());
                OffsetDateTime read = rows.getObject(1, OffsetDateTime.class);
                assertEquals(written, read.toInstant());
            }
        }
    }

    private RestClient client() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    private static String queryString(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }
}
