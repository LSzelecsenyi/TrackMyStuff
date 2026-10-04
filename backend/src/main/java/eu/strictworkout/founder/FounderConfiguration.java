package eu.strictworkout.founder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
class FounderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FounderConfiguration.class);

    @Bean
    FounderRules founderRules(Environment environment) {
        return FounderRulesSelection.select(
                environment.getProperty("strict.founder.rules", "production"),
                environment.acceptsProfiles(Profiles.of("prod"))
        );
    }

    @Bean
    ApplicationRunner founderRulesLog(FounderRules rules) {
        return args -> log.info(
                "Founder rules active: temporaryProWorkouts={}, founderWorkouts={}, distinctDays={}, windowDays={}",
                rules.temporaryProWorkoutCount(),
                rules.founderWorkoutCount(),
                rules.requiredDistinctWorkoutDays(),
                rules.qualificationWindowDays()
        );
    }

    @Bean
    FounderStateMachine founderStateMachine() {
        return new FounderStateMachine();
    }
}
