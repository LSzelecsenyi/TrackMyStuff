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
    void eachPromotionIsReadIndependentlyOfFounderEnrollment() {
        try {
            jdbc.update("update founder_program_capacity set lifecycle = 'ACTIVE' where id = 1");
            jdbc.update("update promotional_program set lifecycle = 'INACTIVE'");
            Map<String, Object> closed = availability();
            assertEquals(true, flag(closed, "founderProgramActive"));
            assertEquals(false, flag(closed, "promotionsEnabled"));
            assertEquals(false, flag(closed, "proDiscoveryEnabled"));
            assertEquals(false, flag(closed, "welcomeBackEnabled"));

            jdbc.update("update founder_program_capacity set lifecycle = 'RETIRED' where id = 1");
            jdbc.update("update promotional_program set lifecycle = 'ACTIVE' where program = 'PRO_DISCOVERY'");
            Map<String, Object> discoveryOnly = availability();
            assertEquals(false, flag(discoveryOnly, "founderProgramActive"));
            assertEquals(true, flag(discoveryOnly, "proDiscoveryEnabled"));
            assertEquals(false, flag(discoveryOnly, "welcomeBackEnabled"));
            assertEquals(false, flag(discoveryOnly, "promotionsEnabled"));

            jdbc.update("update promotional_program set lifecycle = 'ACTIVE' where program = 'WELCOME_BACK'");
            Map<String, Object> both = availability();
            assertEquals(true, flag(both, "proDiscoveryEnabled"));
            assertEquals(true, flag(both, "welcomeBackEnabled"));
            assertEquals(true, flag(both, "promotionsEnabled"));
            assertEquals(false, flag(both, "founderProgramActive"));
        } finally {
            jdbc.update("update founder_program_capacity set lifecycle = 'ACTIVE' where id = 1");
            jdbc.update("update promotional_program set lifecycle = 'INACTIVE'");
        }
    }

    private Map<String, Object> availability() {
        var response = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/v1/promotions/availability")
                .retrieve()
                .toEntity(String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return new BasicJsonParser().parseMap(response.getBody());
    }

    private static boolean flag(Map<String, Object> body, String key) {
        return Boolean.parseBoolean(String.valueOf(body.get(key)));
    }
}
