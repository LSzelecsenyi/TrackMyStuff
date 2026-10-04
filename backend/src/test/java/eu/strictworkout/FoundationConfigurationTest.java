package eu.strictworkout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationConfigurationTest {

    @Test
    void configurationDoesNotDependOnBudapestOrPuff() throws IOException {
        String local = resource("application.yml");
        String prod = resource("application-prod.yml");

        assertFalse(local.contains("Europe/Budapest"));
        assertFalse(local.contains("Budapest"));
        assertFalse(local.contains("user.timezone"));
        assertFalse(local.contains("puff"));
        assertTrue(local.contains("SET TIME ZONE 'UTC'"));
        assertTrue(local.contains("hibernate.jdbc.time_zone: UTC"));
        assertTrue(local.contains("time-zone: UTC"));
        assertTrue(local.contains("ddl-auto: validate"));

        assertFalse(prod.contains("localhost"));
        assertFalse(prod.contains("127.0.0.1"));
        assertFalse(prod.contains("Europe/Budapest"));
        assertFalse(prod.contains("puff"));
        assertTrue(prod.contains("${SPRING_DATASOURCE_URL}"));
        assertTrue(prod.contains("${SPRING_DATASOURCE_USERNAME}"));
        assertTrue(prod.contains("${SPRING_DATASOURCE_PASSWORD}"));
        assertFalse(prod.contains("${SPRING_DATASOURCE_URL:"));
        assertFalse(prod.contains("${SPRING_DATASOURCE_USERNAME:"));
        assertFalse(prod.contains("${SPRING_DATASOURCE_PASSWORD:"));
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = FoundationConfigurationTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
