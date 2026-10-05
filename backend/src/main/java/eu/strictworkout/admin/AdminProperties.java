package eu.strictworkout.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "strict.admin")
public record AdminProperties(Duration sessionLifetime, String googleSubjects, Boolean cookieSecure) {

    public AdminProperties {
        if (sessionLifetime == null
                || sessionLifetime.isZero()
                || sessionLifetime.isNegative()
                || sessionLifetime.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("strict.admin.session-lifetime must be positive and at most 30 days");
        }
        if (googleSubjects == null) {
            googleSubjects = "";
        }
    }

    /**
     * Production always sets Secure. Anywhere else, an omitted value is Secure
     * and only an explicit false allows HTTP development.
     */
    public boolean secureCookie(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            return true;
        }
        return cookieSecure == null || cookieSecure;
    }

    public Set<String> allowedSubjects() {
        return Arrays.stream(googleSubjects.split(","))
                .map(String::trim)
                .filter(subject -> !subject.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
