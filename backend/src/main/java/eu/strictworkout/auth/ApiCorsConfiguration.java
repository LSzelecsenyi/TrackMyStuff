package eu.strictworkout.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Browser clients on another origin. The admin UI is same-origin through nginx,
 * so an empty allowlist is the normal local setting. Native Android calls do not use CORS.
 */
@Configuration
class ApiCorsConfiguration {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${strict.cors.allowed-origins:}") String allowedOriginsRaw
    ) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowedOrigins = parseAllowedOrigins(allowedOriginsRaw);
        if (allowedOrigins.isEmpty()) {
            return source;
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-XSRF-TOKEN"
        ));
        cors.setAllowCredentials(true);
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    static List<String> parseAllowedOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .peek(ApiCorsConfiguration::requireHttpsOrigin)
                .toList();
    }

    private static void requireHttpsOrigin(String origin) {
        if (origin.contains("*") || !origin.startsWith("https://") || origin.length() <= "https://".length()) {
            throw new IllegalStateException(
                    "strict.cors.allowed-origins must be a comma-separated list of https origins");
        }
    }
}
