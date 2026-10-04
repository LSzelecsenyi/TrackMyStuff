package eu.strictworkout.founder;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration
public class FastFounderRulesConfig {

    static final Instant START = Instant.parse("2026-06-01T00:00:00Z");

    @Bean
    @Primary
    FounderRules fastFounderRules() {
        return new FounderRules(1, 2, 1, 45, true, true);
    }

    @Bean
    @Primary
    AdjustableClock adjustableClock() {
        return new AdjustableClock(START);
    }
}
