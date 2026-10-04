package eu.strictworkout.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "strict.admin")
public record AdminProperties(Duration sessionLifetime, String googleSubjects) {

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

    public Set<String> allowedSubjects() {
        return Arrays.stream(googleSubjects.split(","))
                .map(String::trim)
                .filter(subject -> !subject.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
