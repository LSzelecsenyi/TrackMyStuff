package eu.strictworkout.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "strict.google")
public record GoogleProperties(String clientId) {
}
