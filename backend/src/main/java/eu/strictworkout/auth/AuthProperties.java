package eu.strictworkout.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "strict.auth")
public record AuthProperties(Duration sessionLifetime) {

    public AuthProperties {
        if (sessionLifetime == null
                || sessionLifetime.isZero()
                || sessionLifetime.isNegative()
                || sessionLifetime.compareTo(Duration.ofDays(365)) > 0) {
            throw new IllegalArgumentException("strict.auth.session-lifetime must be positive and at most 365 days");
        }
    }
}
