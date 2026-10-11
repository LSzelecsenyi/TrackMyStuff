package eu.strictworkout.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCorsConfigurationTest {

    @Test
    void emptyAllowlistStaysEmpty() {
        assertTrue(ApiCorsConfiguration.parseAllowedOrigins("").isEmpty());
        assertTrue(ApiCorsConfiguration.parseAllowedOrigins("  ").isEmpty());
        assertTrue(ApiCorsConfiguration.parseAllowedOrigins(null).isEmpty());
    }

    @Test
    void httpsOriginsAreTrimmed() {
        assertEquals(
                java.util.List.of("https://admin.strictworkout.eu", "https://admin-dev.strictworkout.eu"),
                ApiCorsConfiguration.parseAllowedOrigins(
                        " https://admin.strictworkout.eu, https://admin-dev.strictworkout.eu ")
        );
    }

    @Test
    void wildcardAndCleartextAreRejected() {
        assertThrows(IllegalStateException.class, () -> ApiCorsConfiguration.parseAllowedOrigins("*"));
        assertThrows(IllegalStateException.class, () -> ApiCorsConfiguration.parseAllowedOrigins("https://*"));
        assertThrows(IllegalStateException.class, () -> ApiCorsConfiguration.parseAllowedOrigins("http://admin.strictworkout.eu"));
    }
}
