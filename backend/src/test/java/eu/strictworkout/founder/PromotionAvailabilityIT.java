package eu.strictworkout.founder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PromotionAvailabilityIT {

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
    private JdbcTemplate jdbc;

    @AfterAll
    static void restoreJvmZone() {
        TimeZone.setDefault(ORIGINAL_ZONE);
    }

    @Test
    void unauthenticatedReadFollowsFounderEnrollmentAndEnablesPromotionsOnlyWhenItIsClosed() {
        Boolean original = jdbc.queryForObject(
                "SELECT enrollment_open FROM founder_program_capacity WHERE id = 1",
                Boolean.class
        );
        try {
            assertAvailability(Boolean.TRUE.equals(original));
            jdbc.update("UPDATE founder_program_capacity SET enrollment_open = ? WHERE id = 1", !Boolean.TRUE.equals(original));
            assertAvailability(!Boolean.TRUE.equals(original));
        } finally {
            jdbc.update(
                    "UPDATE founder_program_capacity SET enrollment_open = ? WHERE id = 1",
                    Boolean.TRUE.equals(original)
            );
        }
    }

    private void assertAvailability(boolean founderProgramActive) {
        var response = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/v1/promotions/availability")
                .retrieve()
                .toEntity(String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = new BasicJsonParser().parseMap(response.getBody());
        assertEquals(founderProgramActive, Boolean.valueOf(String.valueOf(body.get("founderProgramActive"))));
        assertEquals(!founderProgramActive, Boolean.valueOf(String.valueOf(body.get("promotionsEnabled"))));
    }
}
