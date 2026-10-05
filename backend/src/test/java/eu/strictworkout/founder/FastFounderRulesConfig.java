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
    FounderRulesBinding fastFounderRulesBinding() {
        return new FounderRulesBinding("fast", new FounderRules(1, 2, 1, 45, true, true));
    }

    @Bean
    @Primary
    FounderRules fastFounderRules(FounderRulesBinding binding) {
        return binding.rules();
    }

    @Bean
    @Primary
    AdjustableClock adjustableClock() {
        return new AdjustableClock(START);
    }
}
